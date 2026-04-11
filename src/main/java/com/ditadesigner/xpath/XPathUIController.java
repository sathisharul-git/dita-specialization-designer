package com.ditadesigner.xpath;

import com.ditadesigner.util.LogService;
import com.ditadesigner.xml.XmlCoreService;
import com.ditadesigner.xml.XmlParseResult;
import com.ditadesigner.xslt.XsltEditor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Non-modal window providing the XPath Checker UI.
 *
 * <p>Layout:
 * <ul>
 *   <li>Top — toolbar (Load XML, Validate XML, Validate vs XSD) + XPath expression row
 *       + optional namespace declarations</li>
 *   <li>Center — horizontal split: left = syntax-highlighted XML editor,
 *       right = result list</li>
 *   <li>Bottom — VS Code-style status bar</li>
 * </ul>
 */
public class XPathUIController {

    private static final LogService log = LogService.getInstance();

    private static final String SAMPLE_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!-- Sample DITA topic — replace with your XML -->\n"
            + "<topic id=\"sample\">\n"
            + "  <title>Sample Topic</title>\n"
            + "  <shortdesc>A short description.</shortdesc>\n"
            + "  <body>\n"
            + "    <p>First paragraph.</p>\n"
            + "    <p>Second paragraph.</p>\n"
            + "    <ul>\n"
            + "      <li>Item one</li>\n"
            + "      <li>Item two</li>\n"
            + "    </ul>\n"
            + "  </body>\n"
            + "</topic>\n";

