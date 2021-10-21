package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.Resource;
import com.intellij.remoterobot.fixtures.*;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.utils.Keyboard;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Supplier;

public class TestUI extends BaseUITest {

    /**
     * Apply valid settings, get results and test the results UI
     */
    @Test
    public void testEndToEnd() {
        applySettings();
        getResults();
        checkResultsPanel();
    }

    @Test
    public void testInvalidAuth() {
        openSettings();

        setFields();
        setField(Constants.FIELD_NAME_API_KEY, "invalid");

        click(VALIDATE_BUTTON);

        waitFor(() -> !hasAnyComponent("//div[@accessiblename.key='VALIDATE_IN_PROGRESS']"));
        Assertions.assertFalse(hasAnyComponent("//div[@accessiblename.key='VALIDATE_SUCCESS']"));
        click("//div[@text.key='button.cancel']");
    }

    @Test
    public void testInvalidScanId() {
        applySettings();
        setInvalidScanId();
    }

    @Test
    public void testSelection() {
        applySettings();
        setInvalidScanId();
        clearSelection();
        testSelectionAction(this::findProjectSelection, "Project", Environment.PROJECT_NAME);
        testSelectionAction(this::findBranchSelection, "Branch", Environment.BRANCH_NAME);
        testSelectionAction(this::findScanSelection, "Scan", Environment.SCAN_ID);
        waitFor(() -> findAll(TREE).size() == 1 && checkTreeState(findAll(TREE).get(0)));
    }

    @Test
    public void testClearSelection() {
        testSelection();
        clearSelection();
    }

    @NotNull
    private ActionButtonFixture findProjectSelection() {
        return findSelection("Project");
    }

    @NotNull
    private ActionButtonFixture findBranchSelection() {
        return findSelection("Branch");
    }

    @NotNull
    private ActionButtonFixture findScanSelection() {
        return findSelection("Scan");
    }

    private void testSelectionAction(Supplier<ActionButtonFixture> selectionSupplier, String prefix, String value) {
        waitFor(() -> {
            ActionButtonFixture selection = selectionSupplier.get();
            return findProjectSelection().isEnabled() && selection.hasText(prefix + ": none");
        });
        waitFor(() -> {
            selectionSupplier.get().click();
            return findAll(JListFixture.class, "//div[@class='MyList']").size() == 1
                   && findAll(JListFixture.class, "//div[@class='MyList']").get(0).findAllText().size() > 0;
        });
        enter(value);
    }

    @NotNull
    private ActionButtonFixture findSelection(String s) {
        return find(ActionButtonFixture.class,
                    String.format("//div[@class='ActionButtonWithText' and starts-with(@visible_text,'%s: ')]", s));
    }

    private boolean hasSelection(String s) {
        return hasAnyComponent(String.format(
                "//div[@class='ActionButtonWithText' and starts-with(@visible_text,'%s: ')]",
                s));
    }

    private void clearSelection() {
        waitFor(() -> {
            ActionButtonFixture scanSelection = findScanSelection();
            ActionButtonFixture branchSelection = findBranchSelection();
            ActionButtonFixture projectSelection = findProjectSelection();
            if (!scanSelection.isShowing() || (scanSelection.hasText("Scan: ..."))
                || (!branchSelection.isShowing() || branchSelection.hasText("Branch: ..."))
                || (!projectSelection.isShowing() || projectSelection.hasText("Project: ..."))) {
                return false;
            }
            click("//div[@myaction.key='RESET_ACTION']");
            return hasAnyComponent("//div[@class='ActionButtonWithText' and @visible_text='Project: none']")
                   && hasAnyComponent("//div[@class='ActionButtonWithText' and @visible_text='Branch: none']")
                   && hasAnyComponent("//div[@class='ActionButtonWithText' and @visible_text='Scan: none']")
                   && !hasAnyComponent(TREE)
                   && StringUtils.isBlank(find(JTextFieldFixture.class, SCAN_FIELD).getText());
        });
    }

    private void applySettings() {
        openCxToolWindow();
        openSettings();
        setFields();
        find(JCheckboxFixture.class,
             String.format(FIELD_NAME, Constants.FIELD_NAME_USE_AUTH_URL),
             waitDuration).setValue(false);
        // click the validation button
        click(VALIDATE_BUTTON);
        // wait for the validation success label
        // the test fails if not found
        find(ComponentFixture.class, "//div[@accessiblename.key='VALIDATE_SUCCESS']", waitDuration);
        click("//div[@text.key='button.ok']");
    }

    private void openSettings() {
        openCxToolWindow();
        waitFor(() -> {
            if (hasAnyComponent(SETTINGS_ACTION)) {
                click(SETTINGS_ACTION);
            } else if (hasAnyComponent(SETTINGS_BUTTON)) {
                click(SETTINGS_BUTTON);
            }
            return hasAnyComponent(String.format(FIELD_NAME, Constants.FIELD_NAME_SERVER_URL));
        });
    }

