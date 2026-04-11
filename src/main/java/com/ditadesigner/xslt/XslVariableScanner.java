package com.ditadesigner.xslt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans XSLT source text to discover declared variables, parameters, and named templates.
 *
 * <p>Results are used by the completion service to suggest {@code $varName} completions
 * inside XPath expressions and named-template names inside {@code xsl:call-template}.
 *
 * <p>This is a lightweight regex scan — it does not parse XML. It runs on the FX thread
 * directly after debounce (fast enough for files up to ~5 MB).
 */
public final class XslVariableScanner {

    // xsl:variable name="foo"  or  xsl:variable name='foo'
    private static final Pattern VAR_NAME = Pattern.compile(
            "<xsl:variable\\b[^>]*\\bname=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    // xsl:param name="foo"  (inside any template or at top level)
    private static final Pattern PARAM_NAME = Pattern.compile(
            "<xsl:param\\b[^>]*\\bname=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    // xsl:template name="foo"  (named templates for call-template)
    private static final Pattern TEMPLATE_NAME = Pattern.compile(
            "<xsl:template\\b[^>]*\\bname=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Scan XSLT text and return a {@link ScanResult} with all discovered names.
     * Safe to call from any thread.
     */
    public static ScanResult scan(String xsltText) {
        if (xsltText == null || xsltText.isBlank()) return ScanResult.EMPTY;

        List<String> vars      = collect(VAR_NAME,      xsltText);
        List<String> params    = collect(PARAM_NAME,    xsltText);
        List<String> templates = collect(TEMPLATE_NAME, xsltText);

        // Combine vars + params for XPath $name suggestions (deduplicated, ordered)
        List<String> allVarNames = new ArrayList<>(vars.size() + params.size());
        for (String v : vars)   if (!allVarNames.contains(v)) allVarNames.add(v);
        for (String p : params) if (!allVarNames.contains(p)) allVarNames.add(p);

        return new ScanResult(List.copyOf(allVarNames), List.copyOf(templates));
    }

    /**
     * Given the XSLT text and a caret position, return the {@code $} prefix typed so far.
     * Returns {@code null} when the caret is not immediately after a {@code $} sequence.
     *
     * <p>Example: text {@code "select=\"$myV"} at caret after {@code V} → returns {@code "myV"}.
     */
    public static String detectVarPrefix(String xsltText, int caretPos) {
        if (caretPos <= 0) return null;
        String before = xsltText.substring(0, caretPos);
        // Scan backward for $<word-chars>
        int i = before.length() - 1;
        while (i >= 0 && (Character.isLetterOrDigit(before.charAt(i))
                || before.charAt(i) == '-' || before.charAt(i) == '_' || before.charAt(i) == ':')) {
            i--;
        }
        if (i >= 0 && before.charAt(i) == '$') {
            return before.substring(i + 1); // everything after the $
        }
        return null;
    }

    /**
     * Return filtered {@code $varName} suggestions for a typed prefix.
     *
     * @param scan    result of {@link #scan}
     * @param prefix  what the user typed after {@code $} (may be blank)
     */
    public static List<String> varSuggestions(ScanResult scan, String prefix) {
        String lp = prefix.toLowerCase();
        return scan.allVarNames().stream()
                .filter(n -> n.toLowerCase().startsWith(lp) || lp.isBlank())
                .map(n -> "$" + n)
                .limit(20)
                .toList();
    }

    /**
     * Return filtered named-template suggestions for a typed prefix inside
     * {@code xsl:call-template name=""}.
     */
    public static List<String> templateNameSuggestions(ScanResult scan, String prefix) {
        String lp = prefix.toLowerCase();
        return scan.namedTemplates().stream()
                .filter(n -> n.toLowerCase().startsWith(lp) || lp.isBlank())
                .limit(20)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<String> collect(Pattern p, String text) {
        List<String> results = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            if (!results.contains(name)) results.add(name);
        }
        return results;
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of the XSLT source scan.
     *
     * @param allVarNames    declared variable + parameter names (for {@code $name} suggestions)
     * @param namedTemplates names of templates with a {@code name=""} attribute
     */
    public record ScanResult(List<String> allVarNames, List<String> namedTemplates) {
        public static final ScanResult EMPTY = new ScanResult(List.of(), List.of());
    }
}
