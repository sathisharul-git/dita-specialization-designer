package com.ditadesigner.xslt;

import com.ditadesigner.util.LogService;
import com.ditadesigner.xml.XmlCoreService;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the XSLT Workbench window.
 *
 * <p>Layout:
 * <pre>
 * ┌──────────────── XSLT Workbench ────────────────────────────────┐
 * │ ToolBar: [XML Browse] [XSLT Browse] | [Run] [Validate] [Save]  │
 * ├──────────────────────────────────┬─────────────────────────────┤
 * │  XML Input  (XsltEditor)         │                             │
 * │──────────────────────────────────│  Output Preview             │
 * │  XSLT Editor (XsltEditor)        │  (XsltEditor read-only)     │
 * ├──────────────────────────────────┴─────────────────────────────┤
 * │  Messages / Errors (TextArea)                                   │
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Transforms run on a background daemon thread so the UI stays responsive.
 */
public class XsltUIController {

    private static final LogService log = LogService.getInstance();

    // Default XSLT snippet shown in a new editor
    private static final String DEFAULT_XSLT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

              <xsl:output method="html" encoding="UTF-8" indent="yes"/>

              <!-- Match the root element -->
              <xsl:template match="/">
                <html>
                  <body>
                    <h1>XSLT Output</h1>
                    <xsl:apply-templates/>
                  </body>
                </html>
              </xsl:template>

              <!-- Default pass-through -->
              <xsl:template match="*">
                <xsl:apply-templates/>
              </xsl:template>

