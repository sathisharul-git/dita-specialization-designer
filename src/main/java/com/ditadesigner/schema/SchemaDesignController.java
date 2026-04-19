package com.ditadesigner.schema;

import com.ditadesigner.model.DitaModel;
import com.ditadesigner.schema.canvas.SchemaCanvas;
import com.ditadesigner.schema.outline.SchemaOutlineItem;
import com.ditadesigner.schema.outline.SchemaOutlinePanel;
import com.ditadesigner.schema.palette.SchemaPalette;
import com.ditadesigner.schema.panel.ConstraintsPanel;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Controller for the Schema Design Workbench window (F-254–F-278, sections AA1–AA5).
 * Mirrors the oXygen XML Schema Design Mode: Palette | Canvas | Constraints, with an Outline below left.
 */
public class SchemaDesignController {

    private final Stage stage;
    private final SchemaCanvas canvas = new SchemaCanvas();
    private final ConstraintsPanel constraintsPanel = new ConstraintsPanel();
    private final SchemaOutlinePanel outlinePanel = new SchemaOutlinePanel();
    private SchemaEditService editService;
    private DitaModel model;

    SchemaDesignController(Stage ownerStage) {
        stage = new Stage();
        stage.initOwner(ownerStage);
        stage.initModality(Modality.NONE);
        stage.setTitle("Schema Design Workbench");
        stage.setWidth(1200);
        stage.setHeight(760);
        stage.setMinWidth(800);
        stage.setMinHeight(500);

        stage.setScene(new Scene(buildRoot()));
    }

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #e8eaf0;");

        root.setTop(buildToolbar());
        root.setLeft(buildLeftPanel());
        root.setCenter(canvas);
        root.setRight(constraintsPanel);

        return root;
    }

    private ToolBar buildToolbar() {
        Button fitBtn = new Button("Fit All");
        fitBtn.setOnAction(e -> canvas.fitAll());

        Button addBtn = new Button("+ TopicType");
        addBtn.setOnAction(e -> {
            if (editService != null) editService.addTopicType(60 + model.getTopicTypes().size() * 20,
                    60 + model.getTopicTypes().size() * 20);
        });

        Label hint = new Label("Ctrl+Scroll: zoom   Alt+Drag: pan   Double-click: inline edit");
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        ToolBar tb = new ToolBar(fitBtn, new Separator(), addBtn, new Separator(), hint);
        tb.setStyle("-fx-background-color: #dde2ee;");
        return tb;
    }

    private SplitPane buildLeftPanel() {
        SchemaPalette palette = new SchemaPalette();
        palette.setPrefWidth(120);

        VBox outlineBox = new VBox(4);
        Label outlineHeader = new Label("Outline");
        outlineHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #555;" +
                " -fx-padding: 4 4 2 4;");
        outlineBox.getChildren().addAll(outlineHeader, outlinePanel);
        VBox.setVgrow(outlinePanel, Priority.ALWAYS);
        outlineBox.setStyle("-fx-background-color: #f0f2f8;");

        SplitPane leftSplit = new SplitPane(palette, outlineBox);
        leftSplit.setOrientation(Orientation.VERTICAL);
        leftSplit.setDividerPositions(0.35);
        leftSplit.setPrefWidth(140);
        leftSplit.setMaxWidth(200);

        outlinePanel.setOnSelectCallback(this::onOutlineSelect);

        return leftSplit;
    }

    private void onOutlineSelect(SchemaOutlineItem item) {
        if (item instanceof SchemaOutlineItem.TopicTypeItem tti) {
            constraintsPanel.showTopicType(tti.topicType(), editService);
            canvas.selectById(tti.topicType().getId());
        } else if (item instanceof SchemaOutlineItem.ElementItem ei) {
            constraintsPanel.showElement(ei.owner().getId(), ei.element(), editService);
            canvas.selectById(ei.owner().getId());
        } else if (item instanceof SchemaOutlineItem.AttributeItem ai) {
            constraintsPanel.showAttribute(ai.owner().getId(), ai.attribute(), editService);
            canvas.selectById(ai.owner().getId());
        }
    }

    void show(DitaModel ditaModel) {
        this.model = ditaModel;
        this.editService = new SchemaEditService(ditaModel);

        editService.addListener(event -> {
            canvas.onModelChanged(event);
            outlinePanel.refresh(ditaModel);
        });

        canvas.init(ditaModel, editService);
        canvas.setOnNodeSelect(node -> {
            ditaModel.getTopicTypes().stream()
                    .filter(tt -> tt.getId().equals(node.getModelId()))
                    .findFirst()
                    .ifPresent(tt -> {
                        constraintsPanel.showTopicType(tt, editService);
                        outlinePanel.refresh(ditaModel);
                    });
        });

        outlinePanel.refresh(ditaModel);
        constraintsPanel.showEmpty();

        if (!stage.isShowing()) stage.show();
        else stage.toFront();
    }
}
