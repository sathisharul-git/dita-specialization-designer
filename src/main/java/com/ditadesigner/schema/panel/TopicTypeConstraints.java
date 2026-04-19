package com.ditadesigner.schema.panel;

import com.ditadesigner.model.TopicType;
import com.ditadesigner.schema.SchemaEditService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Constraints panel content for a selected {@link TopicType} (F-271).
 * All field changes write back to the model via {@link SchemaEditService}.
 */
class TopicTypeConstraints extends VBox {

    private static final List<String> BASE_TYPES =
            List.of("topic", "task", "concept", "reference", "map", "bookmap");

    TopicTypeConstraints(TopicType tt, SchemaEditService svc) {
        setSpacing(6);
        setPadding(new Insets(8));

        getChildren().add(panelHeader("TopicType — " + tt.getName()));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(4, 0, 4, 0));
        int row = 0;

        // Name
        TextField nameTf = field(tt.getName());
        nameTf.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.renameTopicType(tt.getId(), nameTf.getText());
        });
        addRow(grid, row++, "Name", nameTf);

        // Base type
        ComboBox<String> baseCb = new ComboBox<>();
        baseCb.getItems().addAll(BASE_TYPES);
        baseCb.setValue(tt.getBaseType());
        baseCb.setMaxWidth(Double.MAX_VALUE);
        baseCb.setOnAction(e -> svc.setTopicTypeBaseType(tt.getId(), baseCb.getValue()));
        addRow(grid, row++, "Base Type", baseCb);

        // Namespace
        TextField nsTf = field(tt.getNamespace());
        nsTf.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.setTopicTypeNamespace(tt.getId(), nsTf.getText());
        });
        addRow(grid, row++, "Namespace", nsTf);

        // Public ID
        TextField pubTf = field(tt.getPublicId());
        pubTf.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.setTopicTypePublicId(tt.getId(), pubTf.getText());
        });
        addRow(grid, row++, "Public ID", pubTf);

        // System ID
        TextField sysTf = field(tt.getSystemId());
        sysTf.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.setTopicTypeSystemId(tt.getId(), sysTf.getText());
        });
        addRow(grid, row++, "System ID", sysTf);

        // Class attribute (read-only)
        String classVal = "- topic/topic " + tt.getBaseType() + "/" + tt.getBaseType()
                + " " + tt.getName() + "/" + tt.getName() + " ";
        TextField classTf = field(classVal);
        classTf.setEditable(false);
        classTf.setStyle("-fx-background-color: #f0f0f0; -fx-font-size: 10px;");
        addRow(grid, row++, "class", classTf);

        getChildren().add(grid);

        // Description
        getChildren().add(sectionLabel("Description"));
        TextArea descTa = new TextArea(tt.getDescription() != null ? tt.getDescription() : "");
        descTa.setPrefRowCount(3);
        descTa.setWrapText(true);
        descTa.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.setTopicTypeDescription(tt.getId(), descTa.getText());
        });
        getChildren().add(descTa);
    }

    private static Label panelHeader(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1a3a6b;" +
                " -fx-border-color: #c0d0f0; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 4 0;");
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    private static Label sectionLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #555;");
        return lbl;
    }

    private static TextField field(String value) {
        TextField tf = new TextField(value != null ? value : "");
        tf.setStyle("-fx-font-size: 11px;");
        GridPane.setHgrow(tf, Priority.ALWAYS);
        return tf;
    }

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node field) {
        Label lbl = new Label(label + ":");
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #555;");
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }
}
