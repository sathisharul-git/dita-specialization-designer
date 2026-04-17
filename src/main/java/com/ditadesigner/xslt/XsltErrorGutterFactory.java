package com.ditadesigner.xslt;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class XsltErrorGutterFactory implements IntFunction<Node> {

    private final Supplier<Map<Integer, XsltValidationError>> errorsSupplier;

    public XsltErrorGutterFactory(Supplier<Map<Integer, XsltValidationError>> errorsSupplier) {
        this.errorsSupplier = errorsSupplier;
    }

    @Override
    public Node apply(int paragraphIndex) {
        int lineNumber = paragraphIndex + 1;

        Label lineLabel = new Label(String.format("%4d", lineNumber));
        lineLabel.getStyleClass().add("lineno");
        lineLabel.setAlignment(Pos.CENTER_RIGHT);
        lineLabel.setMinWidth(40);

        HBox box = new HBox(lineLabel);
        box.setAlignment(Pos.CENTER_LEFT);

        Map<Integer, XsltValidationError> errors = errorsSupplier.get();
        XsltValidationError error = errors.get(lineNumber);
        if (error != null) {
            Color dotColor = error.getSeverity() == XsltValidationError.Severity.ERROR
                    ? Color.web("#f44747")
                    : Color.web("#cca700");
            Circle dot = new Circle(5, dotColor);
            Tooltip.install(dot, new Tooltip(error.getMessage()));
            dot.setStyle("-fx-cursor: hand;");
            box.getChildren().add(dot);
            box.setSpacing(4);
        }

        return box;
    }
}
