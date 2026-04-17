package com.ditadesigner.xslt;

import java.time.Instant;

public record MessageEntry(MessageType type, int line, String text, Instant timestamp) {

    public enum MessageType { ERROR, WARNING, INFO, XSL_MESSAGE }
}
