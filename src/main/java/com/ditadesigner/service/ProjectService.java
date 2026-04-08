package com.ditadesigner.service;

import com.ditadesigner.model.*;
import com.ditadesigner.util.LogService;

import java.util.*;

/**
 * High-level project operations: create, manipulate, validate models.
 */
public class ProjectService {

    private final LogService log = LogService.getInstance();

    // ── factory methods ───────────────────────────────────────────────

    public DitaModel createNew(String name) {
        DitaModel model = new DitaModel(name);
        log.log("Created new project: " + name);
        return model;
    }

    // ── topic type operations ─────────────────────────────────────────

    public TopicType createTopicType(DitaModel model, String name, String baseType) {
        TopicType tt = new TopicType(name, baseType);
        tt.setModule(name.toLowerCase());
        model.addTopicType(tt);
        log.log("Added TopicType: " + name + " (base: " + baseType + ")");
        return tt;
    }

    public void deleteTopicType(DitaModel model, String id) {
        TopicType tt = model.findTopicTypeById(id);
        if (tt != null) {
            model.removeTopicType(id);
            log.log("Deleted TopicType: " + tt.getName());
        }
    }

    // ── element operations ────────────────────────────────────────────

    public ElementDef createElement(TopicType parent, String name, String contentModel, String cardinality) {
        ElementDef elem = new ElementDef(name, contentModel, cardinality);
        parent.addElement(elem);
        log.log("Added Element: " + name + " to " + parent.getName());
        return elem;
    }

    // ── attribute operations ──────────────────────────────────────────

    public AttributeDef createAttribute(String name, String type, boolean required) {
        AttributeDef attr = new AttributeDef(name, type);
        attr.setRequired(required);
        return attr;
    }

    // ── domain operations ─────────────────────────────────────────────

    public DomainDef createDomain(DitaModel model, String name) {
        DomainDef domain = new DomainDef(name);
        model.addDomain(domain);
        log.log("Added Domain: " + name);
        return domain;
    }

    public void deleteDomain(DitaModel model, String id) {
        DomainDef dd = model.findDomainById(id);
        if (dd != null) {
            model.removeDomain(id);
            log.log("Deleted Domain: " + dd.getName());
        }
    }

    // ── relationship operations ───────────────────────────────────────

    public Relationship createRelationship(DitaModel model, RelationshipType type,
                                           String sourceId, String targetId) {
        Relationship rel = new Relationship(type, sourceId, targetId);
        model.addRelationship(rel);
        log.log("Added relationship: " + type.getLabel()
                + " (" + sourceId + " → " + targetId + ")");
        return rel;
    }

    // ── sample specialization ─────────────────────────────────────────

    /**
     * Builds a demo "phxTask" specialization based on DITA task.
     */
    public DitaModel createSamplePhxTask() {
        DitaModel model = createNew("phxTask Sample");
        model.setDescription("Sample DITA specialization: phxTask extends task");
        model.setVersion("1.0");

        TopicType phxTask = createTopicType(model, "phxTask", "task");
        phxTask.setPublicId("-//PHX//ELEMENTS DITA phxTask//EN");
        phxTask.setSystemId("phxTask.dtd");
        phxTask.setDescription("Phoenix task specialization for engineering procedures.");
        phxTask.setX(120);
        phxTask.setY(80);

        // Specialized elements
        ElementDef phxRequirements = createElement(phxTask, "phxRequirements",
                "(phxReqItem+)", "?");
        phxRequirements.setDescription("Prerequisites specific to Phoenix tasks.");
        phxRequirements.setX(60);
        phxRequirements.setY(260);

        ElementDef phxReqItem = createElement(phxTask, "phxReqItem",
                "(#PCDATA|ph|b|i)*", "*");
        phxReqItem.setDescription("A single prerequisite item.");
        phxReqItem.setX(60);
        phxReqItem.setY(380);

        ElementDef phxSteps = createElement(phxTask, "phxSteps",
                "(phxStep+)", "1");
        phxSteps.setRequired(true);
        phxSteps.setDescription("Ordered list of Phoenix steps.");
        phxSteps.setX(300);
        phxSteps.setY(260);

        ElementDef phxStep = createElement(phxTask, "phxStep",
                "(cmd, info?, substeps?)", "1");
        phxStep.setRequired(true);
        phxStep.setDescription("A single Phoenix step.");
        phxStep.setX(300);
        phxStep.setY(380);

        ElementDef phxResult = createElement(phxTask, "phxResult",
                "(#PCDATA|ph)*", "?");
        phxResult.setDescription("Expected result of the Phoenix task.");
        phxResult.setX(540);
        phxResult.setY(260);

        // Attributes
        phxTask.addAttribute(createAttribute("id", "ID", true));
        phxTask.addAttribute(createAttribute("phx-severity",
                "(critical|high|medium|low)", false));
        phxTask.addAttribute(createAttribute("phx-component", "CDATA", false));

        // Domain
        DomainDef phxDomain = createDomain(model, "phxUI");
        phxDomain.setDescription("Phoenix UI interaction domain.");
        phxDomain.setPublicId("-//PHX//ELEMENTS DITA phxUI Domain//EN");
        phxDomain.setX(560);
        phxDomain.setY(80);

        ElementDef phxUiControl = new ElementDef("phxUiControl", "(#PCDATA)*", "?");
        phxDomain.addElement(phxUiControl);

        // Relationships
        createRelationship(model, RelationshipType.DOMAIN_INCLUSION,
                phxDomain.getId(), phxTask.getId());

        log.logSuccess("Sample 'phxTask' specialization created.");
        return model;
    }
}
