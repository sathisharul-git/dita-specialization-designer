package com.ditadesigner.xslt;

import com.ditadesigner.util.LogService;
import net.sf.saxon.s9api.*;

import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service layer for XSLT 2.0/3.0 transformations using Saxon HE.
 *
 * <p>Stateless: each {@link #transform} call creates its own compiler + transformer.
 * Thread-safe as long as callers supply their own files/text.
 */
public class XsltExecutionService {

    private static final LogService log = LogService.getInstance();

    // One Processor per instance — lightweight, no license needed for HE
    private final Processor processor = new Processor(false);

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Run an XSLT transformation.
     *
     * @param xmlFile  Source XML (must exist on disk)
     * @param xsltFile XSLT stylesheet (must exist on disk)
     * @param params   Named parameters passed to the stylesheet (may be null/empty)
     * @return {@link TransformResult} — always non-null; check {@link TransformResult#isSuccess()}
     */
    public TransformResult transform(File xmlFile, File xsltFile, Map<String, String> params) {
        return transform(xmlFile, xsltFile, params, null);
    }

    public TransformResult transform(File xmlFile, File xsltFile, Map<String, String> params,
                                     Consumer<String> xslMessageCallback) {
        long          start    = System.currentTimeMillis();
        StringBuilder messages = new StringBuilder();

        try {
            XsltCompiler compiler = processor.newXsltCompiler();

            compiler.setErrorReporter(err -> {
                String text = formatError(err.getMessage(), lineOf(err));
                messages.append("[COMPILE] ").append(text).append('\n');
                log.logError(text, null);
            });

            XsltExecutable  executable  = compiler.compile(new StreamSource(xsltFile));
            XsltTransformer transformer = executable.load();

            if (params != null) {
                for (Map.Entry<String, String> e : params.entrySet()) {
                    transformer.setParameter(new QName(e.getKey()),
                                             new XdmAtomicValue(e.getValue()));
                }
            }

            transformer.setMessageHandler(msg -> {
                String text = msg.getContent().getStringValue();
                log.log("xsl:message — " + text);
                if (xslMessageCallback != null) {
                    xslMessageCallback.accept(text);
                } else {
                    messages.append("[MSG] ").append(text).append('\n');
                }
            });

            transformer.setSource(new StreamSource(xmlFile));

            ByteArrayOutputStream baos       = new ByteArrayOutputStream();
            Serializer            serializer = processor.newSerializer(baos);
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            transformer.setDestination(serializer);
            transformer.transform();

            String output  = baos.toString(StandardCharsets.UTF_8);
            long   elapsed = System.currentTimeMillis() - start;
            log.logSuccess("XSLT transform done in " + elapsed + "ms → "
                           + output.length() + " chars output");

            return new TransformResult(output, messages.toString(), null, elapsed);

        } catch (SaxonApiException ex) {
            long   elapsed   = System.currentTimeMillis() - start;
            String errorMsg  = "Transform failed: " + ex.getMessage();
            log.logError(errorMsg, ex);
            return new TransformResult(null, messages.toString(), errorMsg, elapsed);
        }
    }

    /**
     * Transform using in-memory text (writes temporary files).
     * Useful when the editor text has not yet been saved to disk.
     */
    public TransformResult transformText(String xmlText, String xsltText,
                                         Map<String, String> params) {
        try {
            File xmlTmp  = writeTempFile("xslt-src-",  ".xml",  xmlText);
            File xslTmp  = writeTempFile("xslt-style-", ".xsl", xsltText);
            return transform(xmlTmp, xslTmp, params);
        } catch (IOException ex) {
            return new TransformResult(null, "", "Cannot write temp file: " + ex.getMessage(), 0);
        }
    }

    /**
     * Save transformation output to a file (UTF-8).
     */
    public void saveToFile(String content, File outputFile) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outputFile),
                                               StandardCharsets.UTF_8)) {
            w.write(content);
        }
        log.logSuccess("XSLT output saved → " + outputFile.getAbsolutePath());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private File writeTempFile(String prefix, String suffix, String content) throws IOException {
        File tmp = File.createTempFile(prefix, suffix);
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
        return tmp;
    }

    private String formatError(String message, int line) {
        return line > 0 ? "Line " + line + ": " + message : message;
    }

    private int lineOf(XmlProcessingError err) {
        return (err.getLocation() != null) ? err.getLocation().getLineNumber() : -1;
    }

    // ── Result DTO ─────────────────────────────────────────────────────────────

    /**
     * Immutable result of an XSLT transformation.
     *
     * @param output       The serialised output text, or {@code null} on failure.
     * @param messages     Any {@code xsl:message} or compile-warning text collected.
     * @param errorMessage Non-null when the transform failed; {@code null} on success.
     */
    public record TransformResult(String output, String messages, String errorMessage, long elapsedMs) {
        public boolean isSuccess() { return errorMessage == null; }
    }
}
