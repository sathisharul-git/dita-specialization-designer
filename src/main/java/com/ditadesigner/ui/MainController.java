package com.ditadesigner.ui;

import com.ditadesigner.generator.CatalogGenerator;
import com.ditadesigner.generator.DtdGenerator;
import com.ditadesigner.generator.XsdGenerator;
import com.ditadesigner.model.*;
import com.ditadesigner.repository.ProjectRepository;
import com.ditadesigner.service.OasisLoaderService;
import com.ditadesigner.service.ProjectService;
import com.ditadesigner.transformer.ModelTransformer;
import com.ditadesigner.ui.nodes.ConnectionLine;
import com.ditadesigner.ui.nodes.DiagramNode;
import com.ditadesigner.ui.nodes.ElementNode;
import com.ditadesigner.ui.nodes.TopicTypeNode;
import com.ditadesigner.util.FileUtil;
import com.ditadesigner.util.LogService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.transform.Scale;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;

public class MainController implements Initializable {

    // ── FXML injected ─────────────────────────────────────────────────

    @FXML private MenuBar menuBar;
    @FXML private Menu recentProjectsMenu;
    @FXML private Label zoomLabel;

    @FXML private Pane canvas;
    @FXML private ScrollPane canvasScrollPane;
    @FXML private TextArea logArea;
    @FXML private VBox propertiesPanel;
    @FXML private Label statusLabel;
    @FXML private Label propertiesPlaceholder;

    // Toolbox toggles
    @FXML private ToggleButton btnSelect;
    @FXML private ToggleButton btnAddTopicType;
    @FXML private ToggleButton btnAddElement;
    @FXML private ToggleButton btnAddDomain;
    @FXML private ToggleButton btnConnectInherit;
    @FXML private ToggleButton btnConnectContain;

    // ── tool mode ─────────────────────────────────────────────────────

    private enum Tool { SELECT, ADD_TOPIC_TYPE, ADD_ELEMENT, ADD_DOMAIN, CONNECT_INHERIT, CONNECT_CONTAIN }
    private Tool currentTool = Tool.SELECT;
    private ToggleGroup toolGroup;

    // ── services ──────────────────────────────────────────────────────

    private final LogService log = LogService.getInstance();
    private final ProjectService projectService = new ProjectService();
    private final OasisLoaderService oasisLoader = new OasisLoaderService();
    private final ProjectRepository repository = new ProjectRepository();
    private final DtdGenerator dtdGenerator = new DtdGenerator();
    private final XsdGenerator xsdGenerator = new XsdGenerator();
    private final CatalogGenerator catalogGenerator = new CatalogGenerator();
    private final ModelTransformer transformer = new ModelTransformer();

    // ── state ─────────────────────────────────────────────────────────

    private DitaModel currentModel;
    private File currentProjectFile;
    private DiagramNode selectedDiagramNode;
    private DiagramNode connectionSourceNode;

    private final Map<String, TopicTypeNode> topicTypeNodeMap = new LinkedHashMap<>();
    private final Map<String, ElementNode>   elementNodeMap   = new LinkedHashMap<>();
    private final List<ConnectionLine>       connectionLines  = new ArrayList<>();
    private final Map<String, ConnectionLine> connectionLineMap = new LinkedHashMap<>();

    // ── zoom ──────────────────────────────────────────────────────────

    private double zoomScale = 1.0;
    private final Scale canvasScale = new Scale(1.0, 1.0, 0, 0);

    // ── dirty / unsaved tracking ──────────────────────────────────────

    private boolean dirty = false;

    // ── recent projects ───────────────────────────────────────────────

    private final List<File> recentFiles = new ArrayList<>();

    // ── undo stack (lightweight: store inverse Runnables) ─────────────

    private final Deque<Runnable> undoStack = new ArrayDeque<>();