            </xsl:stylesheet>
            """;

    // ── Services ──────────────────────────────────────────────────────────────
    private final XsltExecutionService   executionService   = new XsltExecutionService();
    private final XsltValidationService  validationService  = new XsltValidationService();
    private final XPathSuggestionService suggestionService  =
            new XPathSuggestionService(new XmlCoreService());
    private final XPathSuggestionPopup   suggestionPopup    = new XPathSuggestionPopup();

    // ── State ─────────────────────────────────────────────────────────────────
    private final Stage ownerStage;
    private       Stage workbenchStage;
    private       File  currentXmlFile;
    private       File  currentXsltFile;
    private       String lastOutput = "";

    // ── UI References ─────────────────────────────────────────────────────────
    private XsltEditor xmlEditor;
    private XsltEditor xsltEditor;
    private XsltEditor outputEditor;
    private TextField  xmlPathField;
    private TextField  xsltPathField;
    private TextArea   messageArea;
    private Label      statusLabel;
    private Button     runBtn;

    // ── Background thread ─────────────────────────────────────────────────────
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xslt-worker");
        t.setDaemon(true);
        return t;
    });

    // ── Constructor ───────────────────────────────────────────────────────────

    public XsltUIController(Stage ownerStage) {
        this.ownerStage = ownerStage;
    }

    // ── Public entry points ───────────────────────────────────────────────────

    /** Open or bring the workbench to front. */
    public void show() {
        if (workbenchStage != null && workbenchStage.isShowing()) {
            workbenchStage.toFront();
            return;
        }
        workbenchStage = buildStage();
        workbenchStage.show();
    }

    /**
     * Open the workbench pre-loaded with specific files (DITA→HTML shortcut).
     *
     * @param xmlFile  XML/DITA source (may be {@code null})
     * @param xsltFile XSLT stylesheet (may be {@code null})
     */
    public void show(File xmlFile, File xsltFile) {
        show();
        if (xmlFile != null)  loadXmlFile(xmlFile);
        if (xsltFile != null) loadXsltFile(xsltFile);
    }

    // ── Stage construction ─────────────────────────────────────────────────────

    private Stage buildStage() {
        Stage stage = new Stage();
        stage.setTitle("XSLT Workbench");
        stage.initOwner(ownerStage);
        stage.initModality(Modality.NONE);
        stage.setWidth(1300);
        stage.setHeight(820);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setOnCloseRequest(e -> executor.shutdown());

        BorderPane root = new BorderPane();
        root.setTop(buildToolBar());
        root.setCenter(buildEditorArea());
        root.setBottom(buildMessagePanel());

        Scene scene = new Scene(root);
        try {
            scene.getStylesheets().add(
                    XsltUIController.class.getResource("/css/xslt-editor.css").toExternalForm());
            // Inherit the application stylesheet for consistent chrome
            scene.getStylesheets().add(
                    XsltUIController.class.getResource("/css/styles.css").toExternalForm());
        } catch (Exception ignored) { /* stylesheet not found — continue */ }

        stage.setScene(scene);
        return stage;
    }

    // ── Tool bar ───────────────────────────────────────────────────────────────

    private VBox buildToolBar() {
        // ── Row 1: file selectors ─────────────────────────────────────────────
        xmlPathField = new TextField();
        xmlPathField.setPromptText("XML / DITA source file…");
        xmlPathField.setEditable(false);
        HBox.setHgrow(xmlPathField, Priority.ALWAYS);

        Button browseXmlBtn = new Button("Browse XML…");
        browseXmlBtn.setOnAction(e -> browseXml());

        xsltPathField = new TextField();
        xsltPathField.setPromptText("XSLT stylesheet…");
        xsltPathField.setEditable(false);
        HBox.setHgrow(xsltPathField, Priority.ALWAYS);

        Button browseXsltBtn = new Button("Browse XSLT…");
        browseXsltBtn.setOnAction(e -> browseXslt());

        Button ditaHtmlBtn = new Button("Use DITA→HTML Template");
        ditaHtmlBtn.setStyle("-fx-font-size: 10px;");
        ditaHtmlBtn.setOnAction(e -> loadBuiltinDitaHtml());
        Tooltip.install(ditaHtmlBtn, new Tooltip(
                "Load the built-in DITA-to-HTML XSLT stylesheet into the editor"));

        HBox fileRow = new HBox(6,
                new Label("XML:"),  xmlPathField,  browseXmlBtn,
                new Separator(Orientation.VERTICAL),
                new Label("XSLT:"), xsltPathField, browseXsltBtn,
                ditaHtmlBtn);
        fileRow.setPadding(new Insets(5, 8, 5, 8));
        fileRow.setStyle("-fx-alignment: CENTER_LEFT;");

        // ── Row 2: action buttons ─────────────────────────────────────────────
        runBtn = new Button("▶  Run XSLT");
        runBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; "
                        + "-fx-font-weight: bold; -fx-padding: 5 14 5 14;");
        runBtn.setOnAction(e -> runTransform());

        Button validateBtn = new Button("✔  Validate XSLT");
        validateBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; "
                             + "-fx-padding: 5 12 5 12;");
        validateBtn.setOnAction(e -> validateXslt());

        Button saveOutputBtn = new Button("Save Output…");
        saveOutputBtn.setOnAction(e -> saveOutput());

        Button clearBtn = new Button("Clear All");
        clearBtn.setOnAction(e -> clearOutputAndMessages());

        statusLabel = new Label("Ready — open an XML file and an XSLT stylesheet to begin.");
        statusLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        HBox actionRow = new HBox(8,
                runBtn, validateBtn,
                new Separator(Orientation.VERTICAL),
                saveOutputBtn, clearBtn,
                statusLabel);
        actionRow.setPadding(new Insets(5, 8, 5, 8));
        actionRow.setStyle("-fx-alignment: CENTER_LEFT;");

        VBox toolbar = new VBox(fileRow, new Separator(), actionRow);
        toolbar.setStyle("-fx-background-color: #f5f5f5; "
                         + "-fx-border-color: #d0d0d0; -fx-border-width: 0 0 1 0;");
        return toolbar;
    }

    // ── Editor area ────────────────────────────────────────────────────────────

    private SplitPane buildEditorArea() {
        // Left: XML input on top, XSLT editor below
        xmlEditor  = new XsltEditor(false);
        xsltEditor = new XsltEditor(false);
        xsltEditor.setText(DEFAULT_XSLT);  // seed with a starter template

        TitledPane xmlPane  = editorPane("XML Input",    xmlEditor);
        TitledPane xsltPane = editorPane("XSLT Editor",  xsltEditor);

        SplitPane leftSplit = new SplitPane(xmlPane, xsltPane);
        leftSplit.setOrientation(Orientation.VERTICAL);
        leftSplit.setDividerPositions(0.40);

        // Right: output (read-only)
        outputEditor = new XsltEditor(true);
        TitledPane outputPane = editorPane("Output Preview", outputEditor);

        SplitPane mainSplit = new SplitPane(leftSplit, outputPane);
        mainSplit.setDividerPositions(0.50);

        installSuggestions();
        return mainSplit;
    }

    // ── XPath smart suggestions ────────────────────────────────────────────────

    /**
     * Wire the XML editor, XSLT editor, suggestion service, and popup together.
     * Called once after both editors are constructed.
     */
    private void installSuggestions() {
        // Rebuild XML structure model in background whenever XML content changes
        xmlEditor.getCodeArea().textProperty().addListener((obs, old, text) -> {
            if (text != null && !text.isBlank()) {
                executor.submit(() -> suggestionService.updateXml(text));
            }
        });

        // Trigger suggestions on every caret move in the XSLT editor
        xsltEditor.getCodeArea().caretPositionProperty().addListener(
                (obs, old, pos) -> triggerSuggestions(
                        xsltEditor.getCodeArea().getText(), pos.intValue()));

        // Accept: replace the typed prefix in the editor with the selected completion
        suggestionPopup.setOnAccept(completion -> {
            var ca       = xsltEditor.getCodeArea();
            int caretPos = ca.getCaretPosition();
            String text  = ca.getText();
            XPathSuggestionService.SuggestionContext ctx =
                    suggestionService.detectContext(text, caretPos);
            int start = caretPos - ctx.prefix().length();
            if (start >= 0) ca.replaceText(start, caretPos, completion);
        });

        // Match count: evaluate on background thread, post result to popup label
        suggestionPopup.setMatchCounter((expr, callback) ->
                executor.submit(() -> callback.accept(suggestionService.countMatches(expr))));

        // Keyboard: ↑ ↓ Enter Tab Escape are intercepted when popup is open
        suggestionPopup.installKeyHandler(xsltEditor.getCodeArea());
    }

    /**
     * Detect context at the current caret position and show or hide the popup.
     * Must be called on the FX thread (called from a property listener).
     */
    private void triggerSuggestions(String xsltText, int caretPos) {
        if (!suggestionService.hasModel()) { suggestionPopup.hide(); return; }

        XPathSuggestionService.SuggestionContext ctx =
                suggestionService.detectContext(xsltText, caretPos);

        if (ctx.context() == XmlStructureModel.AttributeContext.NONE) {
            suggestionPopup.hide();
            return;
        }

        List<String> suggestions = suggestionService.getSuggestions(ctx.context(), ctx.prefix());
        showSuggestionPopup(suggestions);
    }

    /** Position and display the suggestion popup below the editor caret. */
    private void showSuggestionPopup(List<String> suggestions) {
        if (suggestions.isEmpty()) { suggestionPopup.hide(); return; }

        var ca = xsltEditor.getCodeArea();
        if (ca.getScene() == null) return;
        Window window = ca.getScene().getWindow();

        Optional<Bounds> boundsOpt = ca.caretBoundsProperty().getValue();
        if (boundsOpt.isEmpty()) { suggestionPopup.hide(); return; }

        // caretBounds are in the CodeArea's local coordinate space — convert to screen
        Bounds screen = ca.localToScreen(boundsOpt.get());
        if (screen == null) { suggestionPopup.hide(); return; }

        suggestionPopup.show(window, screen.getMinX(), screen.getMaxY() + 2, suggestions);
    }

    private TitledPane editorPane(String title, javafx.scene.Node content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setCollapsible(false);
        pane.setMaxHeight(Double.MAX_VALUE);
        SplitPane.setResizableWithParent(pane, true);
        return pane;
    }

    // ── Message / error panel ──────────────────────────────────────────────────

    private VBox buildMessagePanel() {
        messageArea = new TextArea();
        messageArea.setEditable(false);
        messageArea.setPrefHeight(130);
        messageArea.setMaxHeight(200);
        messageArea.getStyleClass().add("xslt-message-area");
        messageArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; "
                             + "-fx-font-size: 11px; -fx-control-inner-background: #1e1e1e; "
                             + "-fx-text-fill: #ccc;");

        Label hdr = new Label("Messages / Errors");
        hdr.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; "
                     + "-fx-padding: 3 6 3 6; -fx-text-fill: #333;");

        VBox panel = new VBox(3, hdr, messageArea);
        panel.setStyle("-fx-border-color: #ccc; -fx-border-width: 1 0 0 0; "
                       + "-fx-background-color: #f9f9f9;");
        VBox.setVgrow(messageArea, Priority.ALWAYS);
        return panel;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void browseXml() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select XML / DITA Source File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML / DITA Files",
                        "*.xml", "*.dita", "*.ditamap", "*.ditaval"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File f = fc.showOpenDialog(workbenchStage);
        if (f != null) loadXmlFile(f);
    }

    private void browseXslt() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select XSLT Stylesheet");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XSLT Files", "*.xsl", "*.xslt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File f = fc.showOpenDialog(workbenchStage);
        if (f != null) loadXsltFile(f);
    }

    private void loadXmlFile(File f) {
        try {
            currentXmlFile = f;
            xmlPathField.setText(f.getAbsolutePath());
            xmlEditor.setText(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            setStatus("XML loaded: " + f.getName());
            log.log("[XSLT] Loaded XML: " + f.getAbsolutePath());
        } catch (Exception ex) {
            appendMessage("ERROR loading XML: " + ex.getMessage());
        }
    }

    private void loadXsltFile(File f) {
        try {
            currentXsltFile = f;
            xsltPathField.setText(f.getAbsolutePath());
            xsltEditor.setText(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            setStatus("XSLT loaded: " + f.getName());
            log.log("[XSLT] Loaded stylesheet: " + f.getAbsolutePath());
        } catch (Exception ex) {
            appendMessage("ERROR loading XSLT: " + ex.getMessage());
        }
    }

    /** Load the built-in DITA→HTML stylesheet into the XSLT editor. */
    private void loadBuiltinDitaHtml() {
        try (InputStream is = XsltUIController.class
                .getResourceAsStream("/xslt/dita-to-html.xsl")) {
            if (is == null) { appendMessage("ERROR: built-in DITA→HTML template not found."); return; }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            xsltEditor.setText(content);
            currentXsltFile = null;
            xsltPathField.setText("<built-in DITA→HTML template>");
            setStatus("Built-in DITA→HTML stylesheet loaded.");
        } catch (Exception ex) {
            appendMessage("ERROR loading template: " + ex.getMessage());
        }
    }

    private void runTransform() {
        File xmlFile  = resolveXmlSource();
        File xsltFile = resolveXsltSource();

        if (xmlFile == null) { appendMessage("ERROR: XML input is empty. Open a file or type XML."); return; }
        if (xsltFile == null) { appendMessage("ERROR: XSLT stylesheet is empty. Open a file or use the editor."); return; }

        setStatus("Running transformation…");
        appendMessage("── Transform started ──────────────────────────────");
        outputEditor.clear();
        runBtn.setDisable(true);

        final File xml  = xmlFile;
        final File xslt = xsltFile;

        executor.submit(() -> {
            XsltExecutionService.TransformResult result =
                    executionService.transform(xml, xslt, Map.of());

            Platform.runLater(() -> {
                runBtn.setDisable(false);

                if (result.isSuccess()) {
                    lastOutput = result.output();
                    outputEditor.setText(lastOutput);
                    setStatus("Transform complete. Output: "
                              + lastOutput.length() + " chars.");
                    appendMessage("── Transform OK (" + lastOutput.length() + " chars) ─────────");
                } else {
                    appendMessage("ERROR: " + result.errorMessage());
                    setStatus("Transform failed.");
                }

                if (result.messages() != null && !result.messages().isBlank()) {
                    appendMessage("── xsl:message / compile notices ──────────────");
                    appendMessage(result.messages().strip());
                }
            });
        });
    }

    private void validateXslt() {
        File xsltFile = resolveXsltSource();
        if (xsltFile == null) {
            appendMessage("ERROR: No XSLT stylesheet to validate.");
            return;
        }

        appendMessage("── Validation started: " + xsltFile.getName() + " ───");
        setStatus("Validating XSLT…");

        executor.submit(() -> {
            List<XsltValidationError> errors = validationService.validate(xsltFile);

            Platform.runLater(() -> {
                if (errors.isEmpty()) {
                    appendMessage("OK — stylesheet is valid.");
                    setStatus("Validation passed.");
                } else {
                    long errCount  = errors.stream()
                            .filter(e -> e.getSeverity() == XsltValidationError.Severity.ERROR)
                            .count();
                    long warnCount = errors.size() - errCount;
                    errors.forEach(e -> appendMessage(e.toString()));
                    setStatus("Validation: " + errCount + " error(s), " + warnCount + " warning(s).");
                }
            });
        });
    }

    private void saveOutput() {
        if (lastOutput.isBlank()) {
            appendMessage("No output yet — run a transformation first.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Transformation Output");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("HTML Files", "*.html"),
                new FileChooser.ExtensionFilter("XML Files",  "*.xml"),
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files",  "*.*"));
        fc.setInitialFileName("output.html");
        File f = fc.showSaveDialog(workbenchStage);
        if (f == null) return;
        try {
            executionService.saveToFile(lastOutput, f);
            appendMessage("Output saved → " + f.getAbsolutePath());
            setStatus("Saved: " + f.getName());
        } catch (Exception ex) {
            appendMessage("ERROR saving file: " + ex.getMessage());
        }
    }

    private void clearOutputAndMessages() {
        outputEditor.clear();
        messageArea.clear();
        lastOutput = "";
        setStatus("Cleared.");
    }

    // ── File resolution helpers ────────────────────────────────────────────────

    /**
     * Return the XML source file. Uses the loaded file if present; otherwise
     * writes the editor content to a temp file.
     */
    private File resolveXmlSource() {
        if (currentXmlFile != null && currentXmlFile.exists()) return currentXmlFile;
        String text = xmlEditor.getText();
        if (text.isBlank()) return null;
        return writeTempFile("xslt-xml-src-", ".xml", text);
    }

    /**
     * Return the XSLT source file. Uses the loaded file if present; otherwise
     * writes the editor text (useful for the "built-in template" case) to a temp file.
     */
    private File resolveXsltSource() {
        if (currentXsltFile != null && currentXsltFile.exists()) return currentXsltFile;
        String text = xsltEditor.getText();
        if (text.isBlank()) return null;
        return writeTempFile("xslt-style-", ".xsl", text);
    }

    private File writeTempFile(String prefix, String suffix, String content) {
        try {
            File tmp = File.createTempFile(prefix, suffix);
            tmp.deleteOnExit();
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            return tmp;
        } catch (Exception ex) {
            appendMessage("Cannot write temp file: " + ex.getMessage());
            return null;
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
        log.log("[XSLT Workbench] " + msg);
    }

    private void appendMessage(String msg) {
        if (messageArea == null) return;
        messageArea.appendText(msg + "\n");
        messageArea.setScrollTop(Double.MAX_VALUE);
    }
}
