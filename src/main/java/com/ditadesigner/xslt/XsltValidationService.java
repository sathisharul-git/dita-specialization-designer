package com.ditadesigner.xslt;

import com.ditadesigner.util.LogService;
import net.sf.saxon.s9api.*;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates XSLT stylesheets using Saxon HE's compiler.
 *
 * <p>Validation is purely syntactic/semantic — it compiles the stylesheet
 * without running it, collecting all errors and warnings reported by Saxon.
 */
public class XsltValidationService {

    private static final LogService log = LogService.getInstance();

    private final Processor processor = new Processor(false);

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Validate an XSLT file on disk.
     *
     * @param xsltFile the stylesheet to validate
     * @return list of errors/warnings; empty list means the stylesheet is valid
     */
    public List<XsltValidationError> validate(File xsltFile) {
        List<XsltValidationError> errors = new ArrayList<>();

        XsltCompiler compiler = processor.newXsltCompiler();

        compiler.setErrorReporter(err -> {
            int    line = lineOf(err);
            String msg  = err.getMessage();
            XsltValidationError.Severity sev =
                    err.isWarning() ? XsltValidationError.Severity.WARNING
                                    : XsltValidationError.Severity.ERROR;
            errors.add(new XsltValidationError(line, msg, sev));
            log.logError("[XSLT " + sev + (line > 0 ? " L" + line : "") + "] " + msg, null);
        });

        try {
            compiler.compile(new StreamSource(xsltFile));
        } catch (SaxonApiException ex) {
            // Compile errors should already be captured via the error reporter.
            // Add a fallback entry only if the reporter added nothing.
            if (errors.isEmpty()) {
                errors.add(new XsltValidationError(-1, ex.getMessage(),
                                                   XsltValidationError.Severity.ERROR));
            }
        }

        if (errors.isEmpty()) {
            log.logSuccess("XSLT valid: " + xsltFile.getName());
        } else {
            long errorCount = errors.stream()
                    .filter(e -> e.getSeverity() == XsltValidationError.Severity.ERROR)
                    .count();
            log.log("XSLT validation: " + errorCount + " error(s), "
                    + (errors.size() - errorCount) + " warning(s) in " + xsltFile.getName());
        }

        return errors;
    }

    /**
     * Validate XSLT source text without requiring an on-disk file.
     * Writes to a temp file internally.
     */
    public List<XsltValidationError> validateText(String xsltContent) {
        try {
            File tmp = File.createTempFile("xslt-validate-", ".xsl");
            tmp.deleteOnExit();
            Files.writeString(tmp.toPath(), xsltContent, StandardCharsets.UTF_8);
            return validate(tmp);
        } catch (Exception ex) {
            return List.of(new XsltValidationError(-1,
                    "Cannot write temp file: " + ex.getMessage(),
                    XsltValidationError.Severity.ERROR));
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private int lineOf(XmlProcessingError err) {
        return (err.getLocation() != null) ? err.getLocation().getLineNumber() : -1;
    }
}
