package fi.helsinki.cs.tmc.spyware.eventsources;

import fi.helsinki.cs.tmc.data.Exercise;
import fi.helsinki.cs.tmc.model.CourseDb;
import fi.helsinki.cs.tmc.model.ProjectMediator;
import fi.helsinki.cs.tmc.model.TmcProjectInfo;
import fi.helsinki.cs.tmc.spyware.EventReceiver;
import fi.helsinki.cs.tmc.spyware.LoggableEvent;
import fi.helsinki.cs.tmc.spyware.SpywareSettings;
import fi.helsinki.cs.tmc.utilities.ActiveThreadSet;
import fi.helsinki.cs.tmc.utilities.TmcSwingUtilities;
import fi.helsinki.cs.tmc.utilities.zip.RecursiveZipper;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.filesystems.*;

public class SourceSnapshotEventSource implements FileChangeListener, Closeable {
    private enum ChangeType {
        FILE_CREATE, FOLDER_CREATE, FILE_CHANGE, FILE_DELETE, FILE_RENAME;
    }
    
    private static final Logger log = Logger.getLogger(SourceSnapshotEventSource.class.getName());
    
    private SpywareSettings settings;
    private EventReceiver receiver;
    private ActiveThreadSet snapshotterThreads;
    private boolean closed;

    public SourceSnapshotEventSource(SpywareSettings settings, EventReceiver receiver) {
        this.settings = settings;
        this.receiver = receiver;
        
        this.snapshotterThreads = new ActiveThreadSet();
    }
    
    public void startListeningToFileChanges() {
        FileUtil.addFileChangeListener(this);
    }
    
    /**
     * Waits for all pending events to be sent.
     */
    @Override
    public void close() {
        TmcSwingUtilities.ensureEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    closed = true;
                    FileUtil.removeFileChangeListener(SourceSnapshotEventSource.this);
                    snapshotterThreads.joinAll();
                } catch (InterruptedException ex) {
                }
            }
        });
    }
    
    @Override
    public void fileFolderCreated(FileEvent fe) {
        reactToChange(ChangeType.FOLDER_CREATE, fe.getFile());
    }

    @Override
    public void fileDataCreated(FileEvent fe) {
        reactToChange(ChangeType.FILE_CREATE, fe.getFile());
    }

    @Override
    public void fileChanged(FileEvent fe) {
        reactToChange(ChangeType.FILE_CHANGE, fe.getFile());
    }

    @Override
    public void fileDeleted(FileEvent fe) {
        reactToChange(ChangeType.FILE_DELETE, fe.getFile());
    }

    @Override
    public void fileRenamed(FileRenameEvent fre) {
        reactToRename(ChangeType.FILE_RENAME, fre);
    }

    @Override
    public void fileAttributeChanged(FileAttributeEvent fae) {
    }
    
    private void reactToChange(final ChangeType changeType, final FileObject fileObject) {
        String filePath = getFileObjectPath(fileObject);
        if(filePath == null) {
            return;
        }
        
        String details = String.format("{cause:'%s',file:'%s'}",
                changeType.name().toLowerCase(),
                filePath);
        invokeSnapshotThreadViaEdt(fileObject, details);
    }    
    
    private void reactToRename(final ChangeType changeType, final FileRenameEvent renameEvent) {
        String filePath = getFileObjectPath(renameEvent.getFile());
        if(filePath == null) {
            return;
        }
        
        String details = String.format("{cause:'%s',file:'%s',previous_name:'%s.%s'}",
                changeType.name().toLowerCase(),
                filePath,
                renameEvent.getName(),
                renameEvent.getExt());
        invokeSnapshotThreadViaEdt(renameEvent.getFile(), details);
    }
    
    private String getFileObjectPath(FileObject fileObject) {
        String filePath = fileObject.getPath();
        
        try {
            Project p = FileOwnerQuery.getOwner(fileObject);
            String projectDirectory = p.getProjectDirectory().getPath();
            if (filePath.contains(projectDirectory)) {
                filePath = filePath.substring(filePath.indexOf(projectDirectory) + projectDirectory.length());
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to determine project path for file {0}.", filePath);
        }
        
        return filePath;
    }
    
    // I have no idea what thread FileUtil callbacks are made in,
    // so I'll go to the EDT to safely read the global state.
    private void invokeSnapshotThreadViaEdt(final FileObject fileObject, final String details) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                
                try {
                    startSnapshotThread(fileObject, details);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to start snapshot thread", e);
                }
            }
        });
    }
    
    private void startSnapshotThread(FileObject changedFile, String details) {
        if (!settings.isSpywareEnabled()) {
            return;
        }
        
        log.log(Level.FINE, "Changed file: {0}", changedFile);
        
        ProjectMediator pm = ProjectMediator.getInstance();
        TmcProjectInfo project = pm.tryGetProjectOwningFile(changedFile);
        log.log(Level.FINE, "Project: {0}", project);
        // only log TMC-projects
        if (project != null) {
            CourseDb courseDb = CourseDb.getInstance();
            Exercise exercise = pm.tryGetExerciseForProject(project, courseDb);
            
            if (exercise != null) {
                log.log(Level.FINER, "Exercise: {0}", exercise);
                
                SnapshotThread thread = new SnapshotThread(receiver, exercise, project, details);
                snapshotterThreads.addThread(thread);
                thread.setDaemon(true);
                thread.start();
            }
        }
    }
    
    private static class SnapshotThread extends Thread {
        private final EventReceiver receiver;
        private final Exercise exercise;
        private final TmcProjectInfo projectInfo;
        private final String details;

        private SnapshotThread(EventReceiver receiver, Exercise exercise, TmcProjectInfo projectInfo, String details) {
            super("Source snapshot");
            this.receiver = receiver;
            this.exercise = exercise;
            this.projectInfo = projectInfo;
            this.details = details;
        }

        @Override
        public void run() {
            File projectDir = projectInfo.getProjectDirAsFile();
            RecursiveZipper zipper = new RecursiveZipper(projectDir, projectInfo.getZippingDecider());
            try {
                byte[] data = zipper.zipProjectSources();
                LoggableEvent event = new LoggableEvent(exercise, "code_snapshot", data, details);
                receiver.receiveEvent(event);
            } catch (IOException ex) {
                log.log(Level.WARNING, "Error zipping project sources in: " + projectDir, ex);
            }
        }
    }
}
