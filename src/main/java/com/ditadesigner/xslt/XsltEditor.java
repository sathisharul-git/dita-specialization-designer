package com.ditadesigner.xslt;

import javafx.application.Platform;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        // Load syntax-highlight CSS
        String cssUrl = XsltEditor.class.getResource("/css/xslt-editor.css").toExternalForm();
        codeArea.getStylesheets().add(cssUrl);

        // Debounced re-highlighting (100 ms after last keystroke)
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(100))
                .subscribe(ignore -> Platform.runLater(this::applyHighlighting));

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

    // ── Highlighting ──────────────────────────────────────────────────────────

    private void applyHighlighting() {
        String text = codeArea.getText();
        if (text.isEmpty()) return;
        try {
            codeArea.setStyleSpans(0, computeHighlighting(text));
        } catch (Exception ignored) {
            // Never crash the UI on a highlighting error
        }
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
}