    // ── initialize ────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.setLogArea(logArea);
        setupToolGroup();
        canvas.getTransforms().add(canvasScale);
        setupCanvas();
        setupKeyboardShortcuts();
        onNewProject();
        log.log("DITA Specialization Designer ready.");
        log.log("Tip: Ctrl+Scroll to zoom · Delete to remove · Escape to deselect · F1 for shortcuts");
    }

    private void setupToolGroup() {
        toolGroup = new ToggleGroup();
        btnSelect.setToggleGroup(toolGroup);
        btnAddTopicType.setToggleGroup(toolGroup);
        btnAddElement.setToggleGroup(toolGroup);
        btnAddDomain.setToggleGroup(toolGroup);
        btnConnectInherit.setToggleGroup(toolGroup);
        btnConnectContain.setToggleGroup(toolGroup);
        btnSelect.setSelected(true);
    }

    private void setupCanvas() {
        canvas.setOnMouseClicked(e -> {
            if (e.getTarget() == canvas && e.getButton() == MouseButton.PRIMARY) {
                handleCanvasClick(e.getX(), e.getY());
            }
        });

        // Right-click context menu on canvas
        setupCanvasContextMenu();

        // Zoom with Ctrl+Scroll on the scroll pane
        canvasScrollPane.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) {
                double factor = e.getDeltaY() > 0 ? 1.1 : 0.9;
                zoomTo(zoomScale * factor);
                e.consume();
            }
        });
    }

    private void setupCanvasContextMenu() {
        final double[] clickXY = {0, 0};
        ContextMenu cm = new ContextMenu();

        MenuItem addTopic  = new MenuItem("⬛ Add Topic Type here");
        MenuItem addElem   = new MenuItem("◻ Add Element here");
        MenuItem addDomain = new MenuItem("◈ Add Domain here");
        SeparatorMenuItem sep1 = new SeparatorMenuItem();
        MenuItem fitScreen = new MenuItem("⊡ Fit to Screen");
        MenuItem zoomIn    = new MenuItem("Zoom In (Ctrl++)");
        MenuItem zoomOut   = new MenuItem("Zoom Out (Ctrl+-)");
        MenuItem zoomReset = new MenuItem("Reset Zoom (Ctrl+0)");

        addTopic.setOnAction(e  -> addTopicTypeAtPosition(clickXY[0], clickXY[1]));
        addElem.setOnAction(e   -> addElementAtPosition(clickXY[0], clickXY[1]));
        addDomain.setOnAction(e -> addDomainAtPosition(clickXY[0], clickXY[1]));
        fitScreen.setOnAction(e -> fitToScreen());
        zoomIn.setOnAction(e    -> zoomTo(zoomScale * 1.2));
        zoomOut.setOnAction(e   -> zoomTo(zoomScale / 1.2));
        zoomReset.setOnAction(e -> zoomTo(1.0));

        cm.getItems().addAll(addTopic, addElem, addDomain, sep1, fitScreen, zoomIn, zoomOut, zoomReset);

        canvas.setOnContextMenuRequested(e -> {
            if (e.getTarget() == canvas) {
                clickXY[0] = e.getX();
                clickXY[1] = e.getY();
                cm.show(canvas, e.getScreenX(), e.getScreenY());
                e.consume();
            }
        });
    }

    private void setupKeyboardShortcuts() {
        canvas.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKeyPress);
            }
        });
    }

    private void handleGlobalKeyPress(KeyEvent e) {
        // Don't intercept if focus is in a text input
        boolean inTextField = e.getTarget() instanceof TextField || e.getTarget() instanceof TextArea;

        if (e.getCode() == KeyCode.ESCAPE) {
            if (connectionSourceNode != null) {
                connectionSourceNode.setSelected(false);
                connectionSourceNode = null;
                setStatus("Connection cancelled.");
            } else {
                deselectAll();
                setTool(Tool.SELECT);
                btnSelect.setSelected(true);
            }
            e.consume();
        } else if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) && !inTextField) {
            deleteSelectedNode();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.A && !inTextField) {
            onSelectAll();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.Z && !inTextField) {
            onUndo();
            e.consume();
        } else if (e.isControlDown() && (e.getCode() == KeyCode.EQUALS || e.getCode() == KeyCode.ADD)) {
            zoomTo(zoomScale * 1.2);
            e.consume();
        } else if (e.isControlDown() && (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT)) {
            zoomTo(zoomScale / 1.2);
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.DIGIT0) {
            zoomTo(1.0);
            e.consume();
        }
    }

    // ── zoom ─────────────────────────────────────────────────────────

    private void zoomTo(double scale) {
        zoomScale = Math.max(0.20, Math.min(4.0, scale));
        canvasScale.setX(zoomScale);
        canvasScale.setY(zoomScale);
        if (zoomLabel != null) zoomLabel.setText(String.format("%.0f%%", zoomScale * 100));
        setStatus(String.format("Zoom: %.0f%%", zoomScale * 100));
    }

    @FXML private void onZoomIn()    { zoomTo(zoomScale * 1.2); }
    @FXML private void onZoomOut()   { zoomTo(zoomScale / 1.2); }
    @FXML private void onZoomReset() { zoomTo(1.0); }

    @FXML
    private void onFitScreen() { fitToScreen(); }

    private void fitToScreen() {
        if (topicTypeNodeMap.isEmpty() && elementNodeMap.isEmpty()) { zoomTo(1.0); return; }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (TopicTypeNode n : topicTypeNodeMap.values()) {
            minX = Math.min(minX, n.getLayoutX());
            minY = Math.min(minY, n.getLayoutY());
            double nH = n.getHeight() > 0 ? n.getHeight() : 200;
            maxX = Math.max(maxX, n.getLayoutX() + n.getPrefWidth());
            maxY = Math.max(maxY, n.getLayoutY() + nH);
        }
        for (ElementNode n : elementNodeMap.values()) {
            minX = Math.min(minX, n.getLayoutX());
            minY = Math.min(minY, n.getLayoutY());
            double nH = n.getHeight() > 0 ? n.getHeight() : 80;
            maxX = Math.max(maxX, n.getLayoutX() + n.getPrefWidth());
            maxY = Math.max(maxY, n.getLayoutY() + nH);
        }

        double contentW = (maxX - minX) + 80;
        double contentH = (maxY - minY) + 80;
        double viewW = canvasScrollPane.getWidth()  - 30;
        double viewH = canvasScrollPane.getHeight() - 30;
        double scale = Math.min(viewW / contentW, viewH / contentH);
        zoomTo(scale);
        setStatus("Fit to screen — zoom " + String.format("%.0f%%", zoomScale * 100));
    }

    // ── dirty / title management ──────────────────────────────────────

    private void markDirty() {
        dirty = true;
        updateTitle();
    }

    // ── canvas interaction ────────────────────────────────────────────

    private void handleCanvasClick(double x, double y) {
        switch (currentTool) {
            case ADD_TOPIC_TYPE -> addTopicTypeAtPosition(x, y);
            case ADD_ELEMENT    -> addElementAtPosition(x, y);
            case ADD_DOMAIN     -> addDomainAtPosition(x, y);
            case SELECT         -> deselectAll();
            default -> {}
        }
    }

    private void addTopicTypeAtPosition(double x, double y) {
        String name = promptName("New Topic Type", "topicName", "MyTopic");
        if (name == null || name.isBlank()) return;

        String baseType = chooseBaseType();
        if (baseType == null) return;

        TopicType tt = projectService.createTopicType(currentModel, name, baseType);
        tt.setX(x - 100);
        tt.setY(y - 40);
        placeTopicTypeNode(tt);
        markDirty();
        setStatus("Added TopicType: " + name);
        log.log("Added TopicType '" + name + "' (base: " + baseType + ")");
    }

    private void addElementAtPosition(double x, double y) {
        String name = promptName("New Element", "elementName", "myElement");
        if (name == null || name.isBlank()) return;

        ElementDef elem = new ElementDef(name);
        elem.setX(x - 80);
        elem.setY(y - 30);

        if (!currentModel.getTopicTypes().isEmpty()) {
            TopicType parent = currentModel.getTopicTypes().get(0);
            parent.addElement(elem);
            TopicTypeNode pn = topicTypeNodeMap.get(parent.getId());
            if (pn != null) pn.refresh();
        }

        placeElementNode(elem);
        markDirty();
        setStatus("Added Element: " + name);
    }

    private void addDomainAtPosition(double x, double y) {
        String name = promptName("New Domain", "domainName", "myDomain");
        if (name == null || name.isBlank()) return;

        DomainDef domain = projectService.createDomain(currentModel, name);
        domain.setX(x - 100);
        domain.setY(y - 40);
        placeDomainNode(domain);
        markDirty();
        setStatus("Added Domain: " + name);
    }

    // ── node placement ────────────────────────────────────────────────

    private void placeTopicTypeNode(TopicType tt) {
        TopicTypeNode node = new TopicTypeNode(tt);
        node.setOnClickCallback(this::handleNodeClick);
        node.setOnDragStopCallback((n, done) -> {
            updateAllConnections();
            if (done) markDirty();
        });
        node.setOnDeleteCallback(n -> deleteTopicTypeNodeById(tt.getId(), n));
        node.setOnDuplicateCallback(this::duplicateTopicType);
        node.setOnGenerateDtdCallback(this::generateDtdForNode);
        canvas.getChildren().add(node);
        topicTypeNodeMap.put(tt.getId(), node);
    }

    private void placeDomainNode(DomainDef domain) {
        TopicType pseudo = new TopicType(domain.getName(), "domain");
        pseudo.setId(domain.getId());
        pseudo.setX(domain.getX());
        pseudo.setY(domain.getY());
        pseudo.setDescription(domain.getDescription());

        TopicTypeNode node = new TopicTypeNode(pseudo);
        node.setOnClickCallback(this::handleNodeClick);
        node.setOnDragStopCallback((n, done) -> {
            updateAllConnections();
            if (done) markDirty();
        });
        canvas.getChildren().add(node);
        topicTypeNodeMap.put(domain.getId(), node);
    }

    private void placeElementNode(ElementDef elem) {
        ElementNode node = new ElementNode(elem);
        node.setOnClickCallback(this::handleElementNodeClick);
        node.setOnDragStopCallback((n, done) -> {
            updateAllConnections();
            if (done) markDirty();
        });
        canvas.getChildren().add(node);
        elementNodeMap.put(elem.getId(), node);
    }

    // ── delete node ──────────────────────────────────────────────────

    @FXML
    private void onDeleteSelected() { deleteSelectedNode(); }

    private void deleteSelectedNode() {
        if (selectedDiagramNode == null) return;
        String id = selectedDiagramNode.getModelId();
        if (selectedDiagramNode instanceof TopicTypeNode ttn) {
            deleteTopicTypeNodeById(id, ttn);
        } else if (selectedDiagramNode instanceof ElementNode en) {
            deleteElementNodeById(id, en);
        }
    }

    private void deleteTopicTypeNodeById(String id, TopicTypeNode node) {
        // Collect relationships first (removeTopicType will also remove them from model)
        List<Relationship> affectedRels = new ArrayList<>();
        for (Relationship rel : currentModel.getRelationships()) {
            if (rel.getSourceId().equals(id) || rel.getTargetId().equals(id)) {
                affectedRels.add(rel);
            }
        }

        currentModel.removeTopicType(id);

        for (Relationship rel : affectedRels) {
            ConnectionLine line = connectionLineMap.remove(rel.getId());
            if (line != null) {
                connectionLines.remove(line);
                canvas.getChildren().remove(line);
            }
        }

        canvas.getChildren().remove(node);
        topicTypeNodeMap.remove(id);
        selectedDiagramNode = null;
        showNoSelection();
        markDirty();
        setStatus("Deleted: " + node.getTopicType().getName());
        log.log("Deleted TopicType: " + node.getTopicType().getName());
    }

    private void deleteElementNodeById(String id, ElementNode node) {
        List<Relationship> affectedRels = new ArrayList<>();
        for (Relationship rel : currentModel.getRelationships()) {
            if (rel.getSourceId().equals(id) || rel.getTargetId().equals(id)) {
                affectedRels.add(rel);
            }
        }
        for (Relationship rel : affectedRels) {
            currentModel.getRelationships().remove(rel);
            ConnectionLine line = connectionLineMap.remove(rel.getId());
            if (line != null) {
                connectionLines.remove(line);
                canvas.getChildren().remove(line);
            }
        }
        canvas.getChildren().remove(node);
        elementNodeMap.remove(id);
        selectedDiagramNode = null;
        showNoSelection();
        markDirty();
        setStatus("Deleted element node.");
    }

    // ── duplicate topic type ─────────────────────────────────────────

    private void duplicateTopicType(TopicTypeNode sourceNode) {
        TopicType orig = sourceNode.getTopicType();
        TopicType copy = new TopicType(orig.getName() + "_copy", orig.getBaseType());
        copy.setNamespace(orig.getNamespace());
        copy.setDescription(orig.getDescription());
        copy.setX(orig.getX() + 30);
        copy.setY(orig.getY() + 30);

        for (ElementDef e : orig.getElements()) {
            ElementDef ec = new ElementDef(e.getName());
            ec.setContentModel(e.getContentModel());
            ec.setCardinality(e.getCardinality());
            ec.setRequired(e.isRequired());
            ec.setDescription(e.getDescription());
            copy.addElement(ec);
        }
        for (AttributeDef a : orig.getAttributes()) {
            AttributeDef ac = projectService.createAttribute(a.getName(), a.getType(), a.isRequired());
            ac.setDefaultValue(a.getDefaultValue());
            copy.addAttribute(ac);
        }

        currentModel.getTopicTypes().add(copy);
        placeTopicTypeNode(copy);
        markDirty();
        setStatus("Duplicated: " + orig.getName() + " → " + copy.getName());
        log.log("Duplicated '" + orig.getName() + "' as '" + copy.getName() + "'");
    }

    // ── generate DTD for single node ─────────────────────────────────

    private void generateDtdForNode(TopicTypeNode node) {
        File outputDir = chooseOutputDir();
        if (outputDir == null) return;
        try {
            DitaModel tempModel = new DitaModel();
            tempModel.setName(currentModel.getName());
            tempModel.getTopicTypes().add(node.getTopicType());
            dtdGenerator.generate(tempModel, outputDir);
            String name = node.getTopicType().getName();
            log.logSuccess("DTD generated for '" + name + "' → " + outputDir.getPath());
            setStatus("DTD generated for: " + name);
        } catch (Exception ex) {
            showError("DTD Generation Failed", ex.getMessage());
            log.logError("DTD generation failed", ex);
        }
    }

    // ── undo ─────────────────────────────────────────────────────────

    @FXML
    private void onUndo() {
        if (undoStack.isEmpty()) {
            setStatus("Nothing to undo.");
            return;
        }
        Runnable undoAction = undoStack.pop();
        undoAction.run();
        setStatus("Undo performed.");
    }

    // ── select all ───────────────────────────────────────────────────

    @FXML
    private void onSelectAll() {
        int count = topicTypeNodeMap.size() + elementNodeMap.size();
        // For multi-select we just report count; full multi-select would need extra state
        setStatus("Nodes on canvas: " + count);
        log.log("Canvas contains " + topicTypeNodeMap.size() + " topic/domain nodes and "
                + elementNodeMap.size() + " element nodes.");
    }

    // ── node click handlers ───────────────────────────────────────────

    private void handleNodeClick(TopicTypeNode node) {
        if (currentTool == Tool.CONNECT_INHERIT || currentTool == Tool.CONNECT_CONTAIN) {
            handleConnectionClick(node);
        } else {
            selectDiagramNode(node);
            showTopicTypeProperties(node.getTopicType());
        }
    }

    private void handleElementNodeClick(ElementNode node) {
        if (currentTool == Tool.CONNECT_INHERIT || currentTool == Tool.CONNECT_CONTAIN) {
            handleConnectionClick(node);
        } else {
            selectDiagramNode(node);
            showElementProperties(node.getElementDef());
        }
    }

    private void handleConnectionClick(DiagramNode clickedNode) {
        if (connectionSourceNode == null) {
            connectionSourceNode = clickedNode;
            clickedNode.setSelected(true);
            setStatus("Connection: click target node to complete…");
        } else {
            if (connectionSourceNode != clickedNode) {
                RelationshipType relType = (currentTool == Tool.CONNECT_INHERIT)
                        ? RelationshipType.INHERITANCE : RelationshipType.CONTAINMENT;
                Relationship rel = projectService.createRelationship(
                        currentModel, relType,
                        connectionSourceNode.getModelId(), clickedNode.getModelId());
                drawConnection(rel, connectionSourceNode, clickedNode);
                markDirty();
                log.log("Created " + relType.getLabel() + ": "
                        + connectionSourceNode.getModelId() + " → " + clickedNode.getModelId());
            }
            connectionSourceNode.setSelected(false);
            connectionSourceNode = null;
            setStatus("Connection created.");
        }
    }

    private void drawConnection(Relationship rel, DiagramNode source, DiagramNode target) {
        ConnectionLine line = new ConnectionLine(rel, source, target);
        connectionLines.add(line);
        connectionLineMap.put(rel.getId(), line);
        canvas.getChildren().add(0, line);
    }

    private void updateAllConnections() {
        for (ConnectionLine line : connectionLines) line.update();
    }

    // ── selection ─────────────────────────────────────────────────────

    private void selectDiagramNode(DiagramNode node) {
        deselectAll();
        selectedDiagramNode = node;
        node.setSelected(true);
    }

    private void deselectAll() {
        if (selectedDiagramNode != null) {
            selectedDiagramNode.setSelected(false);
            selectedDiagramNode = null;
        }
        if (connectionSourceNode != null) {
            connectionSourceNode.setSelected(false);
            connectionSourceNode = null;
        }
        showNoSelection();
    }

    // ── validate model ────────────────────────────────────────────────

    @FXML
    private void onValidateModel() {
        List<String> issues = transformer.validate(currentModel);
        if (issues.isEmpty()) {
            log.logSuccess("Validation passed — no issues found.");
            setStatus("Validation: OK");
            // Clear any invalid marks
            for (TopicTypeNode n : topicTypeNodeMap.values()) n.markInvalid(false);
        } else {
            log.log("Validation found " + issues.size() + " issue(s):");
            for (String issue : issues) log.log("  • " + issue);
            setStatus("Validation: " + issues.size() + " issue(s) found — see console");
            showWarnings(issues);
        }
    }

    // ── properties panel ──────────────────────────────────────────────

    private void showNoSelection() {
        propertiesPanel.getChildren().clear();
        Label title = new Label("Properties");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label ph = new Label("Select a node to edit properties.");
        ph.setStyle("-fx-text-fill: #888; -fx-font-style: italic;");
        ph.setWrapText(true);
        propertiesPanel.getChildren().addAll(title, new Separator(), ph);
    }

    private void showTopicTypeProperties(TopicType tt) {
        propertiesPanel.getChildren().clear();

        Label title = new Label("TopicType Properties");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1565C0;");
        propertiesPanel.getChildren().addAll(title, new Separator());

        addPropField("Name:", tt.getName(), value -> {
            tt.setName(value);
            TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
            if (n != null) n.refresh();
        });

        addPropCombo("Base Type:", tt.getBaseType(), oasisLoader.getAvailableBaseTypes(), value -> {
            tt.setBaseType(value);
            TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
            if (n != null) n.refresh();
        });

        addPropField("Namespace:", nvl(tt.getNamespace()), value ->
                tt.setNamespace(value.isBlank() ? null : value));

        addPropField("Public ID:", nvl(tt.getPublicId()), value ->
                tt.setPublicId(value.isBlank() ? null : value));

        addPropField("System ID:", nvl(tt.getSystemId()), value ->
                tt.setSystemId(value.isBlank() ? null : value));

        addPropField("Module:", nvl(tt.getModule()), value ->
                tt.setModule(value.isBlank() ? null : value));

        addPropTextArea("Description:", nvl(tt.getDescription()), tt::setDescription);

        // ── Derived class attribute preview ───────────────────────────
        propertiesPanel.getChildren().add(new Separator());
        String classAttr = transformer.buildClassAttribute(tt);
        Label classLabel = new Label("class: " + classAttr);
        classLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #666; -fx-font-family: monospace;");
        classLabel.setWrapText(true);
        propertiesPanel.getChildren().add(classLabel);

        // ── Elements ─────────────────────────────────────────────────
        propertiesPanel.getChildren().add(new Separator());
        Label elemHeader = new Label("Elements (" + tt.getElements().size() + ")");
        elemHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        propertiesPanel.getChildren().add(elemHeader);

        List<ElementDef> elements = tt.getElements();
        for (int i = 0; i < elements.size(); i++) {
            final int idx = i;
            ElementDef elem = elements.get(i);

            HBox row = new HBox(2);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label lbl = new Label("+ " + elem.getName() + " " + elem.getCardinality());
            lbl.setStyle("-fx-font-size: 10px;");
            HBox.setHgrow(lbl, Priority.ALWAYS);

            Button up   = smallButton("▲", () -> {
                if (idx > 0) {
                    elements.remove(idx);
                    elements.add(idx - 1, elem);
                    TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
                    if (n != null) n.refresh();
                    markDirty();
                    showTopicTypeProperties(tt);
                }
            });
            Button down = smallButton("▼", () -> {
                if (idx < elements.size() - 1) {
                    elements.remove(idx);
                    elements.add(idx + 1, elem);
                    TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
                    if (n != null) n.refresh();
                    markDirty();
                    showTopicTypeProperties(tt);
                }
            });
            Button del  = smallButton("✕", () -> {
                tt.getElements().remove(elem);
                TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
                if (n != null) n.refresh();
                markDirty();
                showTopicTypeProperties(tt);
            });
            up.setDisable(idx == 0);
            down.setDisable(idx == elements.size() - 1);
            del.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-text-fill: #c0392b;");

            row.getChildren().addAll(lbl, up, down, del);
            propertiesPanel.getChildren().add(row);

            // DTD fragment preview
            String card = elem.getCardinality() != null ? elem.getCardinality() : "?";
            String dtdLine = "<!ELEMENT " + elem.getName() + " "
                    + nvl(elem.getContentModel(), "(#PCDATA)*") + ">";
            Label dtdPreview = new Label("  " + dtdLine);
            dtdPreview.setStyle("-fx-font-size: 9px; -fx-text-fill: #999; -fx-font-family: monospace;");
            propertiesPanel.getChildren().add(dtdPreview);
        }

        Button addElem = new Button("+ Add Element");
        addElem.setOnAction(e -> {
            String name = promptName("Add Element", "elementName", "newElem");
            if (name != null && !name.isBlank()) {
                projectService.createElement(tt, name, "(#PCDATA)*", "?");
                TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
                if (n != null) n.refresh();
                markDirty();
                showTopicTypeProperties(tt);
            }
        });
        addElem.setStyle("-fx-font-size: 10px;");
        propertiesPanel.getChildren().add(addElem);

        // ── Attributes ────────────────────────────────────────────────
        propertiesPanel.getChildren().add(new Separator());
        Label attrHeader = new Label("Attributes (" + tt.getAttributes().size() + ")");
        attrHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        propertiesPanel.getChildren().add(attrHeader);

        for (AttributeDef attr : new ArrayList<>(tt.getAttributes())) {
            HBox row = new HBox(4);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label lbl = new Label("• " + attr.getName() + " : " + attr.getType());
            lbl.setStyle("-fx-font-size: 10px;");
            HBox.setHgrow(lbl, Priority.ALWAYS);

            String req = attr.isRequired() ? " #REQUIRED" : " #IMPLIED";
            Label attrPreview = new Label(attr.toDtdFragment());
            attrPreview.setStyle("-fx-font-size: 9px; -fx-text-fill: #999; -fx-font-family: monospace;");

            Button del = smallButton("✕", () -> {
                tt.getAttributes().remove(attr);
                markDirty();
                showTopicTypeProperties(tt);
            });
            del.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-text-fill: #c0392b;");

            row.getChildren().addAll(lbl, del);
            propertiesPanel.getChildren().addAll(row, attrPreview);
        }

        Button addAttr = new Button("+ Add Attribute");
        addAttr.setOnAction(e -> {
            String name = promptName("Add Attribute", "attributeName", "myAttr");
            if (name != null && !name.isBlank()) {
                tt.addAttribute(projectService.createAttribute(name, "CDATA", false));
                markDirty();
                showTopicTypeProperties(tt);
            }
        });
        addAttr.setStyle("-fx-font-size: 10px;");
        propertiesPanel.getChildren().add(addAttr);
    }

    private void showElementProperties(ElementDef elem) {
        propertiesPanel.getChildren().clear();

        Label title = new Label("Element Properties");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #2e7d32;");
        propertiesPanel.getChildren().addAll(title, new Separator());

        addPropField("Name:", elem.getName(), elem::setName);
        addPropField("Content Model:", nvl(elem.getContentModel()), elem::setContentModel);

        addPropCombo("Cardinality:", elem.getCardinality(),
                List.of("1", "?", "+", "*"), elem::setCardinality);

        CheckBox required = new CheckBox("Required");
        required.setSelected(elem.isRequired());
        required.setOnAction(e -> { elem.setRequired(required.isSelected()); markDirty(); });
        propertiesPanel.getChildren().add(required);

        addPropTextArea("Description:", nvl(elem.getDescription()), elem::setDescription);

        propertiesPanel.getChildren().add(new Separator());

        // DTD fragment preview
        String dtdLine = "<!ELEMENT " + elem.getName() + " "
                + nvl(elem.getContentModel(), "(#PCDATA)*") + ">";
        Label dtdPreview = new Label(dtdLine);
        dtdPreview.setStyle("-fx-font-size: 10px; -fx-text-fill: #555; -fx-font-family: monospace;");
        dtdPreview.setWrapText(true);
        propertiesPanel.getChildren().add(dtdPreview);

        propertiesPanel.getChildren().add(new Separator());

        Label attrHeader = new Label("Attributes (" + elem.getAttributes().size() + ")");
        attrHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        propertiesPanel.getChildren().add(attrHeader);

        for (AttributeDef attr : new ArrayList<>(elem.getAttributes())) {
            HBox row = new HBox(4);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label lbl = new Label("• " + attr.getName() + " : " + attr.getType());
            lbl.setStyle("-fx-font-size: 10px;");
            HBox.setHgrow(lbl, Priority.ALWAYS);
            Button del = smallButton("✕", () -> {
                elem.getAttributes().remove(attr);
                markDirty();
                showElementProperties(elem);
            });
            del.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-text-fill: #c0392b;");
            row.getChildren().addAll(lbl, del);
            propertiesPanel.getChildren().add(row);
        }

        Button addAttr = new Button("+ Add Attribute");
        addAttr.setOnAction(e -> {
            String name = promptName("Add Attribute", "name", "myAttr");
            if (name != null && !name.isBlank()) {
                elem.addAttribute(projectService.createAttribute(name, "CDATA", false));
                markDirty();
                showElementProperties(elem);
            }
        });
        addAttr.setStyle("-fx-font-size: 10px;");
        propertiesPanel.getChildren().add(addAttr);
    }

    // ── properties panel helpers ──────────────────────────────────────

    private void addPropField(String labelText, String value, Consumer<String> setter) {
        Consumer<String> ms = v -> { setter.accept(v); markDirty(); };
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        TextField tf = new TextField(value);
        tf.setStyle("-fx-font-size: 11px;");
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.focusedProperty().addListener((obs, was, is) -> { if (!is) ms.accept(tf.getText()); });
        tf.setOnAction(e -> ms.accept(tf.getText()));
        propertiesPanel.getChildren().addAll(lbl, tf);
    }

    private void addPropCombo(String labelText, String value, List<String> options,
                              Consumer<String> setter) {
        Consumer<String> ms = v -> { setter.accept(v); markDirty(); };
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll(options);
        cb.setValue(value);
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setStyle("-fx-font-size: 11px;");
        cb.setOnAction(e -> { if (cb.getValue() != null) ms.accept(cb.getValue()); });
        propertiesPanel.getChildren().addAll(lbl, cb);
    }

    private void addPropTextArea(String labelText, String value, Consumer<String> setter) {
        Consumer<String> ms = v -> { setter.accept(v); markDirty(); };
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        TextArea ta = new TextArea(value);
        ta.setPrefRowCount(3);
        ta.setWrapText(true);
        ta.setStyle("-fx-font-size: 11px;");
        ta.focusedProperty().addListener((obs, was, is) -> { if (!is) ms.accept(ta.getText()); });
        propertiesPanel.getChildren().addAll(lbl, ta);
    }

    private Button smallButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4;");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    // ── FXML tool button handlers ─────────────────────────────────────

    @FXML private void onToolSelect()         { setTool(Tool.SELECT); }
    @FXML private void onToolTopicType()      { setTool(Tool.ADD_TOPIC_TYPE); }
    @FXML private void onToolElement()        { setTool(Tool.ADD_ELEMENT); }
    @FXML private void onToolDomain()         { setTool(Tool.ADD_DOMAIN); }
    @FXML private void onToolConnectInherit() { setTool(Tool.CONNECT_INHERIT); }
    @FXML private void onToolConnectContain() { setTool(Tool.CONNECT_CONTAIN); }

    private void setTool(Tool tool) {
        currentTool = tool;
        if (connectionSourceNode != null) {
            connectionSourceNode.setSelected(false);
            connectionSourceNode = null;
        }
        String desc = switch (tool) {
            case SELECT          -> "Select / Move";
            case ADD_TOPIC_TYPE  -> "Click canvas to add Topic Type";
            case ADD_ELEMENT     -> "Click canvas to add Element";
            case ADD_DOMAIN      -> "Click canvas to add Domain";
            case CONNECT_INHERIT -> "Click source node, then target to draw Inheritance";
            case CONNECT_CONTAIN -> "Click source node, then target to draw Containment";
        };
        setStatus("Tool: " + desc);
    }

    // ── FXML menu/toolbar handlers ────────────────────────────────────

    @FXML
    private void onNewProject() {
        if (dirty && !confirmDiscard()) return;
        currentModel = projectService.createNew("Untitled");
        currentProjectFile = null;
        dirty = false;
        clearCanvas();
        showNoSelection();
        setStatus("New project created.");
        updateTitle();
        log.log("New project started.");
    }

    @FXML
    private void onOpenProject() {
        if (dirty && !confirmDiscard()) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Open DITA Designer Project");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("DITA Designer Project (*.ddp)", "*.ddp"));
        File file = fc.showOpenDialog(getStage());
        if (file == null) return;

        try {
            currentModel = repository.load(file);
            currentProjectFile = file;
            dirty = false;
            clearCanvas();
            rebuildCanvasFromModel();
            setStatus("Opened: " + file.getName());
            updateTitle();
            addToRecentFiles(file);
            log.logSuccess("Project loaded from " + file.getPath());
        } catch (Exception ex) {
            showError("Open Failed", ex.getMessage());
            log.logError("Open failed", ex);
        }
    }

    @FXML
    private void onSaveProject() {
        if (currentProjectFile == null) {
            onSaveProjectAs();
        } else {
            doSave(currentProjectFile);
        }
    }

    @FXML
    private void onSaveProjectAs() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save DITA Designer Project");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("DITA Designer Project (*.ddp)", "*.ddp"));
        fc.setInitialFileName(currentModel.getName() + ".ddp");
        File file = fc.showSaveDialog(getStage());
        if (file == null) return;
        currentProjectFile = file;
        doSave(file);
    }

    private void doSave(File file) {
        try {
            // Backup previous version
            if (file.exists()) {
                File backup = new File(file.getPath() + ".bak");
                FileUtil.copyFile(file, backup);
            }
            repository.save(currentModel, file);
            dirty = false;
            setStatus("Saved: " + file.getName());
            updateTitle();
            addToRecentFiles(file);
            log.logSuccess("Project saved → " + file.getPath());
        } catch (Exception ex) {
            showError("Save Failed", ex.getMessage());
            log.logError("Save failed", ex);
        }
    }

    @FXML
    private void onExportZip() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select output directory for generated files");
        File outputDir = dc.showDialog(getStage());
        if (outputDir == null) return;

        try {
            doGenerate(outputDir);
            File zip = new File(outputDir.getParent(),
                    currentModel.getName().replaceAll("\\s+", "_") + "_output.zip");
            FileUtil.zipDirectory(outputDir, zip);
            log.logSuccess("ZIP exported to: " + zip.getPath());
            setStatus("ZIP exported: " + zip.getName());
        } catch (Exception ex) {
            showError("Export Failed", ex.getMessage());
            log.logError("Export failed", ex);
        }
    }

    @FXML
    private void onExit() {
        if (dirty) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "You have unsaved changes. Save before exiting?",
                    ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("Save changes to " + currentModel.getName() + "?");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() == ButtonType.CANCEL) return;
            if (result.get() == ButtonType.YES) onSaveProject();
        }
        Platform.exit();
    }

    @FXML
    private void onLoadDitaLibraries() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select DITA library directory (containing .dtd/.xsd files)");
        File dir = dc.showDialog(getStage());
        if (dir == null) return;

        int count = oasisLoader.loadFromDirectory(dir);
        setStatus("Loaded " + count + " DITA library files.");
        log.logSuccess("Loaded " + count + " DITA library files from " + dir.getPath());
    }

    @FXML
    private void onGenerateDtd() {
        File outputDir = chooseOutputDir();
        if (outputDir == null) return;

        List<String> issues = transformer.validate(currentModel);
        if (!issues.isEmpty()) showWarnings(issues);

        try {
            dtdGenerator.generate(currentModel, outputDir);
            int count = currentModel.getTopicTypes().size();
            log.logSuccess("DTD generated for " + count + " topic type(s) → " + outputDir.getPath());
            logGenerationReport(outputDir, "DTD");
            setStatus("DTD generated (" + count + " type(s)).");
        } catch (Exception ex) {
            showError("DTD Generation Failed", ex.getMessage());
            log.logError("DTD generation failed", ex);
        }
    }

    @FXML
    private void onGenerateXsd() {
        File outputDir = chooseOutputDir();
        if (outputDir == null) return;

        try {
            xsdGenerator.generate(currentModel, outputDir);
            int count = currentModel.getTopicTypes().size();
            log.logSuccess("XSD generated for " + count + " topic type(s) → " + outputDir.getPath());
            logGenerationReport(outputDir, "XSD");
            setStatus("XSD generated (" + count + " type(s)).");
        } catch (Exception ex) {
            showError("XSD Generation Failed", ex.getMessage());
            log.logError("XSD generation failed", ex);
        }
    }

    @FXML
    private void onGenerateCatalog() {
        File outputDir = chooseOutputDir();
        if (outputDir == null) return;

        try {
            catalogGenerator.generate(currentModel, outputDir);
            log.logSuccess("catalog.xml generated → " + outputDir.getPath());
            setStatus("Catalog generated.");
        } catch (Exception ex) {
            showError("Catalog Generation Failed", ex.getMessage());
            log.logError("Catalog generation failed", ex);
        }
    }

    @FXML
    private void onGenerateAll() {
        File outputDir = chooseOutputDir();
        if (outputDir == null) return;

        List<String> issues = transformer.validate(currentModel);
        if (!issues.isEmpty()) showWarnings(issues);

        try {
            doGenerate(outputDir);
            setStatus("All artefacts generated → " + outputDir.getPath());
        } catch (Exception ex) {
            showError("Generation Failed", ex.getMessage());
            log.logError("Generate all failed", ex);
        }
    }

    private void doGenerate(File outputDir) throws Exception {
        FileUtil.ensureDir(outputDir);
        log.log("─── Generate All ──────────────────────────────");
        log.log("Output: " + outputDir.getPath());
        log.log("Model: " + currentModel.getName()
                + " · " + currentModel.getTopicTypes().size() + " type(s)"
                + " · " + currentModel.getDomains().size() + " domain(s)");

        dtdGenerator.generate(currentModel, outputDir);
        logGenerationReport(outputDir, "DTD");

        xsdGenerator.generate(currentModel, outputDir);
        logGenerationReport(outputDir, "XSD");

        catalogGenerator.generate(currentModel, outputDir);
        log.log("Catalog: output/catalog.xml");

        log.logSuccess("Generation complete → " + outputDir.getPath());
        log.log("───────────────────────────────────────────────");
    }

    private void logGenerationReport(File outputDir, String type) {
        File dir = new File(outputDir, type.toLowerCase());
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                log.log(type + ": " + f.getName() + " (" + f.length() + " bytes)");
            }
        }
    }

    @FXML
    private void onAddSample() {
        if (dirty && !confirmDiscard()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "This will replace the current project with the phxTask sample. Continue?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Load Sample");
        confirm.setHeaderText("Load phxTask Sample Specialization");
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        currentModel = projectService.createSamplePhxTask();
        currentProjectFile = null;
        dirty = false;
        clearCanvas();
        rebuildCanvasFromModel();
        updateTitle();
        setStatus("Sample 'phxTask' loaded.");
        log.logSuccess("phxTask sample loaded — "
                + currentModel.getTopicTypes().size() + " topic types, "
                + currentModel.getDomains().size() + " domains.");
    }

    @FXML
    private void onClearLog() { log.clear(); }

    // ── Project Metadata dialog ───────────────────────────────────────

    @FXML
    private void onProjectMetadata() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Project Metadata");
        dialog.setHeaderText("Edit Project Properties");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));

        TextField nameField    = new TextField(nvl(currentModel.getName()));
        TextField versionField = new TextField(nvl(currentModel.getVersion(), "1.0"));
        TextField nsField      = new TextField(nvl(currentModel.getTargetNamespace()));
        TextField outField     = new TextField(nvl(currentModel.getOutputDir(), "output"));
        TextArea  descField    = new TextArea(nvl(currentModel.getDescription()));
        descField.setPrefRowCount(3);
        descField.setWrapText(true);

        nameField.setPrefWidth(220);
        nsField.setPrefWidth(220);

        grid.add(new Label("Project Name:"),   0, 0); grid.add(nameField,    1, 0);
        grid.add(new Label("Version:"),        0, 1); grid.add(versionField, 1, 1);
        grid.add(new Label("Target Namespace:"),0,2); grid.add(nsField,      1, 2);
        grid.add(new Label("Output Dir:"),     0, 3); grid.add(outField,     1, 3);
        grid.add(new Label("Description:"),    0, 4); grid.add(descField,    1, 4);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                currentModel.setName(nameField.getText().trim());
                currentModel.setVersion(versionField.getText().trim());
                currentModel.setTargetNamespace(nsField.getText().trim());
                currentModel.setOutputDir(outField.getText().trim());
                currentModel.setDescription(descField.getText().trim());
                markDirty();
                updateTitle();
                setStatus("Project metadata updated.");
                log.log("Project metadata saved: " + currentModel.getName()
                        + " v" + currentModel.getVersion());
            }
        });
    }

    // ── Keyboard Shortcuts dialog ─────────────────────────────────────

    @FXML
    private void onKeyboardShortcuts() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Keyboard Shortcuts");
        alert.setHeaderText("DITA Specialization Designer — Keyboard Shortcuts");
        TextArea ta = new TextArea(
            "File\n" +
            "  Ctrl+N          New Project\n" +
            "  Ctrl+O          Open Project\n" +
            "  Ctrl+S          Save Project\n\n" +
            "Canvas\n" +
            "  Delete           Delete selected node\n" +
            "  Escape           Deselect / Cancel connection\n" +
            "  Ctrl+A           Show node count\n" +
            "  Ctrl+Z           Undo last action\n" +
            "  Double-click     Inline rename node\n\n" +
            "Zoom\n" +
            "  Ctrl+Scroll      Zoom in / out\n" +
            "  Ctrl++ / Ctrl+-  Zoom in / out (keyboard)\n" +
            "  Ctrl+0           Reset zoom to 100%\n\n" +
            "Generate\n" +
            "  Ctrl+G           Generate All artefacts\n\n" +
            "Help\n" +
            "  F1               This dialog\n"
        );
        ta.setEditable(false);
        ta.setPrefRowCount(18);
        ta.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        alert.getDialogPane().setContent(ta);
        alert.getDialogPane().setPrefWidth(420);
        alert.showAndWait();
    }

    // ── About dialog ──────────────────────────────────────────────────

    @FXML
    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("DITA Specialization Designer v1.0");
        alert.setContentText(
                "A visual modeling tool for DITA specialization.\n\n" +
                "Generates:\n" +
                " • DTD (.dtd shell + .mod + .ent)\n" +
                " • XSD (xs:complexType / xs:extension)\n" +
                " • XML Catalog (OASIS V1.1)\n\n" +
                "Built with Java 17 + JavaFX 21 + Gradle.\n\n" +
                "Press F1 for keyboard shortcuts."
        );
        alert.showAndWait();
    }

    // ── Recent Projects ───────────────────────────────────────────────

    private void addToRecentFiles(File file) {
        recentFiles.remove(file);
        recentFiles.add(0, file);
        if (recentFiles.size() > 5) recentFiles.subList(5, recentFiles.size()).clear();
        updateRecentProjectsMenu();
    }

    private void updateRecentProjectsMenu() {
        if (recentProjectsMenu == null) return;
        recentProjectsMenu.getItems().clear();
        recentProjectsMenu.setDisable(recentFiles.isEmpty());
        for (File file : recentFiles) {
            MenuItem item = new MenuItem(file.getName());
            item.setOnAction(e -> openRecentFile(file));
            Tooltip.install(item.getGraphic(), new Tooltip(file.getPath()));
            recentProjectsMenu.getItems().add(item);
        }
        if (!recentFiles.isEmpty()) {
            recentProjectsMenu.getItems().add(new SeparatorMenuItem());
            MenuItem clearItem = new MenuItem("Clear Recent");
            clearItem.setOnAction(e -> { recentFiles.clear(); updateRecentProjectsMenu(); });
            recentProjectsMenu.getItems().add(clearItem);
        }
    }

    private void openRecentFile(File file) {
        if (!file.exists()) {
            showError("File Not Found", "The file no longer exists:\n" + file.getPath());
            recentFiles.remove(file);
            updateRecentProjectsMenu();
            return;
        }
        if (dirty && !confirmDiscard()) return;
        try {
            currentModel = repository.load(file);
            currentProjectFile = file;
            dirty = false;
            clearCanvas();
            rebuildCanvasFromModel();
            setStatus("Opened: " + file.getName());
            updateTitle();
            addToRecentFiles(file);
            log.logSuccess("Recent project loaded: " + file.getPath());
        } catch (Exception ex) {
            showError("Open Failed", ex.getMessage());
            log.logError("Open recent failed", ex);
        }
    }

    // ── canvas rebuild ────────────────────────────────────────────────

    private void clearCanvas() {
        canvas.getChildren().clear();
        topicTypeNodeMap.clear();
        elementNodeMap.clear();
        connectionLines.clear();
        connectionLineMap.clear();
        selectedDiagramNode = null;
        connectionSourceNode = null;
    }

    private void rebuildCanvasFromModel() {
        for (TopicType tt : currentModel.getTopicTypes()) {
            placeTopicTypeNode(tt);
        }
        for (DomainDef domain : currentModel.getDomains()) {
            placeDomainNode(domain);
        }
        for (Relationship rel : currentModel.getRelationships()) {
            DiagramNode source = findDiagramNode(rel.getSourceId());
            DiagramNode target = findDiagramNode(rel.getTargetId());
            if (source != null && target != null) {
                drawConnection(rel, source, target);
            }
        }
        // Refresh all connections after layout
        Platform.runLater(this::updateAllConnections);
    }

    private DiagramNode findDiagramNode(String id) {
        DiagramNode node = topicTypeNodeMap.get(id);
        if (node != null) return node;
        return elementNodeMap.get(id);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private String promptName(String title, String fieldName, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(fieldName + ":");
        return dialog.showAndWait().orElse(null);
    }

    private String chooseBaseType() {
        List<String> types = oasisLoader.getAvailableBaseTypes();
        ChoiceDialog<String> dialog = new ChoiceDialog<>("task", types);
        dialog.setTitle("Choose Base Type");
        dialog.setHeaderText(null);
        dialog.setContentText("Specialization base type:");
        return dialog.showAndWait().orElse(null);
    }

    private File chooseOutputDir() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Output Directory");
        return dc.showDialog(getStage());
    }

    /** Confirm discarding unsaved changes. Returns true if user says OK. */
    private boolean confirmDiscard() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Discard unsaved changes to '" + currentModel.getName() + "'?",
                ButtonType.YES, ButtonType.CANCEL);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.YES;
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message != null ? message : "An unexpected error occurred.");
        alert.showAndWait();
    }

    private void showWarnings(List<String> issues) {
        String msg = String.join("\n", issues);
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Validation Warnings");
        alert.setHeaderText("Model has " + issues.size() + " validation issue(s):");
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void updateTitle() {
        Stage stage = getStage();
        if (stage != null) {
            String name  = currentModel.getName();
            String file  = currentProjectFile != null ? " — " + currentProjectFile.getName() : " (unsaved)";
            String mark  = dirty ? " *" : "";
            stage.setTitle("DITA Specialization Designer — " + name + file + mark);
        }
    }

    private Stage getStage() {
        if (canvas != null && canvas.getScene() != null) {
            return (Stage) canvas.getScene().getWindow();
        }
        return null;
    }

    private static String nvl(String s)              { return s != null ? s : ""; }
    private static String nvl(String s, String def)  { return (s != null && !s.isBlank()) ? s : def; }
}
