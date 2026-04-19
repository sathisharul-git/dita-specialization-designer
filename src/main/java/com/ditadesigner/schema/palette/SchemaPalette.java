package com.ditadesigner.schema.palette;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * Left-panel palette for the Schema Design Workbench (F-263–F-265).
 * Contains draggable tiles for TopicType, Element, Attribute, Domain, and Relationship.
 */
public class SchemaPalette extends ScrollPane {

    public SchemaPalette() {
        setFitToWidth(true);
        setPrefWidth(120);
        setStyle("-fx-background-color: #f0f2f8;");

        VBox root = new VBox(8);
        root.setPadding(new Insets(8));

        root.getChildren().add(sectionLabel("Components"));

        FlowPane tiles = new FlowPane(6, 6);
        tiles.setPrefWrapLength(110);
        tiles.getChildren().addAll(
                new PaletteTile("📄", "TopicType", "TOPIC_TYPE"),
                new PaletteTile("▸", "Element",   "ELEMENT"),
                new PaletteTile("●", "Attribute",  "ATTRIBUTE"),
                new PaletteTile("⬡", "Domain",     "DOMAIN")
        );
        root.getChildren().add(tiles);

        root.getChildren().add(sectionLabel("Relationships"));
        FlowPane relTiles = new FlowPane(6, 6);
        relTiles.setPrefWrapLength(110);
        relTiles.getChildren().addAll(
                new PaletteTile("↑", "Inherits",  "REL_INHERITANCE"),
                new PaletteTile("⊂", "Contains",  "REL_CONTAINMENT"),
                new PaletteTile("⊕", "Domain\nInclusion", "REL_DOMAIN")
        );
        root.getChildren().add(relTiles);

        setContent(root);
    }

    private static Label sectionLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #666;" +
                " -fx-padding: 4 0 2 0;");
        return lbl;
    }
}
