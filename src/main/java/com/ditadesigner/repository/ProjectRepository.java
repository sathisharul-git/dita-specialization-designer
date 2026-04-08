package com.ditadesigner.repository;

import com.ditadesigner.model.DitaModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;

/**
 * Saves and loads a {@link DitaModel} to/from a JSON project file.
 */
public class ProjectRepository {

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public void save(DitaModel model, File file) throws IOException {
        file.getParentFile().mkdirs();
        MAPPER.writeValue(file, model);
    }

    public DitaModel load(File file) throws IOException {
        return MAPPER.readValue(file, DitaModel.class);
    }

    public String toJson(DitaModel model) throws IOException {
        return MAPPER.writeValueAsString(model);
    }

    public DitaModel fromJson(String json) throws IOException {
        return MAPPER.readValue(json, DitaModel.class);
    }
}
