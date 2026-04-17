package com.ditadesigner.xslt;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax-highlighted XML/XSLT code editor built on RichTextFX {@link CodeArea}.
 *
 * <p>Recognised tokens (VS Code Dark+ colour scheme via {@code xslt-editor.css}):
 * <ul>
 *   <li>{@code xsl-comment}  — XML comments</li>
 *   <li>{@code xsl-cdata}    — CDATA sections</li>
 *   <li>{@code xsl-keyword}  — {@code xsl:*} instruction names</li>
 *   <li>{@code xsl-string}   — attribute string values</li>
 *   <li>{@code xsl-tag}      — element tag names</li>
 *   <li>{@code xsl-attr}     — attribute names</li>
 *   <li>{@code xsl-bracket}  — angle brackets / punctuation</li>
 *   <li>{@code xsl-entity}   — XML entity references</li>
 *   <li>{@code xsl-xpath}    — XPath AVT expressions {@code {…}}</li>
 * </ul>
 */
public class XsltEditor extends VBox {

    // ── Tag auto-close pattern: last unclosed opening tag on the current line ──
    private static final Pattern TAG_OPEN_END = Pattern.compile(
            "<([a-zA-Z][a-zA-Z0-9:._-]*)(?:\\s[^/\\n>]*)?" );

    private static final Preferences EDITOR_PREFS =
            Preferences.userNodeForPackage(XsltEditor.class);

