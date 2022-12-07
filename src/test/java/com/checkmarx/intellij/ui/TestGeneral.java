package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.tool.window.GroupBy;
import com.checkmarx.intellij.tool.window.ResultState;
import com.checkmarx.intellij.tool.window.Severity;
import com.intellij.remoterobot.fixtures.*;
import com.intellij.remoterobot.utils.Keyboard;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.checkmarx.intellij.ui.Xpath.*;

public class TestGeneral extends BaseUITest {

    @BeforeEach
    public void checkResults() {
        getResults();
    }

    @Test
    @Video
    public void testEndToEnd() {
        checkResultsPanel();
    }

    @Test
    @Video
    public void testFilters() {
        waitForScanIdSelection();

        // disable all severities and check for empty tree
        Arrays.stream(Severity.values()).forEach(severity -> toggleFilter(severity, false));
        navigate("Scan", 1);

        // enable all severities and check for at least 1 result
        Arrays.stream(Severity.values()).forEach(severity -> toggleFilter(severity, true));
        navigate("Scan", 2);
    }

    @Test
    @Video
    public void testInvalidScanId() {
        waitFor(() -> {
            click(SCAN_FIELD);
            return find(JTextFieldFixture.class, SCAN_FIELD).getHasFocus();
        });
        find(JTextFieldFixture.class, SCAN_FIELD).setText("inva-lid");
        waitFor(() -> {
            click(SCAN_FIELD);
            if (!find(JTextFieldFixture.class, SCAN_FIELD).getHasFocus()) {
                return false;
            }
            new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
            List<ComponentFixture> trees = findAll(TREE);
            if (trees.size() != 1) {
                return false;
            }
            ComponentFixture tree = trees.get(0);
            return tree.getData().getAll().size() == 1
                    && tree.getData()
                    .getAll()
                    .get(0)
                    .getText()
                    .contains(Bundle.message(
                            Resource.INVALID_SCAN_ID));
        });
    }

    @Test
    @Video
    public void testSelection() {
        clearSelection();
        testSelectionAction(projectCombobox, "Project", Environment.PROJECT_NAME);
        testSelectionAction(branchCombobox, "Branch", Environment.BRANCH_NAME);
        findLatestScanSelection();

        testSelectionAction(scanCombobox, "Scan", Environment.SCAN_ID);
        waitFor(() -> findAll(TREE).size() == 1 && checkTreeState(findAll(TREE).get(0)));
    }

    @Test
    @Video
    public void testClearSelection() {
        testSelection();
        clearSelection();
    }

    private void findLatestScanSelection() {
        @Language("XPath") String xpath = String.format(
                "//div[@class='ActionButtonWithText' and substring(@visible_text, string-length(@visible_text) - string-length('%s') + 1)  = '%s']",
                Utils.formatLatest(true), Utils.formatLatest(true));
        waitFor(() -> hasAnyComponent(xpath));
    }

    private boolean checkTreeState(ComponentFixture tree) {
        return tree.getData().getAll().size() > 0 && !tree.getData()
                .getAll()
                .get(0)
                .getText()
                .contains(Bundle.message(Resource.GETTING_RESULTS_ERROR));
    }

    private void toggleFilter(Severity severity, boolean enabled) {
        @Language("XPath") String xpath = filterXPath(severity);
        waitFor(() -> {
            click(xpath);
            if (!hasAnyComponent(xpath)) {
                return false;
            }

            ActionButtonFixture filter = find(ActionButtonFixture.class, xpath);
            log(filter.popState().name());
            return filter.popState().equals(enabled
                    ? ActionButtonFixture.PopState.PUSHED
                    : ActionButtonFixture.PopState.POPPED);
        });
    }

