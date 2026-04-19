package com.ditadesigner.schema.panel;

import com.ditadesigner.model.ElementDef;
import com.ditadesigner.schema.SchemaEditService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Constraints panel content for a selected {@link ElementDef} (F-272).
 */
class ElementConstraints extends VBox {

    ElementConstraints(String topicTypeId, ElementDef elem, SchemaEditService svc) {
        setSpacing(6);
        setPadding(new Insets(8));

        getChildren().add(panelHeader("Element — " + elem.getName()));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(4, 0, 4, 0));
        int row = 0;

        // Name
        TextField nameTf = field(elem.getName());
        nameTf.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.updateElementName(topicTypeId, elem.getId(), nameTf.getText());
        });
        addRow(grid, row++, "Name", nameTf);

        // Cardinality
        ComboBox<String> cardCb = new ComboBox<>();
        cardCb.getItems().addAll("1", "?", "+", "*");
        cardCb.setValue(elem.getCardinality());
        cardCb.setMaxWidth(Double.MAX_VALUE);
        cardCb.setOnAction(e -> svc.updateElementCardinality(topicTypeId, elem.getId(), cardCb.getValue()));
        addRow(grid, row++, "Cardinality", cardCb);

        // Required
        CheckBox reqCb = new CheckBox();
        reqCb.setSelected(elem.isRequired());
        reqCb.setOnAction(e -> svc.updateElementRequired(topicTypeId, elem.getId(), reqCb.isSelected()));
        addRow(grid, row++, "Required", reqCb);

        getChildren().add(grid);

        // Content Model
        getChildren().add(sectionLabel("Content Model"));
        TextField cmTf = new TextField(elem.getContentModel() != null ? elem.getContentModel() : "");
        cmTf.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        cmTf.focusedProperty().addListener((o, was, is) -> {
            if (!is) svc.updateElementContentModel(topicTypeId, elem.getId(), cmTf.getText());
        });
        getChildren().add(cmTf);

        // DTD fragment preview (read-only)
        getChildren().add(sectionLabel("DTD Fragment (preview)"));
        String dtdFrag = "<!ELEMENT " + elem.getName() + " "
                + (elem.getContentModel() != null ? elem.getContentModel() : "(#PCDATA)*") + ">";
        Label dtdLbl = new Label(dtdFrag);
        dtdLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 10px; -fx-text-fill: #555;" +
                " -fx-background-color: #f5f5f5; -fx-padding: 4; -fx-border-color: #ddd;" +
                " -fx-border-width: 1; -fx-wrap-text: true;");
        dtdLbl.setWrapText(true);
        getChildren().add(dtdLbl);

        // Description
        getChildren().add(sectionLabel("Description"));
        TextArea descTa = new TextArea(elem.getDescription() != null ? elem.getDescription() : "");
        descTa.setPrefRowCount(2);
        descTa.setWrapText(true);
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
