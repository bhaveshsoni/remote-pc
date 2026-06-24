package io.orbion.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

/**
 * Translates remote commands into real keyboard input using the Java Robot API.
 * Text payloads are placed on the clipboard and pasted with CTRL+V so they work
 * in any focused application (Claude Code, Cursor, VS Code, terminals, browsers).
 */
@Service
public class CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutor.class);

    private static final Map<String, Integer> KEY_MAP = Map.of(
            "ENTER", KeyEvent.VK_ENTER,
            "ESC", KeyEvent.VK_ESCAPE,
            "UP", KeyEvent.VK_UP,
            "DOWN", KeyEvent.VK_DOWN,
            "LEFT", KeyEvent.VK_LEFT,
            "RIGHT", KeyEvent.VK_RIGHT,
            "TAB", KeyEvent.VK_TAB,
            "SPACE", KeyEvent.VK_SPACE,
            "BACKSPACE", KeyEvent.VK_BACK_SPACE);

    private Robot robot;

    private synchronized Robot robot() throws Exception {
        if (robot == null) {
            if (GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException("Headless environment: keyboard automation unavailable");
            }
            robot = new Robot();
            robot.setAutoDelay(15);
        }
        return robot;
    }

    public boolean pressKey(String command) {
        String cmd = command == null ? "" : command.toUpperCase();
        try {
            switch (cmd) {
                case "ALT_TAB" -> {
                    // Switch to next window
                    pressCombo(KeyEvent.VK_ALT, KeyEvent.VK_TAB);
                    log.info("Pressed combo: ALT+TAB");
                    return true;
                }
                case "ALT_SHIFT_TAB" -> {
                    // Switch to previous window
                    pressCombo(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_TAB);
                    log.info("Pressed combo: ALT+SHIFT+TAB");
                    return true;
                }
                default -> {
                    Integer keyCode = KEY_MAP.get(cmd);
                    if (keyCode == null) {
                        log.warn("Unknown key command: {}", command);
                        return false;
                    }
                    Robot r = robot();
                    r.keyPress(keyCode);
                    r.keyRelease(keyCode);
                    log.info("Pressed key: {}", cmd);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Failed to press key {}: {}", command, e.getMessage());
            return false;
        }
    }

    /** Presses all keys in order, then releases them in reverse order. */
    private void pressCombo(int... keys) throws Exception {
        Robot r = robot();
        for (int key : keys) {
            r.keyPress(key);
        }
        for (int i = keys.length - 1; i >= 0; i--) {
            r.keyRelease(keys[i]);
        }
    }

    public boolean pasteText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            Robot r = robot();
            r.keyPress(KeyEvent.VK_CONTROL);
            r.keyPress(KeyEvent.VK_V);
            r.keyRelease(KeyEvent.VK_V);
            r.keyRelease(KeyEvent.VK_CONTROL);
            log.info("Pasted text ({} chars)", text.length());
            return true;
        } catch (Exception e) {
            log.error("Failed to paste text: {}", e.getMessage());
            return false;
        }
    }

    public boolean mouseMove(int dx, int dy) {
        try {
            var pos = MouseInfo.getPointerInfo().getLocation();
            robot().mouseMove(pos.x + dx, pos.y + dy);
            return true;
        } catch (Exception e) {
            log.error("Failed to move mouse: {}", e.getMessage());
            return false;
        }
    }

    public boolean mouseClick(String button) {
        try {
            int mask = "right".equalsIgnoreCase(button) ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
            Robot r = robot();
            r.mousePress(mask);
            r.mouseRelease(mask);
            log.info("Mouse click: {}", button);
            return true;
        } catch (Exception e) {
            log.error("Failed to click mouse: {}", e.getMessage());
            return false;
        }
    }

    public boolean scroll(int dy) {
        try {
            if (dy == 0) return true;
            // Clamp to 1 unit per frame for smooth scrolling
            int units = dy > 0 ? 1 : -1;
            robot().mouseWheel(-units);
            return true;
        } catch (Exception e) {
            log.error("Failed to scroll: {}", e.getMessage());
            return false;
        }
    }
}
