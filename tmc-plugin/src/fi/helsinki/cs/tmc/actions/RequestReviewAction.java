package fi.helsinki.cs.tmc.actions;

import fi.helsinki.cs.tmc.data.Exercise;
import fi.helsinki.cs.tmc.model.CourseDb;
import fi.helsinki.cs.tmc.model.ProjectMediator;
import fi.helsinki.cs.tmc.model.ServerAccess;
import fi.helsinki.cs.tmc.model.TmcProjectInfo;
import fi.helsinki.cs.tmc.model.TmcSettings;
import fi.helsinki.cs.tmc.ui.ConvenientDialogDisplayer;
import fi.helsinki.cs.tmc.utilities.BgTask;
import fi.helsinki.cs.tmc.utilities.BgTaskListener;
import fi.helsinki.cs.tmc.utilities.CancellableCallable;
import fi.helsinki.cs.tmc.utilities.zip.RecursiveZipper;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;

@ActionID(category="TMC", id="fi.helsinki.cs.tmc.actions.RequestReviewAction")
@ActionRegistration(displayName = "#CTL_RequestReviewAction", lazy = false)
@ActionReferences({
    @ActionReference(path = "Menu/TM&C", position = -5, separatorAfter = 0),
    @ActionReference(path = "Projects/Actions", position = 1350, separatorBefore = 1340, separatorAfter = 1360) // Positioning y u no work?
})
@NbBundle.Messages("CTL_RequestReviewAction=Request code review")
public class RequestReviewAction extends AbstractExerciseSensitiveAction {
    private static final Logger log = Logger.getLogger(RequestReviewAction.class.getName());
    
    private TmcSettings settings;
    private CourseDb courseDb;
    private ProjectMediator projectMediator;
    private ConvenientDialogDisplayer dialogs;

    public RequestReviewAction() {
        this.settings = TmcSettings.getDefault();
        this.courseDb = CourseDb.getInstance();
        this.projectMediator = ProjectMediator.getInstance();
        this.dialogs = ConvenientDialogDisplayer.getDefault();
    }

    @Override
    protected ProjectMediator getProjectMediator() {
        return projectMediator;
    }

    @Override
    protected CourseDb getCourseDb() {
        return courseDb;
    }

    @Override
    boolean enable(Project... projects) {
        if (projects.length > 1) {
            return false; // One at a time please
        } else {
            return super.enable(projects);
        }
    }
    
    @Override
    protected void performAction(Node[] nodes) {
        List<Project> project = projectsFromNodes(nodes);
        if (project.size() == 1) {
            TmcProjectInfo projectInfo = projectMediator.wrapProject(project.get(0));
            Exercise exercise = projectMediator.tryGetExerciseForProject(projectInfo, courseDb);
            if (exercise != null) {
                //TODO TODO: prompt the user for an optional message to the reviewer
                String question = "Request code review for " + exercise.getName() + "?";
                String title = "Confirm code review request";
                if (ConvenientDialogDisplayer.getDefault().askYesNo(question, title)) {
                    requestCodeReviewFor(projectInfo, exercise);
                }
            } else {
                log.log(Level.WARNING, "RequestReviewAction called in a context without a valid TMC project.");
            }
        } else {
            log.log(Level.WARNING, "RequestReviewAction called in a context with {0} projects", project.size());
        }
    }
    
    private void requestCodeReviewFor(final TmcProjectInfo project, final Exercise exercise) {
        projectMediator.saveAllFiles();
        
        final String errorMsgLocale = settings.getErrorMsgLocale().toString();
        
        BgTask.start("Zipping up " + exercise.getName(), new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                RecursiveZipper zipper = new RecursiveZipper(project.getProjectDirAsFile(), project.getZippingDecider());
                return zipper.zipProjectSources();
            }
        }, new BgTaskListener<byte[]>() {
            @Override
            public void bgTaskReady(byte[] zipData) {
                Map<String, String> extraParams = new HashMap<String, String>();
                extraParams.put("error_msg_locale", errorMsgLocale);
                extraParams.put("request_review", "1");
                
                CancellableCallable<URI> submitTask = new ServerAccess().getSubmittingExerciseTask(exercise, zipData, extraParams);
                BgTask.start("Sending " + exercise.getName(), submitTask, new BgTaskListener<URI>() {
                    @Override
                    public void bgTaskReady(URI result) {
                        dialogs.displayMessage("Code submitted for review.\nYou will be notified when an instructor has reviewed your code.");
                    }

                    @Override
                    public void bgTaskCancelled() {
                    }

                    @Override
                    public void bgTaskFailed(Throwable ex) {
                        dialogs.displayError("Failed to submit exercise for code review", ex);
                    }
                });
            }

            @Override
            public void bgTaskCancelled() {
            }

            @Override
            public void bgTaskFailed(Throwable ex) {
                dialogs.displayError("Failed to zip up exercise", ex);
            }
        });
    }

    @Override
    public String getName() {
        return "Request code review";
    }
}
