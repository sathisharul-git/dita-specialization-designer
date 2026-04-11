package com.ditadesigner.xslt;

import com.ditadesigner.xml.XmlCoreService;
import com.ditadesigner.xml.XmlParseResult;
import net.sf.saxon.s9api.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds and caches an {@link XmlStructureModel} from the loaded XML and produces
 * context-aware XPath suggestions for the XSLT workbench editor.
 *
 * <h3>Threading</h3>
 * <ul>
 *   <li>{@link #updateXml} must be called from a background thread (parsing is expensive).</li>
 *   <li>{@link #detectContext} and {@link #getSuggestions} are fast and run on the FX thread.</li>
 *   <li>{@link #countMatches} is Saxon evaluation — always call from a background thread.</li>
 * </ul>
 */
public class XPathSuggestionService {

    // Scan backward from caret: find the nearest select/match/test attribute whose value is open.
    // Group 1 = attribute name, Group 2 = text typed so far inside the value (double-quoted).
    private static final Pattern ATTR_DQ = Pattern.compile(
            "(select|match|test)\\s*=\\s*\"([^\"\\n]*)$",
            Pattern.CASE_INSENSITIVE);

    // Same for single-quoted values.
    private static final Pattern ATTR_SQ = Pattern.compile(
            "(select|match|test)\\s*=\\s*'([^'\\n]*)$",
            Pattern.CASE_INSENSITIVE);

    private final XmlCoreService xmlCore;

    // Guarded by 'this'
    private XmlStructureModel model;
    private XmlParseResult    cachedParse;
    private String            cachedXmlText = "";

    public XPathSuggestionService(XmlCoreService xmlCore) {
        this.xmlCore = xmlCore;
    }

    // ── Model update ──────────────────────────────────────────────────────────

    /**
     * Parse {@code xmlText}, rebuild the structure model, and cache the Saxon
     * document for later match-count evaluation.
     *
     * <p>Call from a background thread. Returns {@code true} when the XML changed
     * and the model was rebuilt (or at least attempted).
     */
    public synchronized boolean updateXml(String xmlText) {
        if (xmlText == null || xmlText.equals(cachedXmlText)) return false;
        cachedXmlText = xmlText;

        // Saxon parse — kept for XPath evaluation (match counts)
        cachedParse = xmlCore.parse(xmlText);

        // JAXP-based structure model — graceful: keep previous model on failure
        try {
            model = XmlStructureModel.build(xmlText);
        } catch (Exception ignored) {
            // Malformed XML: retain last valid model so suggestions stay available
        }
        return true;
    }

    public synchronized boolean hasModel() {
        return model != null;
    }

    public synchronized boolean isXmlWellFormed() {
        return cachedParse != null && cachedParse.isWellFormed();
    }

    // ── Context detection ─────────────────────────────────────────────────────

    /**
     * Determine the suggestion context and the XPath prefix typed so far.
     *
     * <p>Scans the text before the caret for an unclosed {@code select="}, {@code match="},
     * or {@code test="} attribute value.
     *
     * @param xsltText full XSLT editor content
     * @param caretPos character offset of the caret (0-based)
     * @return context record; {@code context == NONE} when the caret is not inside a relevant attribute
     */
    public SuggestionContext detectContext(String xsltText, int caretPos) {
        if (caretPos <= 0 || caretPos > xsltText.length()) {
            return new SuggestionContext(XmlStructureModel.AttributeContext.NONE, "");
        }
        String before = xsltText.substring(0, caretPos);

        Matcher m = ATTR_DQ.matcher(before);
        if (m.find()) return new SuggestionContext(toContext(m.group(1)), m.group(2));

        Matcher msq = ATTR_SQ.matcher(before);
        if (msq.find()) return new SuggestionContext(toContext(msq.group(1)), msq.group(2));

        return new SuggestionContext(XmlStructureModel.AttributeContext.NONE, "");
    }

    private XmlStructureModel.AttributeContext toContext(String attrName) {
        return switch (attrName.toLowerCase()) {
            case "select" -> XmlStructureModel.AttributeContext.SELECT;
            case "match"  -> XmlStructureModel.AttributeContext.MATCH;
            case "test"   -> XmlStructureModel.AttributeContext.TEST;
            default       -> XmlStructureModel.AttributeContext.SELECT;
        };
    }

    // ── Suggestion retrieval ──────────────────────────────────────────────────

    /**
     * Return filtered suggestions for the given context and typed prefix.
     * Returns an empty list when no model is cached yet.
     */
    public synchronized List<String> getSuggestions(
            XmlStructureModel.AttributeContext context, String prefix) {
        if (model == null) return List.of();
        return model.getSuggestions(context, prefix);
    }

    // ── Match count (run on background thread) ────────────────────────────────

    /**
     * Evaluate {@code expression} against the cached Saxon document and return
     * the number of matching nodes/items.
     *
     * @return match count, or {@code -1} on error or when no XML is loaded
     */
    public int countMatches(String expression) {
        XmlParseResult parse;
        synchronized (this) { parse = cachedParse; }
        if (parse == null || !parse.isWellFormed()) return -1;
        try {
            Processor     processor = xmlCore.getProcessor();
            XPathCompiler compiler  = processor.newXPathCompiler();
            XPathExecutable exe     = compiler.compile(expression);
            XPathSelector   sel     = exe.load();
            sel.setContextItem(parse.document());
            return sel.evaluate().size();
        } catch (Exception e) {
            return -1;
        }
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /** Detected context and the XPath prefix typed so far inside the attribute value. */
    public record SuggestionContext(
            XmlStructureModel.AttributeContext context,
            String prefix) {}
}
