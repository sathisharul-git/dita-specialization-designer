package com.ditadesigner;

import com.ditadesigner.model.*;
import com.ditadesigner.repository.ProjectRepository;
import com.ditadesigner.service.ProjectService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProjectRepository: JSON save/load round-trips across multiple
 * model configurations.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepositoryTest {

    private static final ProjectRepository repo = new ProjectRepository();

    @TempDir
    static File tempDir;

    // ── Scenario 1: Minimal model ─────────────────────────────────────

    @Test @Order(1)
    void minimalModelRoundTrip() throws IOException {
        DitaModel model = new DitaModel("MinimalSpec");
        model.setVersion("2.0");

        File f = new File(tempDir, "minimal.ddp");
        repo.save(model, f);
        assertTrue(f.exists(), "save must create the file");

        DitaModel loaded = repo.load(f);
        assertEquals("MinimalSpec", loaded.getName());
        assertEquals("2.0", loaded.getVersion());
    }

    // ── Scenario 2: Full phxTask model ───────────────────────────────

    @Test @Order(2)
    void fullPhxTaskRoundTrip() throws IOException {
        DitaModel model = new ProjectService().createSamplePhxTask();
        File f = new File(tempDir, "phxTask.ddp");
        repo.save(model, f);

        DitaModel loaded = repo.load(f);
        assertEquals(model.getName(), loaded.getName());
        assertEquals(model.getTopicTypes().size(), loaded.getTopicTypes().size());
        assertEquals(model.getDomains().size(), loaded.getDomains().size());
        assertEquals(model.getRelationships().size(), loaded.getRelationships().size());
    }

    // ── Scenario 3: Attribute defaults preserved ──────────────────────

    @Test @Order(3)
    void attributeDefaultValueSurvivesSerialization() throws IOException {
        DitaModel model = new DitaModel("AttrTest");
        TopicType tt = new TopicType("myTopic", "topic");
        tt.addAttribute(new AttributeDef("status", "CDATA", "draft", false));
        model.addTopicType(tt);

        File f = new File(tempDir, "attr.ddp");
        repo.save(model, f);
        DitaModel loaded = repo.load(f);

        AttributeDef attr = loaded.findTopicTypeByName("myTopic")
                .getAttributes().get(0);
        assertEquals("status",  attr.getName());
        assertEquals("draft",   attr.getDefaultValue());
        assertFalse(attr.isRequired());
    }

    @Test @Order(4)
    void requiredFlagSurvivesSerialization() throws IOException {
        DitaModel model = new DitaModel("ReqTest");
        TopicType tt = new TopicType("myTopic", "topic");
        tt.addAttribute(new AttributeDef("id", "ID", null, true));
        model.addTopicType(tt);

        File f = new File(tempDir, "req.ddp");
        repo.save(model, f);
        DitaModel loaded = repo.load(f);

        assertTrue(loaded.findTopicTypeByName("myTopic")
                .getAttributes().get(0).isRequired());
    }

    // ── Scenario 4: Multiple topic types ─────────────────────────────

    @Test @Order(5)
    void multipleTopicTypesRoundTrip() throws IOException {
        DitaModel model = new DitaModel("Multi");
        model.addTopicType(new TopicType("typeA", "topic"));
        model.addTopicType(new TopicType("typeB", "concept"));
        model.addTopicType(new TopicType("typeC", "task"));

        TopicType typeA = model.findTopicTypeByName("typeA");
        typeA.addElement(new ElementDef("elemA1"));
        typeA.addElement(new ElementDef("elemA2"));

        File f = new File(tempDir, "multi.ddp");
        repo.save(model, f);
        DitaModel loaded = repo.load(f);

        assertEquals(3, loaded.getTopicTypes().size());
        assertEquals(2, loaded.findTopicTypeByName("typeA").getElements().size());
        assertNotNull(loaded.findTopicTypeByName("typeB"));
        assertNotNull(loaded.findTopicTypeByName("typeC"));
    }

    // ── Scenario 5: Relationships serialized ─────────────────────────

    @Test @Order(6)
    void relationshipsRoundTrip() throws IOException {
        DitaModel model = new DitaModel("RelTest");
        TopicType tt = new TopicType("myTask", "task");
        DomainDef dd = new DomainDef("myDomain");
        model.addTopicType(tt);
        model.addDomain(dd);
        model.addRelationship(
                new Relationship(RelationshipType.DOMAIN_INCLUSION, tt.getId(), dd.getId()));

        File f = new File(tempDir, "rel.ddp");
        repo.save(model, f);
        DitaModel loaded = repo.load(f);

        assertEquals(1, loaded.getRelationships().size());
        assertEquals(RelationshipType.DOMAIN_INCLUSION,
                loaded.getRelationships().get(0).getType());
    }

    // ── Scenario 6: File overwrite ────────────────────────────────────

    @Test @Order(7)
    void fileOverwritePreservesLatestData() throws IOException {
        File f = new File(tempDir, "overwrite.ddp");

        DitaModel v1 = new DitaModel("Version1");
        repo.save(v1, f);

        DitaModel v2 = new DitaModel("Version2");
        repo.save(v2, f); // second write to same path

        DitaModel loaded = repo.load(f);
        assertEquals("Version2", loaded.getName(), "second save must overwrite first");
    }

    // ── Scenario 7: Load non-existent file throws ─────────────────────

    @Test @Order(8)
    void loadNonExistentFileThrows() {
        File missing = new File(tempDir, "nonexistent.ddp");
        assertThrows(IOException.class, () -> repo.load(missing),
                "loading a missing file must throw IOException");
    }

    // ── Scenario 8: Unicode project name ─────────────────────────────

    @Test @Order(9)
    void unicodeProjectNamePreserved() throws IOException {
        String unicodeName = "测试-ДизайнЕр-テスト";
        DitaModel model = new DitaModel(unicodeName);

        File f = new File(tempDir, "unicode.ddp");
        repo.save(model, f);
        DitaModel loaded = repo.load(f);

        assertEquals(unicodeName, loaded.getName(), "Unicode name must survive serialization");
    }

    // ── Scenario 9: JSON string round-trip ───────────────────────────

    @Test @Order(10)
    void jsonStringRoundTrip() throws IOException {
        DitaModel model = new DitaModel("JsonSpec");
        model.addTopicType(new TopicType("someType", "reference"));

        String json   = repo.toJson(model);
        assertNotNull(json);
        assertTrue(json.contains("JsonSpec"), "JSON must contain the model name");

        DitaModel loaded = repo.fromJson(json);
        assertEquals("JsonSpec", loaded.getName());
        assertEquals(1, loaded.getTopicTypes().size());
    }

    // ── Scenario 10: Large model performance ─────────────────────────

    @Test @Order(11)
    void largeModelSaveLoadUnder2Seconds() throws IOException {
        DitaModel model = new DitaModel("LargeModel");
        for (int t = 0; t < 20; t++) {
            TopicType tt = new TopicType("type" + t, "topic");
            for (int e = 0; e < 10; e++) {
                tt.addElement(new ElementDef("elem" + t + "_" + e));
            }
            for (int a = 0; a < 5; a++) {
                tt.addAttribute(new AttributeDef("attr" + t + "_" + a, "CDATA", null, false));
            }
            model.addTopicType(tt);
        }

        File f = new File(tempDir, "large.ddp");
        long start = System.currentTimeMillis();
        repo.save(model, f);
        DitaModel loaded = repo.load(f);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(20, loaded.getTopicTypes().size());
        assertTrue(elapsed < 2000, "save+load of large model must complete under 2 seconds");
    }
}
