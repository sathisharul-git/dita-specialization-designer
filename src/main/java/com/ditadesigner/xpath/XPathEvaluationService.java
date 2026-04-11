package com.ditadesigner.xpath;

import com.ditadesigner.util.LogService;
import com.ditadesigner.xml.XmlCoreService;
import com.ditadesigner.xml.XmlParseResult;
import net.sf.saxon.s9api.*;

import java.util.*;

/**
 * Evaluates XPath 2.0/3.1 expressions against an XML document using Saxon HE.
 *
 * <p>Reuses the {@link XmlCoreService} Saxon {@link net.sf.saxon.s9api.Processor}
 * so XML is parsed only once and the {@link XdmNode} is shared.
 */
public class XPathEvaluationService {

    private static final LogService log = LogService.getInstance();

    private final XmlCoreService xmlCore;

    public XPathEvaluationService(XmlCoreService xmlCore) {
        this.xmlCore = xmlCore;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Evaluate an XPath expression against an XML string.
     *
     * @param xmlText    the XML document to query
     * @param expression XPath 2.0/3.1 expression
     * @param namespaces prefix→URI map for namespace declarations (may be null)
     * @return {@link XPathResult} — always non-null
     */
    public XPathResult evaluate(String xmlText, String expression,
                                Map<String, String> namespaces) {
        long start = System.currentTimeMillis();

        // Step 1 — parse and well-formedness check
        XmlParseResult parsed = xmlCore.parse(xmlText);
        if (!parsed.isWellFormed()) {
            return XPathResult.failure("XML is not well-formed:\n"
                    + String.join("\n", parsed.errors()));
        }

        // Step 2 — compile + evaluate
        try {
            Processor processor = xmlCore.getProcessor();
            XPathCompiler compiler = processor.newXPathCompiler();
            applyNamespaces(compiler, namespaces);

            XPathExecutable executable = compiler.compile(expression);
            XPathSelector   selector   = executable.load();
            selector.setContextItem(parsed.document());

            XdmValue result = selector.evaluate();

            List<String> items = new ArrayList<>();
            for (XdmItem item : result) {
                items.add(item.getStringValue());
            }

            long elapsed = System.currentTimeMillis() - start;
            log.log("[XPath] " + items.size() + " match(es) in " + elapsed + "ms  »  " + expression);
            return XPathResult.success(items);

        } catch (SaxonApiException ex) {
            return XPathResult.failure("XPath error: " + ex.getMessage());
        }
    }

    /**
     * Check whether an XPath expression is syntactically valid.
     *
     * @param expression XPath 2.0/3.1 expression
     * @param namespaces prefix-to-URI declarations (optional)
     * @return validation result with message for UI display
     */
    public ExpressionCheckResult checkExpression(String expression, Map<String, String> namespaces) {
        try {
            Processor processor = xmlCore.getProcessor();
            XPathCompiler compiler = processor.newXPathCompiler();
            applyNamespaces(compiler, namespaces);
            compiler.compile(expression);
            return new ExpressionCheckResult(true, "XPath syntax is valid.");
        } catch (SaxonApiException ex) {
            return new ExpressionCheckResult(false, "XPath syntax error: " + ex.getMessage());
        }
    }

    private void applyNamespaces(XPathCompiler compiler, Map<String, String> namespaces) {
        if (namespaces == null) return;
        for (Map.Entry<String, String> e : namespaces.entrySet()) {
            if (!e.getKey().isBlank() && !e.getValue().isBlank()) {
                compiler.declareNamespace(e.getKey().trim(), e.getValue().trim());
            }
        }
    }

    // ── Result DTO ─────────────────────────────────────────────────────────────

    /**
     * Immutable result of an XPath evaluation.
     *
     * @param items        matched node string values (empty when error or no match)
     * @param errorMessage non-null when evaluation failed; {@code null} on success
     * @param success      {@code true} iff evaluation completed without error
     */
    public record XPathResult(List<String> items, String errorMessage, boolean success) {

        static XPathResult success(List<String> items) {
            return new XPathResult(items, null, true);
        }

        static XPathResult failure(String message) {
            return new XPathResult(List.of(), message, false);
        }
    }

    /** Result of XPath syntax validation. */
    public record ExpressionCheckResult(boolean valid, String message) {}
}
