package com.ditadesigner.util;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogService {

    private static LogService instance;
    private TextArea logArea;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private LogService() {}

    public static LogService getInstance() {
        if (instance == null) {
            instance = new LogService();
        }
        return instance;
    }

    public void setLogArea(TextArea logArea) {
        this.logArea = logArea;
    }

    public void log(String message) {
        String entry = "[" + LocalDateTime.now().format(FMT) + "] " + message + "\n";
        System.out.print(entry);
        if (logArea != null) {
            if (Platform.isFxApplicationThread()) {
                logArea.appendText(entry);
                scrollToBottom();
            } else {
                Platform.runLater(() -> {
                    logArea.appendText(entry);
                    scrollToBottom();
                });
            }
        }
    }

    public void logError(String message, Throwable t) {
        log("ERROR: " + message + (t != null ? " — " + t.getMessage() : ""));
        if (t != null) {
            t.printStackTrace();
        }
    }

    public void logSuccess(String message) {
        log("OK: " + message);
    }

    public void clear() {
        if (logArea != null) {
            if (Platform.isFxApplicationThread()) {
                logArea.clear();
            } else {
                Platform.runLater(logArea::clear);
            }
        }
    }

    private void scrollToBottom() {
        if (logArea != null) {
            logArea.setScrollTop(Double.MAX_VALUE);
        }
    }
}