    private void getResults() {
        JTextFieldFixture scanField = find(JTextFieldFixture.class, SCAN_FIELD);
        waitFor(() -> hasSelection("Project") && hasSelection("Scan"));
        setInvalidScanId();
        scanField.setText(Environment.SCAN_ID);
        new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
        waitFor(() -> hasAnyComponent(String.format("//div[@class='Tree' and @visible_text='Scan %s']",
                                                    Environment.SCAN_ID)));
    }

    private void checkResultsPanel() {
        // check project selection for the project name
        waitFor(() -> hasAnyComponent(String.format(
                "//div[@class='ActionButtonWithText' and @visible_text='Project: %s']",
                Environment.PROJECT_NAME)));
        // check scan selection for the scan id
        waitFor(() -> hasAnyComponent(String.format(
                "//div[@class='ActionButtonWithText' and substring(@visible_text, string-length(@visible_text) - string-length('%s') + 1)  = '%s']",
                Environment.SCAN_ID,
                Environment.SCAN_ID)));
        // navigate the tree for a result
        expand();
        collapse();
        severity();
        queryName();
        JTreeFixture tree = find(JTreeFixture.class, TREE);
        navigate(tree, "Scan", 2);
        navigate(tree, "sast", 4);
        int row = -1;
        for (int i = 0; i < tree.collectRows().size(); i++) {
            if (tree.getValueAtRow(i).contains(".java:")) {
                row = i;
                break;
            }
        }
        // open first node of the opened result
        final int resultRow = row;
        Assertions.assertTrue(resultRow > 2);
        waitFor(() -> {
            tree.clickRow(resultRow);
            return findAll(LINK_LABEL).size() > 0;
        });
        waitFor(() -> {
            findAll(LINK_LABEL).get(0).click();
            return hasAnyComponent(EDITOR);
        });
        Assertions.assertDoesNotThrow(() -> find(EditorFixture.class, EDITOR, waitDuration));
        EditorFixture editor = find(EditorFixture.class, EDITOR, waitDuration);
        // check we opened the correct line:column
        // token is the string in a link label, enclosed in parens, e.g. (token)
        List<RemoteText> labelText = findAll(LINK_LABEL).get(0).getData().getAll();
        String token = labelText.get(labelText.size() - 1).getText().trim();
        // cleanToken removes the parens
        String cleanToken = token.substring(token.indexOf('(') + 1, token.lastIndexOf(')'));
        String editorAtCaret = editor.getText().substring(editor.getCaretOffset());
        Assertions.assertTrue(editorAtCaret.startsWith(cleanToken),
                              String.format("editor: %s | token: %s",
                                            editorAtCaret.substring(0, token.length()),
                                            token));
    }

    private void queryName() {
        groupAction("Query");
    }

    private void severity() {
        groupAction("Severity");
    }

    private void groupAction(String value) {
        openGroupBy();
        waitFor(() -> {
            enter(value);
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void openGroupBy() {
        expand();
        waitFor(() -> {
            click(GROUP_BY_ACTION);
            return findAll(JListFixture.class, "//div[@class='MyList']").size() == 1
                   && findAll(JListFixture.class, "//div[@class='MyList']").get(0).findAllText().size() == 2;
        });
    }

    private void collapse() {
        waitFor(() -> {
            click(COLLAPSE_ACTION);
            return find(JTreeFixture.class, TREE).findAllText().size() == 1;
        });
    }

    private void expand() {
        waitFor(() -> {
            click(EXPAND_ACTION);
            return find(JTreeFixture.class, TREE).findAllText().size() > 1;
        });
    }

    private void setInvalidScanId() {
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

    private void navigate(JTreeFixture tree, String prefix, int minExpectedSize) {
        waitFor(() -> {
            tree.doubleClickRowWithText(prefix, false);
            return tree.findAllText().size() >= minExpectedSize;
        });
    }

    private void setFields() {
        setField(Constants.FIELD_NAME_SERVER_URL, Environment.BASE_URL);
        setField(Constants.FIELD_NAME_TENANT, Environment.TENANT);
        setField(Constants.FIELD_NAME_API_KEY, Environment.API_KEY);
        setField(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS, "--debug");
    }

    private boolean checkTreeState(ComponentFixture tree) {
        return tree.getData().getAll().size() > 1 || (tree.getData().getAll().size() == 1
                                                      && tree.getData()
                                                             .getAll()
                                                             .get(0)
                                                             .getText()
                                                             .contains(Bundle.message(
                                                                     Resource.GETTING_RESULTS)));
    }
}