    // LRU cache for tag-match patterns (keyed by tag name, capped at 20 entries)
    private static final Map<String, Pattern[]> TAG_PATTERN_CACHE =
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Pattern[]> e) {
                    return size() > 20;
                }
            };

    // Shared merge function for StyleSpans overlays
    private static final java.util.function.BiFunction<
            Collection<String>, Collection<String>, Collection<String>> MERGE_SPANS =
        (a, b) -> {
            if (b.isEmpty()) return a;
            List<String> merged = new ArrayList<>(a);
            merged.addAll(b);
            return merged;
        };

    // ── Lexer pattern — order matters (most specific first) ───────────────────
    private static final Pattern PATTERN = Pattern.compile(
            "(?<COMMENT><!--.*?-->)"                      // XML comment
            + "|(?<CDATA><!\\[CDATA\\[.*?\\]\\]>)"       // CDATA
            + "|(?<XPATH>\\{[^}]*\\})"                    // XPath AVT {expr}
            + "|(?<XSLKW>xsl:[a-z][a-z-]*)"              // xsl: instruction
            + "|(?<STRING>\"[^\"\\r\\n]*\"|'[^'\\r\\n]*')"// attribute value
            + "|(?<TAGNAME></?[\\w:.-]+)"                 // tag name
            + "|(?<ATTR>[\\w:.-]+)(?=\\s*=)"              // attribute name
            + "|(?<BRACKET>[<>/!?=])"                     // punctuation
            + "|(?<ENTITY>&[\\w#]+;)",                    // entity ref
            Pattern.DOTALL
    );

    private final CodeArea codeArea;
    private final XsltErrorGutterFactory gutterFactory;
    private final Map<Integer, XsltValidationError> errorsByLine = new HashMap<>();
    private final List<int[]> tagMatchRanges = new ArrayList<>();
    private final PauseTransition tagMatchDebounce =
            new PauseTransition(javafx.util.Duration.millis(80));
    private double fontSize;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Editable editor. */
    public XsltEditor() {
        this(false);
    }

    /**
     * @param readOnly {@code true} for the output preview panel (non-editable)
     */
    public XsltEditor(boolean readOnly) {
        codeArea = new CodeArea();
        codeArea.setEditable(!readOnly);
        gutterFactory = new XsltErrorGutterFactory(() -> errorsByLine);
        codeArea.setParagraphGraphicFactory(gutterFactory);

        // Load syntax-highlight CSS
        String cssUrl = XsltEditor.class.getResource("/css/xslt-editor.css").toExternalForm();
        codeArea.getStylesheets().add(cssUrl);

        // Debounced re-highlighting (100 ms after last keystroke)
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(100))
                .subscribe(ignore -> Platform.runLater(this::applyHighlighting));

        // Font size (F-243)
        fontSize = EDITOR_PREFS.getDouble("editorFontSize", 13.0);
        applyFontSize();

        // Tag auto-close + paired quote (F-240, F-241) — editable editors only
        if (!readOnly) {
            codeArea.addEventFilter(KeyEvent.KEY_TYPED, this::handleKeyTyped);
        }

        // Matching tag highlight on caret move — debounced 80ms (F-244)
        tagMatchDebounce.setOnFinished(e -> Platform.runLater(this::applyHighlighting));
        codeArea.caretPositionProperty().addListener((obs, oldVal, newVal) -> {
            if (updateTagMatchRanges(newVal)) {
                tagMatchDebounce.playFromStart();
            }
        });

        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
        VBox.setVgrow(this, Priority.ALWAYS);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Replace all editor content. Scrolls to top afterwards. */
    public void setText(String text) {
        codeArea.replaceText(text == null ? "" : text);
        codeArea.moveTo(0);
        Platform.runLater(this::applyHighlighting);
    }

    /** Return the full editor content. */
    public String getText() {
        return codeArea.getText();
    }

    /** Append text at the end (useful for streaming output). */
    public void appendText(String text) {
        codeArea.appendText(text);
    }

    /** Clear all content. */
    public void clear() {
        codeArea.clear();
    }

    /** Direct access to the underlying {@link CodeArea} for advanced configuration. */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /** Mark error/warning lines in the gutter and underline them in the editor. */
    public void setErrorMarkers(List<XsltValidationError> errors) {
        errorsByLine.clear();
        for (XsltValidationError e : errors) {
            errorsByLine.put(e.getLine(), e);
        }
        codeArea.setParagraphGraphicFactory(gutterFactory);
        Platform.runLater(this::applyHighlighting);
    }

    /** Remove all error/warning markers from the gutter and editor. */
    public void clearErrorMarkers() {
        errorsByLine.clear();
        codeArea.setParagraphGraphicFactory(gutterFactory);
        Platform.runLater(this::applyHighlighting);
    }

    /** Toggle XML comment on every line in the current selection (or current line if no selection). */
    public void toggleLineComment() {
        int selStart = codeArea.getSelection().getStart();
        int selEnd   = codeArea.getSelection().getEnd();

        int firstPara = codeArea.offsetToPosition(selStart, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
        int lastPara  = selEnd > selStart
                ? codeArea.offsetToPosition(selEnd - 1, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor()
                : firstPara;

        for (int i = firstPara; i <= lastPara; i++) {
            String line = codeArea.getParagraph(i).getText();
            String trimmed = line.stripLeading();
            int paraStart = codeArea.getAbsolutePosition(i, 0);

            if (trimmed.startsWith("<!--") && trimmed.endsWith("-->")) {
                int commentOpen  = line.indexOf("<!--");
                int commentClose = line.lastIndexOf("-->");
                String inner = line.substring(commentOpen + 4, commentClose);
                if (inner.startsWith(" ") && inner.endsWith(" ") && inner.length() > 2) {
                    inner = inner.substring(1, inner.length() - 1);
                }
                codeArea.replaceText(paraStart + commentOpen,
                        paraStart + commentClose + 3,
                        inner);
            } else {
                codeArea.replaceText(paraStart, paraStart + line.length(),
                        "<!-- " + line + " -->");
            }
        }
    }

    /** Duplicate the current line, inserting the copy on the line below, and move caret there. */
    public void duplicateCurrentLine() {
        int paraIndex = codeArea.getCurrentParagraph();
        String lineText = codeArea.getParagraph(paraIndex).getText();
        int lineStart = codeArea.getAbsolutePosition(paraIndex, 0);
        int lineEnd   = lineStart + lineText.length();
        codeArea.insertText(lineEnd, "\n" + lineText);
        codeArea.moveTo(lineEnd + 1);
    }

    /** Indent every line in the current selection by two spaces (F-242). */
    public void indentSelection() {
        int first = firstSelectedPara();
        int last  = lastSelectedPara();
        for (int i = first; i <= last; i++) {
            codeArea.insertText(codeArea.getAbsolutePosition(i, 0), "  ");
        }
    }

    /** Remove up to two leading spaces from every line in the current selection (F-242). */
    public void dedentSelection() {
        int first = firstSelectedPara();
        int last  = lastSelectedPara();
        for (int i = first; i <= last; i++) {
            String line = codeArea.getParagraph(i).getText();
            int pos = codeArea.getAbsolutePosition(i, 0);
            if (line.startsWith("  "))      codeArea.deleteText(pos, pos + 2);
            else if (line.startsWith(" "))  codeArea.deleteText(pos, pos + 1);
        }
    }

    /** Increase editor font size by 1pt, max 28, persisted via Preferences (F-243). */
    public void increaseFontSize() {
        fontSize = Math.min(fontSize + 1, 28);
        applyFontSize();
        EDITOR_PREFS.putDouble("editorFontSize", fontSize);
    }

    /** Decrease editor font size by 1pt, min 9, persisted via Preferences (F-243). */
    public void decreaseFontSize() {
        fontSize = Math.max(fontSize - 1, 9);
        applyFontSize();
        EDITOR_PREFS.putDouble("editorFontSize", fontSize);
    }

    // ── Highlighting ──────────────────────────────────────────────────────────

    private void applyHighlighting() {
        String text = codeArea.getText();
        if (text.isEmpty()) return;
        try {
            StyleSpans<Collection<String>> spans = computeHighlighting(text);
            if (!errorsByLine.isEmpty()) spans = mergeErrorStyles(text, spans);
            if (!tagMatchRanges.isEmpty()) spans = mergeTagMatchStyles(text, spans);
            codeArea.setStyleSpans(0, spans);
        } catch (Exception ignored) {
            // Never crash the UI on a highlighting error
        }
    }

    private StyleSpans<Collection<String>> mergeErrorStyles(
            String text, StyleSpans<Collection<String>> base) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = 0;
        for (int para = 0; para < codeArea.getParagraphs().size(); para++) {
            int paraLen  = codeArea.getParagraph(para).length();
            int newline  = (para < codeArea.getParagraphs().size() - 1) ? 1 : 0;
            int segLen   = paraLen + newline;
            XsltValidationError err = errorsByLine.get(para + 1);
            if (err != null && paraLen > 0) {
                String styleClass = err.getSeverity() == XsltValidationError.Severity.ERROR
                        ? "xsl-error-line" : "xsl-warning-line";
                builder.add(List.of(styleClass), paraLen);
                if (newline > 0) builder.add(Collections.emptyList(), newline);
            } else {
                builder.add(Collections.emptyList(), segLen);
            }
            pos += segLen;
        }
        if (pos < text.length()) builder.add(Collections.emptyList(), text.length() - pos);
        return base.overlay(builder.create(), MERGE_SPANS);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        Matcher matcher = PATTERN.matcher(text);
        int cursor = 0;

        while (matcher.find()) {
            // Gap before this match — unstyled
            builder.add(Collections.emptyList(), matcher.start() - cursor);
            // Matched token
            builder.add(List.of(resolveClass(matcher)), matcher.end() - matcher.start());
            cursor = matcher.end();
        }

        // Remaining text
        builder.add(Collections.emptyList(), text.length() - cursor);
        return builder.create();
    }

    private String resolveClass(Matcher m) {
        if (m.group("COMMENT") != null) return "xsl-comment";
        if (m.group("CDATA")   != null) return "xsl-cdata";
        if (m.group("XPATH")   != null) return "xsl-xpath";
        if (m.group("XSLKW")   != null) return "xsl-keyword";
        if (m.group("STRING")  != null) return "xsl-string";
        if (m.group("TAGNAME") != null) return "xsl-tag";
        if (m.group("ATTR")    != null) return "xsl-attr";
        if (m.group("BRACKET") != null) return "xsl-bracket";
        if (m.group("ENTITY")  != null) return "xsl-entity";
        return "default-text";
    }

    // ── Font size (F-243) ─────────────────────────────────────────────────────

    private void applyFontSize() {
        codeArea.setStyle("-fx-font-size: " + (int) fontSize + "px;");
    }

    // ── Selection helpers ─────────────────────────────────────────────────────

    private int firstSelectedPara() {
        int sel = codeArea.getSelection().getStart();
        return codeArea.offsetToPosition(sel,
                org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
    }

    private int lastSelectedPara() {
        var sel = codeArea.getSelection();
        if (sel.getLength() == 0) return firstSelectedPara();
        return codeArea.offsetToPosition(sel.getEnd() - 1,
                org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
    }

    // ── Key-typed handlers (F-240, F-241) ────────────────────────────────────

    private void handleKeyTyped(KeyEvent event) {
        String ch = event.getCharacter();
        if (">".equals(ch))  handleTagAutoClose(event);
        else if ("\"".equals(ch)) handlePairedQuote(event);
    }

    private void handleTagAutoClose(KeyEvent event) {
        int caretPos = codeArea.getCaretPosition();
        String textBefore = codeArea.getText(0, caretPos);
        Matcher m = TAG_OPEN_END.matcher(textBefore);
        String lastTag = null;
        while (m.find()) {
            String hit = m.group(0);
            if (!hit.startsWith("</") && !hit.startsWith("<!") && !hit.endsWith("/")) {
                lastTag = m.group(1);
            }
        }
        if (lastTag != null && !lastTag.isEmpty()) {
            event.consume();
            codeArea.insertText(caretPos, "></" + lastTag + ">");
            codeArea.moveTo(caretPos + 1);
        }
    }

    private void handlePairedQuote(KeyEvent event) {
        int caretPos = codeArea.getCaretPosition();
        String text  = codeArea.getText();
        // Only act when caret is inside a tag (unbalanced < before >)
        int lineStart = codeArea.getAbsolutePosition(codeArea.getCurrentParagraph(), 0);
        String before = text.substring(lineStart, caretPos);
        long opens  = before.chars().filter(c -> c == '<').count();
        long closes = before.chars().filter(c -> c == '>').count();
        if (opens <= closes) return;
        // Skip over existing closing quote
        if (caretPos < text.length() && text.charAt(caretPos) == '"') {
            event.consume();
            codeArea.moveTo(caretPos + 1);
            return;
        }
        event.consume();
        codeArea.insertText(caretPos, "\"\"");
        codeArea.moveTo(caretPos + 1);
    }

    // ── Matching tag highlight (F-244) ────────────────────────────────────────

    private boolean updateTagMatchRanges(int caretPos) {
        boolean hadMatches = !tagMatchRanges.isEmpty();
        tagMatchRanges.clear();
        String text = codeArea.getText();
        if (caretPos <= 0 || caretPos > text.length()) return hadMatches;

        // Find word at caret
        int wordStart = caretPos;
        while (wordStart > 0 && isTagNameChar(text.charAt(wordStart - 1))) wordStart--;
        int wordEnd = caretPos;
        while (wordEnd < text.length() && isTagNameChar(text.charAt(wordEnd))) wordEnd++;
        if (wordStart >= wordEnd) return hadMatches;

        String tagName = text.substring(wordStart, wordEnd);

        if (wordStart >= 2
                && text.charAt(wordStart - 2) == '<'
                && text.charAt(wordStart - 1) == '/') {
            // Closing tag — find matching opening tag
            int[] openRange = findOpeningTag(text, tagName, wordStart - 2);
            if (openRange != null) tagMatchRanges.add(openRange);
            tagMatchRanges.add(new int[]{wordStart - 2, Math.min(wordEnd + 1, text.length())});
        } else if (wordStart >= 1 && text.charAt(wordStart - 1) == '<') {
            // Opening tag — find matching closing tag
            tagMatchRanges.add(new int[]{wordStart - 1, wordEnd});
            int[] closeRange = findClosingTag(text, tagName, wordEnd);
            if (closeRange != null) tagMatchRanges.add(closeRange);
        } else {
            return hadMatches;
        }
        return true;
    }

    private boolean isTagNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == ':' || c == '-' || c == '_' || c == '.';
    }

    private static Pattern[] cachedTagPatterns(String tagName) {
        return TAG_PATTERN_CACHE.computeIfAbsent(tagName, n -> new Pattern[]{
            Pattern.compile("<"  + Pattern.quote(n) + "(?=[\\s>/])"),
            Pattern.compile("</" + Pattern.quote(n) + "(?=[\\s>])")
        });
    }

    private int[] findClosingTag(String text, String tagName, int afterPos) {
        Matcher m = cachedTagPatterns(tagName)[1].matcher(text);
        if (m.find(afterPos)) return new int[]{m.start(), m.end()};
        return null;
    }

    private int[] findOpeningTag(String text, String tagName, int beforePos) {
        Matcher m = cachedTagPatterns(tagName)[0].matcher(text.substring(0, beforePos));
        int start = -1, end = -1;
        while (m.find()) { start = m.start(); end = m.end(); }
        return start >= 0 ? new int[]{start, end} : null;
    }

    private StyleSpans<Collection<String>> mergeTagMatchStyles(
            String text, StyleSpans<Collection<String>> base) {
        // Ranges are always added in ascending position order — no sort needed
        StyleSpansBuilder<Collection<String>> overlay = new StyleSpansBuilder<>();
        int pos = 0;
        for (int[] range : tagMatchRanges) {
            int start = Math.max(0, range[0]);
            int end   = Math.min(text.length(), range[1]);
            if (start >= end || start < pos) continue;
            if (pos < start) overlay.add(Collections.emptyList(), start - pos);
            overlay.add(List.of("xsl-tag-match"), end - start);
            pos = end;
        }
        if (pos < text.length()) overlay.add(Collections.emptyList(), text.length() - pos);
        try {
            return base.overlay(overlay.create(), MERGE_SPANS);
        } catch (Exception e) {
            return base;
        }
    }
}
