package com.ditadesigner;

import com.ditadesigner.model.*;
import com.ditadesigner.transformer.ModelTransformer;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ModelTransformer: validate(), buildClassAttribute(),
 * buildElementClassAttribute(), derivePublicId(), and orphan / cycle detection.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransformerTest {

    private static final ModelTransformer transformer = new ModelTransformer();

    // ── Helpers ────────────────────────────────────────────────────────

    private static DitaModel validModel(String name) {
        DitaModel m = new DitaModel(name);
        m.addTopicType(new TopicType("myTopic", "topic"));
        return m;
    }

    // ── Scenario 1: Empty / blank model ───────────────────────────────

    @Test @Order(1)
    void emptyModelReportsNameMissing() {
        DitaModel m = new DitaModel();
        m.setName("");
        List<String> issues = transformer.validate(m);
        assertFalse(issues.isEmpty(), "blank model name must produce a validation issue");
        assertTrue(issues.stream().anyMatch(s -> s.toLowerCase().contains("name")),
                "issue must mention 'name'");
    }

    @Test @Order(2)
    void nullModelNameReportsIssue() {
        DitaModel m = new DitaModel();
        m.setName(null);
        List<String> issues = transformer.validate(m);
        assertFalse(issues.isEmpty());
    }

    // ── Scenario 2: Valid minimal model ───────────────────────────────

    @Test @Order(3)
    void validMinimalModelPassesValidation() {
        List<String> issues = transformer.validate(validModel("SimpleSpec"));
        assertTrue(issues.isEmpty(), "valid model must produce no issues: " + issues);
    }

    // ── Scenario 3: TopicType without baseType ─────────────────────────

    @Test @Order(4)
    void topicTypeWithoutBaseTypeIsInvalid() {
        DitaModel m = new DitaModel("Test");
        TopicType tt = new TopicType("badType", "");
        m.addTopicType(tt);
        List<String> issues = transformer.validate(m);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(s -> s.contains("base type")),
                "issue must mention 'base type'");
    }

    // ── Scenario 4: Duplicate TopicType names ─────────────────────────

    @Test @Order(5)
    void duplicateTopicTypeNameIsInvalid() {
        DitaModel m = new DitaModel("Dup");
        m.addTopicType(new TopicType("myTopic", "topic"));
        m.addTopicType(new TopicType("myTopic", "concept")); // same name
        List<String> issues = transformer.validate(m);
        assertTrue(issues.stream().anyMatch(s -> s.toLowerCase().contains("duplicate")),
                "duplicate name must be flagged");
    }

    // ── Scenario 5: Class attribute derivation ────────────────────────

    @Test @Order(6)
    void classAttributeForTaskSpecialization() {
        TopicType tt = new TopicType("phxTask", "task");
        String cls = transformer.buildClassAttribute(tt);
        assertTrue(cls.contains("topic/topic"), "must contain topic/topic");
        assertTrue(cls.contains("task/task"),   "must contain task/task");
        assertTrue(cls.contains("phxTask/phxTask"), "must contain phxTask/phxTask");
    }

    @Test @Order(7)
    void classAttributeForTopicSpecialization() {
        TopicType tt = new TopicType("mySpec", "topic");
        String cls = transformer.buildClassAttribute(tt);
        assertTrue(cls.contains("topic/topic"));
        assertTrue(cls.contains("mySpec/mySpec"));
    }

    @Test @Order(8)
    void classAttributeForConceptSpecialization() {
        TopicType tt = new TopicType("myConcept", "concept");
        String cls = transformer.buildClassAttribute(tt);
        assertTrue(cls.contains("concept/concept"));
        assertTrue(cls.contains("myConcept/myConcept"));
    }

    // ── Scenario 6: Element class attribute ───────────────────────────

    @Test @Order(9)
    void elementClassAttributeUsesParentBaseType() {
        TopicType tt = new TopicType("phxTask", "task");
        ElementDef el = new ElementDef("phxSteps");
        String cls = transformer.buildElementClassAttribute(tt, el, "steps");
        assertTrue(cls.contains("topic/steps"),   "must contain base element reference");
        assertTrue(cls.contains("task/steps"),    "must contain base-type element reference");
        assertTrue(cls.contains("phxTask/phxSteps"), "must contain specialization element");
    }

    @Test @Order(10)
    void elementClassAttributeWithNullBaseElemFallsBackToElemName() {
        TopicType tt = new TopicType("myTopic", "topic");
        ElementDef el = new ElementDef("customElem");
        String cls = transformer.buildElementClassAttribute(tt, el, null);
        assertTrue(cls.contains("customElem"), "elem name used as fallback base elem");
    }

    // ── Scenario 7: Known base types ──────────────────────────────────

    @Test @Order(11)
    void isKnownBaseTypeForStandardTypes() {
        assertTrue(transformer.isKnownBaseType("topic"));
        assertTrue(transformer.isKnownBaseType("task"));
        assertTrue(transformer.isKnownBaseType("concept"));
        assertTrue(transformer.isKnownBaseType("reference"));
        assertTrue(transformer.isKnownBaseType("map"));
    }

    @Test @Order(12)
    void isKnownBaseTypeReturnsFalseForUnknown() {
        assertFalse(transformer.isKnownBaseType("unknownType"));
        assertFalse(transformer.isKnownBaseType(""));
    }

    // ── Scenario 8: Public ID derivation ─────────────────────────────

    @Test @Order(13)
    void derivePublicIdFromOrgName() {
        TopicType tt = new TopicType("phxTask", "task");
        String pid = transformer.derivePublicId(tt, "Phoenix Engineering");
        assertTrue(pid.contains("PHOENIX ENGINEERING"), "org name must be uppercased");
        assertTrue(pid.contains("phxTask"), "topic type name must be in public ID");
        assertTrue(pid.startsWith("-//"), "public ID must start with -//");
    }

    @Test @Order(14)
    void derivePublicIdPreservesExplicitId() {
        TopicType tt = new TopicType("phxTask", "task");
        tt.setPublicId("-//MY-ORG//CUSTOM ID//EN");
        String pid = transformer.derivePublicId(tt, "Other Org");
        assertEquals("-//MY-ORG//CUSTOM ID//EN", pid, "explicit public ID must not be overwritten");
    }

    @Test @Order(15)
    void derivePublicIdFallsBackToLocalWhenNoOrg() {
        TopicType tt = new TopicType("mySpec", "topic");
        String pid = transformer.derivePublicId(tt, null);
        assertTrue(pid.contains("LOCAL"), "null org defaults to LOCAL");
    }

    // ── Scenario 9: Module system ID ──────────────────────────────────

    @Test @Order(16)
    void deriveModSystemId() {
        TopicType tt = new TopicType("phxTask", "task");
        assertEquals("phxtask.mod", transformer.deriveModSystemId(tt));
    }

    @Test @Order(17)
    void deriveModSystemIdFromExplicitModule() {
        TopicType tt = new TopicType("phxTask", "task");
        tt.setModule("custom-module");
        assertEquals("custom-module.mod", transformer.deriveModSystemId(tt));
    }

    // ── Scenario 10: Circular inheritance ────────────────────────────

    @Test @Order(18)
    void circularInheritanceIsDetected() {
        DitaModel m = new DitaModel("Circular");
        TopicType a = new TopicType("TypeA", "topic");
        TopicType b = new TopicType("TypeB", "topic");
        m.addTopicType(a);
        m.addTopicType(b);
        // A inherits from B, B inherits from A — cycle
        m.addRelationship(new Relationship(RelationshipType.INHERITANCE, a.getId(), b.getId()));
        m.addRelationship(new Relationship(RelationshipType.INHERITANCE, b.getId(), a.getId()));
        List<String> issues = transformer.validate(m);
        assertTrue(issues.stream().anyMatch(s -> s.toLowerCase().contains("circular")),
                "circular inheritance must be flagged");
    }

    // ── Scenario 11: Domain validation ────────────────────────────────

    @Test @Order(19)
    void domainWithoutNameIsInvalid() {
        DitaModel m = new DitaModel("DomainTest");
        m.addTopicType(new TopicType("myTopic", "topic"));
        DomainDef dd = new DomainDef();
        // no name set
        m.addDomain(dd);
        List<String> issues = transformer.validate(m);
        assertTrue(issues.stream().anyMatch(s -> s.toLowerCase().contains("domain")),
                "unnamed domain must be flagged");
    }

    // ── Scenario 12: Required attribute with no default ───────────────

    @Test @Order(20)
    void requiredNonIdAttributeWithoutDefaultIsWarned() {
        DitaModel m = new DitaModel("AttrTest");
        TopicType tt = new TopicType("myTopic", "topic");
        // CDATA required with no default → should warn (ID/IDREF are exempt)
        tt.addAttribute(new AttributeDef("importance", "CDATA", null, true));
        m.addTopicType(tt);
        List<String> issues = transformer.validate(m);
        assertFalse(issues.isEmpty(), "required CDATA attribute without default must be flagged");
    }

    @Test @Order(21)
    void requiredIdAttributeIsExemptFromDefaultCheck() {
        DitaModel m = new DitaModel("IdTest");
        TopicType tt = new TopicType("myTopic", "topic");
        tt.addAttribute(new AttributeDef("id", "ID", null, true)); // ID type is exempt
        m.addTopicType(tt);
        List<String> issues = transformer.validate(m);
        assertTrue(issues.isEmpty(), "required ID attribute needs no default: " + issues);
    }
}
