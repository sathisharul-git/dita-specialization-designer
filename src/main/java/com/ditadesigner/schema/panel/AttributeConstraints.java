package com.ditadesigner.schema.panel;

import com.ditadesigner.model.AttributeDef;
import com.ditadesigner.schema.SchemaEditService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;

/**
 * Constraints panel content for a selected {@link AttributeDef} (F-273).
 */
class AttributeConstraints extends VBox {

    AttributeConstraints(String topicTypeId, AttributeDef attr, SchemaEditService svc) {
        setSpacing(6);
        setPadding(new Insets(8));

        getChildren().add(panelHeader("Attribute — " + attr.getName()));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(4, 0, 4, 0));
        int row = 0;

        // Name
        TextField nameTf = field(attr.getName());
        nameTf.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.updateAttributeName(topicTypeId, attr.getId(), nameTf.getText());
        });
        addRow(grid, row++, "Name", nameTf);

        // Type
        ComboBox<String> typeCb = new ComboBox<>();
        typeCb.getItems().addAll("CDATA","NMTOKEN","NMTOKENS","ID","IDREF","IDREFS","enumeration");
        typeCb.setValue(attr.getType());
        typeCb.setMaxWidth(Double.MAX_VALUE);
        typeCb.setOnAction(e -> svc.updateAttributeType(topicTypeId, attr.getId(), typeCb.getValue()));
        addRow(grid, row++, "Type", typeCb);

        // Default value
        TextField defTf = field(attr.getDefaultValue());
        defTf.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.updateAttributeDefaultValue(topicTypeId, attr.getId(), defTf.getText());
        });
        addRow(grid, row++, "Default", defTf);

        // Fixed value
        TextField fixedTf = field(attr.getFixedValue());
        fixedTf.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.updateAttributeFixedValue(topicTypeId, attr.getId(), fixedTf.getText());
        });
        addRow(grid, row++, "Fixed", fixedTf);

        // Required
        CheckBox reqCb = new CheckBox();
        reqCb.setSelected(attr.isRequired());
        reqCb.setOnAction(e -> svc.updateAttributeRequired(topicTypeId, attr.getId(), reqCb.isSelected()));
        addRow(grid, row++, "Required", reqCb);

        // Specializes from
        ComboBox<String> specCb = new ComboBox<>();
        specCb.getItems().addAll("(none)", "props", "base");
        String curSpec = attr.getSpecializesFrom();
        specCb.setValue(curSpec != null ? curSpec : "(none)");
        specCb.setMaxWidth(Double.MAX_VALUE);
        addRow(grid, row++, "Specializes", specCb);

        getChildren().add(grid);

        // Enum values
        getChildren().add(sectionLabel("Enum Values"));
        ListView<String> enumList = new ListView<>();
        enumList.getItems().addAll(attr.getEnumValues());
        enumList.setPrefHeight(80);
        enumList.setStyle("-fx-font-size: 10px;");

        TextField newEnumTf = new TextField();
        newEnumTf.setPromptText("Add value…");
        newEnumTf.setStyle("-fx-font-size: 11px;");

        Button addEnumBtn = new Button("+");
        addEnumBtn.setOnAction(e -> {
            String val = newEnumTf.getText().trim();
            if (!val.isEmpty() && !enumList.getItems().contains(val)) {
                enumList.getItems().add(val);
                newEnumTf.clear();
                svc.updateAttributeEnumValues(topicTypeId, attr.getId(),
                        new ArrayList<>(enumList.getItems()));
            }
        });
        Button removeEnumBtn = new Button("−");
        removeEnumBtn.setOnAction(e -> {
            String sel = enumList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                enumList.getItems().remove(sel);
                svc.updateAttributeEnumValues(topicTypeId, attr.getId(),
                        new ArrayList<>(enumList.getItems()));
            }
        });

        HBox enumBtns = new HBox(4, newEnumTf, addEnumBtn, removeEnumBtn);
        HBox.setHgrow(newEnumTf, Priority.ALWAYS);
        getChildren().addAll(enumList, enumBtns);

        // DTD ATTLIST fragment (read-only)
        getChildren().add(sectionLabel("DTD ATTLIST fragment"));
        Label dtdLbl = new Label(attr.toDtdFragment());
        dtdLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 10px; -fx-text-fill: #555;" +
                " -fx-background-color: #f5f5f5; -fx-padding: 4; -fx-border-color: #ddd;" +
                " -fx-border-width: 1;");
        dtdLbl.setWrapText(true);
        getChildren().add(dtdLbl);
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
