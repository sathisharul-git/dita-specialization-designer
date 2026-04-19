package com.ditadesigner.schema;

/** Notified whenever the DitaModel is mutated through the Schema Design Workbench. */
@FunctionalInterface
public interface SchemaModelListener {
    void onModelChanged(SchemaModelChangeEvent event);
}