    private void checkResultsPanel() {
        waitForScanIdSelection();
        // navigate the tree for a result
        expand();
        collapse();
        severity();

        // enable and disable state
        state();
        state();
        file();
        file();

        vulnerabilityType();
        urgent();

        // enable all severities and check for at least 1 result
        Arrays.stream(Severity.values()).forEach(severity -> toggleFilter(severity, true));

        navigate("Scan", 2);
        navigate("sast", 4);
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        int row = -1;
        for (int i = 0; i < tree.collectRows().size(); i++) {
            if (tree.getValueAtRow(i).contains("dsvw.py")) {
                row = i;
                break;
            }
        }
        // open first node of the opened result
        final int resultRow = row;
        Assertions.assertTrue(resultRow > 1); // at least scan (0) and sast (1)
        waitFor(() -> {
            tree.clickRow(resultRow);
            return findAll(LINK_LABEL).size() > 0;
        });

        String commentUUID = UUID.randomUUID().toString();

        waitFor(() -> {
            if (findAll(SEVERITY_COMBOBOX_ARROW).size() < 1) {
                return false;
            }
            find(SEVERITY_COMBOBOX_ARROW).click();

            List<JListFixture> lists = findAll(JListFixture.class, JLIST);
            if (lists.size() < 1) {
                return false;
            }
            JListFixture list = lists.get(0);
            if (list.isShowing()) {
                try {
                    list.clickItem("LOW", true);
                } catch (Throwable ice) {
                    return false;
                }
            }
            return findAll(TRIAGE_LOW).size() > 0;
        });

        waitFor(() -> {
            if (findAll(STATE_COMBOBOX_ARROW).size() < 1) {
                return false;
            }
            find(STATE_COMBOBOX_ARROW).click();

            if (findAll(JLIST).size() < 1) {
                return false;
            }
            JListFixture list = find(JListFixture.class, JLIST);
            if (list.isShowing()) {
                try {
                    list.clickItem("CONFIRMED", true);
                } catch (Throwable ice) {
                    return false;
                }
            }
            return findAll(TRIAGE_CONFIRMED).size() > 0;
        });

        JTextFieldFixture commentField = find(JTextFieldFixture.class, TRIAGE_COMMENT);
        commentField.setText(commentUUID);

        waitFor(() -> {
            find(JButtonFixture.class, UPDATE_BTN).click();
            return !find(JButtonFixture.class, UPDATE_BTN).isEnabled();
        });

        waitFor(() -> find(JButtonFixture.class, UPDATE_BTN).isEnabled());

        waitFor(() -> {
            find(TAB_CHANGES).click();
            @Language("XPath") String fieldXpath = String.format(CHANGES_COMMENT, commentUUID, commentUUID);

            find(TAB_CHANGES_CONTENT).isShowing();
            return findAll(fieldXpath).size() > 0;
        });

        testFileNavigation();

        waitFor(() -> {
            find(TAB_LEARN_MORE).click();
            return findAll(TAB_RISK).size() > 0 && findAll(CAUSE).size() > 0 && findAll(TAB_RECOMMENDATIONS).size() > 0;
        });

        waitFor(() -> {
            find(TAB_RECOMMENDATIONS_EXAMPLES).click();
            return find(TAB_RECOMMENDATIONS_EXAMPLES).isShowing();
        });
    }

    private void collapse() {
        waitFor(() -> {
            click(COLLAPSE_ACTION);
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void vulnerabilityType() {
        groupAction("Vulnerability");
    }

    private void severity() {
        groupAction("Severity");
    }

    private void state() {
        groupAction("State");
    }

    private void file() {
        groupAction("File");
    }

    private void urgent() {
        openFilterBy();
        waitFor(() -> {
            enter("Urgent");
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void groupAction(String value) {
        openGroupBy();
        waitFor(() -> {
            enter(value);
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void openFilterBy() {
        expand();
        waitFor(() -> {
            click(FILTER_BY_ACTION);
            return findAll(JListFixture.class, MY_LIST).size() == 1
                    && findAll(JListFixture.class, MY_LIST).get(0).findAllText().size()
                    == ResultState.values().length;
        });
    }

    private void openGroupBy() {
        expand();
        waitFor(() -> {
            click(GROUP_BY_ACTION);
            return findAll(JListFixture.class, MY_LIST).size() == 1
                    && findAll(JListFixture.class, MY_LIST).get(0).findAllText().size()
                    == GroupBy.values().length - GroupBy.HIDDEN_GROUPS.size();
        });
    }

    @Language("XPath")
    private static String filterXPath(Severity filter) {
        return String.format("//div[@myicon='%s.svg']", filter.tooltipSupplier().get().toLowerCase());
    }

    private void expand() {
        waitFor(() -> {
            click(EXPAND_ACTION);
            return find(JTreeFixture.class, TREE).findAllText().size() > 1;
        });
    }
}