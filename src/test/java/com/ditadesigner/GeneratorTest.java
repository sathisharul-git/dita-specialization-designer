package com.ditadesigner;

import com.ditadesigner.generator.CatalogGenerator;
import com.ditadesigner.generator.DtdGenerator;
import com.ditadesigner.generator.XsdGenerator;
import com.ditadesigner.model.*;
import com.ditadesigner.repository.ProjectRepository;
import com.ditadesigner.service.ProjectService;
import com.ditadesigner.transformer.ModelTransformer;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GeneratorTest {

    private static DitaModel phxTaskModel;
    private static File tempDir;

    private static final ProjectService projectService = new ProjectService();
    private static final DtdGenerator dtdGenerator = new DtdGenerator();
    private static final XsdGenerator xsdGenerator = new XsdGenerator();
    private static final CatalogGenerator catalogGenerator = new CatalogGenerator();
    private static final ProjectRepository repository = new ProjectRepository();
    private static final ModelTransformer transformer = new ModelTransformer();

    @BeforeAll
    static void setup() throws IOException {
        phxTaskModel = projectService.createSamplePhxTask();
        tempDir = Files.createTempDirectory("dita-test").toFile();
    }

    @AfterAll
    static void cleanup() {
        deleteDir(tempDir);
    }

    // ── Model ─────────────────────────────────────────────────────────

    @Test @Order(1)
    void modelHasTopicTypes() {
        assertFalse(phxTaskModel.getTopicTypes().isEmpty(), "Model should have topic types");
    }

    @Test @Order(2)
    void phxTaskTopicTypeExists() {
        TopicType tt = phxTaskModel.findTopicTypeByName("phxTask");
        assertNotNull(tt, "phxTask TopicType must exist");
        assertEquals("task", tt.getBaseType(), "Base type must be 'task'");
    }

    @Test @Order(3)
    void phxTaskHasElements() {
        TopicType tt = phxTaskModel.findTopicTypeByName("phxTask");
        assertNotNull(tt);
        assertFalse(tt.getElements().isEmpty(), "phxTask must have child elements");

        boolean hasRequirements = tt.getElements().stream()
                .anyMatch(e -> "phxRequirements".equals(e.getName()));
        boolean hasSteps = tt.getElements().stream()
                .anyMatch(e -> "phxSteps".equals(e.getName()));
        assertTrue(hasRequirements, "phxRequirements element must exist");
        assertTrue(hasSteps, "phxSteps element must exist");
    }

    @Test @Order(4)
    void phxTaskHasAttributes() {
        TopicType tt = phxTaskModel.findTopicTypeByName("phxTask");
        assertNotNull(tt);
        assertTrue(tt.getAttributes().stream().anyMatch(a -> "id".equals(a.getName())),
                "id attribute must exist");
    }

    @Test @Order(5)
    void domainExists() {
        assertFalse(phxTaskModel.getDomains().isEmpty(), "Model must have at least one domain");
        DomainDef domain = phxTaskModel.getDomains().get(0);
        assertEquals("phxUI", domain.getName());
    }

    @Test @Order(6)
    void relationshipExists() {
        assertFalse(phxTaskModel.getRelationships().isEmpty(), "Model must have relationships");
        boolean hasDomainInclusion = phxTaskModel.getRelationships().stream()
                .anyMatch(r -> r.getType() == RelationshipType.DOMAIN_INCLUSION);
        assertTrue(hasDomainInclusion, "Domain inclusion relationship must exist");
    }

    // ── Transformer validation ─────────────────────────────────────────

    @Test @Order(7)
    void modelValidatesClean() {
        List<String> issues = transformer.validate(phxTaskModel);
        assertTrue(issues.isEmpty(), "Model should validate without issues: " + issues);
    }

    @Test @Order(8)
    void emptyModelHasIssues() {
        DitaModel empty = new DitaModel();
        empty.setName(""); // Deliberately invalid
        List<String> issues = transformer.validate(empty);
        assertFalse(issues.isEmpty(), "Empty model should have validation issues");
    }

    @Test @Order(9)
    void classAttributeBuilt() {
        TopicType tt = phxTaskModel.findTopicTypeByName("phxTask");
        String cls = transformer.buildClassAttribute(tt);
        assertTrue(cls.contains("phxTask/phxTask"), "Class attribute must contain phxTask/phxTask");
        assertTrue(cls.contains("task/task"), "Class attribute must contain task/task");
    }

    // ── DTD Generator ─────────────────────────────────────────────────

    @Test @Order(10)
    void dtdGeneratesWithoutException() throws Exception {
        dtdGenerator.generate(phxTaskModel, tempDir);
    }

    @Test @Order(11)
    void dtdFilesExist() {
        File dtdDir = new File(tempDir, "dtd");
        assertTrue(dtdDir.exists(), "dtd/ directory must exist");
        assertTrue(new File(dtdDir, "phxtask.dtd").exists(), "phxtask.dtd must exist");
        assertTrue(new File(dtdDir, "phxtask.mod").exists(), "phxtask.mod must exist");
        assertTrue(new File(dtdDir, "phxtask.ent").exists(), "phxtask.ent must exist");
    }

    @Test @Order(12)
    void modFileContainsElementDecl() throws IOException {
        File modFile = new File(tempDir, "dtd/phxtask.mod");
        String content = Files.readString(modFile.toPath());
        assertTrue(content.contains("<!ELEMENT phxtask"), "Must contain ELEMENT declaration");
        assertTrue(content.contains("phxRequirements"), "Must contain phxRequirements reference");
    }

    @Test @Order(13)
    void entFileContainsEntity() throws IOException {
        File entFile = new File(tempDir, "dtd/phxtask.ent");
        String content = Files.readString(entFile.toPath());
        assertTrue(content.contains("<!ENTITY"), "Must contain ENTITY declaration");
    }

    // ── XSD Generator ─────────────────────────────────────────────────

    @Test @Order(14)
    void xsdGeneratesWithoutException() throws Exception {
        xsdGenerator.generate(phxTaskModel, tempDir);
    }

    @Test @Order(15)
    void xsdFileExists() {
        File xsdDir = new File(tempDir, "xsd");
        assertTrue(xsdDir.exists(), "xsd/ directory must exist");
        assertTrue(new File(xsdDir, "phxtask.xsd").exists(), "phxtask.xsd must exist");
    }

    @Test @Order(16)
    void xsdContainsComplexType() throws IOException {
        File xsdFile = new File(tempDir, "xsd/phxtask.xsd");
        String content = Files.readString(xsdFile.toPath());
        assertTrue(content.contains("xs:complexType"), "Must contain xs:complexType");
        assertTrue(content.contains("xs:extension"), "Must contain xs:extension for specialization");
        assertTrue(content.contains("base=\"task.class\""), "Must extend task.class");
    }

    @Test @Order(17)
    void xsdContainsElementDeclaration() throws IOException {
        File xsdFile = new File(tempDir, "xsd/phxtask.xsd");
        String content = Files.readString(xsdFile.toPath());
        assertTrue(content.contains("xs:element name=\"phxtask\""), "Must declare phxtask element");
    }

    // ── Catalog Generator ─────────────────────────────────────────────

    @Test @Order(18)
    void catalogGeneratesWithoutException() throws Exception {
        catalogGenerator.generate(phxTaskModel, tempDir);
    }

    @Test @Order(19)
    void catalogFileExists() {
        assertTrue(new File(tempDir, "catalog.xml").exists(), "catalog.xml must exist");
    }

    @Test @Order(20)
    void catalogContainsPublicId() throws IOException {
        File catalogFile = new File(tempDir, "catalog.xml");
        String content = Files.readString(catalogFile.toPath());
        assertTrue(content.contains("<public"), "Must contain <public> entries");
        assertTrue(content.contains("phxtask.mod"), "Must reference phxtask.mod");
        assertTrue(content.contains("<system"), "Must contain <system> entries");
    }

    // ── JSON Persistence ──────────────────────────────────────────────

    @Test @Order(21)
    void saveAndLoadRoundTrip() throws IOException {
        File projectFile = new File(tempDir, "phxtask.ddp");
        repository.save(phxTaskModel, projectFile);
        assertTrue(projectFile.exists(), "Project file must be saved");

        DitaModel loaded = repository.load(projectFile);
        assertNotNull(loaded);
        assertEquals(phxTaskModel.getName(), loaded.getName());
        assertEquals(phxTaskModel.getTopicTypes().size(), loaded.getTopicTypes().size());
        assertEquals(phxTaskModel.getDomains().size(), loaded.getDomains().size());
        assertEquals(phxTaskModel.getRelationships().size(), loaded.getRelationships().size());
    }

    @Test @Order(22)
    void jsonSerializationPreservesElements() throws IOException {
        File projectFile = new File(tempDir, "phxtask2.ddp");
        repository.save(phxTaskModel, projectFile);
        DitaModel loaded = repository.load(projectFile);

        TopicType ttLoaded = loaded.findTopicTypeByName("phxTask");
        assertNotNull(ttLoaded);
        assertEquals(phxTaskModel.findTopicTypeByName("phxTask").getElements().size(),
                ttLoaded.getElements().size(), "Element count must survive serialization");
    }

    // ── Attribute DTD fragment ─────────────────────────────────────────

    @Test @Order(23)
    void attributeDtdFragmentRequired() {
        AttributeDef attr = new AttributeDef("id", "ID", null, true);
        String fragment = attr.toDtdFragment();
        assertTrue(fragment.contains("id"), "Fragment must contain name");
        assertTrue(fragment.contains("#REQUIRED"), "Must be REQUIRED");
    }

    @Test @Order(24)
    void attributeDtdFragmentDefault() {
        AttributeDef attr = new AttributeDef("status", "CDATA", "draft", false);
        String fragment = attr.toDtdFragment();
        assertTrue(fragment.contains("\"draft\""), "Fragment must contain default value");
    }

    // ── helper ────────────────────────────────────────────────────────

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteDir(f);
            else f.delete();
        }
        dir.delete();
    }
}
