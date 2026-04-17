package com.ditadesigner.xslt;

import com.ditadesigner.util.LogService;
import com.ditadesigner.xml.XmlCoreService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    enum MessageType { ERROR, WARNING, INFO, XSL_MESSAGE }

    record MessageEntry(MessageType type, int line, String text, Instant timestamp) {}

    record ParamEntry(SimpleStringProperty name, SimpleStringProperty value) {
        ParamEntry(String n, String v) { this(new SimpleStringProperty(n), new SimpleStringProperty(v)); }
    }

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

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

    // Detect "<xsl:" followed by a partial element name at the end of the text-before-caret
    private static final Pattern XSL_ELEM_CTX = Pattern.compile(
            "<xsl:([a-z-]*)$", Pattern.CASE_INSENSITIVE);
    // Detect xsl:call-template name="" with an open value at end of text-before-caret
    private static final Pattern CALL_TMPL_CTX = Pattern.compile(
            "call-template\\b[^>]*\\bname=[\"']([^\"'\\n]*)$", Pattern.CASE_INSENSITIVE);

    // ── Services ──────────────────────────────────────────────────────────────
    private final XsltExecutionService   executionService   = new XsltExecutionService();
    private final XsltValidationService  validationService  = new XsltValidationService();
    private final XmlCoreService         xmlCoreForSuggestions = new XmlCoreService();
    private final XPathSuggestionService suggestionService  =
            new XPathSuggestionService(xmlCoreForSuggestions);
    private final XPathSuggestionPopup   suggestionPopup    = new XPathSuggestionPopup();

    // ── Cached XSLT scan (variables, params, named templates) ────────────────
    private volatile XslVariableScanner.ScanResult cachedScan = XslVariableScanner.ScanResult.EMPTY;

    // ── Preferences keys for recent files ─────────────────────────────────────
    private static final String PREF_RECENT_XSLT = "recentXslt";
    private static final String PREF_RECENT_XML  = "recentXml";
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(XsltUIController.class);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Stage ownerStage;
    private       Stage workbenchStage;
    private       File  currentXmlFile;
    private       File  currentXsltFile;
    private       String lastOutput = "";
    private       boolean xsltDirty = false;

    // ── F-232: XSLT parameters ────────────────────────────────────────────────
    private final ObservableList<ParamEntry> xsltParams = FXCollections.observableArrayList();
    private TableView<ParamEntry>            paramsTable;

    // ── F-236: Auto-run ───────────────────────────────────────────────────────
    private ToggleButton  autoRunBtn;
    private final PauseTransition autoRunTimer = new PauseTransition(Duration.millis(800));

    // ── UI References ─────────────────────────────────────────────────────────
    private XsltEditor              xmlEditor;
    private XsltEditor              xsltEditor;
    private XsltEditor              outputEditor;
    private TextField               xmlPathField;
    private TextField               xsltPathField;
    private ListView<MessageEntry>  messageList;
    private Label                   statusLabel;
    private Button                  runBtn;

    // ── F-234: HTML preview ───────────────────────────────────────────────────
    private javafx.scene.web.WebView webView;
    private TabPane                  outputTabPane;
    private static final Pattern     HTML_METHOD_PAT =
            Pattern.compile("method=[\"'](html|xhtml)[\"']", Pattern.CASE_INSENSITIVE);

    // ── F-235: Find in output ─────────────────────────────────────────────────
    private HBox         findBar;
    private TextField    findField;
    private Label        matchLabel;
    private List<Integer> matchPositions = new ArrayList<>();
    private int          currentMatchIdx = 0;

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
        stage.setOnCloseRequest(event -> {
            if (xsltDirty) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Unsaved Changes");
                alert.setHeaderText("The XSLT stylesheet has unsaved changes.");
                alert.setContentText("Save before closing?");
                alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
                    event.consume();
                    return;
                } else if (result.get() == ButtonType.YES) {
                    saveXslt();
                }
            }
            executor.shutdown();
        });

        autoRunTimer.setOnFinished(e -> runTransform());

        BorderPane root = new BorderPane();
        VBox topArea = new VBox(buildToolBar(), buildParamPanel());
        root.setTop(topArea);
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

        // Ctrl+Shift+P → XPath Builder
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.P,
                        KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::openXPathBuilder);

        // Keyboard-first workflow shortcuts (Section Z2)
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),
                this::runTransform);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                this::validateXslt);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                this::saveXslt);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.SLASH, KeyCombination.CONTROL_DOWN),
                () -> xsltEditor.toggleLineComment());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN),
                () -> xsltEditor.duplicateCurrentLine());

        // F-235: Ctrl+F → show Find-in-Output bar when XML Output tab is active
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> {
                    if (outputTabPane != null
                            && outputTabPane.getSelectionModel().getSelectedIndex() == 0) {
                        findBar.setVisible(true);
                        findBar.setManaged(true);
                        findField.requestFocus();
                    }
                });

        // Editor ergonomics shortcuts (Section Z5)
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.CLOSE_BRACKET, KeyCombination.CONTROL_DOWN),
                () -> xsltEditor.indentSelection());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.OPEN_BRACKET, KeyCombination.CONTROL_DOWN),
                () -> xsltEditor.dedentSelection());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN),
                () -> xsltEditor.increaseFontSize());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN),
                () -> xsltEditor.decreaseFontSize());

        return stage;
    }

    // ── Tool bar ───────────────────────────────────────────────────────────────

    private VBox buildToolBar() {
        // ── Row 1: file selectors ─────────────────────────────────────────────
        xmlPathField = new TextField();
        xmlPathField.setPromptText("XML / DITA source file…");
        xmlPathField.setEditable(false);
        HBox.setHgrow(xmlPathField, Priority.ALWAYS);

        SplitMenuButton browseXmlBtn = new SplitMenuButton();
        browseXmlBtn.setText("Browse XML…");
        browseXmlBtn.setOnAction(e -> browseXml());
        Menu recentXmlMenu = new Menu("Recent XML Files");
        recentXmlMenu.setOnShowing(e -> populateRecentMenu(recentXmlMenu, PREF_RECENT_XML,
                path -> loadXmlFile(new File(path))));
        browseXmlBtn.getItems().add(recentXmlMenu);

        xsltPathField = new TextField();
        xsltPathField.setPromptText("XSLT stylesheet…");
        xsltPathField.setEditable(false);
        HBox.setHgrow(xsltPathField, Priority.ALWAYS);

        SplitMenuButton browseXsltBtn = new SplitMenuButton();
        browseXsltBtn.setText("Browse XSLT…");
        browseXsltBtn.setOnAction(e -> browseXslt());
        Menu recentXsltMenu = new Menu("Recent XSLT Files");
        recentXsltMenu.setOnShowing(e -> populateRecentMenu(recentXsltMenu, PREF_RECENT_XSLT,
                path -> loadXsltFile(new File(path))));
        browseXsltBtn.getItems().add(recentXsltMenu);

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
        Tooltip.install(runBtn, new Tooltip("Run transformation (Ctrl+R)"));

        Button validateBtn = new Button("✔  Validate XSLT");
        validateBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; "
                             + "-fx-padding: 5 12 5 12;");
        validateBtn.setOnAction(e -> validateXslt());
        Tooltip.install(validateBtn, new Tooltip("Validate XSLT stylesheet (Ctrl+E)"));

        Button saveXsltBtn = new Button("Save XSLT");
        saveXsltBtn.setOnAction(e -> saveXslt());
        Tooltip.install(saveXsltBtn, new Tooltip("Save XSLT editor content (Ctrl+S)"));

        Button saveOutputBtn = new Button("Save Output…");
        saveOutputBtn.setOnAction(e -> saveOutput());

        Button clearBtn = new Button("Clear All");
        clearBtn.setOnAction(e -> clearOutputAndMessages());

        statusLabel = new Label("Ready — open an XML file and an XSLT stylesheet to begin.");
        statusLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        Button xpathBuilderBtn = new Button("XPath Builder…");
        xpathBuilderBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; "
                                 + "-fx-padding: 5 12 5 12;");
        Tooltip.install(xpathBuilderBtn, new Tooltip(
                "Open visual XPath Builder (Ctrl+Shift+P)"));
        xpathBuilderBtn.setOnAction(e -> openXPathBuilder());

        autoRunBtn = new ToggleButton("Auto-run");
        Tooltip.install(autoRunBtn, new Tooltip(
                "Automatically re-run the transform 800 ms after each XSLT edit"));

        HBox actionRow = new HBox(8,
                runBtn, validateBtn, saveXsltBtn,
                new Separator(Orientation.VERTICAL),
                saveOutputBtn, clearBtn,
                new Separator(Orientation.VERTICAL),
                xpathBuilderBtn,
                new Separator(Orientation.VERTICAL),
                autoRunBtn,
                statusLabel);
        actionRow.setPadding(new Insets(5, 8, 5, 8));
        actionRow.setStyle("-fx-alignment: CENTER_LEFT;");

        VBox toolbar = new VBox(fileRow, new Separator(), actionRow);
        toolbar.setStyle("-fx-background-color: #f5f5f5; "
                         + "-fx-border-color: #d0d0d0; -fx-border-width: 0 0 1 0;");
        return toolbar;
    }

    // ── Parameter panel (F-232) ────────────────────────────────────────────────

    private TitledPane buildParamPanel() {
        TableColumn<ParamEntry, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cd -> cd.getValue().name());
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> e.getRowValue().name().set(e.getNewValue()));
        nameCol.setPrefWidth(180);

        TableColumn<ParamEntry, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(cd -> cd.getValue().value());
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setOnEditCommit(e -> e.getRowValue().value().set(e.getNewValue()));
        valueCol.setPrefWidth(300);

        paramsTable = new TableView<>(xsltParams);
        paramsTable.setEditable(true);
        paramsTable.getColumns().addAll(nameCol, valueCol);
        paramsTable.setPrefHeight(120);
        paramsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button addBtn = new Button("Add");
        addBtn.setOnAction(e -> {
            xsltParams.add(new ParamEntry("", ""));
            paramsTable.getSelectionModel().selectLast();
        });

        Button removeBtn = new Button("Remove");
        removeBtn.setOnAction(e -> {
            ParamEntry sel = paramsTable.getSelectionModel().getSelectedItem();
            if (sel != null) xsltParams.remove(sel);
        });

        HBox buttons = new HBox(6, addBtn, removeBtn);
        buttons.setPadding(new Insets(4, 0, 2, 0));

        VBox content = new VBox(4, paramsTable, buttons);
        content.setPadding(new Insets(6));

        TitledPane pane = new TitledPane("Parameters", content);
        pane.setExpanded(false);
        return pane;
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

        // Right: output (read-only) with HTML preview tab and find bar
        outputEditor = new XsltEditor(true);
        webView      = new javafx.scene.web.WebView();
        Tab xmlTab   = new Tab("XML Output",    outputEditor);
        Tab htmlTab  = new Tab("HTML Preview",  webView);
        xmlTab.setClosable(false);
        htmlTab.setClosable(false);
        outputTabPane = new TabPane(xmlTab, htmlTab);
        VBox.setVgrow(outputTabPane, Priority.ALWAYS);

        findBar = buildFindBar();
        VBox outputArea = new VBox(outputTabPane, findBar);
        VBox.setVgrow(outputArea, Priority.ALWAYS);
        TitledPane outputPane = editorPane("Output Preview", outputArea);

        SplitPane mainSplit = new SplitPane(leftSplit, outputPane);
        mainSplit.setDividerPositions(0.50);

        installSuggestions();
        return mainSplit;
    }

    // ── XPath smart suggestions + XSL IDE completion ──────────────────────────

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

        // Re-scan XSLT for variables/params/named-templates on text change; track dirty state
        xsltEditor.getCodeArea().textProperty().addListener((obs, old, text) -> {
            if (text != null) {
                cachedScan = XslVariableScanner.scan(text);
                if (!xsltDirty) {
                    xsltDirty = true;
                    if (workbenchStage != null) {
                        String title = workbenchStage.getTitle();
                        if (!title.startsWith("* ")) {
                            workbenchStage.setTitle("* " + title);
                        }
                    }
                }
                if (autoRunBtn != null && autoRunBtn.isSelected()) {
                    autoRunTimer.stop();
                    autoRunTimer.playFromStart();
                }
            }
        });

        // Trigger suggestions on every caret move in the XSLT editor
        xsltEditor.getCodeArea().caretPositionProperty().addListener(
                (obs, old, pos) -> triggerSuggestions(
                        xsltEditor.getCodeArea().getText(),
                        xsltEditor.getCodeArea().getCaretPosition()));

        // Accept: dispatch to the correct insertion strategy
        suggestionPopup.setOnAccept(completion -> {
            CodeArea ca      = xsltEditor.getCodeArea();
            int caretPos     = ca.getCaretPosition();
            String text      = ca.getText();
            String before    = caretPos > 0 ? text.substring(0, caretPos) : "";

            // XSL element snippet insertion
            if (completion.startsWith("xsl:")) {
                String localName = completion.substring(4);
                XslKnowledgeBase.element(localName).ifPresentOrElse(
                        el -> insertXslSnippet(ca, before, caretPos, el),
                        () -> replaceToken(ca, caretPos, before, completion));
                return;
            }
            // $variable insertion — strip leading $ from prefix length
            String varPrefix = XslVariableScanner.detectVarPrefix(text, caretPos);
            if (varPrefix != null && completion.startsWith("$")) {
                int start = caretPos - varPrefix.length() - 1; // -1 for the '$'
                if (start >= 0) ca.replaceText(start, caretPos, completion);
                return;
            }
            // Named-template name — replace inside name="" value
            Matcher ctm = CALL_TMPL_CTX.matcher(before);
            if (ctm.find()) {
                int start = caretPos - ctm.group(1).length();
                if (start >= 0) ca.replaceText(start, caretPos, completion);
                return;
            }
            // XSL element-name completion after "<xsl:"
            Matcher xem = XSL_ELEM_CTX.matcher(before);
            if (xem.find()) {
                int start = caretPos - xem.group(1).length();
                if (start >= 0) {
                    String localName = completion.startsWith("xsl:") ? completion.substring(4) : completion;
                    XslKnowledgeBase.element(localName).ifPresentOrElse(
                            el -> insertXslSnippet(ca, before.substring(0, start - 4), start - 4, el),
                            () -> ca.replaceText(start, caretPos, completion));
                }
                return;
            }
            // Default: XPath attribute value prefix replacement
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
     * Insert an XSL snippet into the editor, replacing the opening tag typed so far.
     * The {@code |} marker in the snippet is removed and the caret is placed there.
     */
    private void insertXslSnippet(CodeArea ca, String before, int tagStart, XslKnowledgeBase.XslElement el) {
        // Find where "<xsl:" starts (searching backward from tagStart)
        int openTag = before.lastIndexOf("<xsl:");
        if (openTag < 0) openTag = tagStart;
        String snippet     = el.snippet();
        int    cursorMark  = snippet.indexOf('|');
        String insertText  = snippet.replace("|", "");
        ca.replaceText(openTag, tagStart, insertText);
        if (cursorMark >= 0) ca.moveTo(openTag + cursorMark);
    }

    /** Generic token replace: remove typed prefix before caret and insert completion. */
    private void replaceToken(CodeArea ca, int caretPos, String before, String completion) {
        // Find start of current word token before caret
        int i = before.length() - 1;
        while (i >= 0 && !Character.isWhitespace(before.charAt(i))
                && before.charAt(i) != '<' && before.charAt(i) != '"'
                && before.charAt(i) != '\'') i--;
        int start = i + 1;
        ca.replaceText(start, caretPos, completion);
    }

    /**
     * Detect context at the current caret position and show or hide the popup.
     * Priority order: $var → &lt;xsl: element → call-template name → XPath attribute value.
     */
    private void triggerSuggestions(String xsltText, int caretPos) {
        if (caretPos < 0) return;
        String before = caretPos > 0 ? xsltText.substring(0, caretPos) : "";

        // 1. $variable context
        String varPrefix = XslVariableScanner.detectVarPrefix(xsltText, caretPos);
        if (varPrefix != null) {
            showSuggestionPopup(XslVariableScanner.varSuggestions(cachedScan, varPrefix));
            return;
        }

        // 2. <xsl: element name context
        Matcher xem = XSL_ELEM_CTX.matcher(before);
        if (xem.find()) {
            String typed = xem.group(1);
            List<String> elems = XslKnowledgeBase.elementNames().stream()
                    .filter(n -> n.startsWith(typed))
                    .map(n -> "xsl:" + n)
                    .toList();
            showSuggestionPopup(elems);
            return;
        }

        // 3. call-template name="" context
        Matcher ctm = CALL_TMPL_CTX.matcher(before);
        if (ctm.find()) {
            showSuggestionPopup(
                    XslVariableScanner.templateNameSuggestions(cachedScan, ctm.group(1)));
            return;
        }

        // 4. XPath attribute value (select / match / test) — needs XML model
        if (!suggestionService.hasModel()) { suggestionPopup.hide(); return; }
        XPathSuggestionService.SuggestionContext ctx =
                suggestionService.detectContext(xsltText, caretPos);
        if (ctx.context() == XmlStructureModel.AttributeContext.NONE) {
            suggestionPopup.hide();
            return;
        }
        showSuggestionPopup(suggestionService.getSuggestions(ctx.context(), ctx.prefix()));
    }

    /** Position and display the suggestion popup below the editor caret. */
    private void showSuggestionPopup(List<String> suggestions) {
        if (suggestions.isEmpty()) { suggestionPopup.hide(); return; }

        CodeArea ca = xsltEditor.getCodeArea();
        if (ca.getScene() == null) return;
        Window window = ca.getScene().getWindow();

        Optional<Bounds> boundsOpt = ca.caretBoundsProperty().getValue();
        if (boundsOpt.isEmpty()) { suggestionPopup.hide(); return; }

        Bounds screen = ca.localToScreen(boundsOpt.get());
        if (screen == null) { suggestionPopup.hide(); return; }

        suggestionPopup.show(window, screen.getMinX(), screen.getMaxY() + 2, suggestions);
    }

    // ── XPath Builder ─────────────────────────────────────────────────────────

    /** Open the visual XPath Builder dialog and insert the result at the XSLT caret. */
    private void openXPathBuilder() {
        XmlStructureModel model = suggestionService.hasModel()
                ? null   // model is package-private; pass null — dialog uses xmlCore directly
                : null;
        XPathBuilderDialog dlg = new XPathBuilderDialog(
                workbenchStage, model, xmlCoreForSuggestions);
        dlg.setMatchCounter(suggestionService::countMatches);

        dlg.showAndWait().ifPresent(xpath -> {
            CodeArea ca = xsltEditor.getCodeArea();
            int pos = ca.getCaretPosition();
            ca.insertText(pos, xpath);
        });
    }

    private TitledPane editorPane(String title, javafx.scene.Node content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setCollapsible(false);
        pane.setMaxHeight(Double.MAX_VALUE);
        SplitPane.setResizableWithParent(pane, true);
        return pane;
    }

    // ── F-235: Find-in-Output bar ─────────────────────────────────────────────

    private HBox buildFindBar() {
        findField  = new TextField();
        findField.setPromptText("Find in output…");
        findField.setPrefWidth(220);
        matchLabel = new Label();
        Button btnPrev  = new Button("▲");
        Button btnNext  = new Button("▼");
        Button btnClose = new Button("✕");

        findField.textProperty().addListener((obs, o, term) -> {
            matchPositions.clear();
            currentMatchIdx = 0;
            if (!term.isBlank()) {
                String hay  = outputEditor.getCodeArea().getText().toLowerCase();
                String needle = term.toLowerCase();
                int idx = 0;
                while ((idx = hay.indexOf(needle, idx)) >= 0) {
                    matchPositions.add(idx);
                    idx += needle.length();
                }
            }
            matchLabel.setText(matchPositions.isEmpty() ? "" : "1 / " + matchPositions.size());
            if (!matchPositions.isEmpty()) highlightMatch(0, term.length());
        });

        btnNext.setOnAction(e -> {
            if (matchPositions.isEmpty()) return;
            currentMatchIdx = (currentMatchIdx + 1) % matchPositions.size();
            highlightMatch(currentMatchIdx, findField.getText().length());
        });
        btnPrev.setOnAction(e -> {
            if (matchPositions.isEmpty()) return;
            currentMatchIdx = (currentMatchIdx - 1 + matchPositions.size()) % matchPositions.size();
            highlightMatch(currentMatchIdx, findField.getText().length());
        });
        btnClose.setOnAction(e -> {
            findBar.setVisible(false);
            findBar.setManaged(false);
            outputEditor.getCodeArea().deselect();
        });
        findField.setOnKeyPressed(ke -> {
            if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) btnClose.fire();
        });

        HBox bar = new HBox(4, new Label("Find:"), findField, btnPrev, btnNext, matchLabel, btnClose);
        bar.setPadding(new Insets(3, 6, 3, 6));
        bar.setVisible(false);
        bar.setManaged(false);
        return bar;
    }

    private void highlightMatch(int idx, int termLen) {
        if (matchPositions.isEmpty()) return;
        int start = matchPositions.get(idx);
        outputEditor.getCodeArea().selectRange(start, start + termLen);
        outputEditor.getCodeArea().requestFollowCaret();
        matchLabel.setText((idx + 1) + " / " + matchPositions.size());
    }

    // ── Message / error panel ──────────────────────────────────────────────────

    private VBox buildMessagePanel() {
        messageList = new ListView<>();
        messageList.setPrefHeight(130);
        messageList.setMaxHeight(200);
        messageList.setCellFactory(lv -> new MessageCell());

        Label hdr = new Label("Messages / Errors");
        hdr.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; "
                     + "-fx-padding: 3 6 3 6; -fx-text-fill: #333;");

        Button copyBtn = new Button("Copy");
        copyBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 8 2 8;");
        copyBtn.setOnAction(e -> {
            String all = messageList.getItems().stream()
                    .map(en -> {
                        String lineTag = en.line() > 0 ? " L" + en.line() : "";
                        return "[" + TS_FMT.format(en.timestamp()) + " "
                                + en.type() + lineTag + "] " + en.text();
                    })
                    .collect(Collectors.joining("\n"));
            ClipboardContent cc = new ClipboardContent();
            cc.putString(all);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        });

        HBox header = new HBox(6, hdr, copyBtn);
        header.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 2 6 2 6;");

        VBox panel = new VBox(3, header, messageList);
        panel.setStyle("-fx-border-color: #ccc; -fx-border-width: 1 0 0 0; "
                       + "-fx-background-color: #f9f9f9;");
        VBox.setVgrow(messageList, Priority.ALWAYS);
        return panel;
    }

    private class MessageCell extends ListCell<MessageEntry> {
        @Override
        protected void updateItem(MessageEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setStyle(null);
                setOnMouseClicked(null);
                return;
            }
            setText("[" + TS_FMT.format(entry.timestamp()) + "] " + entry.text());
            String colour = switch (entry.type()) {
                case ERROR       -> "#f44747";
                case WARNING     -> "#cca700";
                case INFO        -> "#9cdcfe";
                case XSL_MESSAGE -> "#4ec9b0";
            };
            setStyle("-fx-font-family: 'Consolas','Courier New',monospace; "
                     + "-fx-font-size: 11px; -fx-text-fill: " + colour + "; "
                     + "-fx-background-color: #1e1e1e;");
            if (entry.line() > 0) {
                setOnMouseClicked(ev -> {
                    CodeArea ca = xsltEditor.getCodeArea();
                    ca.showParagraphAtTop(Math.max(0, entry.line() - 1));
                    ca.moveTo(entry.line() - 1, 0);
                    ca.requestFocus();
                });
            } else {
                setOnMouseClicked(null);
            }
        }
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
            saveRecentFile(PREF_RECENT_XML, f.getAbsolutePath());
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
            saveRecentFile(PREF_RECENT_XSLT, f.getAbsolutePath());
            clearDirty();
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

        xsltEditor.clearErrorMarkers();
        setStatus("Running transformation…");
        appendMessage("── Transform started ──────────────────────────────");
        outputEditor.clear();
        runBtn.setDisable(true);

        final File xml  = xmlFile;
        final File xslt = xsltFile;

        Map<String, String> params = xsltParams.stream()
                .filter(p -> !p.name().get().isBlank())
                .collect(Collectors.toMap(p -> p.name().get(), p -> p.value().get(),
                        (a, b) -> b));

        executor.submit(() -> {
            // Live xsl:message callback — each message gets its own teal row immediately
            XsltExecutionService.TransformResult result =
                    executionService.transform(xml, xslt, params,
                            msgText -> appendEntry(MessageType.XSL_MESSAGE, -1, msgText));

            // Count elements, attributes, and text nodes in the output (if XML)
            int[] stats = {0, 0, 0}; // [elements, attributes, textNodes]
            if (result.isSuccess() && result.output() != null && !result.output().isBlank()) {
                try {
                    SAXParserFactory spf = SAXParserFactory.newInstance();
                    spf.setNamespaceAware(true);
                    SAXParser sp = spf.newSAXParser();
                    sp.parse(new ByteArrayInputStream(
                            result.output().getBytes(StandardCharsets.UTF_8)),
                            new DefaultHandler() {
                                @Override
                                public void startElement(String uri, String local,
                                        String qName, Attributes atts) {
                                    stats[0]++;
                                    stats[1] += atts.getLength();
                                }
                                @Override
                                public void characters(char[] ch, int start, int length) {
                                    if (new String(ch, start, length).isBlank()) return;
                                    stats[2]++;
                                }
                            });
                } catch (Exception ignored) {
                    // Output may be non-XML (e.g. plain text / HTML) — skip counts
                }
            }

            final int[] finalStats = stats;
            Platform.runLater(() -> {
                runBtn.setDisable(false);

                if (result.isSuccess()) {
                    lastOutput = result.output();
                    outputEditor.setText(lastOutput);
                    // F-234: HTML preview tab
                    boolean isHtml = HTML_METHOD_PAT.matcher(xsltEditor.getText()).find();
                    webView.getEngine().loadContent(isHtml ? lastOutput : "", "text/html");
                    // F-248: status bar with element/attribute/text counts and elapsed time
                    String statMsg;
                    if (finalStats[0] > 0) {
                        statMsg = String.format(
                                "Transform complete in %dms — %d element(s), %d attribute(s), "
                                + "%d text node(s) | %d chars output",
                                result.elapsedMs(), finalStats[0], finalStats[1],
                                finalStats[2], lastOutput.length());
                    } else {
                        statMsg = String.format(
                                "Transform complete in %dms — %d chars output",
                                result.elapsedMs(), lastOutput.length());
                    }
                    setStatus(statMsg);
                    appendMessage("── Transform OK (" + result.elapsedMs() + "ms, "
                            + lastOutput.length() + " chars) ─────────");
                } else {
                    appendMessage("ERROR: " + result.errorMessage());
                    setStatus("Transform failed.");
                }

                // Compile-time notices (not xsl:message — those are already dispatched above)
                if (result.messages() != null && !result.messages().isBlank()) {
                    for (String line : result.messages().strip().split("\n")) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;
                        if (trimmed.startsWith("[COMPILE]")) {
                            appendEntry(MessageType.WARNING, -1,
                                    trimmed.substring("[COMPILE]".length()).trim());
                        } else {
                            appendEntry(MessageType.INFO, -1, trimmed);
                        }
                    }
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
                    xsltEditor.clearErrorMarkers();
                } else {
                    long errCount  = errors.stream()
                            .filter(e -> e.getSeverity() == XsltValidationError.Severity.ERROR)
                            .count();
                    long warnCount = errors.size() - errCount;
                    errors.forEach(e -> appendMessage(e.toString()));
                    setStatus("Validation: " + errCount + " error(s), " + warnCount + " warning(s).");
                    xsltEditor.setErrorMarkers(errors);
                }
            });
        });
    }

    private void saveXslt() {
        String content = xsltEditor.getText();
        if (content.isBlank()) {
            appendMessage("Nothing to save — XSLT editor is empty.");
            return;
        }
        if (currentXsltFile != null && currentXsltFile.exists()) {
            try {
                Files.writeString(currentXsltFile.toPath(), content, StandardCharsets.UTF_8);
                clearDirty();
                setStatus("XSLT saved: " + currentXsltFile.getName());
                appendMessage("Saved → " + currentXsltFile.getAbsolutePath());
            } catch (Exception ex) {
                appendMessage("ERROR saving XSLT: " + ex.getMessage());
            }
        } else {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save XSLT Stylesheet");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("XSLT Files", "*.xsl", "*.xslt"),
                    new FileChooser.ExtensionFilter("All Files", "*.*"));
            fc.setInitialFileName("stylesheet.xsl");
            File f = fc.showSaveDialog(workbenchStage);
            if (f == null) return;
            try {
                Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
                currentXsltFile = f;
                xsltPathField.setText(f.getAbsolutePath());
                clearDirty();
                setStatus("XSLT saved: " + f.getName());
                appendMessage("Saved → " + f.getAbsolutePath());
            } catch (Exception ex) {
                appendMessage("ERROR saving XSLT: " + ex.getMessage());
            }
        }
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
        webView.getEngine().loadContent("", "text/html");
        if (messageList != null) messageList.getItems().clear();
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
        appendEntry(MessageType.INFO, -1, msg);
    }

    private void appendEntry(MessageType type, int line, String text) {
        MessageEntry entry = new MessageEntry(type, line, text, Instant.now());
        Platform.runLater(() -> {
            if (messageList != null) {
                messageList.getItems().add(entry);
                messageList.scrollTo(messageList.getItems().size() - 1);
            }
        });
    }

    // ── Dirty state ────────────────────────────────────────────────────────────

    private void clearDirty() {
        xsltDirty = false;
        if (workbenchStage != null) {
            String title = workbenchStage.getTitle();
            if (title.startsWith("* ")) {
                workbenchStage.setTitle(title.substring(2));
            }
        }
    }

    // ── Recent files (Preferences-backed) ─────────────────────────────────────

    private List<String> loadRecentFiles(String key) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String val = PREFS.get(key + "_" + i, null);
            if (val != null) result.add(val);
        }
        return result;
    }

    private void saveRecentFile(String key, String path) {
        List<String> list = new ArrayList<>();
        list.add(path);
        for (int i = 0; i < 5; i++) {
            String val = PREFS.get(key + "_" + i, null);
            if (val != null && !val.equals(path)) list.add(val);
        }
        for (int i = 0; i < Math.min(list.size(), 5); i++) {
            PREFS.put(key + "_" + i, list.get(i));
        }
    }

    private void populateRecentMenu(Menu menu, String key, java.util.function.Consumer<String> loader) {
        menu.getItems().clear();
        List<String> paths = loadRecentFiles(key);
        if (paths.isEmpty()) {
            MenuItem none = new MenuItem("(no recent files)");
            none.setDisable(true);
            menu.getItems().add(none);
        } else {
            for (String path : paths) {
                MenuItem item = new MenuItem(path);
                item.setOnAction(e -> loader.accept(path));
                menu.getItems().add(item);
            }
        }
    }
}
