package com.ditadesigner.schema.outline;

import com.ditadesigner.model.AttributeDef;
import com.ditadesigner.model.ElementDef;
import com.ditadesigner.model.TopicType;

/** Sealed hierarchy for tree nodes in the Schema Outline panel. */
public sealed interface SchemaOutlineItem
        permits SchemaOutlineItem.TopicTypeItem,
                SchemaOutlineItem.ElementItem,
                SchemaOutlineItem.AttributeItem {

    record TopicTypeItem(TopicType topicType) implements SchemaOutlineItem {}
    record ElementItem(ElementDef element, TopicType owner) implements SchemaOutlineItem {}
    record AttributeItem(AttributeDef attribute, TopicType owner) implements SchemaOutlineItem {}

    /** Display label used in cells and tooltips. */
    default String label() {
        if (this instanceof TopicTypeItem t)  return t.topicType().getName();
        if (this instanceof ElementItem e)    return e.element().getName() + " " + e.element().getCardinality();
        if (this instanceof AttributeItem a)  return a.attribute().getName() + " : " + a.attribute().getType();
        return toString();
    }
}
