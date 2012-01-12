package fi.helsinki.cs.tmc.ui;

import fi.helsinki.cs.tmc.data.SubmissionResult;
import fi.helsinki.cs.tmc.data.TestCaseResult;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.StringEscapeUtils;
import org.openide.NotifyDescriptor;
import org.openide.awt.HtmlBrowser;

public class TestResultDisplayer {
    private static TestResultDisplayer instance;
    
    public static TestResultDisplayer getInstance() {
        if (instance == null) {
            instance = new TestResultDisplayer();
        }
        return instance;
    }
    
    private ConvenientDialogDisplayer dialogs;
    
    /*package*/ TestResultDisplayer() {
        this.dialogs = ConvenientDialogDisplayer.getDefault();
    }
    
    public void showSubmissionResult(SubmissionResult result) {
        switch (result.getStatus()) {
            case OK:
                displayTestCases(result.getTestCases(), false);
                displaySuccessfulSubmissionMsg(result);
                break;
            case FAIL:
                displayTestCases(result.getTestCases(), true);
                break;
            case ERROR:
                clearTestCaseView();
                displayError(result.getError());
                break;
        }
    }

    private void displaySuccessfulSubmissionMsg(final SubmissionResult result) {
        JPanel dialog = new JPanel();
        dialog.setLayout(new BoxLayout(dialog, BoxLayout.PAGE_AXIS));
        JLabel label = new JLabel("All tests passed!");
        label.setIcon(dialogs.getSmileyIcon());
        label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        dialog.add(label);
        
        if (result.getSolutionUrl() != null) {
            JButton solutionButton = new JButton(new AbstractAction("View model solution") {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    try {
                        HtmlBrowser.URLDisplayer.getDefault().showURLExternal(new URL(result.getSolutionUrl()));
                    } catch (Exception ex) {
                        dialogs.displayError("Failed to open browser.\n" + ex.getMessage());
                    }
                }
            });
            dialog.add(solutionButton);
        }
        
        dialog.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        dialogs.showDialog(dialog, NotifyDescriptor.PLAIN_MESSAGE, "Success.", true);
    }
    
    /**
     * Shows local results and returns whether a submission should be started.
     */
    public boolean showLocalRunResult(List<TestCaseResult> results) {
        int numFailed = 0;
        for (TestCaseResult result : results) {
            if (!result.isSuccessful()) {
                numFailed += 1;
            }
        }
        
        if (numFailed == 0) {
            displayTestCases(results, false);
            return dialogs.askYesNo("All tests passed. Submit to server?", "Submit?");
        } else {
            displayTestCases(results, true);
            return false;
        }
    }
    
    private void displayError(String error) {
        String htmlError =
                "<html><font face=\"monospaced\" color=\"red\">" +
                preformattedToHtml(error) +
                "</font></html>";
        LongTextDisplayPanel panel = new LongTextDisplayPanel(htmlError);
        dialogs.showDialog(panel, NotifyDescriptor.ERROR_MESSAGE, "", false);
    }
    
    private String preformattedToHtml(String text) {
        // <font> doesn't work around pre, so we do this
        return StringEscapeUtils.escapeHtml4(text)
                .replace(" ", "&nbsp;")
                .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")
                .replace("\n", "<br>");
    }
    
    private void displayTestCases(List<TestCaseResult> testCases, boolean activate) {
        TestResultWindow window = TestResultWindow.get();
        window.setTestCaseResults(testCases);
        if (activate) {
            window.openAtTabPosition(0);
            window.requestActive();
        }
    }
    
    private void clearTestCaseView() {
        TestResultWindow.get().clear();
    }
}