    private final Stage ownerStage;
    private final XmlCoreService xmlCore;
    private final XPathEvaluationService evalService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xpath-eval");
        t.setDaemon(true);
        return t;
    });

    // UI components
    private Stage stage;
    private XsltEditor xmlEditor;
    private TextField xpathField;
    private TextArea namespacesArea;
    private TextArea resultsArea;
    private Label resultCountLabel;
    private Label statusLabel;

    public XPathUIController(Stage ownerStage, XmlCoreService xmlCore) {
        this.ownerStage  = ownerStage;
        this.xmlCore     = xmlCore;
        this.evalService = new XPathEvaluationService(xmlCore);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void show() {
        ensureStage();
        stage.show();
        stage.toFront();
    }

    public void show(String xmlText) {
        ensureStage();
        if (xmlText != null && !xmlText.isBlank()) {
            xmlEditor.setText(xmlText);
        }
        stage.show();
        stage.toFront();
    }

    // ── Stage construction ─────────────────────────────────────────────────────

    private void ensureStage() {
        if (stage == null) {
            stage = buildStage();
        }
    }

    private Stage buildStage() {
        Stage s = new Stage();
        s.initOwner(ownerStage);
        s.initModality(Modality.NONE);
        s.setTitle("XPath Checker — DITA Specialization Designer");
        s.setWidth(1100);
        s.setHeight(700);
        s.setOnCloseRequest(e -> executor.shutdown());

        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());

        s.setScene(new Scene(root));
        return s;
    }
    private VBox buildTopBar() {
        // Toolbar row
        Button loadXmlBtn     = new Button("Load XML...");
        Button prettyPrintBtn = new Button("Pretty Print XML");
        Button validateBtn    = new Button("Well-formed?");
        Button validateXsdBtn = new Button("Validate vs XSD...");
        loadXmlBtn.setOnAction(e -> loadXml());
        prettyPrintBtn.setOnAction(e -> prettyPrintXml());
        validateBtn.setOnAction(e -> validateWellFormed());
        validateXsdBtn.setOnAction(e -> validateAgainstXsd());

        ToolBar xmlBar = new ToolBar(loadXmlBtn, prettyPrintBtn, new Separator(), validateBtn, validateXsdBtn);

        // XPath expression row
        Label xpathLabel = new Label("XPath:");
        xpathLabel.setStyle("-fx-font-weight:bold;");

        xpathField = new TextField();
        xpathField.setPromptText("e.g. //topic/title  |  //*[@id]  |  count(//p)");
        xpathField.setOnAction(e -> evaluate());
        HBox.setHgrow(xpathField, Priority.ALWAYS);

        Button evalBtn = new Button("Evaluate");
        evalBtn.setStyle("-fx-background-color:#007acc; -fx-text-fill:white;");
        evalBtn.setDefaultButton(true);
        evalBtn.setOnAction(e -> evaluate());

        Button checkBtn = new Button("Check XPath");
        checkBtn.setOnAction(e -> checkXPathSyntax());

        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> {
            resultsArea.clear();
            resultCountLabel.setText("Results");
        });

        HBox xpathRow = new HBox(8, xpathLabel, xpathField, checkBtn, evalBtn, clearBtn);
        xpathRow.setPadding(new Insets(4, 8, 4, 8));
        xpathRow.setAlignment(Pos.CENTER_LEFT);

        // Namespace declarations row
        Label nsLabel = new Label("Namespaces (prefix=uri, one per line):");
        nsLabel.setStyle("-fx-font-size:10px;");

        namespacesArea = new TextArea();
        namespacesArea.setPrefRowCount(2);
        namespacesArea.setPromptText(
                "dita=http://dita.oasis-open.org/architecture/2005/\n"
                + "xs=http://www.w3.org/2001/XMLSchema");
        namespacesArea.setStyle("-fx-font-size:10px; -fx-font-family:monospace;");

        HBox nsRow = new HBox(8, nsLabel, namespacesArea);
        nsRow.setPadding(new Insets(2, 8, 4, 8));
        nsRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(namespacesArea, Priority.ALWAYS);

        return new VBox(xmlBar, xpathRow, nsRow);
    }
    private SplitPane buildCenter() {
        // Left panel — XML editor
        xmlEditor = new XsltEditor();
        xmlEditor.setText(SAMPLE_XML);

        Label xmlTitle = new Label("XML Input");
        xmlTitle.setStyle("-fx-font-weight:bold; -fx-padding: 4 4 2 4;");

        VBox xmlPanel = new VBox(xmlTitle, xmlEditor);
        VBox.setVgrow(xmlEditor, Priority.ALWAYS);

        // Right panel — results
        resultCountLabel = new Label("Results");
        resultCountLabel.setStyle("-fx-font-weight:bold; -fx-padding: 4 4 2 4;");

        resultsArea = new TextArea();
        resultsArea.setEditable(false);
        resultsArea.setStyle("-fx-font-family:monospace; -fx-font-size:12px;");
        resultsArea.setPromptText("XPath evaluation results appear here…");

        VBox resultsPanel = new VBox(4, resultCountLabel, resultsArea);
        resultsPanel.setPadding(new Insets(0, 4, 4, 4));
        VBox.setVgrow(resultsArea, Priority.ALWAYS);

        SplitPane split = new SplitPane(xmlPanel, resultsPanel);
        split.setDividerPositions(0.55);
        return split;
    }

    private HBox buildStatusBar() {
        statusLabel = new Label("Ready — enter an XPath expression and press Enter or ▶ Evaluate");
        statusLabel.setStyle("-fx-text-fill:white; -fx-font-size:11px; -fx-padding:3 8 3 8;");
        HBox bar = new HBox(statusLabel);
        bar.setStyle("-fx-background-color:#007acc;");
        return bar;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void evaluate() {
        String xmlText = xmlEditor.getText().trim();
        String expr    = xpathField.getText().trim();

        if (xmlText.isEmpty()) { setStatus("No XML content — load or type an XML document first."); return; }
        if (expr.isEmpty())    { setStatus("No XPath expression — enter one in the field above.");  return; }

        Map<String, String> ns = parseNamespaces();
        setStatus("Evaluating…");
        resultsArea.clear();

        executor.submit(() -> {
            XPathEvaluationService.XPathResult result = evalService.evaluate(xmlText, expr, ns);
            Platform.runLater(() -> displayResult(result, expr));
        });
    }

    private void displayResult(XPathEvaluationService.XPathResult result, String expr) {
        if (!result.success()) {
            resultsArea.setText(result.errorMessage());
            resultCountLabel.setText("Error");
            setStatus("XPath error — see results panel.");
            return;
        }

        List<String> items = result.items();
        resultCountLabel.setText("Results: " + items.size() + " match(es)   for:  " + expr);

        if (items.isEmpty()) {
            resultsArea.setText("(no matches)");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                sb.append('[').append(i + 1).append("] ").append(items.get(i)).append('\n');
            }
            resultsArea.setText(sb.toString());
        }
        setStatus("XPath evaluated — " + items.size() + " match(es).");
    }

    private void validateWellFormed() {
        String xmlText = xmlEditor.getText().trim();
        if (xmlText.isEmpty()) { setStatus("No XML content to validate."); return; }

        XmlParseResult parsed = xmlCore.parse(xmlText);
        resultCountLabel.setText("Well-formedness Check");
        if (parsed.isWellFormed()) {
            resultsArea.setText("✓  XML is well-formed.");
            setStatus("XML is well-formed.");
            log.logSuccess("XPath Checker: XML is well-formed.");
        } else {
            resultsArea.setText("✗  XML errors:\n\n" + String.join("\n", parsed.errors()));
            setStatus("XML has errors — see results panel.");
        }
    }

    private void validateAgainstXsd() {
        String xmlText = xmlEditor.getText().trim();
        if (xmlText.isEmpty()) { setStatus("No XML content to validate."); return; }

        FileChooser fc = new FileChooser();
        fc.setTitle("Select XSD Schema");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD files", "*.xsd"));
        File xsdFile = fc.showOpenDialog(stage);
        if (xsdFile == null) return;

        String schemaName = xsdFile.getName();
        setStatus("Validating against " + schemaName + "…");

        executor.submit(() -> {
            List<String> errors = xmlCore.validateAgainstXsd(xmlText, xsdFile);
            Platform.runLater(() -> {
                resultCountLabel.setText("XSD Validation — " + schemaName);
                if (errors.isEmpty()) {
                    resultsArea.setText("✓  XML is valid against " + schemaName);
                    setStatus("Valid against " + schemaName);
                    log.logSuccess("XPath Checker: XML valid against " + schemaName);
                } else {
                    resultsArea.setText("✗  Errors against " + schemaName + ":\n\n"
                            + String.join("\n", errors));
                    setStatus("Validation failed — " + errors.size() + " error(s).");
                }
            });
        });
    }

    private void checkXPathSyntax() {
        String expr = xpathField.getText().trim();
        if (expr.isEmpty()) {
            setStatus("No XPath expression - enter one in the field above.");
            return;
        }

        Map<String, String> ns = parseNamespaces();
        setStatus("Checking XPath syntax...");
        resultsArea.clear();

        executor.submit(() -> {
            XPathEvaluationService.ExpressionCheckResult result = evalService.checkExpression(expr, ns);
            Platform.runLater(() -> {
                resultCountLabel.setText("XPath Syntax Check");
                resultsArea.setText(result.message());
                if (result.valid()) {
                    setStatus("XPath syntax is valid.");
                } else {
                    setStatus("XPath syntax error - see results panel.");
                }
            });
        });
    }

    private void prettyPrintXml() {
        String xmlText = xmlEditor.getText().trim();
        if (xmlText.isEmpty()) {
            setStatus("No XML content to format.");
            return;
        }

        XmlParseResult parsed = xmlCore.parse(xmlText);
        resultCountLabel.setText("XML Pretty Print");
        if (!parsed.isWellFormed()) {
            resultsArea.setText("Cannot format invalid XML:\n\n" + String.join("\n", parsed.errors()));
            setStatus("XML has errors - fix well-formedness before formatting.");
            return;
        }

        try {
            String pretty = xmlCore.prettyPrint(xmlText);
            xmlEditor.setText(pretty);
            resultsArea.setText("XML formatted.");
            setStatus("XML pretty print complete.");
            log.logSuccess("XPath Checker: XML pretty print complete.");
        } catch (Exception ex) {
            resultsArea.setText("Pretty print failed:\n\n" + ex.getMessage());
            setStatus("Pretty print failed - see results panel.");
        }
    }

    private void loadXml() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load XML File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML / DITA files", "*.xml", "*.dita", "*.ditamap"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        try {
            String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            xmlEditor.setText(content);
            setStatus("Loaded: " + f.getName() + "  (" + content.length() + " chars)");
            log.log("[XPath Checker] Loaded " + f.getAbsolutePath());
        } catch (IOException ex) {
            setStatus("Error loading file: " + ex.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Parse "prefix=uri" lines from the namespace textarea. */
    private Map<String, String> parseNamespaces() {
        Map<String, String> ns = new LinkedHashMap<>();
        String text = namespacesArea.getText();
        if (text == null || text.isBlank()) return ns;
        for (String line : text.split("\\R")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                ns.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return ns;
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }
}
