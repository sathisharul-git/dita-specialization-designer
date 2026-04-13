package com.ditadesigner;

import com.ditadesigner.model.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for model/ entities: DitaModel, TopicType, ElementDef, AttributeDef,
 * DomainDef, and Relationship — all in-memory, no I/O.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModelTest {

    // ── Scenario 1: Minimal model ──────────────────────────────────────

    @Test @Order(1)
    void freshDitaModelHasDefaults() {
        DitaModel m = new DitaModel();
        assertNotNull(m.getId(), "id must be auto-assigned");
        assertEquals("Untitled", m.getName());
        assertEquals("1.0", m.getVersion());
        assertNotNull(m.getTopicTypes());
        assertNotNull(m.getDomains());
        assertNotNull(m.getRelationships());
    }

    @Test @Order(2)
    void freshTopicTypeHasEmptyLists() {
        TopicType tt = new TopicType();
        assertNotNull(tt.getElements(), "elements list must not be null");
        assertNotNull(tt.getAttributes(), "attributes list must not be null");
        assertTrue(tt.getElements().isEmpty());
        assertTrue(tt.getAttributes().isEmpty());
        assertEquals("topic", tt.getBaseType(), "default base type is 'topic'");
    }

    @Test @Order(3)
    void topicTypeConstructorSetsNameAndBase() {
        TopicType tt = new TopicType("myTask", "task");
        assertEquals("myTask", tt.getName());
        assertEquals("task", tt.getBaseType());
    }

    // ── Scenario 2: Find by name ───────────────────────────────────────

    @Test @Order(4)
    void findTopicTypeByNameFound() {
        DitaModel m = new DitaModel("Test");
        TopicType tt = new TopicType("phxTask", "task");
        m.addTopicType(tt);
        assertSame(tt, m.findTopicTypeByName("phxTask"));
    }

    @Test @Order(5)
    void findTopicTypeByNameCaseInsensitive() {
        DitaModel m = new DitaModel("Test");
        TopicType tt = new TopicType("PhxTask", "task");
        m.addTopicType(tt);
        assertNotNull(m.findTopicTypeByName("phxtask"), "lookup must be case-insensitive");
    }

    @Test @Order(6)
    void findTopicTypeByNameNotFound() {
        DitaModel m = new DitaModel("Test");
        assertNull(m.findTopicTypeByName("nonexistent"));
    }

    // ── Scenario 3: Multiple topic types ──────────────────────────────

    @Test @Order(7)
    void multipleTopicTypesInModel() {
        DitaModel m = new DitaModel("Multi");
        m.addTopicType(new TopicType("typeA", "topic"));
        m.addTopicType(new TopicType("typeB", "concept"));
        m.addTopicType(new TopicType("typeC", "task"));
        assertEquals(3, m.getTopicTypes().size());
        assertNotNull(m.findTopicTypeByName("typeB"));
    }

    // ── Scenario 4: Elements and attributes ───────────────────────────

    @Test @Order(8)
    void addElementToTopicType() {
        TopicType tt = new TopicType("myTopic", "topic");
        ElementDef el = new ElementDef("myTitle");
        tt.addElement(el);
        assertEquals(1, tt.getElements().size());
        assertSame(el, tt.getElements().get(0));
        assertEquals(tt.getId(), el.getParentId(), "parentId must be set on add");
    }

    @Test @Order(9)
    void findElementByName() {
        TopicType tt = new TopicType("myTopic", "topic");
        tt.addElement(new ElementDef("titleElem"));
        tt.addElement(new ElementDef("bodyElem"));
        assertNotNull(tt.findElementByName("titleElem"));
        assertNull(tt.findElementByName("missing"));
    }

    @Test @Order(10)
    void addAttributeToTopicType() {
        TopicType tt = new TopicType("myTopic", "topic");
        AttributeDef attr = new AttributeDef("id", "ID", null, true);
        tt.addAttribute(attr);
        assertEquals(1, tt.getAttributes().size());
    }

    // ── Scenario 5: AttributeDef DTD fragment ─────────────────────────

    @Test @Order(11)
    void attributeFragmentRequired() {
        AttributeDef attr = new AttributeDef("id", "ID", null, true);
        String frag = attr.toDtdFragment();
        assertTrue(frag.contains("id"), "fragment must contain attribute name");
        assertTrue(frag.contains("ID"), "fragment must contain type");
        assertTrue(frag.contains("#REQUIRED"));
    }

    @Test @Order(12)
    void attributeFragmentWithDefault() {
        AttributeDef attr = new AttributeDef("status", "CDATA", "draft", false);
        String frag = attr.toDtdFragment();
        assertTrue(frag.contains("\"draft\""), "fragment must include default value in quotes");
        assertFalse(frag.contains("#REQUIRED"));
    }

    @Test @Order(13)
    void attributeFragmentImplied() {
        AttributeDef attr = new AttributeDef("class", "CDATA", null, false);
        String frag = attr.toDtdFragment();
        assertTrue(frag.contains("#IMPLIED"));
    }

    @Test @Order(14)
    void attributeFragmentFixed() {
        AttributeDef attr = new AttributeDef();
        attr.setName("domains");
        attr.setType("CDATA");
        attr.setFixedValue("(phx-ui)");
        String frag = attr.toDtdFragment();
        assertTrue(frag.contains("#FIXED"), "fixed attribute must use #FIXED");
        assertTrue(frag.contains("(phx-ui)"));
    }

    @Test @Order(15)
    void attributeFragmentEnum() {
        AttributeDef attr = new AttributeDef("importance", "NMTOKEN", null, false);
        attr.addEnumValue("high");
        attr.addEnumValue("low");
        String frag = attr.toDtdFragment();
        assertTrue(frag.contains("(high|low)"), "enum values must appear in parentheses");
    }

    @Test @Order(16)
    void attributeEnumDeduplication() {
        AttributeDef attr = new AttributeDef("level", "NMTOKEN", null, false);
        attr.addEnumValue("a");
        attr.addEnumValue("a");
        assertEquals(1, attr.getEnumValues().size(), "duplicates must be ignored");
    }

    // ── Scenario 6: DomainDef ─────────────────────────────────────────

    @Test @Order(17)
    void domainDefFieldsRoundTrip() {
        DomainDef dd = new DomainDef();
        dd.setName("phxUI");
        dd.setNamespace("http://phoenix.example.com/ui");
        assertEquals("phxUI", dd.getName());
        assertEquals("http://phoenix.example.com/ui", dd.getNamespace());
        assertNotNull(dd.getId());
    }

    // ── Scenario 7: Relationship ──────────────────────────────────────

    @Test @Order(18)
    void relationshipDomainInclusion() {
        TopicType tt = new TopicType("myTask", "task");
        DomainDef dd = new DomainDef();
        dd.setName("phxUI");

        DitaModel m = new DitaModel("Rel-Test");
        m.addTopicType(tt);
        m.addDomain(dd);

        Relationship rel = new Relationship(RelationshipType.DOMAIN_INCLUSION, tt.getId(), dd.getId());
        m.addRelationship(rel);

        assertEquals(1, m.getRelationships().size());
        assertEquals(RelationshipType.DOMAIN_INCLUSION, m.getRelationships().get(0).getType());
    }

    @Test @Order(19)
    void duplicateRelationshipIgnored() {
        TopicType tt = new TopicType("t", "topic");
        DomainDef dd = new DomainDef();
        dd.setName("d");

        DitaModel m = new DitaModel("Dup");
        m.addTopicType(tt);
        m.addDomain(dd);

        Relationship r = new Relationship(RelationshipType.DOMAIN_INCLUSION, tt.getId(), dd.getId());
        m.addRelationship(r);
        m.addRelationship(r); // exact same object — should be deduplicated
        assertEquals(1, m.getRelationships().size(), "duplicate relationship must not be added");
    }

    // ── Scenario 8: Edge cases ────────────────────────────────────────

    @Test @Order(20)
    void topicTypeWithHyphenatedName() {
        TopicType tt = new TopicType("my-topic-type-2", "reference");
        assertEquals("my-topic-type-2", tt.getName());
        assertEquals("my-topic-type-2", tt.resolvedModule());
    }

    @Test @Order(21)
    void elementDefCardinalityUnbounded() {
        ElementDef el = new ElementDef("items");
        el.setCardinality("*");
        assertEquals("*", el.getCardinality());
        assertEquals("*", el.cardinalitySuffix());
    }

    @Test @Order(22)
    void removeTopicTypeAlsoCleansRelationships() {
        DitaModel m = new DitaModel("CleanUp");
        TopicType tt = new TopicType("toRemove", "topic");
        DomainDef dd = new DomainDef();
        dd.setName("d");
        m.addTopicType(tt);
        m.addDomain(dd);
        m.addRelationship(new Relationship(RelationshipType.DOMAIN_INCLUSION, tt.getId(), dd.getId()));

        m.removeTopicType(tt.getId());
        assertTrue(m.getTopicTypes().isEmpty());
        assertTrue(m.getRelationships().isEmpty(), "relationships to removed type must be cleaned up");
    }

    @Test @Order(23)
    void attributeIsAttributeDomain() {
        AttributeDef attr = new AttributeDef();
        attr.setName("audience");
        attr.setSpecializesFrom("props");
        assertTrue(attr.isAttributeDomain());

        attr.setSpecializesFrom("base");
        assertTrue(attr.isAttributeDomain());

        attr.setSpecializesFrom(null);
        assertFalse(attr.isAttributeDomain());
    }

    // ── Scenario 9: Full model structure ─────────────────────────────

    @Test @Order(24)
    void fullModelWithAllComponentTypes() {
        DitaModel m = new DitaModel("FullModel");
        m.setVersion("2.0");
        m.setCopyrightOwner("ACME Corp");

        TopicType tt = new TopicType("acmeTask", "task");
        tt.addElement(new ElementDef("acmeSteps"));
        tt.addElement(new ElementDef("acmeResult"));
        tt.addAttribute(new AttributeDef("id", "ID", null, true));
        tt.addAttribute(new AttributeDef("status", "CDATA", "draft", false));
        m.addTopicType(tt);

        DomainDef dd = new DomainDef();
        dd.setName("acmeUI");
        m.addDomain(dd);

        m.addRelationship(new Relationship(RelationshipType.DOMAIN_INCLUSION, tt.getId(), dd.getId()));

        assertEquals(1, m.getTopicTypes().size());
        assertEquals(1, m.getDomains().size());
        assertEquals(1, m.getRelationships().size());
        assertEquals(2, m.findTopicTypeByName("acmeTask").getElements().size());
        assertEquals(2, m.findTopicTypeByName("acmeTask").getAttributes().size());
    }
}
