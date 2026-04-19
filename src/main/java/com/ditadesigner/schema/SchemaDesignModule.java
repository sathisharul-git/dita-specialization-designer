package com.ditadesigner.schema;

import com.ditadesigner.model.DitaModel;
import javafx.stage.Stage;

/** Entry point for the Schema Design Workbench (F-254). */
public final class SchemaDesignModule {

    private static SchemaDesignController controller;

    private SchemaDesignModule() {}

    public static void openWorkbench(Stage ownerStage, DitaModel model) {
        if (controller == null) {
            controller = new SchemaDesignController(ownerStage);
        }
        controller.show(model);
    }
}
