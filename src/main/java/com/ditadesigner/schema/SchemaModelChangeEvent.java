package com.ditadesigner.schema;

/** Immutable event fired by {@link SchemaEditService} whenever the DitaModel changes via the Schema Design Workbench. */
public record SchemaModelChangeEvent(String modelObjectId, SchemaEditService.ChangeKind kind) {}
