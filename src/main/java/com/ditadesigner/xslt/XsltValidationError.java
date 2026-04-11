package com.ditadesigner.xslt;

/**
 * Represents a single XSLT validation or compilation error/warning,
 * with line number and severity.
 */
public class XsltValidationError {

    public enum Severity { ERROR, WARNING }

    private final int      line;
    private final String   message;
    private final Severity severity;

    public XsltValidationError(int line, String message, Severity severity) {
        this.line     = line;
        this.message  = message;
        this.severity = severity;
    }

    public int      getLine()     { return line; }
    public String   getMessage()  { return message; }
    public Severity getSeverity() { return severity; }

    @Override
    public String toString() {
        String tag = severity == Severity.WARNING ? "WARNING" : "ERROR";
        return "[" + tag + (line > 0 ? " line " + line : "") + "] " + message;
    }
}
