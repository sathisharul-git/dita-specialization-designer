package com.ditadesigner.ui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.ditadesigner.generator.CatalogGenerator;
import com.ditadesigner.generator.DtdGenerator;
import com.ditadesigner.generator.HtmlDocGenerator;
import com.ditadesigner.generator.XsdGenerator;
import com.ditadesigner.model.AttributeDef;
import com.ditadesigner.model.DitaModel;
import com.ditadesigner.model.DomainDef;
import com.ditadesigner.model.ElementDef;
import com.ditadesigner.model.Relationship;
import com.ditadesigner.model.RelationshipType;
import com.ditadesigner.model.TopicType;
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
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class MainController implements Initializable {

    // ── FXML injected ─────────────────────────────────────────────────

    @FXML private MenuBar menuBar;
    @FXML private Menu recentProjectsMenu;
    @FXML private Label zoomLabel;
    @FXML private CheckMenuItem darkModeItem;
    @FXML private CheckMenuItem logVisibleItem;
    @FXML private CheckMenuItem explorerVisibleItem;

    // ── Project Explorer (F-167) ──────────────────────────────────────
    @FXML private TreeView<String> explorerTree;
    @FXML private VBox explorerPanel;
    @FXML private CheckBox liveSyncCheckBox;
    @FXML private TextField explorerFilter;  // F-127

    @FXML private SplitPane mainSplitPane;  // F-138
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

    // ── bottom log area ───────────────────────────────────────────────
    @FXML private VBox logContainer;

    // ── services ──────────────────────────────────────────────────────

    private final LogService log = LogService.getInstance();
    private final ProjectService projectService = new ProjectService();
    private final OasisLoaderService oasisLoader = new OasisLoaderService();
    private final ProjectRepository repository = new ProjectRepository();
    private final DtdGenerator dtdGenerator = new DtdGenerator();
    private final XsdGenerator xsdGenerator = new XsdGenerator();
    private final CatalogGenerator catalogGenerator = new CatalogGenerator();
    private final HtmlDocGenerator htmlDocGenerator = new HtmlDocGenerator();
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

    // ── undo/redo stacks ──────────────────────────────────────────────

    private final Deque<Runnable> undoStack  = new ArrayDeque<>();
    private final Deque<Runnable> redoStack  = new ArrayDeque<>();
    private final Deque<String>   undoLabels = new ArrayDeque<>();  // F-023: human-readable labels

    // ── multi-select (F-013) ──────────────────────────────────────────

    private final Set<DiagramNode> multiSelectedNodes = new java.util.HashSet<>();

    // ── stale-file tracking (F-175) ───────────────────────────────────

    private long lastModelChangeMs = 0;
    private long lastGenerationMs  = 0;

    // ── auto-save ─────────────────────────────────────────────────────

    private ScheduledExecutorService autoSaveExecutor;

    // ── dark-mode ─────────────────────────────────────────────────────

    private boolean darkMode = false;

    // ── last validation issues (for export) ───────────────────────────

    private List<String> lastValidationIssues = new ArrayList<>();

    // ── last used DITA lib directory ──────────────────────────────────

    private File lastLibDir = null;

    // ── Project File Explorer / Live Sync (F-167 to F-176) ───────────

    private boolean liveSyncEnabled = false;
    /** The resolved output directory for the current project (for Live Sync). */
    private File resolvedOutputDir = null;

    // ── initialize ────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.setLogArea(logArea);
        setupToolGroup();
        canvas.getTransforms().add(canvasScale);
        setupCanvas();
        setupKeyboardShortcuts();

        // Initialize with a blank model so the canvas is valid before the welcome dialog
        currentModel = projectService.createNew("Untitled");
        dirty = false;
        startAutoSave();

        // Restore last lib dir from prefs
        String libDirPath = Preferences.userNodeForPackage(MainController.class).get("last.lib.dir", null);
        if (libDirPath != null) { File d = new File(libDirPath); if (d.isDirectory()) lastLibDir = d; }

        // F-155 fix: restore recent files from previous session
        loadRecentFilesFromPrefs();
        updateRecentProjectsMenu();

        // Restore window state once the window is ready (welcome dialog is shown from App.java)
        canvas.sceneProperty().addListener((obs, o, scene) -> {
            if (scene != null) {
                scene.windowProperty().addListener((obs2, o2, win) -> {
                    if (win instanceof javafx.stage.Stage stage) {
                        restoreWindowState();
                        stage.setOnCloseRequest(e -> saveWindowState());
                    }
                });
            }
        });

        // Load bundled DITA 1.3 grammar in background so elements are available immediately
        Thread bundledLoader = new Thread(() -> {
            oasisLoader.loadBundledLibrary();
            Platform.runLater(() -> log.log("Bundled DITA 1.3 grammars ready."));
        }, "dita-bundled-loader");
        bundledLoader.setDaemon(true);
        bundledLoader.start();

        // F-167: populate explorer after initialization
        Platform.runLater(() -> {
            refreshExplorer();
            setupExplorerContextMenu();  // F-176
        });

        log.log("DITA Specialization Designer ready.");
        log.log("Tip: Ctrl+Scroll to zoom · Delete to remove · Escape to deselect · F1 for shortcuts");
    }

    /** Startup welcome dialog — create new, open existing, or open a recent project. */
    public void showWelcomeDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Welcome to DITA Specialization Designer");
        dialog.setHeaderText("What would you like to do?");

        ButtonType createBtn = new ButtonType("Create New Project", ButtonBar.ButtonData.LEFT);
        ButtonType openBtn   = new ButtonType("Open Existing Project…", ButtonBar.ButtonData.LEFT);
        ButtonType cancelBtn = ButtonType.CANCEL;
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, openBtn, cancelBtn);

        VBox content = new VBox(10);
        content.setPadding(new Insets(12, 16, 4, 16));

        // Recent projects list
        if (!recentFiles.isEmpty()) {
            Label recentLabel = new Label("Recent Projects:");
            recentLabel.setStyle("-fx-font-weight: bold;");
            ListView<String> recentList = new ListView<>();
            recentFiles.forEach(f -> recentList.getItems().add(f.getName() + "  —  " + f.getParent()));
            recentList.setPrefHeight(Math.min(recentFiles.size() * 26 + 4, 120));
            recentList.getSelectionModel().selectFirst();

            content.getChildren().addAll(recentLabel, recentList);

            // Double-click on recent list opens directly
            recentList.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && recentList.getSelectionModel().getSelectedIndex() >= 0) {
                    dialog.setResult(ButtonType.CANCEL); // dismiss via cancel; open handled below
                    dialog.close();
                    int idx = recentList.getSelectionModel().getSelectedIndex();
                    openRecentFile(recentFiles.get(idx));
                }
            });

            // Wire OK button to open selected recent when user clicks it from button bar
            Button createButton = (Button) dialog.getDialogPane().lookupButton(createBtn);
            Button openButton   = (Button) dialog.getDialogPane().lookupButton(openBtn);
            // keep original behaviour — the result handler below does the routing
            content.getProperties().put("recentList", recentList);
        }

        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(result -> {
            if (result == createBtn) {
                doNewProjectSetup();
            } else if (result == openBtn) {
                onOpenProject();
            }
            // CANCEL or window close: leave the blank canvas as-is
        });
    }

    /** Show the project setup dialog and create the new project. Returns false if cancelled. */
    private boolean doNewProjectSetup() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Project");
        dialog.setHeaderText("Configure your new DITA specialization project");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));

        TextField nameField      = new TextField("MySpecialization");
        TextField versionField   = new TextField("1.0");
        TextField nsField        = new TextField("urn:myorg:dita:");
        TextField copyrightField = new TextField();
        TextArea  descField      = new TextArea();
        descField.setPrefRowCount(2);
        descField.setWrapText(true);

        // Output folder row with Browse button
        TextField outField  = new TextField();
        outField.setPromptText("Select output folder…");
        outField.setPrefWidth(200);
        Button browseBtn = new Button("Browse…");
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Output Folder");
            File chosen = dc.showDialog(getStage());
            if (chosen != null) outField.setText(chosen.getAbsolutePath());
        });
        HBox outRow = new HBox(6, outField, browseBtn);

        nameField.setPrefWidth(220);
        nsField.setPrefWidth(220);

        // Auto-update namespace when name changes
        nameField.textProperty().addListener((obs, old, val) -> {
            String slug = val.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!slug.isEmpty()) nsField.setText("urn:myorg:dita:" + slug + ":");
        });

        grid.add(new Label("Project Name *:"),   0, 0); grid.add(nameField,      1, 0);
        grid.add(new Label("Version:"),           0, 1); grid.add(versionField,   1, 1);
        grid.add(new Label("Target Namespace:"),  0, 2); grid.add(nsField,        1, 2);
        grid.add(new Label("Output Folder *:"),   0, 3); grid.add(outRow,         1, 3);
        grid.add(new Label("Copyright Owner:"),   0, 4); grid.add(copyrightField, 1, 4);
        grid.add(new Label("Description:"),       0, 5); grid.add(descField,      1, 5);

        // Disable OK until name and output folder are filled
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        Runnable validate = () -> okButton.setDisable(
                nameField.getText().trim().isEmpty() || outField.getText().trim().isEmpty());
        nameField.textProperty().addListener((obs, o, n) -> validate.run());
        outField.textProperty().addListener((obs, o, n) -> validate.run());

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        return dialog.showAndWait().filter(r -> r == ButtonType.OK).map(r -> {
            String name = nameField.getText().trim();
            currentModel = projectService.createNew(name);
            currentModel.setVersion(versionField.getText().trim());
            currentModel.setTargetNamespace(nsField.getText().trim());
            currentModel.setOutputDir(outField.getText().trim());
            currentModel.setCopyrightOwner(copyrightField.getText().trim());
            currentModel.setDescription(descField.getText().trim());
            // Auto-save .ddp file inside the output directory
            String outDirStr = outField.getText().trim();
            File outDir = new File(outDirStr);
            outDir.mkdirs(); // create the output directory if it doesn't exist yet
            File ddpFile = new File(outDir, name + ".ddp");
            currentProjectFile = ddpFile;
            dirty = true; // mark dirty so doSave persists everything
            clearCanvas();
            showNoSelection();
            updateTitle();
            doSave(ddpFile); // saves and resets dirty=false, updates title, adds to recent
            // Reinitialize Live Sync with the new output dir
            if (liveSyncCheckBox != null && liveSyncCheckBox.isSelected()) {
                liveSyncEnabled = true;
                resolvedOutputDir = resolveOutputDir();
            }
            refreshExplorer();
            setStatus("New project created: " + name + "  —  Click 'Topic Type' in the Toolbox to start");
            log.logSuccess("New project: " + name + " → " + outDirStr + "  [auto-saved: " + ddpFile.getName() + "]");
            return true;
        }).orElse(false);
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
        } else if (e.isControlDown() && e.getCode() == KeyCode.Y && !inTextField) {
            onRedo();
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
        lastModelChangeMs = System.currentTimeMillis();  // F-175
        updateTitle();
        liveSyncGenerate();
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
        TopicType tt = showAddTopicTypeDialog(x, y);
        if (tt == null) return;
        pushUndo();
        tt.setX(x - 100);
        tt.setY(y - 40);
        currentModel.addTopicType(tt);
        placeTopicTypeNode(tt);
        markDirty();
        setStatus("Added TopicType: " + tt.getName());
        log.log("Added TopicType '" + tt.getName() + "' (base: " + tt.getBaseType() + ")");
    }

    /**
     * Shows a dialog with two modes:
     *  1. Create New – enter a name + pick a DITA base type.
     *  2. Import & Extend – browse for an existing XSD, pre-fill fields, edit name/base.
     * Returns a ready-to-place {@link TopicType}, or null if cancelled.
     */
    private TopicType showAddTopicTypeDialog(double canvasX, double canvasY) {
        // ── state ────────────────────────────────────────────────────────
        final TopicType[] importedHolder = {null}; // holds the parsed XSD result

        // ── dialog shell ─────────────────────────────────────────────────
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Add Topic Type");
        dlg.setHeaderText("Choose how to create the new topic type");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // ── mode radio buttons ───────────────────────────────────────────
        RadioButton rbNew    = new RadioButton("Create New");
        RadioButton rbImport = new RadioButton("Import & Extend existing XSD");
        ToggleGroup modeGroup = new ToggleGroup();
        rbNew.setToggleGroup(modeGroup);
        rbImport.setToggleGroup(modeGroup);
        rbNew.setSelected(true);

        HBox modeRow = new HBox(16, rbNew, rbImport);
        modeRow.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 0 0 6 0;");

        // ── "Create New" fields ──────────────────────────────────────────
        TextField newNameField = new TextField("MyTopic");
        newNameField.setPrefWidth(220);

        List<String> baseTypes = oasisLoader.getAvailableBaseTypes();
        ComboBox<String> baseCombo = new ComboBox<>();
        baseCombo.getItems().addAll(baseTypes);
        baseCombo.setValue(baseTypes.contains("task") ? "task" : (baseTypes.isEmpty() ? "topic" : baseTypes.get(0)));
        baseCombo.setMaxWidth(Double.MAX_VALUE);

        GridPane newPane = new GridPane();
        newPane.setHgap(8); newPane.setVgap(6);
        newPane.addRow(0, new Label("Topic Name:"), newNameField);
        newPane.addRow(1, new Label("Base DITA Type:"), baseCombo);
        javafx.scene.layout.ColumnConstraints cc1 = new javafx.scene.layout.ColumnConstraints();
        cc1.setMinWidth(110);
        javafx.scene.layout.ColumnConstraints cc2 = new javafx.scene.layout.ColumnConstraints();
        cc2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        newPane.getColumnConstraints().addAll(cc1, cc2);

        // ── "Import & Extend" fields — declared before browseBtn so lambda can capture them ──
        TextField xsdPathField = new TextField();
        xsdPathField.setPromptText("Select an XSD file…");
        xsdPathField.setEditable(false);
        xsdPathField.setPrefWidth(210);

        TextField importNameField = new TextField();
        importNameField.setPromptText("Specialization name");
        importNameField.setPrefWidth(210);

        Label importBaseLabel = new Label("(pick an XSD first)");
        importBaseLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #666;");

        TextField importNsField = new TextField();
        importNsField.setPromptText("urn:example:dita");
        importNsField.setPrefWidth(210);

        Label importSummaryLabel = new Label("");
        importSummaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #444;");
        importSummaryLabel.setWrapText(true);

        Button browseBtn = new Button("Browse…");
        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select XSD to Import");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));
            File f = fc.showOpenDialog(getStage());
            if (f == null) return;
            xsdPathField.setText(f.getAbsolutePath());
            try {
                com.ditadesigner.importer.XsdImporter importer = new com.ditadesigner.importer.XsdImporter();
                TopicType parsed = importer.importXsd(f);
                importedHolder[0] = parsed;
                importNameField.setText(parsed.getName());
                importBaseLabel.setText(parsed.getBaseType());
                importNsField.setText(parsed.getNamespace() != null ? parsed.getNamespace() : "");
                importSummaryLabel.setText(
                        parsed.getElements().size() + " element(s), " +
                        parsed.getAttributes().size() + " attribute(s) imported from XSD");
                log.log("Parsed XSD: " + f.getName() + " → base=" + parsed.getBaseType()
                        + ", " + parsed.getElements().size() + " elements");
            } catch (Exception ex) {
                showError("XSD Parse Error", ex.getMessage());
                importedHolder[0] = null;
                importSummaryLabel.setText("Parse failed: " + ex.getMessage());
            }
        });

        GridPane importPane = new GridPane();
        importPane.setHgap(8); importPane.setVgap(6);
        HBox xsdRow = new HBox(6, xsdPathField, browseBtn);
        xsdRow.setStyle("-fx-alignment: CENTER_LEFT;");
        javafx.scene.layout.HBox.setHgrow(xsdPathField, javafx.scene.layout.Priority.ALWAYS);
        importPane.addRow(0, new Label("XSD File:"),            xsdRow);
        importPane.addRow(1, new Label("Specialization Name:"), importNameField);
        importPane.addRow(2, new Label("Extends (Base Type):"), importBaseLabel);
        importPane.addRow(3, new Label("Target Namespace:"),    importNsField);
        importPane.addRow(4, new Label(""), importSummaryLabel);
        javafx.scene.layout.ColumnConstraints ic1 = new javafx.scene.layout.ColumnConstraints();
        ic1.setMinWidth(130);
        javafx.scene.layout.ColumnConstraints ic2 = new javafx.scene.layout.ColumnConstraints();
        ic2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        importPane.getColumnConstraints().addAll(ic1, ic2);

        // Initially hide the import pane
        importPane.setVisible(false);
        importPane.setManaged(false);

        // ── mode toggle wires pane visibility ────────────────────────────
        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            boolean isImport = (n == rbImport);
            newPane.setVisible(!isImport);
            newPane.setManaged(!isImport);
            importPane.setVisible(isImport);
            importPane.setManaged(isImport);
            // enable/disable OK
            javafx.scene.Node okBtn = dlg.getDialogPane().lookupButton(ButtonType.OK);
            if (isImport) {
                okBtn.setDisable(importedHolder[0] == null);
                xsdPathField.textProperty().addListener((o2, ov, nv) ->
                        okBtn.setDisable(importedHolder[0] == null));
            } else {
                okBtn.setDisable(false);
            }
        });

        // ── also keep OK disabled when import mode + no XSD loaded ───────
        dlg.getDialogPane().lookupButton(ButtonType.OK); // force lookup

        VBox content = new VBox(10, modeRow, new javafx.scene.control.Separator(), newPane, importPane);
        content.setPrefWidth(420);
        content.setStyle("-fx-padding: 4 0 0 0;");
        dlg.getDialogPane().setContent(content);

        // ── handle result ────────────────────────────────────────────────
        return dlg.showAndWait().filter(r -> r == ButtonType.OK).map(r -> {
            TopicType tt;
            if (rbImport.isSelected() && importedHolder[0] != null) {
                // Use the imported TopicType, possibly with a user-adjusted name
                tt = importedHolder[0];
                String specName = importNameField.getText().trim();
                if (!specName.isBlank() && !specName.equals(tt.getName())) {
                    tt.setName(specName);
                }
                String ns = importNsField.getText().trim();
                if (!ns.isBlank()) tt.setNamespace(ns);
            } else {
                // Create New
                String name = newNameField.getText().trim();
                if (name.isBlank()) name = "MyTopic";
                String base = baseCombo.getValue();
                tt = projectService.createTopicType(currentModel, name, base);
                // Remove from model — addTopicTypeAtPosition will re-add after positioning
                currentModel.getTopicTypes().remove(tt);
            }
            // Auto-derive namespace if blank
            if (tt.getNamespace() == null || tt.getNamespace().isBlank()) {
                tt.setNamespace(deriveNamespace(currentModel, tt.getName()));
            }
            return tt;
        }).orElse(null);
    }

    private void addElementAtPosition(double x, double y) {
        String name = promptName("New Element", "elementName", "myElement");
        if (name == null || name.isBlank()) return;

        // F-157 fix: standalone element node is NOT auto-attached to any TopicType.
        // The user can attach it via the Containment arrow tool.
        ElementDef elem = new ElementDef(name);
        elem.setX(x - 80);
        elem.setY(y - 30);

        // Add to model as a standalone element (parentId left unset)
        currentModel.getStandaloneElements().add(elem);

        placeElementNode(elem);
        pushUndo();
        markDirty();
        setStatus("Added standalone Element: " + name + "  (use Containment arrow to link to a TopicType)");
        log.log("Added standalone element '" + name + "'");
    }

    private void addDomainAtPosition(double x, double y) {
        String name = promptName("New Domain", "domainName", "myDomain");
        if (name == null || name.isBlank()) return;

        pushUndo();
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
        // F-173: delete stale files when node is renamed via inline double-click
        node.setOnRenameCallback((oldName, newName) -> {
            String oldStem = (tt.getModule() != null && !tt.getModule().isBlank())
                    ? tt.getModule() : oldName.toLowerCase();
            deleteSchemaFiles(oldStem);
            markDirty();
        });
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
        // F-013: delete all multi-selected nodes first
        if (!multiSelectedNodes.isEmpty()) {
            List<DiagramNode> toDelete = new ArrayList<>(multiSelectedNodes);
            clearMultiSelect();
            for (DiagramNode n : toDelete) {
                if (n instanceof TopicTypeNode ttn) deleteTopicTypeNodeById(ttn.getModelId(), ttn);
                else if (n instanceof ElementNode en) deleteElementNodeById(en.getModelId(), en);
            }
            return;
        }
        if (selectedDiagramNode == null) return;
        String id = selectedDiagramNode.getModelId();
        if (selectedDiagramNode instanceof TopicTypeNode ttn) {
            deleteTopicTypeNodeById(id, ttn);
        } else if (selectedDiagramNode instanceof ElementNode en) {
            deleteElementNodeById(id, en);
        }
    }

    private void deleteTopicTypeNodeById(String id, TopicTypeNode node) {
        pushUndo("Delete TopicType: " + node.getTopicType().getName());
        String stem = node.getTopicType().resolvedModule();  // F-173: capture before removal
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
        multiSelectedNodes.remove(node);  // F-013
        selectedDiagramNode = null;
        showNoSelection();
        deleteSchemaFiles(stem);  // F-173
        markDirty();
        setStatus("Deleted: " + node.getTopicType().getName());
        log.log("Deleted TopicType: " + node.getTopicType().getName());
    }

    private void deleteElementNodeById(String id, ElementNode node) {
        pushUndo();
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

    // ── undo / redo ───────────────────────────────────────────────────

    /**
     * F-154 fix: capture a JSON snapshot of the current model onto the undo stack.
     * Call this BEFORE every model-mutating operation so the state can be restored.
     */
    private void pushUndo() { pushUndo("Change"); }

    private void pushUndo(String label) {
        try {
            String snapshot = repository.toJson(currentModel);
            undoLabels.push(label);  // F-023: record human-readable label
            // Capture the current snapshot for redo when this undo fires
            undoStack.push(() -> {
                try {
                    String redoSnapshot = repository.toJson(currentModel);
                    restoreSnapshot(snapshot);
                    redoStack.push(() -> {
                        try { restoreSnapshot(redoSnapshot); }
                        catch (Exception ex) { log.logError("Redo restore failed", ex); }
                    });
                } catch (Exception ex) {
                    log.logError("Undo restore failed", ex);
                }
            });
            // Cap undo stack at 50 entries
            while (undoStack.size() > 50) {
                List<Runnable> tmp = new ArrayList<>(undoStack);
                undoStack.clear();
                tmp.subList(0, 50).forEach(undoStack::addLast);
                if (!undoLabels.isEmpty()) {
                    List<String> lblTmp = new ArrayList<>(undoLabels);
                    undoLabels.clear();
                    lblTmp.subList(0, Math.min(50, lblTmp.size())).forEach(undoLabels::addLast);
                }
            }
        } catch (Exception ex) {
            log.logError("Failed to create undo snapshot", ex);
        }
    }

    /** F-023: Undo history dialog */
    @FXML
    private void onUndoHistory() {
        if (undoStack.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "The undo stack is empty.", ButtonType.OK);
            a.setTitle("Undo History");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Undo History");
        dlg.setHeaderText(undoStack.size() + " undoable action(s) — most recent first");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox list = new VBox(4);
        list.setPadding(new Insets(8));
        List<String> labels = new ArrayList<>(undoLabels);
        for (int i = 0; i < labels.size(); i++) {
            Label lbl = new Label((i + 1) + ".  " + labels.get(i));
            lbl.setStyle("-fx-font-size: 12px;");
            list.getChildren().add(lbl);
        }
        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setPrefHeight(280);
        dlg.getDialogPane().setContent(sp);
        dlg.showAndWait();
    }

    private void restoreSnapshot(String json) throws Exception {
        currentModel = repository.fromJson(json);
        clearCanvas();
        rebuildCanvasFromModel();
        markDirty();
        showNoSelection();
    }

    @FXML
    private void onUndo() {
        if (undoStack.isEmpty()) {
            setStatus("Nothing to undo.");
            return;
        }
        String label = undoLabels.isEmpty() ? "Change" : undoLabels.pop();
        Runnable undoAction = undoStack.pop();
        undoAction.run();
        setStatus("Undo: " + label);
    }

    @FXML
    private void onRedo() {
        if (redoStack.isEmpty()) {
            setStatus("Nothing to redo.");
            return;
        }
        Runnable redoAction = redoStack.pop();
        redoAction.run();
        setStatus("Redo performed.");
    }

    // ── Dark mode (F-135) ─────────────────────────────────────────────

    @FXML
    private void onToggleDarkMode() {
        darkMode = !darkMode;
        if (darkModeItem != null) darkModeItem.setSelected(darkMode);
        applyTheme();
        setStatus("Theme: " + (darkMode ? "Dark" : "Light"));
        log.log("Theme switched to " + (darkMode ? "dark" : "light") + " mode.");
    }

    private void applyTheme() {
        if (canvas == null || canvas.getScene() == null) return;
        var stylesheets = canvas.getScene().getStylesheets();
        String darkCss = getClass().getResource("/css/dark.css").toExternalForm();
        if (darkMode) {
            if (!stylesheets.contains(darkCss)) stylesheets.add(darkCss);
        } else {
            stylesheets.remove(darkCss);
        }
        Preferences.userNodeForPackage(MainController.class).putBoolean("dark.mode", darkMode);
    }

    // ── Show/Hide Log (F-137) ─────────────────────────────────────────

    @FXML
    private void onToggleLog() {
        if (logArea == null) return;
        // The log area's parent VBox is the bottom section
        var parent = logArea.getParent();
        if (parent != null) {
            boolean nowVisible = !logArea.isVisible();
            parent.setVisible(nowVisible);
            parent.setManaged(nowVisible);
            logArea.setVisible(nowVisible);
            logArea.setManaged(nowVisible);
            if (logVisibleItem != null) logVisibleItem.setSelected(nowVisible);
            setStatus("Log panel " + (nowVisible ? "shown" : "hidden"));
        }
    }

    // ── Export PNG (F-144) ────────────────────────────────────────────

    @FXML
    private void onExportPng() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Canvas as PNG");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        fc.setInitialFileName(currentModel.getName().replaceAll("\\s+", "_") + "_diagram.png");
        File file = fc.showSaveDialog(getStage());
        if (file == null) return;

        try {
            WritableImage image = canvas.snapshot(null, null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            log.logSuccess("Canvas exported as PNG: " + file.getPath());
            setStatus("PNG exported: " + file.getName());
        } catch (IOException ex) {
            showError("PNG Export Failed", ex.getMessage());
            log.logError("PNG export failed", ex);
        }
    }

    // ── Copy diagram to clipboard (F-145) ─────────────────────────────

    @FXML
    private void onCopyDiagramToClipboard() {
        WritableImage image = canvas.snapshot(null, null);
        ClipboardContent content = new ClipboardContent();
        content.putImage(image);
        Clipboard.getSystemClipboard().setContent(content);
        setStatus("Diagram copied to clipboard.");
        log.logSuccess("Canvas diagram copied to clipboard as image.");
    }

    // ── Generate HTML documentation (F-142) ───────────────────────────

    @FXML
    private void onGenerateHtmlDocs() {
        File outputDir = chooseOutputDir();
        if (outputDir == null) return;
        try {
            File htmlFile = htmlDocGenerator.generate(currentModel, outputDir);
            log.logSuccess("HTML documentation generated: " + htmlFile.getPath());
            setStatus("HTML docs generated: " + htmlFile.getName());

            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("HTML Documentation");
            info.setHeaderText("Documentation generated successfully.");
            info.setContentText("File: " + htmlFile.getPath() + "\n\nOpen in a browser to view.");
            info.showAndWait();
        } catch (Exception ex) {
            showError("HTML Generation Failed", ex.getMessage());
            log.logError("HTML doc generation failed", ex);
        }
    }

    // ── Export validation report (F-125) ──────────────────────────────

    @FXML
    private void onExportValidationReport() {
        // Run validation first to get fresh results (validate() already includes orphan detection)
        lastValidationIssues = transformer.validate(currentModel);

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Validation Report");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text File", "*.txt"));
        fc.setInitialFileName(currentModel.getName().replaceAll("\\s+", "_") + "_validation.txt");
        File file = fc.showSaveDialog(getStage());
        if (file == null) return;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("DITA Specialization Designer — Validation Report\n");
            sb.append("=".repeat(50)).append("\n");
            sb.append("Project: ").append(currentModel.getName())
              .append(" v").append(nvl(currentModel.getVersion(), "1.0")).append("\n");
            sb.append("Date: ").append(java.time.LocalDate.now()).append("\n");
            sb.append("=".repeat(50)).append("\n\n");

            if (lastValidationIssues.isEmpty()) {
                sb.append("✓ No validation issues found.\n");
            } else {
                sb.append("Issues found: ").append(lastValidationIssues.size()).append("\n\n");
                for (int i = 0; i < lastValidationIssues.size(); i++) {
                    sb.append((i + 1)).append(". ").append(lastValidationIssues.get(i)).append("\n");
                }
            }
            sb.append("\n--- End of Report ---\n");

            FileUtil.writeString(file, sb.toString());
            log.logSuccess("Validation report exported: " + file.getPath());
            setStatus("Validation report exported: " + file.getName());
        } catch (IOException ex) {
            showError("Export Failed", ex.getMessage());
            log.logError("Validation report export failed", ex);
        }
    }

    // ── Export Summary CSV (F-export-csv) ─────────────────────────────

    @FXML
    private void onExportCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Summary CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        fc.setInitialFileName(currentModel.getName().replaceAll("\\s+", "_") + "_summary.csv");
        File file = fc.showSaveDialog(getStage());
        if (file == null) return;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Type,Name,BaseType,Elements,Attributes,Namespace\n");
            for (TopicType tt : currentModel.getTopicTypes()) {
                sb.append("TopicType,")
                  .append(csv(tt.getName())).append(",")
                  .append(csv(tt.getBaseType())).append(",")
                  .append(tt.getElements().size()).append(",")
                  .append(tt.getAttributes().size()).append(",")
                  .append(csv(nvl(tt.getNamespace()))).append("\n");
                for (ElementDef elem : tt.getElements()) {
                    sb.append("Element,")
                      .append(csv(elem.getName())).append(",")
                      .append(csv(nvl(elem.getContentModel()))).append(",")
                      .append(",").append(elem.getAttributes().size()).append(",\n");
                }
            }
            for (DomainDef domain : currentModel.getDomains()) {
                sb.append("Domain,")
                  .append(csv(domain.getName())).append(",,")
                  .append(domain.getElements().size()).append(",,\n");
            }
            FileUtil.writeString(file, sb.toString());
            log.logSuccess("CSV summary exported: " + file.getPath());
            setStatus("CSV exported: " + file.getName());
        } catch (IOException ex) {
            showError("CSV Export Failed", ex.getMessage());
            log.logError("CSV export failed", ex);
        }
    }

    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ── Reload Libraries (F-092) ──────────────────────────────────────

    @FXML
    private void onReloadLibraries() {
        if (lastLibDir == null) {
            onLoadDitaLibraries();
            return;
        }
        int count = oasisLoader.loadFromDirectory(lastLibDir);
        setStatus("Reloaded " + count + " DITA library files from " + lastLibDir.getName());
        log.logSuccess("Reloaded " + count + " library files from " + lastLibDir.getPath());
    }

    // ── Auto-save (F-080) ─────────────────────────────────────────────

    private void startAutoSave() {
        autoSaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dita-autosave");
            t.setDaemon(true);
            return t;
        });
        autoSaveExecutor.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            if (dirty && currentProjectFile != null) {
                try {
                    repository.save(currentModel, currentProjectFile);
                    dirty = false;
                    updateTitle();
                    log.log("[Auto-save] Saved to " + currentProjectFile.getName());
                } catch (Exception ex) {
                    log.logError("Auto-save failed", ex);
                }
            }
        }), 5, 5, TimeUnit.MINUTES);
    }

    // ── Window state persistence (F-139) ──────────────────────────────

    private void saveWindowState() {
        Stage stage = getStage();
        if (stage == null) return;
        Preferences prefs = Preferences.userNodeForPackage(MainController.class);
        prefs.putDouble("win.x", stage.getX());
        prefs.putDouble("win.y", stage.getY());
        prefs.putDouble("win.w", stage.getWidth());
        prefs.putDouble("win.h", stage.getHeight());
        prefs.putBoolean("win.max", stage.isMaximized());
        prefs.putBoolean("dark.mode", darkMode);
    }

    private void restoreWindowState() {
        Stage stage = getStage();
        if (stage == null) return;
        Preferences prefs = Preferences.userNodeForPackage(MainController.class);
        double w = prefs.getDouble("win.w", -1);
        if (w > 200) {
            if (!stage.isMaximized()) {
                stage.setX(prefs.getDouble("win.x", 50));
                stage.setY(prefs.getDouble("win.y", 50));
                stage.setWidth(w);
                stage.setHeight(prefs.getDouble("win.h", 800));
            }
            boolean wasMax = prefs.getBoolean("win.max", true);
            if (wasMax) stage.setMaximized(true);
        }
        // Restore dark mode
        if (prefs.getBoolean("dark.mode", false)) {
            darkMode = true;
            if (darkModeItem != null) darkModeItem.setSelected(true);
            Platform.runLater(this::applyTheme);
        }
    }

    // ── Project File Explorer (F-167 to F-176) ────────────────────────

    /** Refresh the explorer tree from the current project's output directory. */
    private void refreshExplorer() {
        if (explorerTree == null) return;

        String projectName = currentModel != null ? currentModel.getName() : "Project";
        // F-175: stale indicator when model changed after last generation
        boolean stale = dirty && (lastGenerationMs == 0 || lastModelChangeMs > lastGenerationMs)
                && !liveSyncEnabled;
        String staleMarker = stale ? " ⚠" : "";
        TreeItem<String> root = new TreeItem<>("📁 " + projectName + staleMarker);
        root.setExpanded(true);

        String filter = (explorerFilter != null) ? explorerFilter.getText().trim().toLowerCase() : "";
        File outputDir = resolveOutputDir();
        if (outputDir != null && outputDir.exists()) {
            buildExplorerTree(root, outputDir, outputDir, filter);
        } else {
            TreeItem<String> noOutput = new TreeItem<>("(no output generated yet)");
            root.getChildren().add(noOutput);
        }
        explorerTree.setRoot(root);
    }

    /** F-127: Called when the filter text field changes. */
    @FXML
    private void onExplorerFilter() {
        refreshExplorer();
    }

    private void buildExplorerTree(TreeItem<String> parent, File dir, File rootDir, String filter) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> {
            // directories first, then files
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File f : children) {
            // F-127: apply filter (skip files that don't match; always include dirs that may have matches)
            if (!filter.isEmpty() && !f.isDirectory() && !f.getName().toLowerCase().contains(filter)) continue;
            // F-175: mark stale files (modified before last generation but after model changed)
            boolean fileStale = !f.isDirectory() && lastGenerationMs > 0
                    && f.lastModified() < lastGenerationMs && lastModelChangeMs > lastGenerationMs;
            String icon = f.isDirectory() ? "📁 " : (fileStale ? "⚠ " : fileIcon(f.getName()));
            TreeItem<String> item = new TreeItem<>(icon + f.getName());
            item.setExpanded(true);
            parent.getChildren().add(item);
            if (f.isDirectory()) buildExplorerTree(item, f, rootDir, filter);
        }
    }

    private String fileIcon(String name) {
        if (name.endsWith(".xsd"))  return "⊞ ";
        if (name.endsWith(".dtd"))  return "⊟ ";
        if (name.endsWith(".mod"))  return "⊡ ";
        if (name.endsWith(".ent"))  return "⊠ ";
        if (name.endsWith(".xml") || name.endsWith(".catalog")) return "⊛ ";
        if (name.endsWith(".ddp"))  return "◉ ";
        return "◦ ";
    }

    /** Resolve the project's output directory from model.outputDir or a default. */
    private File resolveOutputDir() {
        if (currentModel == null) return null;
        String outDir = currentModel.getOutputDir();
        if (outDir != null && !outDir.isBlank()) {
            File f = new File(outDir);
            if (!f.isAbsolute() && currentProjectFile != null) {
                f = new File(currentProjectFile.getParent(), outDir);
            }
            return f;
        }
        // Default: sibling "output/" folder next to the project file
        if (currentProjectFile != null) {
            return new File(currentProjectFile.getParent(), "output");
        }
        return null;
    }

    @FXML
    private void onRefreshExplorer() {
        refreshExplorer();
        setStatus("Explorer refreshed.");
    }

    /** F-176: Open the output folder in the OS file manager. */
    @FXML
    private void onOpenOutputDir() {
        File dir = resolveOutputDir();
        if (dir == null || !dir.exists()) {
            showError("No Output Folder", "Generate artefacts first or set an output directory in Project Metadata.");
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            showError("Cannot Open Folder", ex.getMessage());
        }
    }

    /** F-169: Double-click a file in the explorer to preview its content. */
    @FXML
    private void onExplorerClick(javafx.scene.input.MouseEvent e) {
        TreeItem<String> selected = explorerTree.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        File file = resolveFileFromTreeItem(selected);
        if (file == null || !file.isFile()) return;

        // Single click: navigate to the topic type that owns this file
        if (e.getClickCount() == 1) {
            navigateToTopicTypeForFile(file);
        }
        // Double click: also open a preview
        if (e.getClickCount() == 2) {
            showFilePreview(file);
        }
    }

    /**
     * Given a generated schema file (e.g. phxtask.xsd, phxtask.dtd, phxtask.mod),
     * find the TopicType whose resolvedModule() matches the file stem, select its
     * canvas node and populate the properties panel.
     */
    private void navigateToTopicTypeForFile(File file) {
        String name = file.getName();
        // Strip known extensions to get the stem
        String stem = name;
        for (String ext : new String[]{".dtd", ".mod", ".ent", ".xsd", ".xml"}) {
            if (name.toLowerCase().endsWith(ext)) {
                stem = name.substring(0, name.length() - ext.length());
                break;
            }
        }
        final String finalStem = stem;

        // Find matching TopicType by resolvedModule()
        TopicType match = (currentModel != null)
                ? currentModel.getTopicTypes().stream()
                        .filter(tt -> tt.resolvedModule().equalsIgnoreCase(finalStem))
                        .findFirst().orElse(null)
                : null;

        // No match in current model — offer to import from the XSD file
        if (match == null && file.getName().toLowerCase().endsWith(".xsd")) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "'" + name + "' is not in the current project model.\n\n" +
                    "Import it now to load its elements and attributes onto the canvas?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Import XSD");
            confirm.setHeaderText("Topic type not found in model");
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                try {
                    com.ditadesigner.importer.XsdImporter importer = new com.ditadesigner.importer.XsdImporter();
                    TopicType imported = importer.importXsd(file);
                    currentModel.addTopicType(imported);
                    placeTopicTypeNode(imported);
                    match = imported;
                    markDirty();
                    log.logSuccess("Imported from Explorer: " + imported.getName()
                            + " (" + imported.getElements().size() + " elements)");
                } catch (Exception ex) {
                    showError("Import Failed", ex.getMessage());
                    log.logError("XSD import failed", ex);
                    return;
                }
            } else {
                return;
            }
        }

        if (match == null) {
            setStatus("No topic type matches '" + finalStem + "' in the current project.");
            return;
        }

        TopicTypeNode node = topicTypeNodeMap.get(match.getId());
        if (node == null) {
            // Topic type is in the model but not on the canvas — place it now
            placeTopicTypeNode(match);
            node = topicTypeNodeMap.get(match.getId());
        }

        if (node != null) {
            clearMultiSelect();
            selectDiagramNode(node);
            showTopicTypeProperties(match);

            // Scroll canvas to centre on the node
            double nx = node.getLayoutX() * zoomScale;
            double ny = node.getLayoutY() * zoomScale;
            double vw = canvasScrollPane.getViewportBounds().getWidth();
            double vh = canvasScrollPane.getViewportBounds().getHeight();
            double cw = canvas.getBoundsInLocal().getWidth()  * zoomScale;
            double ch = canvas.getBoundsInLocal().getHeight() * zoomScale;
            if (cw > vw) canvasScrollPane.setHvalue(Math.max(0, Math.min(1, (nx - vw / 2) / (cw - vw))));
            if (ch > vh) canvasScrollPane.setVvalue(Math.max(0, Math.min(1, (ny - vh / 2) / (ch - vh))));

            setStatus("Selected: " + match.getName() + " (" + name + ")");
        }
    }

    /** Reconstruct the absolute File by walking up the tree hierarchy. */
    private File resolveFileFromTreeItem(TreeItem<String> item) {
        List<String> parts = new ArrayList<>();
        TreeItem<String> cur = item;
        while (cur != null && cur.getParent() != null) {
            String raw = cur.getValue();
            if (raw != null) {
                // Strip icon prefix: everything up to and including the first space.
                // Icons may be emoji (surrogate pairs = 2 Java chars) + space, or BMP + space.
                // Using indexOf(' ') handles any icon width correctly.
                int sp = raw.indexOf(' ');
                raw = (sp >= 0) ? raw.substring(sp + 1) : raw;
                // Also strip trailing stale marker " ⚠" from directory names
                if (raw.endsWith(" ⚠")) raw = raw.substring(0, raw.length() - 2);
            }
            parts.add(0, raw != null ? raw : "");
            cur = cur.getParent();
        }
        File base = resolveOutputDir();
        if (base == null) return null;
        File result = base;
        for (String part : parts) result = new File(result, part);
        return result;
    }

    /** F-170: Toggle Live Sync on/off. */
    @FXML
    private void onToggleLiveSync() {
        liveSyncEnabled = liveSyncCheckBox != null && liveSyncCheckBox.isSelected();
        if (liveSyncEnabled) {
            File dir = resolveOutputDir();
            if (dir == null) {
                Alert a = new Alert(Alert.AlertType.WARNING,
                        "Set an Output Directory in File → Project Metadata before enabling Live Sync.",
                        ButtonType.OK);
                a.setTitle("Live Sync");
                a.showAndWait();
                if (liveSyncCheckBox != null) liveSyncCheckBox.setSelected(false);
                liveSyncEnabled = false;
                return;
            }
            resolvedOutputDir = dir;
            log.log("⚡ Live Sync enabled → " + dir.getPath());
            setStatus("Live Sync ON — changes will auto-generate to " + dir.getName());
        } else {
            log.log("Live Sync disabled.");
            setStatus("Live Sync OFF.");
        }
    }

    /** F-170/171: If Live Sync is on, regenerate all artefacts and refresh explorer. */
    private void liveSyncGenerate() {
        if (!liveSyncEnabled) return;
        // Always resolve the current output dir — don't rely on a stale cached value
        File dir = resolveOutputDir();
        if (dir == null) return;
        resolvedOutputDir = dir; // keep cache in sync
        try {
            doGenerate(dir);
            Platform.runLater(this::refreshExplorer);
        } catch (Exception ex) {
            log.logError("Live Sync generation failed", ex);
        }
    }

    /** F-168: Show/hide the explorer panel. */
    @FXML
    private void onToggleExplorer() {
        if (explorerPanel == null) return;
        boolean show = explorerVisibleItem == null || explorerVisibleItem.isSelected();
        explorerPanel.setVisible(show);
        explorerPanel.setManaged(show);
    }

    // ── F-176: Explorer right-click context menu ───────────────────────

    private void setupExplorerContextMenu() {
        if (explorerTree == null) return;
        ContextMenu cm = new ContextMenu();
        MenuItem previewItem  = new MenuItem("👁 Preview");
        MenuItem copyPathItem = new MenuItem("📋 Copy Path");
        MenuItem revealItem   = new MenuItem("📂 Reveal in File Manager");
        cm.getItems().addAll(previewItem, new SeparatorMenuItem(), copyPathItem, revealItem);

        explorerTree.setOnContextMenuRequested(e -> {
            TreeItem<String> item = explorerTree.getSelectionModel().getSelectedItem();
            if (item == null) { cm.hide(); return; }
            File file = resolveFileFromTreeItem(item);
            boolean isFile = file != null && file.isFile();
            boolean exists  = file != null && file.exists();
            previewItem.setDisable(!isFile);
            copyPathItem.setDisable(!exists);
            revealItem.setDisable(!exists);
            cm.show(explorerTree, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        previewItem.setOnAction(e -> {
            TreeItem<String> item = explorerTree.getSelectionModel().getSelectedItem();
            if (item == null) return;
            File file = resolveFileFromTreeItem(item);
            if (file != null && file.isFile()) showFilePreview(file);
        });

        copyPathItem.setOnAction(e -> {
            TreeItem<String> item = explorerTree.getSelectionModel().getSelectedItem();
            if (item == null) return;
            File file = resolveFileFromTreeItem(item);
            if (file == null) return;
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(file.getAbsolutePath());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            setStatus("Copied: " + file.getAbsolutePath());
        });

        revealItem.setOnAction(e -> {
            TreeItem<String> item = explorerTree.getSelectionModel().getSelectedItem();
            if (item == null) return;
            File file = resolveFileFromTreeItem(item);
            if (file == null) return;
            File dir = file.isDirectory() ? file : file.getParentFile();
            try { java.awt.Desktop.getDesktop().open(dir); }
            catch (Exception ex) { showError("Cannot open folder", ex.getMessage()); }
        });
    }

    /** Show a read-only file preview dialog. */
    private void showFilePreview(File file) {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            Dialog<Void> viewer = new Dialog<>();
            viewer.setTitle("Preview: " + file.getName());
            viewer.setHeaderText(file.getAbsolutePath());
            viewer.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            TextArea ta = new TextArea(content);
            ta.setEditable(false);
            ta.setStyle("-fx-font-family: 'Consolas', 'Cascadia Code', monospace; -fx-font-size: 11px;");
            ta.setPrefWidth(740);
            ta.setPrefHeight(520);
            viewer.getDialogPane().setContent(ta);
            viewer.showAndWait();
        } catch (Exception ex) {
            showError("Preview Failed", ex.getMessage());
        }
    }

    // ── F-173: Stale schema file cleanup ─────────────────────────────

    /** Delete old DTD/XSD/mod/ent files for a topic type stem after rename or delete. */
    private void deleteSchemaFiles(String stem) {
        File outDir = resolveOutputDir();
        if (outDir == null || !outDir.exists()) return;
        String[] relativePaths = {
            "dtd/" + stem + ".dtd",
            "dtd/" + stem + ".mod",
            "dtd/" + stem + ".ent",
            "xsd/" + stem + ".xsd"
        };
        boolean any = false;
        for (String rel : relativePaths) {
            File f = new File(outDir, rel);
            if (f.exists() && f.delete()) {
                log.log("Removed stale file: " + rel);
                any = true;
            }
        }
        if (any) Platform.runLater(this::refreshExplorer);
    }

    // ── Validation with jump-to-node (F-121) ──────────────────────────

    @FXML
    private void onValidateModel() {
        lastValidationIssues = transformer.validate(currentModel);
        // Clear all invalid marks first
        for (TopicTypeNode n : topicTypeNodeMap.values()) n.markInvalid(false);

        if (lastValidationIssues.isEmpty()) {
            log.logSuccess("Validation passed — no issues found.");
            setStatus("Validation: OK");
        } else {
            log.log("Validation found " + lastValidationIssues.size() + " issue(s):");
            for (String issue : lastValidationIssues) log.log("  • " + issue);
            setStatus("Validation: " + lastValidationIssues.size() + " issue(s)");

            // Mark invalid nodes (duplicate names)
            Set<String> dupeNames = findDuplicateTopicTypeNames();
            for (TopicTypeNode n : topicTypeNodeMap.values()) {
                if (dupeNames.contains(n.getTopicType().getName())) n.markInvalid(true);
            }

            showValidationDialog(lastValidationIssues);
        }
    }

    private Set<String> findDuplicateTopicTypeNames() {
        Set<String> seen = new HashSet<>(), dupes = new HashSet<>();
        for (TopicType tt : currentModel.getTopicTypes()) {
            if (!seen.add(tt.getName())) dupes.add(tt.getName());
        }
        return dupes;
    }

    /** F-121: Dialog with jump-to-node links for each issue. */
    private void showValidationDialog(List<String> issues) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Validation Results");
        dialog.setHeaderText(issues.size() + " issue(s) found");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(6);
        content.setPadding(new Insets(10));
        content.setPrefWidth(460);

        for (String issue : issues) {
            HBox row = new HBox(8);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label icon = new Label("⚠");
            icon.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 13px;");
            Label lbl = new Label(issue);
            lbl.setWrapText(true);
            lbl.setStyle("-fx-font-size: 12px;");
            HBox.setHgrow(lbl, Priority.ALWAYS);

            // If issue mentions a TopicType name, add a jump button
            Optional<TopicType> target = currentModel.getTopicTypes().stream()
                    .filter(tt -> issue.contains("'" + tt.getName() + "'"))
                    .findFirst();
            if (target.isPresent()) {
                TopicType tt = target.get();
                Button jump = new Button("→ Go");
                jump.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6;");
                jump.setOnAction(e -> {
                    TopicTypeNode node = topicTypeNodeMap.get(tt.getId());
                    if (node != null) {
                        selectDiagramNode(node);
                        showTopicTypeProperties(tt);
                        dialog.close();
                    }
                });
                row.getChildren().addAll(icon, lbl, jump);
            } else {
                row.getChildren().addAll(icon, lbl);
            }
            content.getChildren().add(row);
        }

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setPrefHeight(300);
        dialog.getDialogPane().setContent(sp);
        dialog.showAndWait();
    }

    // ── select all ───────────────────────────────────────────────────

    @FXML
    private void onSelectAll() {
        // F-013: select all canvas nodes
        clearMultiSelect();
        topicTypeNodeMap.values().forEach(n -> { multiSelectedNodes.add(n); n.setSelected(true); });
        elementNodeMap.values().forEach(n -> { multiSelectedNodes.add(n); n.setSelected(true); });
        int count = multiSelectedNodes.size();
        setStatus(count + " node(s) selected — Press Delete to remove all");
        showNoSelection();
    }

    // ── node click handlers ───────────────────────────────────────────

    private void handleNodeClick(TopicTypeNode node) {
        if (currentTool == Tool.CONNECT_INHERIT || currentTool == Tool.CONNECT_CONTAIN) {
            handleConnectionClick(node);
        } else {
            // F-013: Ctrl+Click → toggle multi-select
            javafx.scene.input.MouseEvent lastEvt = node.getLastClickEvent();
            if (lastEvt != null && lastEvt.isControlDown()) {
                toggleMultiSelect(node);
                return;
            }
            clearMultiSelect();
            selectDiagramNode(node);
            // F-156 fix: detect domain nodes and show domain-specific properties
            DomainDef domain = currentModel.findDomainById(node.getTopicType().getId());
            if (domain != null) {
                showDomainProperties(domain);
            } else {
                showTopicTypeProperties(node.getTopicType());
            }
        }
    }

    // ── F-013: multi-select ───────────────────────────────────────────

    private void toggleMultiSelect(DiagramNode node) {
        if (multiSelectedNodes.contains(node)) {
            multiSelectedNodes.remove(node);
            node.setSelected(false);
        } else {
            multiSelectedNodes.add(node);
            node.setSelected(true);
        }
        setStatus(multiSelectedNodes.size() + " node(s) selected — Press Delete to remove all");
        showNoSelection();
    }

    private void clearMultiSelect() {
        for (DiagramNode n : multiSelectedNodes) n.setSelected(false);
        multiSelectedNodes.clear();
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
            pushUndo();
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
        clearMultiSelect();  // F-013
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

    // ── F-156: Domain Properties Panel ───────────────────────────────

    private void showDomainProperties(DomainDef domain) {
        propertiesPanel.getChildren().clear();

        Label title = new Label("Domain Properties");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #6a1b9a;");
        propertiesPanel.getChildren().addAll(title, new Separator());

        addPropField("Name:", domain.getName(), value -> {
            pushUndo();
            domain.setName(value);
            TopicTypeNode n = topicTypeNodeMap.get(domain.getId());
            if (n != null) { n.getTopicType().setName(value); n.refresh(); }
            markDirty();
        });

        addPropField("Public ID:", nvl(domain.getPublicId()), value -> {
            domain.setPublicId(value.isBlank() ? null : value);
            markDirty();
        });

        addPropTextArea("Description:", nvl(domain.getDescription()), value -> {
            domain.setDescription(value);
            markDirty();
        });

        // Elements
        propertiesPanel.getChildren().add(new Separator());
        Label elemHeader = new Label("Domain Elements (" + domain.getElements().size() + ")");
        elemHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        propertiesPanel.getChildren().add(elemHeader);

        for (ElementDef elem : new ArrayList<>(domain.getElements())) {
            HBox row = new HBox(4);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label lbl = new Label("+ " + elem.getName());
            lbl.setStyle("-fx-font-size: 10px; -fx-cursor: hand;");
            lbl.setTooltip(new Tooltip("Click to edit element"));
            lbl.setOnMouseClicked(ev -> showEditElementDialog(elem, null, domain));
            HBox.setHgrow(lbl, Priority.ALWAYS);

            Button editBtn = smallButton("✎ Edit", () -> showEditElementDialog(elem, null, domain));
            Button del = smallButton("✕", () -> {
                pushUndo();
                domain.getElements().remove(elem);
                markDirty();
                showDomainProperties(domain);
            });
            del.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-text-fill: #c0392b;");
            row.getChildren().addAll(lbl, editBtn, del);
            propertiesPanel.getChildren().add(row);
        }

        Button addElemBtn = new Button("+ Add Element");
        addElemBtn.setStyle("-fx-font-size: 10px;");
        addElemBtn.setOnAction(e -> {
            String name = promptName("Domain Element", "elementName", "myElem");
            if (name == null || name.isBlank()) return;
            pushUndo();
            domain.addElement(new ElementDef(name));
            TopicTypeNode n = topicTypeNodeMap.get(domain.getId());
            if (n != null) n.refresh();
            markDirty();
            showDomainProperties(domain);
        });
        propertiesPanel.getChildren().add(addElemBtn);
    }

    // ── F-177: Auto-derive namespace from project + type name ─────────

    /**
     * Derives a URN namespace from the project's target namespace or name and the
     * topic type's local name.
     * Example: project "MyProduct", type "MyTask" → "urn:myproduct:mytask"
     */
    private String deriveNamespace(DitaModel model, String typeName) {
        String base = model.getTargetNamespace();
        if (base == null || base.isBlank()) {
            base = "urn:" + model.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        }
        // Remove trailing slash
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + ":" + typeName.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private void showTopicTypeProperties(TopicType tt) {
        propertiesPanel.getChildren().clear();

        Label title = new Label("TopicType Properties");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1565C0;");
        propertiesPanel.getChildren().addAll(title, new Separator());

        addPropField("Name:", tt.getName(), value -> {
            String oldStem = tt.resolvedModule();  // F-173: capture before rename
            tt.setName(value);
            TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
            if (n != null) n.refresh();
            String newStem = tt.resolvedModule();
            if (!oldStem.equals(newStem)) deleteSchemaFiles(oldStem);  // F-173
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

        // ── F-097/F-107: Live DTD / XSD preview buttons ───────────────
        propertiesPanel.getChildren().add(new Separator());
        HBox previewRow = new HBox(6);
        previewRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Button dtdPreviewBtn = new Button("👁 DTD Preview");
        dtdPreviewBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6;");
        dtdPreviewBtn.setOnAction(e -> {
            String content = dtdGenerator.previewDtd(tt, currentModel);
            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("DTD Preview: " + tt.getName());
            dlg.setHeaderText(tt.resolvedModule() + ".dtd");
            dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            TextArea ta = new TextArea(content);
            ta.setEditable(false);
            ta.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
            ta.setPrefWidth(700); ta.setPrefHeight(480);
            dlg.getDialogPane().setContent(ta);
            dlg.showAndWait();
        });
        Button xsdPreviewBtn = new Button("👁 XSD Preview");
        xsdPreviewBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6;");
        xsdPreviewBtn.setOnAction(e -> {
            String content = xsdGenerator.previewXsd(tt, currentModel);
            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("XSD Preview: " + tt.getName());
            dlg.setHeaderText(tt.resolvedModule() + ".xsd");
            dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            TextArea ta = new TextArea(content);
            ta.setEditable(false);
            ta.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
            ta.setPrefWidth(700); ta.setPrefHeight(480);
            dlg.getDialogPane().setContent(ta);
            dlg.showAndWait();
        });
        Button sampleXmlBtn = new Button("📄 Sample XML");
        sampleXmlBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6;");
        sampleXmlBtn.setTooltip(new Tooltip("Generate a sample XML instance for this topic type"));
        sampleXmlBtn.setOnAction(e -> onGenerateSampleXml(tt));

        Button validateXmlBtn = new Button("✔ Validate XML…");
        validateXmlBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6;");
        validateXmlBtn.setTooltip(new Tooltip("Validate an XML file against this topic type's XSD"));
        validateXmlBtn.setOnAction(e -> onValidateXmlAgainstXsd(tt));

        previewRow.getChildren().addAll(dtdPreviewBtn, xsdPreviewBtn);
        propertiesPanel.getChildren().add(previewRow);

        HBox xmlRow = new HBox(6, sampleXmlBtn, validateXmlBtn);
        xmlRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        propertiesPanel.getChildren().add(xmlRow);

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
            lbl.setStyle("-fx-font-size: 10px; -fx-cursor: hand;");
            lbl.setTooltip(new Tooltip("Click to edit element"));
            HBox.setHgrow(lbl, Priority.ALWAYS);

            // Single click OR double-click on name opens edit dialog
            lbl.setOnMouseClicked(ev -> showEditElementDialog(elem, tt, null));

            Button edit = smallButton("✎ Edit", () -> showEditElementDialog(elem, tt, null));
            Button up   = smallButton("▲", () -> {
                if (idx > 0) {
                    pushUndo();
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
                    pushUndo();
                    elements.remove(idx);
                    elements.add(idx + 1, elem);
                    TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
                    if (n != null) n.refresh();
                    markDirty();
                    showTopicTypeProperties(tt);
                }
            });
            Button del  = smallButton("✕", () -> {
                pushUndo();
                tt.getElements().remove(elem);
                TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
                if (n != null) n.refresh();
                markDirty();
                showTopicTypeProperties(tt);
            });
            up.setDisable(idx == 0);
            down.setDisable(idx == elements.size() - 1);
            del.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-text-fill: #c0392b;");

            row.getChildren().addAll(lbl, edit, up, down, del);
            propertiesPanel.getChildren().add(row);

            // DTD fragment preview
            String card = elem.getCardinality() != null ? elem.getCardinality() : "?";
            String dtdLine = "<!ELEMENT " + elem.getName() + " "
                    + nvl(elem.getContentModel(), "(#PCDATA)*") + ">";
            Label dtdPreview = new Label("  " + dtdLine);
            dtdPreview.setStyle("-fx-font-size: 9px; -fx-text-fill: #999; -fx-font-family: monospace;");
            propertiesPanel.getChildren().add(dtdPreview);
        }

        HBox elemButtons = new HBox(4);
        Button addElem = new Button("+ New Element");
        addElem.setOnAction(e -> showAddElementDialog(tt));
        addElem.setStyle("-fx-font-size: 10px;");

        Button importElem = new Button("⤓ Import from DITA");
        importElem.setOnAction(e -> showDitaElementBrowser(tt));
        importElem.setStyle("-fx-font-size: 10px;");

        elemButtons.getChildren().addAll(addElem, importElem);
        propertiesPanel.getChildren().add(elemButtons);

        // ── Attributes ────────────────────────────────────────────────
        propertiesPanel.getChildren().add(new Separator());
        Label attrHeader = new Label("Attributes (" + tt.getAttributes().size() + ")");
        attrHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        propertiesPanel.getChildren().add(attrHeader);

        for (AttributeDef attr : new ArrayList<>(tt.getAttributes())) {
            HBox row = new HBox(4);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            String specTag = attr.isAttributeDomain() ? " [specializes @" + attr.getSpecializesFrom() + "]" : "";
            Label lbl = new Label("• " + attr.getName() + " : " + attr.getType() + specTag);
            lbl.setStyle("-fx-font-size: 10px;");
            if (attr.isAttributeDomain()) lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #1565C0;");
            HBox.setHgrow(lbl, Priority.ALWAYS);

            Label attrPreview = new Label(attr.toDtdFragment());
            attrPreview.setStyle("-fx-font-size: 9px; -fx-text-fill: #999; -fx-font-family: monospace;");

            // F-149/F-152: edit button and double-click label
            lbl.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2) showEditAttributeDialog(attr, tt);
            });
            Button editAttr = smallButton("✎", () -> showEditAttributeDialog(attr, tt));
            Button del = smallButton("✕", () -> {
                pushUndo();
                tt.getAttributes().remove(attr);
                markDirty();
                showTopicTypeProperties(tt);
            });
            del.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-text-fill: #c0392b;");

            row.getChildren().addAll(lbl, editAttr, del);
            propertiesPanel.getChildren().addAll(row, attrPreview);
        }

        Button addAttr = new Button("+ Add Attribute");
        addAttr.setOnAction(e -> {
            AttributeDef a = showAddAttributeDialog();
            if (a != null) {
                tt.addAttribute(a);
                markDirty();
                showTopicTypeProperties(tt);
            }
        });
        addAttr.setStyle("-fx-font-size: 10px;");
        propertiesPanel.getChildren().add(addAttr);
    }

    /** Rich element creation dialog with inline attribute editor. */
    private void showAddElementDialog(TopicType tt) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Add Element");
        dlg.setHeaderText("Add element to " + tt.getName());
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(12));

        TextField nameField = new TextField("newElement");
        TextField contentField = new TextField("(#PCDATA)*");
        ComboBox<String> cardCombo = new ComboBox<>();
        cardCombo.getItems().addAll("?", "1", "+", "*");
        cardCombo.setValue("?");
        CheckBox reqCheck = new CheckBox();

        grid.add(new Label("Element Name:"),   0, 0); grid.add(nameField,    1, 0);
        grid.add(new Label("Content Model:"),  0, 1); grid.add(contentField, 1, 1);
        grid.add(new Label("Cardinality:"),    0, 2); grid.add(cardCombo,    1, 2);
        grid.add(new Label("Required:"),       0, 3); grid.add(reqCheck,     1, 3);

        // Attribute sub-table
        Label attrHdr = new Label("Attributes:");
        attrHdr.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        grid.add(attrHdr, 0, 4, 2, 1);

        List<AttributeDef> pendingAttrs = new ArrayList<>();
        VBox attrRows = new VBox(3);
        ScrollPane attrScroll = new ScrollPane(attrRows);
        attrScroll.setFitToWidth(true);
        attrScroll.setPrefHeight(100);
        grid.add(attrScroll, 0, 5, 2, 1);

        Button addAttrBtn = new Button("+ Add Attribute");
        addAttrBtn.setStyle("-fx-font-size: 10px;");
        addAttrBtn.setOnAction(ev -> {
            AttributeDef a = showAddAttributeDialog();
            if (a != null) {
                pendingAttrs.add(a);
                HBox row = new HBox(4);
                String specTag = a.isAttributeDomain() ? " [" + a.getSpecializesFrom() + "]" : "";
                Label lbl = new Label("• " + a.getName() + " : " + a.getType() + specTag);
                lbl.setStyle("-fx-font-size: 10px;");
                HBox.setHgrow(lbl, Priority.ALWAYS);
                Button del = smallButton("✕", () -> { pendingAttrs.remove(a); attrRows.getChildren().remove(row); });
                del.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-text-fill: #c0392b;");
                row.getChildren().addAll(lbl, del);
                attrRows.getChildren().add(row);
            }
        });
        grid.add(addAttrBtn, 0, 6, 2, 1);

        dlg.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dlg.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String eName = nameField.getText().trim();
                if (eName.isBlank()) return;
                ElementDef elem = projectService.createElement(tt, eName, contentField.getText().trim(), cardCombo.getValue());
                elem.setRequired(reqCheck.isSelected());
                for (AttributeDef a : pendingAttrs) elem.addAttribute(a);
                TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
                if (n != null) n.refresh();
                markDirty();
                showTopicTypeProperties(tt);
            }
        });
    }

    // ── F-148/F-150/F-152: Edit element in-place ─────────────────────

    /**
     * Edit an existing element. {@code parentTopic} is set when the element belongs
     * to a TopicType; {@code parentDomain} is set for domain elements. One must be non-null.
     */
    private void showEditElementDialog(ElementDef elem, TopicType parentTopic, DomainDef parentDomain) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Edit Element");
        dlg.setHeaderText("Edit: " + elem.getName());
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setPrefWidth(540);

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));

        // ── Basic fields ──────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);

        TextField nameField    = new TextField(nvl(elem.getName()));
        TextField contentField = new TextField(nvl(elem.getContentModel(), "(#PCDATA)*"));
        ComboBox<String> cardCombo = new ComboBox<>();
        cardCombo.getItems().addAll("?", "1", "+", "*");
        cardCombo.setValue(nvl(elem.getCardinality(), "?"));
        CheckBox reqCheck = new CheckBox();
        reqCheck.setSelected(elem.isRequired());
        TextArea descField = new TextArea(nvl(elem.getDescription()));
        descField.setPrefRowCount(2);
        descField.setWrapText(true);
        nameField.setPrefWidth(300);

        // Content Model row: text field + Build button
        Button buildCmBtn = new Button("⚙ Build…");
        buildCmBtn.setStyle("-fx-font-size: 10px; -fx-cursor: hand;");
        buildCmBtn.setTooltip(new Tooltip("Open the Content Model Builder"));
        buildCmBtn.setOnAction(e -> showContentModelBuilder(contentField, parentTopic, elem));
        HBox cmRow = new HBox(6, contentField, buildCmBtn);
        HBox.setHgrow(contentField, Priority.ALWAYS);

        grid.add(new Label("Name:"),          0, 0); grid.add(nameField,    1, 0);
        grid.add(new Label("Content Model:"), 0, 1); grid.add(cmRow,        1, 1);
        grid.add(new Label("Cardinality:"),   0, 2); grid.add(cardCombo,    1, 2);
        grid.add(new Label("Required:"),      0, 3); grid.add(reqCheck,     1, 3);
        grid.add(new Label("Description:"),   0, 4); grid.add(descField,    1, 4);

        // ── DTD Preview ───────────────────────────────────────────────
        Label dtdPreview = new Label();
        dtdPreview.setStyle("-fx-font-size: 9px; -fx-text-fill: #555; -fx-font-family: monospace;");
        dtdPreview.setWrapText(true);
        Runnable updatePreview = () -> {
            String cm = contentField.getText().isBlank() ? "(#PCDATA)*" : contentField.getText();
            dtdPreview.setText("<!ELEMENT " + nameField.getText() + " " + cm + ">");
        };
        nameField.textProperty().addListener((o, a, b) -> updatePreview.run());
        contentField.textProperty().addListener((o, a, b) -> updatePreview.run());
        updatePreview.run();
        grid.add(new Label("DTD Preview:"), 0, 5); grid.add(dtdPreview, 1, 5);

        // ── Attributes section ────────────────────────────────────────
        Label attrHeader = new Label("Attributes");
        attrHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        Separator attrSep = new Separator();

        // Working copy of attributes so we can add/remove before committing
        List<AttributeDef> workingAttrs = new ArrayList<>(elem.getAttributes());
        VBox attrRows = new VBox(4);

        Runnable rebuildAttrRows = new Runnable() {
            @Override public void run() {
                attrRows.getChildren().clear();
                for (AttributeDef attr : new ArrayList<>(workingAttrs)) {
                    HBox row = new HBox(6);
                    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    // Name
                    TextField attrName = new TextField(nvl(attr.getName()));
                    attrName.setPrefWidth(100);
                    attrName.setPromptText("name");
                    attrName.textProperty().addListener((o, a, b) -> attr.setName(b.trim()));

                    // Type
                    ComboBox<String> attrType = new ComboBox<>();
                    attrType.getItems().addAll("CDATA", "NMTOKEN", "NMTOKENS", "ID", "IDREF", "IDREFS");
                    attrType.setValue(nvl(attr.getType(), "CDATA"));
                    attrType.setPrefWidth(100);
                    attrType.valueProperty().addListener((o, a, b) -> attr.setType(b));

                    // Default value
                    TextField attrDefault = new TextField(nvl(attr.getDefaultValue()));
                    attrDefault.setPrefWidth(90);
                    attrDefault.setPromptText("default");
                    attrDefault.textProperty().addListener((o, a, b) -> attr.setDefaultValue(b.trim()));

                    // Required checkbox
                    CheckBox attrReq = new CheckBox("Req");
                    attrReq.setSelected(attr.isRequired());
                    attrReq.selectedProperty().addListener((o, a, b) -> attr.setRequired(b));

                    // Remove button
                    Button del = new Button("✕");
                    del.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-text-fill: #c0392b; -fx-cursor: hand;");
                    del.setOnAction(e -> { workingAttrs.remove(attr); this.run(); });

                    row.getChildren().addAll(attrName, attrType, attrDefault, attrReq, del);
                    attrRows.getChildren().add(row);
                }
            }
        };
        rebuildAttrRows.run();

        Button addAttrBtn = new Button("+ Add Attribute");
        addAttrBtn.setStyle("-fx-font-size: 10px; -fx-cursor: hand;");
        addAttrBtn.setOnAction(e -> {
            AttributeDef newAttr = new AttributeDef("newAttr", "CDATA");
            workingAttrs.add(newAttr);
            rebuildAttrRows.run();
        });

        root.getChildren().addAll(grid, new Separator(), attrHeader, attrRows, addAttrBtn,
                new Separator(), dtdPreview);
        dlg.getDialogPane().setContent(new ScrollPane(root) {{
            setFitToWidth(true);
            setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            setPrefHeight(480);
        }});
        Platform.runLater(nameField::requestFocus);

        dlg.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String n = nameField.getText().trim();
                if (n.isBlank()) return;
                pushUndo();
                elem.setName(n);
                elem.setContentModel(contentField.getText().trim());
                elem.setCardinality(cardCombo.getValue());
                elem.setRequired(reqCheck.isSelected());
                elem.setDescription(descField.getText().trim());
                // Commit attribute changes
                elem.getAttributes().clear();
                workingAttrs.stream()
                        .filter(a -> a.getName() != null && !a.getName().isBlank())
                        .forEach(elem.getAttributes()::add);
                // Refresh whichever panel is showing
                if (parentTopic != null) {
                    TopicTypeNode node = topicTypeNodeMap.get(parentTopic.getId());
                    if (node != null) node.refresh();
                    markDirty();
                    showTopicTypeProperties(parentTopic);
                } else if (parentDomain != null) {
                    TopicTypeNode node = topicTypeNodeMap.get(parentDomain.getId());
                    if (node != null) node.refresh();
                    markDirty();
                    showDomainProperties(parentDomain);
                }
                log.log("Element '" + n + "' updated (" + elem.getAttributes().size() + " attrs).");
            }
        });
    }

    // ── Content Model Builder ─────────────────────────────────────────

    /**
     * Opens the Content Model Builder dialog. On OK, writes the generated
     * content model string back into {@code contentField}.
     */
    private void showContentModelBuilder(TextField contentField, TopicType parentTopic, ElementDef currentElem) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Content Model Builder");
        dlg.setHeaderText("Define what '" + currentElem.getName() + "' can contain");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setPrefWidth(560);

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));

        // ── Model type ────────────────────────────────────────────────
        Label typeLabel = new Label("Content type:");
        typeLabel.setStyle("-fx-font-weight: bold;");
        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton seqBtn    = new RadioButton("Sequence  (children appear in order)");
        RadioButton choiceBtn = new RadioButton("Choice  (one of the children)");
        RadioButton mixedBtn  = new RadioButton("Mixed  (#PCDATA + elements)");
        RadioButton pcdataBtn = new RadioButton("Text only  (#PCDATA)");
        RadioButton emptyBtn  = new RadioButton("Empty  (no content)");
        for (RadioButton rb : new RadioButton[]{seqBtn, choiceBtn, mixedBtn, pcdataBtn, emptyBtn})
            rb.setToggleGroup(typeGroup);
        seqBtn.setSelected(true);

        // ── Overall cardinality ───────────────────────────────────────
        Label cardLabel = new Label("Group cardinality:");
        cardLabel.setStyle("-fx-font-weight: bold;");
        ToggleGroup cardGroup = new ToggleGroup();
        RadioButton card1 = new RadioButton("1  (exactly one)");
        RadioButton cardQ = new RadioButton("?  (zero or one)");
        RadioButton cardP = new RadioButton("+  (one or more)");
        RadioButton cardS = new RadioButton("*  (zero or more)");
        for (RadioButton rb : new RadioButton[]{card1, cardQ, cardP, cardS}) rb.setToggleGroup(cardGroup);
        cardS.setSelected(true);

        // ── Child elements list ───────────────────────────────────────
        Label childLabel = new Label("Child elements:");
        childLabel.setStyle("-fx-font-weight: bold;");

        // Each entry: [name combo]  [card combo]  [▲] [▼] [✕]
        record ChildEntry(String name, String cardinality) {}
        List<ChildEntry[]> entries = new ArrayList<>();  // use array wrapper so lambda can mutate

        VBox childRows = new VBox(4);

        // Collect available element names from the parent topic type (for the dropdown)
        List<String> availableElems = new ArrayList<>();
        if (parentTopic != null) {
            parentTopic.getElements().stream()
                    .map(ElementDef::getName)
                    .filter(n -> n != null && !n.isBlank() && !n.equals(currentElem.getName()))
                    .forEach(availableElems::add);
        }

        // Preview label
        Label preview = new Label();
        preview.setStyle("-fx-font-size: 10px; -fx-font-family: monospace; -fx-text-fill: #005599;");
        preview.setWrapText(true);

        // Rebuild child rows and preview
        Runnable[] rebuildRef = new Runnable[1];
        Runnable buildPreview = () -> {
            if (pcdataBtn.isSelected()) { preview.setText("(#PCDATA)*"); return; }
            if (emptyBtn.isSelected())  { preview.setText("EMPTY"); return; }
            if (entries.isEmpty())      { preview.setText("(#PCDATA)*"); return; }
            String sep = choiceBtn.isSelected() ? " | " : ", ";
            String body = entries.stream()
                    .map(ea -> ea[0].name + ("1".equals(ea[0].cardinality) ? "" : ea[0].cardinality))
                    .collect(Collectors.joining(sep));
            String prefix = mixedBtn.isSelected() ? "#PCDATA | " : "";
            String card = card1.isSelected() ? "" : cardQ.isSelected() ? "?" :
                          cardP.isSelected() ? "+" : "*";
            preview.setText("(" + prefix + body + ")" + card);
        };

        rebuildRef[0] = () -> {
            childRows.getChildren().clear();
            for (int i = 0; i < entries.size(); i++) {
                final int idx = i;
                ChildEntry[] ea = entries.get(i);

                ComboBox<String> nameBox = new ComboBox<>();
                nameBox.setEditable(true);
                nameBox.getItems().addAll(availableElems);
                nameBox.setValue(ea[0].name);
                nameBox.setPrefWidth(180);
                nameBox.valueProperty().addListener((o, a, b) -> {
                    ea[0] = new ChildEntry(b == null ? "" : b, ea[0].cardinality);
                    buildPreview.run();
                });

                ComboBox<String> cardBox = new ComboBox<>();
                cardBox.getItems().addAll("1", "?", "+", "*");
                cardBox.setValue(ea[0].cardinality);
                cardBox.setPrefWidth(60);
                cardBox.valueProperty().addListener((o, a, b) -> {
                    ea[0] = new ChildEntry(ea[0].name, b == null ? "1" : b);
                    buildPreview.run();
                });

                Button up = new Button("▲");
                up.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4;");
                up.setDisable(idx == 0);
                up.setOnAction(e -> {
                    ChildEntry[] tmp = entries.get(idx - 1);
                    entries.set(idx - 1, entries.get(idx));
                    entries.set(idx, tmp);
                    rebuildRef[0].run();
                    buildPreview.run();
                });

                Button down = new Button("▼");
                down.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4;");
                down.setDisable(idx == entries.size() - 1);
                down.setOnAction(e -> {
                    ChildEntry[] tmp = entries.get(idx + 1);
                    entries.set(idx + 1, entries.get(idx));
                    entries.set(idx, tmp);
                    rebuildRef[0].run();
                    buildPreview.run();
                });

                Button del = new Button("✕");
                del.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4; -fx-text-fill: #c0392b; -fx-cursor: hand;");
                del.setOnAction(e -> { entries.remove(idx); rebuildRef[0].run(); buildPreview.run(); });

                HBox row = new HBox(6, nameBox, cardBox, up, down, del);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                childRows.getChildren().add(row);
            }
            buildPreview.run();
        };

        // ── Add element button ────────────────────────────────────────
        Button addElemBtn = new Button("+ Add Child Element");
        addElemBtn.setStyle("-fx-font-size: 10px; -fx-cursor: hand;");
        addElemBtn.setOnAction(e -> {
            String firstName = availableElems.isEmpty() ? "element" : availableElems.get(0);
            entries.add(new ChildEntry[]{new ChildEntry(firstName, "1")});
            rebuildRef[0].run();
        });

        // ── Parse existing content model to pre-populate ──────────────
        String existing = contentField.getText().trim();
        if (!existing.isBlank() && !existing.equals("(#PCDATA)*")) {
            if ("EMPTY".equalsIgnoreCase(existing)) {
                emptyBtn.setSelected(true);
            } else if (existing.matches("\\(#PCDATA\\).*")) {
                pcdataBtn.setSelected(true);
            } else {
                // Parse (a, b?, c+)* or (a | b | c)?
                String inner = existing.replaceAll("^\\(", "").replaceAll("[)?+*]$", "")
                        .replaceAll("\\)$", "");
                boolean isChoice = inner.contains("|");
                boolean isMixed  = inner.startsWith("#PCDATA");
                if (isChoice) choiceBtn.setSelected(true);
                if (isMixed)  { mixedBtn.setSelected(true); inner = inner.replaceFirst("#PCDATA\\s*\\|\\s*", ""); }

                // Overall cardinality
                String trail = existing.replaceAll(".*\\)", "");
                if ("?".equals(trail)) cardQ.setSelected(true);
                else if ("+".equals(trail)) cardP.setSelected(true);
                else if ("*".equals(trail)) cardS.setSelected(true);
                else card1.setSelected(true);

                String sep = isChoice ? "\\|" : ",";
                for (String part : inner.split(sep)) {
                    String p = part.trim();
                    if (p.isBlank() || p.equals("#PCDATA")) continue;
                    String card = p.endsWith("?") ? "?" : p.endsWith("+") ? "+" :
                                  p.endsWith("*") ? "*" : "1";
                    String name = p.replaceAll("[?+*]$", "").trim();
                    entries.add(new ChildEntry[]{new ChildEntry(name, card)});
                }
            }
        }

        // Wire type toggles to rebuild preview
        for (RadioButton rb : new RadioButton[]{seqBtn, choiceBtn, mixedBtn, pcdataBtn, emptyBtn})
            rb.selectedProperty().addListener((o, a, b) -> { rebuildRef[0].run(); });
        for (RadioButton rb : new RadioButton[]{card1, cardQ, cardP, cardS})
            rb.selectedProperty().addListener((o, a, b) -> buildPreview.run());

        rebuildRef[0].run();

        // ── Layout ────────────────────────────────────────────────────
        VBox typeBox = new VBox(4, typeLabel, seqBtn, choiceBtn, mixedBtn, pcdataBtn, emptyBtn);
        VBox cardBox = new VBox(4, cardLabel, card1, cardQ, cardP, cardS);
        HBox typeAndCard = new HBox(24, typeBox, cardBox);

        Label previewLabel = new Label("Generated content model:");
        previewLabel.setStyle("-fx-font-weight: bold;");

        root.getChildren().addAll(typeAndCard, new Separator(),
                childLabel, childRows, addElemBtn,
                new Separator(), previewLabel, preview);

        dlg.getDialogPane().setContent(new ScrollPane(root) {{
            setFitToWidth(true);
            setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            setPrefHeight(500);
        }});

        dlg.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                contentField.setText(preview.getText());
            }
        });
    }

    // ── F-149/F-151/F-152: Edit attribute in-place ────────────────────

    private void showEditAttributeDialog(AttributeDef attr, TopicType parentTopic) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Edit Attribute");
        dlg.setHeaderText("Edit: " + attr.getName());
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(6);
        grid.setPadding(new Insets(12));

        TextField nameField    = new TextField(nvl(attr.getName()));
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("CDATA", "NMTOKEN", "NMTOKENS", "ID", "IDREF", "IDREFS");
        typeCombo.setValue(nvl(attr.getType(), "CDATA"));
        TextField defaultField = new TextField(nvl(attr.getDefaultValue()));
        TextField fixedField   = new TextField(nvl(attr.getFixedValue()));
        CheckBox reqCheck = new CheckBox();
        reqCheck.setSelected(attr.isRequired());
        TextField enumField = new TextField(String.join(" ", attr.getEnumValues()));
        enumField.setPromptText("val1 val2 val3  (space-separated)");

        ComboBox<String> specializesCombo = new ComboBox<>();
        specializesCombo.getItems().addAll("(none — regular attribute)", "props", "base");
        specializesCombo.setValue(
            attr.getSpecializesFrom() != null ? attr.getSpecializesFrom() : "(none — regular attribute)");

        // F-151: live DTD preview
        Label dtdPreview = new Label();
        dtdPreview.setStyle("-fx-font-size: 9px; -fx-text-fill: #555; -fx-font-family: monospace;");
        dtdPreview.setWrapText(true);
        Runnable updatePreview = () -> {
            // Build a temp attr for preview
            AttributeDef tmp = new AttributeDef(nameField.getText().trim(),
                    typeCombo.getValue() != null ? typeCombo.getValue() : "CDATA");
            tmp.setRequired(reqCheck.isSelected());
            tmp.setDefaultValue(defaultField.getText().trim());
            tmp.setFixedValue(fixedField.getText().trim());
            String enums = enumField.getText().trim();
            if (!enums.isBlank()) {
                for (String v : enums.split("\\s+")) tmp.addEnumValue(v);
            }
            dtdPreview.setText(tmp.toDtdFragment().trim());
        };
        nameField.textProperty().addListener((o,a,b) -> updatePreview.run());
        typeCombo.valueProperty().addListener((o,a,b) -> updatePreview.run());
        defaultField.textProperty().addListener((o,a,b) -> updatePreview.run());
        fixedField.textProperty().addListener((o,a,b) -> updatePreview.run());
        reqCheck.selectedProperty().addListener((o,a,b) -> updatePreview.run());
        enumField.textProperty().addListener((o,a,b) -> updatePreview.run());
        updatePreview.run();

        grid.add(new Label("Name:"),            0, 0); grid.add(nameField,       1, 0);
        grid.add(new Label("Type:"),            0, 1); grid.add(typeCombo,       1, 1);
        grid.add(new Label("Default Value:"),   0, 2); grid.add(defaultField,    1, 2);
        grid.add(new Label("Fixed Value:"),     0, 3); grid.add(fixedField,      1, 3);
        grid.add(new Label("Required:"),        0, 4); grid.add(reqCheck,        1, 4);
        grid.add(new Label("Enum Values:"),     0, 5); grid.add(enumField,       1, 5);
        grid.add(new Label("Specializes From:"),0, 6); grid.add(specializesCombo,1, 6);
        grid.add(new Label("DTD Preview:"),     0, 7); grid.add(dtdPreview,      1, 7);

        dlg.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dlg.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String n = nameField.getText().trim();
                if (n.isBlank()) return;
                pushUndo();
                attr.setName(n);
                attr.setType(typeCombo.getValue());
                attr.setDefaultValue(defaultField.getText().trim());
                attr.setFixedValue(fixedField.getText().trim());
                attr.setRequired(reqCheck.isSelected());
                attr.getEnumValues().clear();
                String enums = enumField.getText().trim();
                if (!enums.isBlank()) {
                    for (String v : enums.split("\\s+")) attr.addEnumValue(v);
                }
                String spec = specializesCombo.getValue();
                attr.setSpecializesFrom("props".equals(spec) || "base".equals(spec) ? spec : null);
                markDirty();
                if (parentTopic != null) showTopicTypeProperties(parentTopic);
                log.log("Attribute '" + n + "' updated.");
            }
        });
    }

    /** Browse available DITA base-type elements and import selected ones. */
    private void showDitaElementBrowser(TopicType tt) {
        String baseType = tt.getBaseType();
        List<com.ditadesigner.model.ElementDef> baseElems = oasisLoader.getBaseElements(baseType);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("DITA Element Browser");
        dlg.setHeaderText("Available elements for base type: " + baseType);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setPrefWidth(480);

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));

        // Search field
        TextField searchField = new TextField();
        searchField.setPromptText("Search elements…");

        // Element list with checkboxes
        VBox listBox = new VBox(2);
        ScrollPane listScroll = new ScrollPane(listBox);
        listScroll.setFitToWidth(true);
        listScroll.setPrefHeight(320);

        Set<String> existingNames = tt.getElements().stream()
                .map(com.ditadesigner.model.ElementDef::getName)
                .collect(Collectors.toSet());
        List<CheckBox> allChecks = new ArrayList<>();

        Runnable rebuildList = () -> {
            String term = searchField.getText().toLowerCase();
            listBox.getChildren().clear();
            allChecks.clear();
            List<com.ditadesigner.model.ElementDef> filtered = baseElems.stream()
                    .filter(e -> e.getName() != null && e.getName().toLowerCase().contains(term))
                    .collect(Collectors.toList());
            for (com.ditadesigner.model.ElementDef be : filtered) {
                CheckBox cb = new CheckBox(be.getName());
                cb.setStyle("-fx-font-size: 11px;");
                if (existingNames.contains(be.getName())) {
                    cb.setDisable(true);
                    cb.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");
                    cb.setText(be.getName() + " (already added)");
                }
                cb.setUserData(be);
                allChecks.add(cb);
                listBox.getChildren().add(cb);
            }
            if (filtered.isEmpty()) {
                Label none = new Label(baseElems.isEmpty()
                        ? "No DITA elements loaded. Load DITA libraries first."
                        : "No elements match search.");
                none.setStyle("-fx-text-fill: #888; -fx-font-style: italic;");
                listBox.getChildren().add(none);
            }
        };

        searchField.textProperty().addListener((obs, o, n) -> rebuildList.run());
        rebuildList.run();

        HBox selectRow = new HBox(8);
        Button selAll = new Button("Select All");
        Button selNone = new Button("Clear");
        selAll.setStyle("-fx-font-size: 10px;");
        selNone.setStyle("-fx-font-size: 10px;");
        selAll.setOnAction(e -> allChecks.forEach(c -> { if (!c.isDisabled()) c.setSelected(true); }));
        selNone.setOnAction(e -> allChecks.forEach(c -> c.setSelected(false)));
        selectRow.getChildren().addAll(selAll, selNone);

        content.getChildren().addAll(searchField, listScroll, selectRow);
        dlg.getDialogPane().setContent(content);

        dlg.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                int added = 0;
                for (CheckBox cb : allChecks) {
                    if (cb.isSelected() && !cb.isDisabled()) {
                        com.ditadesigner.model.ElementDef be = (com.ditadesigner.model.ElementDef) cb.getUserData();
                        ElementDef newElem = new ElementDef(be.getName());
                        newElem.setContentModel(be.getContentModel() != null ? be.getContentModel() : "(#PCDATA)*");
                        newElem.setCardinality(be.getCardinality() != null ? be.getCardinality() : "?");
                        newElem.setDescription(be.getDescription());
                        tt.addElement(newElem);
                        added++;
                    }
                }
                if (added > 0) {
                    TopicTypeNode n = topicTypeNodeMap.get(tt.getId());
                    if (n != null) n.refresh();
                    markDirty();
                    setStatus("Imported " + added + " element(s) from DITA " + baseType + " schema.");
                    log.logSuccess("Imported " + added + " DITA elements into " + tt.getName());
                    showTopicTypeProperties(tt);
                }
            }
        });
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
            String specTag = attr.isAttributeDomain() ? " [specializes @" + attr.getSpecializesFrom() + "]" : "";
            Label lbl = new Label("• " + attr.getName() + " : " + attr.getType() + specTag);
            lbl.setStyle(attr.isAttributeDomain()
                    ? "-fx-font-size: 10px; -fx-text-fill: #1565C0;"
                    : "-fx-font-size: 10px;");
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
            AttributeDef a = showAddAttributeDialog();
            if (a != null) {
                elem.addAttribute(a);
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

    /**
     * Shows a full attribute creation dialog and returns the new AttributeDef,
     * or null if the user cancelled. Supports name, type, default value,
     * required flag, enum values, and DITA specializesFrom (props/base).
     */
    private AttributeDef showAddAttributeDialog() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Add Attribute");
        dlg.setHeaderText("Define new attribute");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(12));

        TextField nameField = new TextField("myAttr");
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("CDATA", "NMTOKEN", "NMTOKENS", "ID", "IDREF", "IDREFS");
        typeCombo.setValue("CDATA");
        TextField defaultField = new TextField();
        CheckBox reqCheck = new CheckBox();
        TextField enumField = new TextField();
        enumField.setPromptText("val1 val2 val3  (space-separated, optional)");

        // DITA attribute specialization
        ComboBox<String> specializesCombo = new ComboBox<>();
        specializesCombo.getItems().addAll("(none — regular attribute)", "props", "base");
        specializesCombo.setValue("(none — regular attribute)");

        Label specNote = new Label();
        specNote.setStyle("-fx-font-size: 9px; -fx-text-fill: #666;");
        specNote.setWrapText(true);
        specializesCombo.setOnAction(e -> {
            String v = specializesCombo.getValue();
            if ("props".equals(v))
                specNote.setText("Specializes @props → use for conditional/filtering attributes.");
            else if ("base".equals(v))
                specNote.setText("Specializes @base → use for non-filtering extensible attributes.");
            else
                specNote.setText("");
        });

        grid.add(new Label("Name:"),              0, 0); grid.add(nameField,       1, 0);
        grid.add(new Label("Type:"),              0, 1); grid.add(typeCombo,       1, 1);
        grid.add(new Label("Default Value:"),     0, 2); grid.add(defaultField,    1, 2);
        grid.add(new Label("Required:"),          0, 3); grid.add(reqCheck,        1, 3);
        grid.add(new Label("Enum Values:"),       0, 4); grid.add(enumField,       1, 4);
        grid.add(new Label("Specializes From:"),  0, 5); grid.add(specializesCombo,1, 5);
        grid.add(specNote, 1, 6);

        dlg.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        return dlg.showAndWait()
                .filter(r -> r == ButtonType.OK)
                .map(r -> {
                    String n = nameField.getText().trim();
                    if (n.isBlank()) return null;
                    AttributeDef a = projectService.createAttribute(n, typeCombo.getValue(), reqCheck.isSelected());
                    String dv = defaultField.getText().trim();
                    if (!dv.isBlank()) a.setDefaultValue(dv);
                    String enums = enumField.getText().trim();
                    if (!enums.isBlank()) {
                        for (String val : enums.split("\\s+")) a.addEnumValue(val);
                    }
                    String spec = specializesCombo.getValue();
                    if ("props".equals(spec) || "base".equals(spec)) a.setSpecializesFrom(spec);
                    return a;
                })
                .orElse(null);
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
        doNewProjectSetup();
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
            refreshExplorer();
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
        if (lastLibDir != null) dc.setInitialDirectory(lastLibDir);
        File dir = dc.showDialog(getStage());
        if (dir == null) return;

        lastLibDir = dir;
        Preferences.userNodeForPackage(MainController.class).put("last.lib.dir", dir.getPath());
        int count = oasisLoader.loadFromDirectory(dir);
        setStatus("Loaded " + count + " DITA library files.");
        log.logSuccess("Loaded " + count + " DITA library files from " + dir.getPath());
    }

    // ── Sample XML generation ─────────────────────────────────────────

    private void onGenerateSampleXml(TopicType tt) {
        String xml = buildSampleXml(tt);

        // Show in a dialog with Save and Copy buttons
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Sample XML — " + tt.getName());
        dlg.setHeaderText("Sample instance document for " + tt.getName() + " (base: " + tt.getBaseType() + ")");

        ButtonType saveBtn  = new ButtonType("💾 Save As…", ButtonBar.ButtonData.LEFT);
        ButtonType copyBtn  = new ButtonType("📋 Copy",     ButtonBar.ButtonData.LEFT);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, copyBtn, ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(700);

        TextArea ta = new TextArea(xml);
        ta.setEditable(true);   // allow user to edit before saving
        ta.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 11px;");
        ta.setPrefHeight(500);
        dlg.getDialogPane().setContent(ta);

        dlg.showAndWait().ifPresent(result -> {
            if (result == saveBtn) {
                FileChooser fc = new FileChooser();
                fc.setTitle("Save Sample XML");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));
                fc.setInitialFileName(tt.resolvedModule() + "-sample.xml");
                // Pre-navigate to output dir if available
                File outDir = resolveOutputDir();
                if (outDir != null && outDir.exists()) fc.setInitialDirectory(outDir);
                File dest = fc.showSaveDialog(getStage());
                if (dest != null) {
                    try {
                        java.nio.file.Files.writeString(dest.toPath(), ta.getText());
                        log.logSuccess("Sample XML saved: " + dest.getPath());
                        setStatus("Sample XML saved: " + dest.getName());
                        refreshExplorer();
                    } catch (Exception ex) {
                        showError("Save Failed", ex.getMessage());
                    }
                }
            } else if (result == copyBtn) {
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                        new javafx.scene.input.ClipboardContent() {{ putString(ta.getText()); }});
                setStatus("Sample XML copied to clipboard.");
            }
        });
    }

    /** Build a minimal but complete XML instance for the given TopicType. */
    private String buildSampleXml(TopicType tt) {
        String stem   = tt.resolvedModule();
        String ns     = tt.getNamespace() != null ? tt.getNamespace() : "";
        String nsDecl = ns.isBlank() ? "" : " xmlns=\"" + ns + "\"";
        String base   = tt.getBaseType();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append("\n");
        sb.append("<!--\n  Sample XML instance for ").append(tt.getName())
          .append(" (specializes: ").append(base).append(")\n  Generated by DITA Specialization Designer\n-->\n");
        sb.append("<!DOCTYPE ").append(stem);
        // Add DOCTYPE reference if public/system ID are set
        String pubId = tt.getPublicId();
        String sysId = tt.getSystemId();
        if (pubId != null && !pubId.isBlank()) {
            sb.append("\n  PUBLIC \"").append(pubId).append("\"");
            sb.append("\n         \"").append(sysId != null ? sysId : stem + ".dtd").append("\"");
        } else {
            sb.append(" SYSTEM \"").append(stem).append(".dtd\"");
        }
        sb.append(">\n\n");

        // Root element
        sb.append("<").append(stem)
          .append(" id=\"sample-").append(stem).append("\"")
          .append(nsDecl);

        // Add required attributes
        for (AttributeDef attr : tt.getAttributes()) {
            if (attr.isRequired() && !"id".equalsIgnoreCase(attr.getName())
                    && !"class".equalsIgnoreCase(attr.getName())) {
                String val = attr.getDefaultValue() != null && !attr.getDefaultValue().isBlank()
                        ? attr.getDefaultValue()
                        : (!attr.getEnumValues().isEmpty() ? attr.getEnumValues().get(0) : "value");
                sb.append("\n  ").append(attr.getName()).append("=\"").append(val).append("\"");
            }
        }
        sb.append(">\n\n");

        // Title (standard DITA element)
        sb.append("  <title>Sample ").append(tt.getName()).append(" Title</title>\n");

        // Short desc
        if (base.equals("concept") || base.equals("topic") || base.equals("reference")) {
            sb.append("  <shortdesc>Short description of this ").append(tt.getName()).append(".</shortdesc>\n");
        }

        // Body element based on base type
        String bodyElem = switch (base) {
            case "task"      -> "taskbody";
            case "concept"   -> "conbody";
            case "reference" -> "refbody";
            default          -> "body";
        };
        sb.append("  <").append(bodyElem).append(">\n");

        // Child elements
        for (ElementDef elem : tt.getElements()) {
            String card = elem.getCardinality();
            boolean optional = "?".equals(card) || "*".equals(card);
            sb.append("    <!-- ").append(optional ? "optional" : "required").append(" -->\n");
            sb.append("    <").append(elem.getName());
            // Required attributes on the element
            for (AttributeDef a : elem.getAttributes()) {
                if (a.isRequired()) {
                    String val = a.getDefaultValue() != null && !a.getDefaultValue().isBlank()
                            ? a.getDefaultValue()
                            : (!a.getEnumValues().isEmpty() ? a.getEnumValues().get(0) : "value");
                    sb.append(" ").append(a.getName()).append("=\"").append(val).append("\"");
                }
            }
            // Self-close if content model is EMPTY, otherwise add placeholder text
            String cm = elem.getContentModel();
            if ("EMPTY".equalsIgnoreCase(cm)) {
                sb.append("/>\n");
            } else {
                sb.append(">Sample ").append(elem.getName()).append(" content</").append(elem.getName()).append(">\n");
            }
        }

        sb.append("  </").append(bodyElem).append(">\n");
        sb.append("\n</").append(stem).append(">\n");
        return sb.toString();
    }

    // ── XML Validation against XSD ────────────────────────────────────

    private void onValidateXmlAgainstXsd(TopicType tt) {
        // Resolve the XSD file path
        File outDir = resolveOutputDir();
        File xsdFile = (outDir != null)
                ? new File(outDir, "xsd/" + tt.resolvedModule() + ".xsd")
                : null;

        if (xsdFile == null || !xsdFile.exists()) {
            // Ask user to locate XSD if not found
            Alert info = new Alert(Alert.AlertType.INFORMATION,
                    "XSD file not found at expected location:\n" +
                    (xsdFile != null ? xsdFile.getPath() : "(no output dir set)") +
                    "\n\nGenerate All first, or the file chooser will open for you to locate the XSD.",
                    ButtonType.OK);
            info.setTitle("XSD Not Found");
            info.showAndWait();
        }

        // Choose XML file to validate
        FileChooser xmlChooser = new FileChooser();
        xmlChooser.setTitle("Select XML file to validate");
        xmlChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        if (outDir != null && outDir.exists()) xmlChooser.setInitialDirectory(outDir);
        File xmlFile = xmlChooser.showOpenDialog(getStage());
        if (xmlFile == null) return;

        // If XSD still missing, let user pick it
        if (xsdFile == null || !xsdFile.exists()) {
            FileChooser xsdChooser = new FileChooser();
            xsdChooser.setTitle("Select XSD Schema to validate against");
            xsdChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("XSD Schema (*.xsd)", "*.xsd"));
            xsdFile = xsdChooser.showOpenDialog(getStage());
            if (xsdFile == null) return;
        }

        // Run validation
        List<String> errors = validateXmlWithXsd(xmlFile, xsdFile);
        showXmlValidationResult(xmlFile, xsdFile, errors);
    }

    private List<String> validateXmlWithXsd(File xmlFile, File xsdFile) {
        List<String> errors = new ArrayList<>();
        try {
            javax.xml.validation.SchemaFactory sf =
                    javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
            javax.xml.validation.Schema schema = sf.newSchema(xsdFile);
            javax.xml.validation.Validator validator = schema.newValidator();

            // Collect all errors/warnings instead of throwing immediately
            validator.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override public void warning(org.xml.sax.SAXParseException e) {
                    errors.add("⚠ Line " + e.getLineNumber() + ": " + e.getMessage());
                }
                @Override public void error(org.xml.sax.SAXParseException e) {
                    errors.add("✕ Line " + e.getLineNumber() + ": " + e.getMessage());
                }
                @Override public void fatalError(org.xml.sax.SAXParseException e) {
                    errors.add("✕✕ Line " + e.getLineNumber() + ": " + e.getMessage());
                }
            });
            validator.validate(new javax.xml.transform.stream.StreamSource(xmlFile));
        } catch (Exception ex) {
            errors.add("Validation error: " + ex.getMessage());
        }
        return errors;
    }

    private void showXmlValidationResult(File xmlFile, File xsdFile, List<String> errors) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(620);

        if (errors.isEmpty()) {
            dlg.setTitle("Validation Passed");
            dlg.setHeaderText("✔ XML is valid against the XSD schema");
            Label msg = new Label(
                    "File: " + xmlFile.getName() + "\nSchema: " + xsdFile.getName() +
                    "\n\nNo errors or warnings found.");
            msg.setStyle("-fx-text-fill: #1a6b1a; -fx-font-size: 12px;");
            dlg.getDialogPane().setContent(msg);
            log.logSuccess("XML valid: " + xmlFile.getName() + " against " + xsdFile.getName());
            setStatus("✔ XML valid: " + xmlFile.getName());
        } else {
            dlg.setTitle("Validation Failed — " + errors.size() + " issue(s)");
            dlg.setHeaderText("✕ " + errors.size() + " issue(s) found in " + xmlFile.getName());
            VBox content = new VBox(8);
            content.setPadding(new Insets(8));
            Label schema = new Label("Schema: " + xsdFile.getName());
            schema.setStyle("-fx-font-size: 10px; -fx-text-fill: #555;");
            TextArea ta = new TextArea(String.join("\n", errors));
            ta.setEditable(false);
            ta.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 11px;");
            ta.setPrefHeight(300);
            content.getChildren().addAll(schema, ta);
            dlg.getDialogPane().setContent(content);
            log.logError("XML validation: " + errors.size() + " issue(s) in " + xmlFile.getName(), null);
            setStatus("✕ XML validation: " + errors.size() + " issue(s)");
        }
        dlg.showAndWait();
    }

    // ── Import XSD / DTD ─────────────────────────────────────────────

    @FXML
    private void onImportXsd() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import XSD Schema");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Schema (*.xsd)", "*.xsd"));
        List<File> files = fc.showOpenMultipleDialog(getStage());
        if (files == null || files.isEmpty()) return;

        com.ditadesigner.importer.XsdImporter importer = new com.ditadesigner.importer.XsdImporter();
        int imported = 0;
        for (File file : files) {
            try {
                TopicType tt = importer.importXsd(file);
                // Check for duplicate name
                boolean exists = currentModel.getTopicTypes().stream()
                        .anyMatch(t -> t.getName().equalsIgnoreCase(tt.getName()));
                if (exists) {
                    log.log("Skipped (already exists): " + tt.getName());
                    continue;
                }
                currentModel.addTopicType(tt);
                placeTopicTypeNode(tt);
                imported++;
                log.logSuccess("Imported: " + tt.getName()
                        + " (base: " + tt.getBaseType() + ", "
                        + tt.getElements().size() + " element(s))");
            } catch (Exception ex) {
                showError("Import Failed", file.getName() + ": " + ex.getMessage());
                log.logError("XSD import failed: " + file.getName(), ex);
            }
        }
        if (imported > 0) {
            markDirty();
            setStatus("Imported " + imported + " topic type(s) from XSD.");
            showNoSelection();
        }
    }

    @FXML
    private void onImportDtd() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import DTD");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("DTD files (*.dtd, *.mod)", "*.dtd", "*.mod"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = fc.showOpenDialog(getStage());
        if (file == null) return;

        // Basic DTD import: parse <!ELEMENT and <!ATTLIST declarations
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            String stem = file.getName().replaceAll("\\.(dtd|mod)$", "");
            String typeName = Character.toUpperCase(stem.charAt(0)) + stem.substring(1);

            TopicType tt = new TopicType(typeName, "topic");
            tt.setModule(stem);

            // Extract <!ELEMENT declarations
            java.util.regex.Pattern elemPat = java.util.regex.Pattern.compile(
                    "<!ELEMENT\\s+(\\S+)\\s+([^>]+)>", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = elemPat.matcher(content);
            boolean first = true;
            while (m.find()) {
                String name = m.group(1).trim();
                String model = m.group(2).trim().replaceAll("\\s+", " ");
                if (first) { first = false; continue; } // skip the root element itself
                ElementDef elem = new ElementDef(name);
                elem.setContentModel(model);
                elem.setCardinality("?");
                tt.addElement(elem);
            }

            // Extract <!ATTLIST for the root element
            java.util.regex.Pattern attPat = java.util.regex.Pattern.compile(
                    "<!ATTLIST\\s+" + java.util.regex.Pattern.quote(stem)
                    + "\\s+([\\s\\S]*?)>", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher am = attPat.matcher(content);
            if (am.find()) {
                String attBlock = am.group(1);
                java.util.regex.Pattern oneAtt = java.util.regex.Pattern.compile(
                        "(\\S+)\\s+(CDATA|NMTOKEN|NMTOKENS|ID|IDREF|IDREFS|\\([^)]+\\))\\s+(#REQUIRED|#IMPLIED|#FIXED|\"[^\"]*\")");
                java.util.regex.Matcher oa = oneAtt.matcher(attBlock);
                while (oa.find()) {
                    String aName = oa.group(1);
                    String aType = oa.group(2).startsWith("(") ? "NMTOKEN" : oa.group(2);
                    boolean req = "#REQUIRED".equals(oa.group(3));
                    AttributeDef attr = new AttributeDef(aName, aType, null, req);
                    tt.getAttributes().add(attr);
                }
            }

            boolean exists = currentModel.getTopicTypes().stream()
                    .anyMatch(t -> t.getName().equalsIgnoreCase(tt.getName()));
            if (exists) {
                showError("Import Skipped", "A topic type named '" + tt.getName() + "' already exists.");
                return;
            }
            currentModel.addTopicType(tt);
            placeTopicTypeNode(tt);
            markDirty();
            setStatus("Imported: " + typeName + " (" + tt.getElements().size() + " elements)");
            log.logSuccess("DTD imported: " + typeName);
        } catch (Exception ex) {
            showError("DTD Import Failed", ex.getMessage());
            log.logError("DTD import failed", ex);
        }
    }

    // ── XPath Checker (XPathModule integration) ─────────────────────────────────

    @FXML
    private void onOpenXPathChecker() {
        com.ditadesigner.xml.xpath.XPathModule.openChecker(getStage());
        log.log("XPath Checker opened.");
    }

    // ── Schema Design Workbench ───────────────────────────────────────────────────

    @FXML
    private void onOpenSchemaDesign() {
        if (currentModel == null) {
            showError("No Model", "Open or create a DITA model first.");
            return;
        }
        com.ditadesigner.schema.SchemaDesignModule.openWorkbench(getStage(), currentModel);
        log.log("Schema Design Workbench opened.");
    }

    // ── XSLT Workbench (XsltModule integration) ─────────────────────────────────

    @FXML
    private void onOpenXsltWorkbench() {
        com.ditadesigner.xslt.XsltModule.openWorkbench(getStage());
        log.log("XSLT Workbench opened.");
    }

    @FXML
    private void onDitaToHtml() {
        // If a generated XSD / output dir exists, offer to pick the XML directly
        File xmlFile = null;
        if (currentModel != null) {
            File outDir = resolveOutputDir();
            if (outDir != null && outDir.exists()) {
                // Look for a DITA XML file in the output dir
                File[] ditaFiles = outDir.listFiles(
                        f -> f.getName().endsWith(".xml") || f.getName().endsWith(".dita"));
                if (ditaFiles != null && ditaFiles.length == 1) {
                    xmlFile = ditaFiles[0];
                    log.log("[XSLT] Pre-selected DITA file: " + xmlFile.getName());
                }
            }
        }
        com.ditadesigner.xslt.XsltModule.openWorkbench(getStage(), xmlFile, null);
        log.log("XSLT Workbench opened for DITA → HTML.");
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
            refreshExplorer();
        } catch (Exception ex) {
            showError("DTD Generation Failed", ex.getMessage());
            log.logError("DTD generation failed", ex);
        }
    }

    @FXML
    private void onGenerateXsd() {
        com.ditadesigner.generator.XsdGenerator.ExportMode mode = showXsdExportModeDialog();
        if (mode == null) return;

        File outputDir = chooseOutputDir();
        if (outputDir == null) return;

        try {
            xsdGenerator.generate(currentModel, outputDir, mode);
            int count = currentModel.getTopicTypes().size();
            log.logSuccess("XSD generated (" + mode + ") for " + count
                    + " topic type(s) → " + outputDir.getPath());
            logGenerationReport(outputDir, "XSD");
            setStatus("XSD generated [" + mode + "] (" + count + " type(s)).");
            refreshExplorer();
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
            refreshExplorer();
        } catch (Exception ex) {
            showError("Catalog Generation Failed", ex.getMessage());
            log.logError("Catalog generation failed", ex);
        }
    }

    @FXML
    private void onGenerateAll() {
        com.ditadesigner.generator.XsdGenerator.ExportMode mode = showXsdExportModeDialog();
        if (mode == null) return;

        File outputDir = chooseOutputDir();
        if (outputDir == null) return;

        List<String> issues = transformer.validate(currentModel);
        if (!issues.isEmpty()) showWarnings(issues);

        try {
            doGenerate(outputDir, mode);
            setStatus("All artefacts generated [XSD: " + mode + "] → " + outputDir.getPath());
            refreshExplorer();
        } catch (Exception ex) {
            showError("Generation Failed", ex.getMessage());
            log.logError("Generate all failed", ex);
        }
    }

    /**
     * Called by Live Sync and other automatic paths — always uses STANDALONE so
     * no dialog interrupts the background generation.
     */
    private void doGenerate(File outputDir) throws Exception {
        doGenerate(outputDir, com.ditadesigner.generator.XsdGenerator.ExportMode.STANDALONE);
    }

    private void doGenerate(File outputDir,
                             com.ditadesigner.generator.XsdGenerator.ExportMode xsdMode) throws Exception {
        FileUtil.ensureDir(outputDir);
        log.log("─── Generate All ──────────────────────────────");
        log.log("Output: " + outputDir.getPath());
        log.log("Model:  " + currentModel.getName()
                + " · " + currentModel.getTopicTypes().size() + " type(s)"
                + " · " + currentModel.getDomains().size() + " domain(s)");
        log.log("XSD mode: " + xsdMode);

        dtdGenerator.generate(currentModel, outputDir);
        logGenerationReport(outputDir, "DTD");

        xsdGenerator.generate(currentModel, outputDir, xsdMode);
        logGenerationReport(outputDir, "XSD");

        catalogGenerator.generate(currentModel, outputDir);
        log.log("Catalog: output/catalog.xml");

        log.logSuccess("Generation complete → " + outputDir.getPath());
        log.log("───────────────────────────────────────────────");
        lastGenerationMs = System.currentTimeMillis();  // F-175
    }

    /**
     * Shows a modal dialog asking the user to choose between STANDALONE and
     * OASIS_CATALOG XSD export modes.
     *
     * @return the chosen {@link com.ditadesigner.generator.XsdGenerator.ExportMode},
     *         or {@code null} if the user cancelled
     */
    private com.ditadesigner.generator.XsdGenerator.ExportMode showXsdExportModeDialog() {
        javafx.scene.control.Dialog<com.ditadesigner.generator.XsdGenerator.ExportMode> dlg =
                new javafx.scene.control.Dialog<>();
        dlg.setTitle("XSD Export Mode");
        dlg.setHeaderText("How should the generated XSD reference DITA base types?");

        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();

        javafx.scene.control.RadioButton standalone = new javafx.scene.control.RadioButton(
                "Standalone  (recommended for most users)\n"
                + "    Embeds minimal DITA base-type stubs inline.\n"
                + "    Validates without DITA-OT or any XML catalog.");
        standalone.setToggleGroup(group);
        standalone.setSelected(true);

        javafx.scene.control.RadioButton oasis = new javafx.scene.control.RadioButton(
                "OASIS DITA-OT compatible\n"
                + "    Uses standard xs:import with OASIS DITA 1.3 URNs.\n"
                + "    Requires OASIS DITA 1.3 schemas registered in your system XML catalog.");
        oasis.setToggleGroup(group);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(14, standalone, oasis);
        content.setPadding(new javafx.geometry.Insets(16));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt == javafx.scene.control.ButtonType.OK) {
                return oasis.isSelected()
                        ? com.ditadesigner.generator.XsdGenerator.ExportMode.OASIS_CATALOG
                        : com.ditadesigner.generator.XsdGenerator.ExportMode.STANDALONE;
            }
            return null;
        });

        return dlg.showAndWait().orElse(null);
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
        dialog.getDialogPane().setPrefWidth(520);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField nameField      = new TextField(nvl(currentModel.getName()));
        TextField versionField   = new TextField(nvl(currentModel.getVersion(), "1.0"));
        TextField nsField        = new TextField(nvl(currentModel.getTargetNamespace()));
        TextField copyrightField = new TextField(nvl(currentModel.getCopyrightOwner()));
        TextArea  descField      = new TextArea(nvl(currentModel.getDescription()));
        descField.setPrefRowCount(3);
        descField.setWrapText(true);

        // Output Dir with Browse button
        String existingOut = nvl(currentModel.getOutputDir(), "");
        TextField outField = new TextField(existingOut);
        outField.setPromptText("Select output folder…");
        outField.setPrefWidth(260);
        HBox.setHgrow(outField, javafx.scene.layout.Priority.ALWAYS);
        Button browseOutBtn = new Button("Browse…");
        browseOutBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Output Folder");
            // Pre-navigate to existing dir if valid
            if (!outField.getText().trim().isEmpty()) {
                File existing = new File(outField.getText().trim());
                if (existing.isDirectory()) dc.setInitialDirectory(existing);
                else if (existing.getParentFile() != null && existing.getParentFile().isDirectory())
                    dc.setInitialDirectory(existing.getParentFile());
            }
            File chosen = dc.showDialog(dialog.getOwner());
            if (chosen != null) outField.setText(chosen.getAbsolutePath());
        });
        HBox outRow = new HBox(6, outField, browseOutBtn);

        nameField.setPrefWidth(300);
        nsField.setPrefWidth(300);

        // Auto-suggest namespace from project name if namespace is empty
        nameField.textProperty().addListener((obs, old, val) -> {
            if (nsField.getText().trim().isEmpty()) {
                String slug = val.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                if (!slug.isEmpty()) nsField.setText("urn:myorg:dita:" + slug + ":");
            }
        });

        Label nameLabel = new Label("Project Name:");
        Label versionLabel = new Label("Version:");
        Label nsLabel = new Label("Target Namespace:");
        Label outLabel = new Label("Output Folder:");
        Label copyrightLabel = new Label("Copyright Owner:");
        Label descLabel = new Label("Description:");
        for (Label l : new Label[]{nameLabel, versionLabel, nsLabel, outLabel, copyrightLabel, descLabel}) {
            l.setMinWidth(130);
        }

        grid.add(nameLabel,      0, 0); grid.add(nameField,      1, 0);
        grid.add(versionLabel,   0, 1); grid.add(versionField,   1, 1);
        grid.add(nsLabel,        0, 2); grid.add(nsField,        1, 2);
        grid.add(outLabel,       0, 3); grid.add(outRow,         1, 3);
        grid.add(copyrightLabel, 0, 4); grid.add(copyrightField, 1, 4);
        grid.add(descLabel,      0, 5); grid.add(descField,      1, 5);

        javafx.scene.layout.ColumnConstraints col0 = new javafx.scene.layout.ColumnConstraints();
        col0.setMinWidth(130);
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                currentModel.setName(nameField.getText().trim());
                currentModel.setVersion(versionField.getText().trim());
                currentModel.setTargetNamespace(nsField.getText().trim());
                currentModel.setOutputDir(outField.getText().trim());
                currentModel.setCopyrightOwner(copyrightField.getText().trim());
                currentModel.setDescription(descField.getText().trim());
                markDirty();
                updateTitle();
                refreshExplorer();
                setStatus("Project metadata updated.");
                log.log("Project metadata saved: " + currentModel.getName()
                        + " v" + currentModel.getVersion()
                        + " → " + outField.getText().trim());
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
        saveRecentFilesToPrefs();   // F-155 fix: persist across sessions
        updateRecentProjectsMenu();
    }

    // F-155 fix: persist recent files list to Preferences
    private void saveRecentFilesToPrefs() {
        Preferences prefs = Preferences.userNodeForPackage(MainController.class);
        prefs.putInt("recent.count", recentFiles.size());
        for (int i = 0; i < recentFiles.size(); i++) {
            prefs.put("recent." + i, recentFiles.get(i).getAbsolutePath());
        }
    }

    private void loadRecentFilesFromPrefs() {
        Preferences prefs = Preferences.userNodeForPackage(MainController.class);
        int count = prefs.getInt("recent.count", 0);
        for (int i = 0; i < count; i++) {
            String path = prefs.get("recent." + i, null);
            if (path != null) {
                File f = new File(path);
                if (f.exists()) recentFiles.add(f);
            }
        }
    }

    private void updateRecentProjectsMenu() {
        if (recentProjectsMenu == null) return;
        recentProjectsMenu.getItems().clear();
        recentProjectsMenu.setDisable(recentFiles.isEmpty());
        for (File file : recentFiles) {
            // F-164 fix: show full path in the menu item text (truncated) and as tooltip via Label graphic
            MenuItem item = new MenuItem(file.getName());
            Label graphic = new Label();
            graphic.setStyle("-fx-font-size: 0;"); // invisible but carries tooltip
            Tooltip.install(graphic, new Tooltip(file.getAbsolutePath()));
            item.setGraphic(graphic);
            item.setOnAction(e -> openRecentFile(file));
            recentProjectsMenu.getItems().add(item);
        }
        if (!recentFiles.isEmpty()) {
            recentProjectsMenu.getItems().add(new SeparatorMenuItem());
            MenuItem clearItem = new MenuItem("Clear Recent");
            clearItem.setOnAction(e -> {
                recentFiles.clear();
                saveRecentFilesToPrefs();
                updateRecentProjectsMenu();
            });
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
        // Auto-layout nodes that haven't been positioned yet (all at 0,0)
        boolean needsAutoLayout = currentModel.getTopicTypes().stream()
                .allMatch(tt -> tt.getX() == 0 && tt.getY() == 0);

        if (needsAutoLayout) {
            int col = 0, row = 0;
            int totalNodes = currentModel.getTopicTypes().size() + currentModel.getDomains().size();
            int cols = Math.max(1, (int) Math.ceil(Math.sqrt(totalNodes)));
            for (TopicType tt : currentModel.getTopicTypes()) {
                tt.setX(40 + col * 260);
                tt.setY(60 + row * 280);
                col++;
                if (col >= cols) { col = 0; row++; }
            }
            for (DomainDef domain : currentModel.getDomains()) {
                domain.setX(40 + col * 260);
                domain.setY(60 + row * 280);
                col++;
                if (col >= cols) { col = 0; row++; }
            }
        }

        for (TopicType tt : currentModel.getTopicTypes()) {
            placeTopicTypeNode(tt);
        }
        for (DomainDef domain : currentModel.getDomains()) {
            placeDomainNode(domain);
        }
        // Place standalone element nodes (F-157 fix)
        for (ElementDef elem : currentModel.getStandaloneElements()) {
            placeElementNode(elem);
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
