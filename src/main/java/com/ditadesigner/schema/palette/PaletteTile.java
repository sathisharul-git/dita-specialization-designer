package com.ditadesigner.schema.palette;

import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;

/** A draggable tile in the Schema Palette. */
class PaletteTile extends VBox {

    static final String DRAG_FORMAT = "application/x-schema-component";

    PaletteTile(String icon, String name, String componentType) {
        setSpacing(2);
        setPrefWidth(90);
        setStyle("-fx-border-color: #c0c8d8; -fx-border-radius: 4; -fx-background-color: #f5f7fb;" +
                " -fx-background-radius: 4; -fx-padding: 6; -fx-cursor: hand;");

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 18px;");
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #333;");
        nameLbl.setWrapText(true);

        getChildren().addAll(iconLbl, nameLbl);

        setOnDragDetected(event -> {
            Dragboard db = startDragAndDrop(TransferMode.COPY);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(componentType);
            db.setContent(cc);
            event.consume();
        });

        setOnMouseEntered(e -> setStyle(getStyle().replace("#f5f7fb", "#e8edf7")));
        setOnMouseExited(e  -> setStyle(getStyle().replace("#e8edf7", "#f5f7fb")));
    }
}
