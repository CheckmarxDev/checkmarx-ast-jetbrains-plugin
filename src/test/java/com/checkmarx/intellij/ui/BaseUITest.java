package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Environment;
import com.intellij.remoterobot.fixtures.*;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.stepsProcessing.StepLogger;
import com.intellij.remoterobot.stepsProcessing.StepWorker;
import com.intellij.remoterobot.utils.Keyboard;
import com.intellij.remoterobot.utils.RepeatUtilsKt;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public abstract class BaseUITest {

    protected static final Duration waitDuration = Duration.ofSeconds(Integer.getInteger("uiWaitDuration"));
    private static boolean initialized = false;
    private static int retries = 0;
    protected static ComponentFixture baseLabel;

    @BeforeAll
    public static void init() {
        System.out.println(" ==========> BASEUI: init");
        if (!initialized) {
            System.out.println(" ==========> BASEUI: Entrei para iniciar");
            log("Initializing the tests");
            log("Wait duration set for " + waitDuration.getSeconds());
            StepWorker.registerProcessor(new StepLogger());
            if (hasAnyComponent(FLAT_WELCOME_FRAME)) {
                find(FROM_VCS_TAB).click();
                find(JTextFieldFixture.class, BORDERLESS_TEXT_FIELD, Duration.ofSeconds(10)).setText(Environment.REPO);
                waitFor(() -> hasAnyComponent(CLONE_BUTTON) && find(JButtonFixture.class, CLONE_BUTTON).isEnabled());
                find(CLONE_BUTTON).click();
                trustClonedProject();
            }
            // Open Checkmarx One plugin
            openCxToolWindow();

            // Resize Checkmarx One plugin so that all toolbar icons are visible
            resizeToolBar();

            // Connect to AST
            testASTConnection(true);

            initialized = true;
            log("Initialization finished");
        } else {
            System.out.println(" ==========> BASEUI: Já está iniciado");
            log("Tests already initialized, skipping");
        }
    }

    private static void resizeToolBar() {
        waitFor(() -> hasAnyComponent(BASE_LABEL));
        baseLabel = find(BASE_LABEL);
        Keyboard keyboard = new Keyboard(remoteRobot);
        for (int i = 0; i < 3; i++) {
            baseLabel.click();
            if (remoteRobot.isMac()) {
                keyboard.hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_META, KeyEvent.VK_UP);
            } else {
                keyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_ALT, KeyEvent.VK_UP);
            }
        }
    }

    protected static void enter(String value) {
        Keyboard keyboard = new Keyboard(remoteRobot);
        waitFor(() -> {
            for (int i = 0; i < value.length(); i++) {
                keyboard.backspace();
            }
            keyboard.enterText(value);
            return hasAnyComponent(String.format(VISIBLE_TEXT, value));
        });
        keyboard.enter();
    }

    private static void trustClonedProject() {
        try {
            waitFor(() -> hasAnyComponent(TRUST_PROJECT) && find(TRUST_PROJECT).isShowing());
            find(TRUST_PROJECT).click();
        } catch (WaitForConditionTimeoutException ignored) {
        }
    }

    private static void setField(String fieldName, String value) {
        log("Setting field " + fieldName);
        @Language("XPath") String fieldXpath = String.format(FIELD_NAME, fieldName);
        waitFor(() -> hasAnyComponent(fieldXpath) && find(fieldXpath).isShowing());
        find(JTextFieldFixture.class, String.format(FIELD_NAME, fieldName), waitDuration).setText(value);
    }

    protected static void waitFor(Supplier<Boolean> condition) {
        try {
            RepeatUtilsKt.waitFor(waitDuration, condition::get);
        } catch (WaitForConditionTimeoutException e) {
            retries++;
            if (retries < 3) {
                if (baseLabel != null && baseLabel.isShowing()) {
                    baseLabel.click();
                }
            } else {
                retries = 0;
                throw e;
            }
        }
    }

    private static void openCxToolWindow() {
        log("Opening Cx Tool Window");
        waitFor(() -> hasAnyComponent(CHECKMARX_STRIPE_BTN));
        if (!(hasAnyComponent(SETTINGS_ACTION) || hasAnyComponent(SETTINGS_BUTTON))) {
            find(CHECKMARX_STRIPE_BTN).click();
        }
    }

    protected static void log(String msg) {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        System.out.printf("%s | %s: %s%n", Instant.now().toString(), st[2], msg);
    }

    protected static void testASTConnection(boolean validCredentials) {
        openSettings();

        setField(Constants.FIELD_NAME_API_KEY, validCredentials ? Environment.API_KEY : "invalidAPIKey");
        setField(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS, "--debug");

        click(VALIDATE_BUTTON);

        waitFor(() -> !hasAnyComponent(VALIDATING_CONNECTION));

        if (validCredentials) {
            Assertions.assertTrue(hasAnyComponent(SUCCESS_CONNECTION));
            click(OK_BTN);
            // Ensure that start scan button and cancel scan button are visible with valid credentials
            waitFor(() -> {
                baseLabel.click();
                return hasAnyComponent(START_SCAN_BTN) && hasAnyComponent(CANCEL_SCAN_BTN);
            });
        } else {
            Assertions.assertFalse(hasAnyComponent(SUCCESS_CONNECTION));
            click(OK_BTN);
            baseLabel.click();
            // Ensure that start scan button and cancel scan button are hidden with invalid credentials
            waitFor(() -> {
                baseLabel.click();
                return !hasAnyComponent(START_SCAN_BTN) && !hasAnyComponent(CANCEL_SCAN_BTN);
            });
        }
    }

    private static void openSettings() {
        waitFor(() -> {
            if (hasAnyComponent(SETTINGS_ACTION)) {
                click(SETTINGS_ACTION);
            } else if (hasAnyComponent(SETTINGS_BUTTON)) {
                click(SETTINGS_BUTTON);
            }
            return hasAnyComponent(String.format(FIELD_NAME, Constants.FIELD_NAME_API_KEY));
        });
    }

    protected static void testFileNavigation() {
        waitFor(() -> {
            baseLabel.click();
            findAll(LINK_LABEL).get(0).doubleClick();
            return hasAnyComponent(EDITOR);
        });
        Assertions.assertDoesNotThrow(() -> find(EditorFixture.class, EDITOR, waitDuration));
        //Confirming if editor is opened
        find(EditorFixture.class, EDITOR, waitDuration);
    }

    protected void getResults() {
        baseLabel.click();
        waitFor(() -> hasAnyComponent(SCAN_FIELD) && hasSelection("Project") && hasSelection("Branch") && hasSelection("Scan"));
        find(JTextFieldFixture.class, SCAN_FIELD).setText(Environment.SCAN_ID);
        new Keyboard(remoteRobot).key(KeyEvent.VK_ENTER);
        waitFor(() -> {
            baseLabel.click();
            return hasAnyComponent(String.format("//div[@class='Tree' and contains(@visible_text,'Scan %s')]", Environment.SCAN_ID));
        });
    }

    private static boolean hasSelection(String s) {
        return hasAnyComponent(String.format(HAS_SELECTION, s));
    }

    protected void waitForScanIdSelection() {
        baseLabel.click();
        // check scan selection for the scan id
        waitFor(() -> hasAnyComponent(String.format(SCAN_ID_SELECTION, Environment.SCAN_ID, Environment.SCAN_ID)));
    }

    protected void navigate(String prefix, int minExpectedSize) {
        waitFor(() -> {
            List<RemoteText> prefixNodes = find(JTreeFixture.class, TREE).getData()
                    .getAll()
                    .stream()
                    .filter(t -> t.getText().startsWith(prefix))
                    .collect(Collectors.toList());
            if (prefixNodes.size() == 0) {
                return false;
            }
            prefixNodes.get(0).doubleClick();
            return find(JTreeFixture.class, TREE).findAllText().size() >= minExpectedSize;
        });
    }

    @NotNull
    protected static ActionButtonFixture findSelection(String s) {
        @Language("XPath") String xpath = String.format(
                "//div[@class='ActionButtonWithText' and starts-with(@visible_text,'%s: ')]",
                s);
        waitFor(() -> hasAnyComponent(xpath));
        return find(ActionButtonFixture.class, xpath);
    }

    protected void testSelectionAction(ActionButtonFixture selection, String prefix, String value) {
        waitFor(() -> {
            baseLabel.click();
            System.out.println(selection.getTemplatePresentationText());
            return selection.isEnabled() && selection.getTemplatePresentationText().contains(prefix);
        });
        waitFor(() -> {
            baseLabel.click();
            selection.click();
            List<JListFixture> jListFixtures = findAll(JListFixture.class, MY_LIST);

            return jListFixtures.size() == 1 && jListFixtures.get(0).findAllText().size() > 0;
        });
        enter(value);
    }

    protected void clearSelection() {
        waitFor(() -> {
            if (hasAnyComponent(NO_PROJECT_SELECTED)
                    && findSelection("Project").isEnabled()
                    && hasAnyComponent(NO_BRANCH_SELECTED)
                    && hasAnyComponent(NO_SCAN_SELECTED)
                    && !hasAnyComponent(TREE)
                    && StringUtils.isBlank(find(JTextFieldFixture.class, SCAN_FIELD).getText())) {
                log("clear selection done");
                return true;
            }
            if (!findSelection("Scan").isShowing() || (findSelection("Scan").hasText("Scan: ..."))
                    || (!findSelection("Branch").isShowing() || findSelection("Branch").hasText("Branch: ..."))
                    || (!findSelection("Project").isShowing() || findSelection("Project").hasText("Project: ..."))) {
                log("clear selection still in progress");
                return false;
            }
            log("clicking refresh action button");
            click(CLEAR_BTN);
            return false;
        });
    }
}
