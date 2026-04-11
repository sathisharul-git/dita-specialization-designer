# DITA Specialization Designer — Feature Checklist

> **Legend:** `[x]` = Implemented · `[ ]` = Pending  
> User story format: **As a** _role_ **I want** _capability_ **so that** _benefit_.  
> Updated automatically as features are implemented.

---

## A · Canvas & Visual Editing

| ID | User Story | Status |
|----|-----------|--------|
| F-001 | **As a** DITA architect **I want** to drag-and-drop TopicType nodes onto a canvas **so that** I can visually layout my specialization hierarchy. | [x] |
| F-002 | **As a** DITA architect **I want** to drag-and-drop Element nodes onto the canvas **so that** I can model element containment visually. | [x] |
| F-003 | **As a** DITA architect **I want** to drag-and-drop Domain nodes onto the canvas **so that** I can represent domain modules in my design. | [x] |
| F-004 | **As a** DITA architect **I want** to connect two nodes with an Inheritance arrow **so that** I can visualise specialization lineage. | [x] |
| F-005 | **As a** DITA architect **I want** to connect two nodes with a Containment arrow **so that** I can show element containment relationships. | [x] |
| F-006 | **As a** DITA architect **I want** to connect a Domain node to a TopicType with a Domain-Inclusion arrow **so that** I can declare domain dependencies. | [x] |
| F-007 | **As a** DITA architect **I want** to click a node to select it **so that** I can view and edit its properties in the right panel. | [x] |
| F-008 | **As a** DITA architect **I want** to press Delete/Backspace to remove a selected node **so that** I can quickly clean up the diagram without using menus. | [x] |
| F-009 | **As a** DITA architect **I want** to press Escape to deselect all and cancel connection mode **so that** I can reset my interaction state quickly. | [x] |
| F-010 | **As a** DITA architect **I want** to zoom the canvas in/out with Ctrl+Mouse Wheel **so that** I can work comfortably on large diagrams. | [x] |
| F-011 | **As a** DITA architect **I want** a "Fit to Screen" button that adjusts zoom so all nodes are visible **so that** I get an instant overview. | [x] |
| F-012 | **As a** DITA architect **I want** a subtle grid on the canvas background **so that** nodes appear aligned and the canvas has visual structure. | [x] |
| F-013 | **As a** DITA architect **I want** to Ctrl+Click multiple nodes to select them together **so that** I can move or delete a group at once. | [x] |
| F-014 | **As a** DITA architect **I want** a right-click context menu on nodes (Rename, Duplicate, Delete) **so that** I have fast access to node actions. | [x] |
| F-015 | **As a** DITA architect **I want** a right-click context menu on the empty canvas (Add Topic Type here, Add Element here) **so that** I can place nodes at a precise location. | [x] |
| F-016 | **As a** DITA architect **I want** to double-click a node title to rename it inline **so that** renaming is fast and stays in context. | [x] |
| F-017 | **As a** DITA architect **I want** to duplicate a node via context menu (Ctrl+D) **so that** I can re-use a design pattern quickly. | [x] |
| F-018 | **As a** DITA architect **I want** to press Ctrl+A to select all nodes **so that** I can move or delete everything at once. | [x] |
| F-019 | **As a** DITA architect **I want** to see the current zoom percentage in the status bar **so that** I always know the canvas scale. | [x] |
| F-020 | **As a** DITA architect **I want** to press Ctrl+0 to reset zoom to 100% **so that** I can return to a standard view instantly. | [x] |

---

## B · Undo / Redo

| ID | User Story | Status |
|----|-----------|--------|
| F-021 | **As a** DITA architect **I want** to undo the last action with Ctrl+Z **so that** I can recover from mistakes without reloading the project. | [x] |
| F-022 | **As a** DITA architect **I want** to redo with Ctrl+Y **so that** I can reapply an action I undid by mistake. | [x] |
| F-023 | **As a** DITA architect **I want** to see a list of recent undo-able actions in Edit → Undo History **so that** I can understand what will be undone. | [x] |
| F-024 | **As a** DITA architect **I want** the undo stack to be cleared when I open a new project **so that** I don't undo changes from a previous session. | [x] |
| F-025 | **As a** DITA architect **I want** Undo and Redo items in the Edit menu (with keyboard shortcuts shown) **so that** the feature is discoverable. | [x] |

---

## C · Topic Type Management

| ID | User Story | Status |
|----|-----------|--------|
| F-026 | **As a** DITA architect **I want** to create a TopicType by clicking "Topic Type" in the toolbox and clicking the canvas **so that** I can start defining a specialization. | [x] |
| F-027 | **As a** DITA architect **I want** to edit the TopicType name in the properties panel **so that** I can correct or refine the name without deleting and recreating it. | [x] |
| F-028 | **As a** DITA architect **I want** a ComboBox to change the base type (task, concept, reference…) **so that** I can update the specialization chain. | [x] |
| F-029 | **As a** DITA architect **I want** to set the namespace URI **so that** the generated XSD uses the correct target namespace. | [x] |
| F-030 | **As a** DITA architect **I want** to set the Public ID **so that** the generated DTD and catalog use a standards-compliant identifier. | [x] |
| F-031 | **As a** DITA architect **I want** to set the System ID **so that** the DTD shell references the correct file name. | [x] |
| F-032 | **As a** DITA architect **I want** to add a textual description to a TopicType **so that** the intent of the specialization is documented in the project. | [x] |
| F-033 | **As a** DITA architect **I want** the node header colour to change based on the base type (blue=task, green=concept, purple=reference…) **so that** I can identify types at a glance. | [x] |
| F-034 | **As a** DITA architect **I want** to see an element-count and attribute-count badge on each node **so that** I have a quick summary without expanding the node. | [x] |
| F-035 | **As a** DITA architect **I want** to collapse/expand a node to show or hide its element list **so that** I can reduce clutter on a large canvas. | [x] |
| F-036 | **As a** DITA architect **I want** to delete a TopicType with a confirmation dialog **so that** I don't accidentally remove work. | [x] |
| F-037 | **As a** DITA architect **I want** to duplicate a TopicType (including its elements and attributes) **so that** I can create a variant without starting from scratch. | [x] |
| F-038 | **As a** DITA architect **I want** to see a tooltip on a node that shows its description **so that** I get documentation context without opening the properties panel. | [x] |
| F-039 | **As a** DITA architect **I want** to see the generated DITA class attribute value in the properties panel **so that** I can verify the specialization chain. | [x] |
| F-040 | **As a** DITA architect **I want** a "Generate DTD for this type" button in the properties panel **so that** I can output a single module quickly. | [x] |

---

## D · Element Management

| ID | User Story | Status |
|----|-----------|--------|
| F-041 | **As a** DITA architect **I want** to add an element to a TopicType from the properties panel **so that** I can define the content model incrementally. | [x] |
| F-042 | **As a** DITA architect **I want** to edit an element name in the properties panel **so that** I can rename it without deleting it. | [x] |
| F-043 | **As a** DITA architect **I want** to set the element's content model string (e.g. `(#PCDATA|ph)*`) **so that** the generator produces the correct `<!ELEMENT>` declaration. | [x] |
| F-044 | **As a** DITA architect **I want** to choose element cardinality (1, ?, +, \*) from a ComboBox **so that** the content model and XSD minOccurs/maxOccurs are set correctly. | [x] |
| F-045 | **As a** DITA architect **I want** to mark an element as required **so that** the generator sets `minOccurs="1"` in XSD and omits `?` in DTD. | [x] |
| F-046 | **As a** DITA architect **I want** to delete an element from the properties panel **so that** I can remove elements that are no longer needed. | [x] |
| F-047 | **As a** DITA architect **I want** to reorder elements with Up/Down arrow buttons **so that** the generated content model reflects the intended order. | [x] |
| F-048 | **As a** DITA architect **I want** to see a read-only DTD fragment preview for each element in the properties panel **so that** I can validate the output before generating files. | [x] |
| F-049 | **As a** DITA architect **I want** a content-model builder dialog with checkboxes for common DITA inline elements **so that** I can assemble content models without memorising DTD syntax. | [x] |
| F-050 | **As a** DITA architect **I want** to place a standalone Element node on the canvas **so that** I can model shared elements that appear in multiple topic types. | [x] |
| F-051 | **As a** DITA architect **I want** to copy an element from one TopicType to another via drag **so that** I can reuse element definitions. | [ ] |
| F-052 | **As a** DITA architect **I want** to import element definitions from a selected base type's module **so that** I start with the correct inheritance point. | [ ] |

---

## E · Attribute Management

| ID | User Story | Status |
|----|-----------|--------|
| F-053 | **As a** DITA architect **I want** to add an attribute with a name and type from the properties panel **so that** I can define ATTLIST entries. | [x] |
| F-054 | **As a** DITA architect **I want** to choose the attribute type (CDATA, ID, IDREF, NMTOKEN, NMTOKENS, IDREFS) from a ComboBox **so that** the correct DTD type is generated. | [x] |
| F-055 | **As a** DITA architect **I want** to set a default attribute value **so that** the DTD ATTLIST has the correct default declaration. | [x] |
| F-056 | **As a** DITA architect **I want** to mark an attribute as required (`#REQUIRED`) **so that** the generated DTD and XSD enforce its presence. | [x] |
| F-057 | **As a** DITA architect **I want** to delete an attribute **so that** I can remove attributes that are no longer needed. | [x] |
| F-058 | **As a** DITA architect **I want** to add enumeration values to an attribute **so that** the DTD uses `(val1|val2|…)` and XSD uses `xs:enumeration`. | [x] |
| F-059 | **As a** DITA architect **I want** to set a fixed attribute value (`#FIXED "value"`) **so that** the DTD enforces a constant value. | [x] |
| F-060 | **As a** DITA architect **I want** to see a read-only DTD fragment for each attribute **so that** I can verify the output inline. | [x] |
| F-061 | **As a** DITA architect **I want** to reorder attributes with Up/Down buttons **so that** the ATTLIST declaration has a logical order. | [x] |
| F-062 | **As a** DITA architect **I want** a full attribute-type ComboBox including all standard DTD types **so that** I don't need to type them manually. | [x] |
| F-063 | **As a** DITA architect **I want** the `class` attribute to be auto-generated and shown as read-only **so that** the DITA specialization chain is always correct. | [x] |
| F-064 | **As a** DITA architect **I want** the `domains` attribute to be auto-generated **so that** domain declarations appear correctly in the DTD. | [x] |

---

## F · Domain Management

| ID | User Story | Status |
|----|-----------|--------|
| F-065 | **As a** DITA architect **I want** to create a Domain node with a name **so that** I can model a DITA domain module. | [x] |
| F-066 | **As a** DITA architect **I want** to set a domain description **so that** the purpose of the domain module is documented. | [x] |
| F-067 | **As a** DITA architect **I want** to set the domain's Public ID **so that** the catalog and DTD reference it correctly. | [x] |
| F-068 | **As a** DITA architect **I want** to draw a Domain-Inclusion arrow from a Domain to a TopicType **so that** the DTD shell includes the domain module. | [x] |
| F-069 | **As a** DITA architect **I want** to delete a domain with a confirmation dialog **so that** I don't lose domain work accidentally. | [x] |
| F-070 | **As a** DITA architect **I want** to rename a domain by double-clicking its node title **so that** renaming is quick and in-context. | [x] |
| F-071 | **As a** DITA architect **I want** to add elements to a domain from the properties panel **so that** I can define the domain vocabulary. | [x] |
| F-072 | **As a** DITA architect **I want** the domain node to show its element count **so that** I can see the vocabulary size at a glance. | [x] |

---

## G · Project Management

| ID | User Story | Status |
|----|-----------|--------|
| F-073 | **As a** DITA architect **I want** to create a new empty project (Ctrl+N) **so that** I can start a fresh specialization design. | [x] |
| F-074 | **As a** DITA architect **I want** to open a saved project file (.ddp, Ctrl+O) **so that** I can continue work across sessions. | [x] |
| F-075 | **As a** DITA architect **I want** to save the project (Ctrl+S) **so that** my work is persisted. | [x] |
| F-076 | **As a** DITA architect **I want** to save the project to a new file (Save As) **so that** I can create a copy or rename it. | [x] |
| F-077 | **As a** DITA architect **I want** to export all generated artefacts as a ZIP **so that** I can share or archive the full specialization package. | [x] |
| F-078 | **As a** DITA architect **I want** to see a "Recent Projects" submenu (last 5 files) **so that** I can reopen recent work without using the file browser. | [x] |
| F-079 | **As a** DITA architect **I want** a project metadata dialog (name, version, description) **so that** I can annotate the design with context. | [x] |
| F-080 | **As a** DITA architect **I want** auto-save every 5 minutes **so that** I don't lose work if the app crashes. | [x] |
| F-081 | **As a** DITA architect **I want** an asterisk (*) in the window title when there are unsaved changes **so that** I always know whether my work is persisted. | [x] |
| F-082 | **As a** DITA architect **I want** a "Save before exit?" prompt when I close the app with unsaved changes **so that** I don't lose work accidentally. | [x] |
| F-083 | **As a** DITA architect **I want** model validation to run on save and show warnings **so that** invalid models are flagged before I generate. | [x] |
| F-084 | **As a** DITA architect **I want** the previous save to be kept as a `.ddp.bak` backup **so that** I can recover the previous version if needed. | [x] |

---

## H · DITA Library Integration

| ID | User Story | Status |
|----|-----------|--------|
| F-085 | **As a** DITA architect **I want** to load DTD/XSD libraries from a local directory **so that** I can use existing DITA-OT installations as the base. | [x] |
| F-086 | **As a** DITA architect **I want** DITA 1.3 built-in base types pre-loaded (topic, task, concept, reference, map…) **so that** I can start without importing any files. | [x] |
| F-087 | **As a** DITA architect **I want** the base-type ComboBox to be populated from loaded libraries **so that** my choices reflect the actual available types. | [x] |
| F-088 | **As a** DITA architect **I want** a library browser panel showing all loaded elements per base type **so that** I can explore the base vocabulary. | [ ] |
| F-089 | **As a** DITA architect **I want** to search elements within the library browser **so that** I can find a specific DITA element quickly. | [ ] |
| F-090 | **As a** DITA architect **I want** to drag a base-type element from the browser onto the canvas **so that** I can start a specialization from an existing element. | [ ] |
| F-091 | **As a** DITA architect **I want** to see element documentation (from DTD comments) as a tooltip **so that** I understand what each base element does. | [ ] |
| F-092 | **As a** DITA architect **I want** a "Reload Libraries" button **so that** I can refresh without restarting the application. | [x] |

---

## I · DTD Generation

| ID | User Story | Status |
|----|-----------|--------|
| F-093 | **As a** DITA architect **I want** to generate a `.dtd` shell file per TopicType **so that** I have a standards-compliant shell DTD. | [x] |
| F-094 | **As a** DITA architect **I want** to generate a `.mod` module file per TopicType **so that** element and ATTLIST declarations are in a reusable module. | [x] |
| F-095 | **As a** DITA architect **I want** to generate a `.ent` entity file per TopicType **so that** public identifiers are declared as entities. | [x] |
| F-096 | **As a** DITA architect **I want** DITA `class` attributes included in all generated ATTLIST declarations **so that** the specialization is DITA-compliant. | [x] |
| F-097 | **As a** DITA architect **I want** a live DTD preview panel that updates as I edit the model **so that** I see the output without generating files. | [x] |
| F-098 | **As a** DITA architect **I want** a "Copy DTD to Clipboard" button **so that** I can paste the output into another editor immediately. | [x] |
| F-099 | **As a** DITA architect **I want** the generated DTD to be validated with the Xerces parser **so that** I know the output is syntactically correct. | [x] |
| F-100 | **As a** DITA architect **I want** to generate DTD for a single selected TopicType **so that** I can test one module at a time. | [x] |
| F-101 | **As a** DITA architect **I want** a configurable copyright/header comment at the top of generated files **so that** all outputs include the correct ownership notice. | [x] |
| F-102 | **As a** DITA architect **I want** a generation report listing element counts and any warnings **so that** I have a summary of what was produced. | [x] |

---

## J · XSD Generation

| ID | User Story | Status |
|----|-----------|--------|
| F-103 | **As a** DITA architect **I want** to generate a `.xsd` file with `xs:complexType` per TopicType **so that** I have a W3C Schema representation. | [x] |
| F-104 | **As a** DITA architect **I want** `xs:extension` from the base-type complex type **so that** the XSD correctly models the specialization hierarchy. | [x] |
| F-105 | **As a** DITA architect **I want** child elements in `xs:sequence` with correct min/maxOccurs **so that** the schema enforces the content model. | [x] |
| F-106 | **As a** DITA architect **I want** `xs:attribute` declarations with correct types and use="required" **so that** attribute constraints are encoded in the schema. | [x] |
| F-107 | **As a** DITA architect **I want** a live XSD preview panel **so that** I can inspect the schema without generating files. | [x] |
| F-108 | **As a** DITA architect **I want** a "Copy XSD to Clipboard" button **so that** I can paste it immediately into a schema editor. | [x] |
| F-109 | **As a** DITA architect **I want** the generated XSD to be validated with Xerces **so that** I know it is well-formed and schema-valid. | [x] |
| F-110 | **As a** DITA architect **I want** `xs:annotation`/`xs:documentation` generated from element and attribute descriptions **so that** the XSD is self-documented. | [x] |

---

## K · XML Catalog

| ID | User Story | Status |
|----|-----------|--------|
| F-111 | **As a** DITA architect **I want** to generate an OASIS XML Catalog V1.1 `catalog.xml` **so that** parsers can resolve my public identifiers. | [x] |
| F-112 | **As a** DITA architect **I want** `<public>` entries mapping public IDs to generated file URIs **so that** DTD references resolve locally. | [x] |
| F-113 | **As a** DITA architect **I want** `<system>` entries for system IDs **so that** both public and system resolution are covered. | [x] |
| F-114 | **As a** DITA architect **I want** the generated catalog to be validated against the OASIS catalog schema **so that** it is standards-compliant. | [ ] |
| F-115 | **As a** DITA architect **I want** to merge my generated catalog with an existing one **so that** I can extend a project-level catalog without replacing it. | [ ] |

---

## L · Validation & Quality

| ID | User Story | Status |
|----|-----------|--------|
| F-116 | **As a** DITA architect **I want** model validation that checks for missing names and base types **so that** invalid models are caught early. | [x] |
| F-117 | **As a** DITA architect **I want** validation warnings shown before generation **so that** I am informed of issues before output is produced. | [x] |
| F-118 | **As a** DITA architect **I want** duplicate-name detection (highlight nodes in red) **so that** naming conflicts are visually obvious. | [x] |
| F-119 | **As a** DITA architect **I want** circular inheritance detection **so that** invalid specialization chains are flagged immediately. | [x] |
| F-120 | **As a** DITA architect **I want** the generated DTD/XSD to be validated and results shown in the log **so that** I know the output is correct. | [x] |
| F-121 | **As a** DITA architect **I want** a validation panel listing all issues with "jump to node" links **so that** I can navigate directly to each problem. | [x] |
| F-122 | **As a** DITA architect **I want** invalid nodes to be shown with a red border **so that** errors are visually prominent on the canvas. | [x] |
| F-123 | **As a** DITA architect **I want** a warning when a required attribute has no default value **so that** I don't generate incomplete schemas. | [x] |
| F-124 | **As a** DITA architect **I want** orphaned element detection (elements not referenced in any content model) **so that** I can clean up unused definitions. | [x] |
| F-125 | **As a** DITA architect **I want** to export the validation report to a `.txt` file **so that** I can include it in a review package. | [x] |

---

## M · Search & Navigation

| ID | User Story | Status |
|----|-----------|--------|
| F-126 | **As a** DITA architect **I want** to search topic types by name (Ctrl+F) **so that** I can find a node on a large canvas instantly. | [x] |
| F-127 | **As a** DITA architect **I want** to filter the toolbox or project tree by keyword **so that** I can narrow down large projects. | [x] |
| F-128 | **As a** DITA architect **I want** to jump to a node by name using a quick-open dialog (Ctrl+G) **so that** navigation is keyboard-driven. | [x] |
| F-129 | **As a** DITA architect **I want** matched nodes to be highlighted on the canvas after a search **so that** results are visually obvious. | [x] |
| F-130 | **As a** DITA architect **I want** to search within the generated output preview **so that** I can locate a specific declaration quickly. | [ ] |

---

## N · UI & Preferences

| ID | User Story | Status |
|----|-----------|--------|
| F-131 | **As a** user **I want** a dark-themed toolbox panel **so that** the tool palette is visually distinct from the canvas. | [x] |
| F-132 | **As a** user **I want** a status bar that shows the current tool and last action **so that** I always know the application state. | [x] |
| F-133 | **As a** user **I want** a log/console panel at the bottom **so that** I can see operation history and any errors. | [x] |
| F-134 | **As a** user **I want** a "Clear Log" button **so that** I can declutter the console. | [x] |
| F-135 | **As a** user **I want** a Light/Dark theme toggle **so that** I can work in my preferred visual environment. | [x] |
| F-136 | **As a** user **I want** the zoom percentage shown in the status bar **so that** I know the canvas scale at all times. | [x] |
| F-137 | **As a** user **I want** to show/hide the log panel with a keyboard shortcut **so that** I can reclaim canvas space. | [x] |
| F-138 | **As a** user **I want** resizable panel dividers **so that** I can adjust the toolbox/canvas/properties proportions. | [x] |
| F-139 | **As a** user **I want** the window size and position remembered on restart **so that** I return to my preferred layout. | [x] |
| F-140 | **As a** user **I want** a Help → Keyboard Shortcuts dialog **so that** I can discover all available shortcuts. | [x] |

---

## O · Documentation & Export

| ID | User Story | Status |
|----|-----------|--------|
| F-141 | **As a** DITA architect **I want** an About dialog showing version, description, and technology stack **so that** I can identify the exact version in use. | [x] |
| F-142 | **As a** DITA architect **I want** to generate an HTML documentation page from the model **so that** I can share a human-readable overview of the specialization. | [x] |
| F-143 | **As a** DITA architect **I want** to print the canvas diagram **so that** I can include it in a design review document. | [ ] |
| F-144 | **As a** DITA architect **I want** to export the canvas as a PNG image **so that** I can embed the diagram in documentation. | [x] |
| F-145 | **As a** DITA architect **I want** to copy the diagram to the clipboard as an image **so that** I can paste it into any document quickly. | [x] |
| F-146 | **As a** DITA architect **I want** to export a model summary CSV (name, base type, element count, attribute count) **so that** I can analyse the design in a spreadsheet. | [x] |
| F-147 | **As a** user **I want** a keyboard shortcuts quick-reference dialog in the Help menu **so that** all shortcuts are discoverable without leaving the app. | [x] |

---

---

## P · Element & Attribute In-Place Editing

> **User-reported gap (2026-04-09):** Once an element or attribute is added to a topic type via the properties panel "+ New Element" / "+ Add Attribute" dialogs, there is no way to edit it — the only option is delete and re-add.

| ID | User Story | Status |
|----|-----------|--------|
| F-148 | **As a** DITA architect **I want** to click an "Edit" button next to any element in the TopicType properties panel **so that** I can open a full edit dialog to change its name, content model, cardinality, required flag, description, and attributes without deleting it. | [x] |
| F-149 | **As a** DITA architect **I want** to click an "Edit" button next to any attribute in the TopicType properties panel **so that** I can modify its name, type, default value, required flag, fixed value, and enum values in-place. | [x] |
| F-150 | **As a** DITA architect **I want** the element edit dialog to update the DTD fragment preview in real time as I type **so that** I can see the impact of my changes before confirming. | [x] |
| F-151 | **As a** DITA architect **I want** the attribute edit dialog to update the DTD fragment preview in real time **so that** I can verify the ATTLIST output before saving. | [x] |
| F-152 | **As a** DITA architect **I want** to double-click an element row in the properties panel to open the edit dialog **so that** editing is accessible without finding a small button. | [x] |
| F-153 | **As a** DITA architect **I want** element and attribute edits to be undoable (Ctrl+Z) **so that** accidental changes can be reverted. | [x] |

---

## Q · Bug Fixes (found during phxTask XSD review — 2026-04-09)

> These stories capture defects identified through code review and generating the phxTask sample specialization XSD and catalog.

| ID | User Story | Status |
|----|-----------|--------|
| F-154 | **As a** DITA architect **I want** Ctrl+Z to actually undo actions **so that** I can recover from mistakes. *(Bug: the undo stack is never populated — `undoStack.push()` is never called anywhere in the code, so Ctrl+Z always reports "Nothing to undo" even after changes.)* | [x] |
| F-155 | **As a** DITA architect **I want** the Recent Projects list to be restored when I reopen the application **so that** I can access yesterday's projects without browsing. *(Bug: `recentFiles` is in-memory only; not saved to Preferences across sessions.)* | [x] |
| F-156 | **As a** DITA architect **I want** clicking a Domain node to show domain-specific properties (description, public ID, element list) **so that** I can edit domain metadata without seeing irrelevant TopicType fields. *(Bug: domain nodes render a pseudo-TopicType, so the panel shows Base Type / Namespace / System ID / Module fields that have no meaning for domains.)* | [x] |
| F-157 | **As a** DITA architect **I want** the "Add Element" canvas tool to place a truly standalone element node, not silently attach it to the first topic type in the model **so that** canvas layout matches model intent. *(Bug: `addElementAtPosition` always calls `parent.addElement(elem)` on `getTopicTypes().get(0)`.)* | [x] |
| F-158 | **As a** DITA architect **I want** the generated XSD to not include a duplicate `id` attribute declaration **so that** the schema is valid W3C XML Schema. *(Bug: XsdGenerator hardcodes `<xs:attribute name="id"…/>` at line 95 AND also emits it again from `tt.getAttributes()` when `id` is explicitly defined — producing two declarations for the same attribute.)* | [x] |
| F-159 | **As a** DITA architect **I want** the generated XSD `xs:import` to reference the correct namespace for the base DITA type **so that** schema validators can resolve the import. *(Bug: `xs:import` always uses `namespace="http://dita.oasis-open.org/architecture/2005/"` — the DITA architecture prefix namespace — instead of the base type schema's own target namespace.)* | [x] |
| F-160 | **As a** DITA architect **I want** nested elements (e.g., `phxReqItem` inside `phxRequirements`) to not appear as siblings in the parent topic's `xs:sequence` **so that** the generated XSD correctly models the content hierarchy. *(Bug: XsdGenerator includes ALL elements in `tt.getElements()` flat in the sequence, regardless of whether they are referenced only by a sibling element's content model.)* | [x] |
| F-161 | **As a** DITA architect **I want** enumeration attributes created via the `(val1\|val2)` type syntax to produce `xs:restriction` with `xs:enumeration` values in the generated XSD **so that** schema validation enforces allowed values. *(Bug: if enum values are stored in the `type` field rather than the `enumValues` list — as in the `phx-severity` sample — the XSD generator emits `type="xs:string"` instead of a restriction.)* | [x] |
| F-162 | **As a** DITA architect **I want** the generated XSD to avoid duplicate global element declarations when two TopicTypes define elements with the same name **so that** the schema is valid. *(Bug: child elements are declared as global `xs:element`s — if two topic types both define an element named `title`, the XSD will contain two conflicting global declarations.)* | [x] |
| F-163 | **As a** DITA architect **I want** Ctrl+Y to trigger Redo from the keyboard **so that** I don't have to reach for the Edit menu. *(Bug: `handleGlobalKeyPress` maps Ctrl+Z to Undo but has no mapping for Ctrl+Y → Redo.)* | [x] |
| F-164 | **As a** DITA architect **I want** Recent Projects menu items to show the full file path as a tooltip **so that** I can distinguish two files with the same name. *(Bug: `Tooltip.install(item.getGraphic(), …)` passes `null` because `MenuItem.getGraphic()` returns null by default — tooltip is never installed.)* | [x] |
| F-165 | **As a** DITA architect **I want** the Validation Report export to list each orphaned-element warning exactly once **so that** the report is not confusing. *(Bug: `onExportValidationReport` previously called `transformer.validate()` — which already calls `detectOrphanedElements()` internally — then called `detectOrphanedElements()` again, doubling every orphan warning. Fixed in code; story tracks regression prevention.)* | [x] |
| F-166 | **As a** DITA architect **I want** the orphaned-element detector to not flag top-level elements with structural content models (e.g., `(phxReqItem+)`) as orphaned **so that** valid specializations don't generate false warnings. *(Bug: the original algorithm flagged any element not appearing in a sibling's content model, including root-level elements that are always part of the parent's generated content model. Fixed in code; story tracks regression prevention.)* | [x] |

---

## R · Project File Explorer & Live File Sync

> **User-requested feature (2026-04-09):** The project should be visible on the left as a file-tree panel (like VS Code). When a new topic type is created, its schema files should be generated immediately in the project folder. When a topic type is modified in the UI, the corresponding files should be updated automatically.

| ID | User Story | Status |
|----|-----------|--------|
| F-167 | **As a** DITA architect **I want** a collapsible left-side Project Explorer panel showing the project's file tree (`dtd/`, `xsd/`, `catalog.xml`, `.ddp`) **so that** I can see all generated artefacts without leaving the tool. | [x] |
| F-168 | **As a** DITA architect **I want** the file tree to refresh automatically whenever generation runs **so that** the explorer always reflects the current state of the output folder. | [x] |
| F-169 | **As a** DITA architect **I want** to click any file in the Project Explorer to open a read-only preview of its content in a viewer pane **so that** I can inspect generated artefacts inline. | [x] |
| F-170 | **As a** DITA architect **I want** a "Live Sync" toggle that automatically regenerates a topic type's `.xsd`, `.dtd`, `.mod`, and `.ent` files on every model change **so that** the output folder is always up-to-date without pressing Generate. | [x] |
| F-171 | **As a** DITA architect **I want** a new topic type's schema files (`.dtd`, `.mod`, `.ent`, `.xsd`) to be created in the project output folder as soon as I add the topic type to the canvas **so that** file-system state immediately matches the model. | [x] |
| F-172 | **As a** DITA architect **I want** the project output folder to be set once per project (in Project Metadata) so that Live Sync knows where to write files **so that** I don't need to choose a directory every time I generate. | [x] |
| F-173 | **As a** DITA architect **I want** renamed or deleted topic types to cause the corresponding old schema files to be renamed or removed from the project folder **so that** stale files don't accumulate. | [x] |
| F-174 | **As a** DITA architect **I want** `catalog.xml` to be regenerated automatically whenever the project output folder changes **so that** the catalog always reflects the current set of topic types and domains. | [x] |
| F-175 | **As a** DITA architect **I want** the Project Explorer to highlight files that are out-of-date (modified model but not yet regenerated) with a visual indicator **so that** I know when a file needs to be refreshed. | [x] |
| F-176 | **As a** DITA architect **I want** to right-click a file in the Project Explorer to copy its path or open its containing folder in the OS file manager **so that** I can hand it off to other tools quickly. | [x] |

---

## S · Namespace & Tooling Improvements

| ID | User Story | Status |
|----|-----------|--------|
| F-177 | **As a** DITA architect **I want** the tool to auto-suggest a namespace URI when I create a new TopicType, derived from the project name and type name **so that** I don't need to type namespaces by hand every time. | [x] |
| F-178 | **As a** DITA architect **I want** the VS Code-inspired dark sidebar, VS Code-blue status bar, and monospace console **so that** the tool feels as polished and professional as my IDE. | [x] |

---

## T · Project Startup, Import & Content Model (2026-04-10)

> Features added during the interactive refinement session.

| ID | User Story | Status |
|----|-----------|--------|
| F-179 | **As a** DITA architect **I want** a Welcome dialog on startup (Create New / Open Existing / Recent Projects) **so that** I never land on a blank Untitled canvas and can immediately choose my workflow. | [x] |
| F-180 | **As a** DITA architect **I want** the New Project wizard to collect name, output folder (with Browse button), namespace, version, copyright, and description **so that** the project is fully configured before I draw anything. | [x] |
| F-181 | **As a** DITA architect **I want** the Project Metadata dialog to have a Browse button for the Output Folder field **so that** I can point to a folder without typing a path. | [x] |
| F-182 | **As a** DITA architect **I want** clicking a schema file in the Project Explorer (e.g. `phxtask.xsd`) to select the matching topic type node on the canvas and populate the Properties panel **so that** file and model stay in sync. | [x] |
| F-183 | **As a** DITA architect **I want** to import an existing `.xsd` file (DITA → Import XSD…) and have its topic type, elements, and attributes appear on the canvas **so that** I can modify an existing specialization without rebuilding it from scratch. | [x] |
| F-184 | **As a** DITA architect **I want** to import an existing `.dtd` or `.mod` file (DITA → Import DTD…) and have its element and attribute declarations converted into a canvas node **so that** DTD-based specializations can be managed in the tool. | [x] |
| F-185 | **As a** DITA architect **I want** a visual Content Model Builder dialog (⚙ Build… button in Edit Element) with Sequence / Choice / Mixed / PCDATA / Empty modes, per-child cardinality, reordering, and a live preview **so that** I can define complex content models without writing DTD syntax by hand. | [x] |
| F-186 | **As a** DITA architect **I want** to add, edit, and remove attributes directly inside the Edit Element dialog **so that** element-level attributes are managed in one place without switching panels. | [x] |
| F-187 | **As a** DITA architect **I want** Live Sync to automatically resolve the output directory from the current project model on every change (not from a stale cached path) **so that** files are always written to the correct folder even after project metadata is updated. | [x] |

---

## U · XSLT Development Environment (2026-04-11)

> **User-requested module:** A fully integrated XSLT workbench with syntax-highlighted editor, Saxon HE transformation engine, and built-in DITA→HTML stylesheet.

| ID | User Story | Status |
|----|-----------|--------|
| F-188 | **As a** DITA architect **I want** an XSLT Workbench window (Ctrl+Shift+X) with a syntax-highlighted XSLT editor **so that** I can write and test XSLT stylesheets without switching tools. | [x] |
| F-189 | **As a** DITA architect **I want** a side-by-side XML source editor in the workbench **so that** I can write or paste input XML/DITA alongside my stylesheet. | [x] |
| F-190 | **As a** DITA architect **I want** to run an XSLT transformation and see the output in a result panel **so that** I can immediately test my stylesheet against real DITA content. | [x] |
| F-191 | **As a** DITA architect **I want** XSLT compile-time validation with line-number error reporting **so that** syntax errors in my stylesheet are flagged precisely. | [x] |
| F-192 | **As a** DITA architect **I want** to pass named parameters to the XSLT transformation **so that** I can test stylesheets that depend on external settings. | [x] |
| F-193 | **As a** DITA architect **I want** `xsl:message` output captured and displayed in the workbench console **so that** I can trace execution without opening a separate log. | [x] |
| F-194 | **As a** DITA architect **I want** to save XSLT transformation output to a file **so that** I can archive or hand off the generated HTML/XML. | [x] |
| F-195 | **As a** DITA architect **I want** a built-in DITA→HTML stylesheet template that I can load with one click **so that** I can transform any DITA topic to HTML without writing XSLT from scratch. | [x] |
| F-196 | **As a** DITA architect **I want** the workbench to be non-modal **so that** I can keep it open while continuing to edit the diagram. | [x] |
| F-197 | **As a** DITA architect **I want** DITA → DITA to HTML (XSLT)… to pre-load the workbench with a detected DITA file from the current project **so that** I can start transforming immediately. | [x] |
| F-198 | **As a** DITA architect **I want** syntax highlighting for XSLT keywords (`xsl:*`), XML tags, attributes, strings, XPath expressions, comments, and entities **so that** the stylesheet is easy to read. | [x] |
| F-199 | **As a** DITA architect **I want** to browse for XML and XSLT files from the workbench toolbar **so that** I can load existing files without typing paths. | [x] |

---

## V · Topic Type Import & Extend (2026-04-11)

> **User-requested feature:** When adding a Topic Type, offer an "Import & Extend" mode to browse an existing DITA XSD and place the parsed topic type on the canvas for modification.

| ID | User Story | Status |
|----|-----------|--------|
| F-200 | **As a** DITA architect **I want** the "Add Topic Type" dialog to offer a "Create New" / "Import & Extend existing XSD" radio button choice **so that** I can either design from scratch or start from an existing schema. | [x] |
| F-201 | **As a** DITA architect **I want** to browse for an existing `.xsd` file and have the tool parse and pre-fill the topic type name, base type, namespace, and element list **so that** I don't need to re-enter information already in the schema. | [x] |
| F-202 | **As a** DITA architect **I want** the imported topic type to appear on the canvas as a normal editable node **so that** I can add, modify, or remove elements and attributes using the standard properties panel. | [x] |

---

## W � XPath Checker Enhancements (2026-04-11)

> Enhancements requested for XPath workflow: expression syntax check and XML pretty print directly in the checker.

| ID | User Story | Status |
|----|-----------|--------|
| F-203 | **As a** DITA architect **I want** a "Check XPath" action in the XPath Checker **so that** I can validate expression syntax and namespace declarations before evaluating against XML. | [x] |
| F-204 | **As a** DITA architect **I want** a "Pretty Print XML" action in the XPath Checker **so that** I can normalize indentation and improve readability of input documents before querying. | [x] |
| F-205 | **As a** DITA architect **I want** XPath syntax check and XML pretty print outcomes shown in the results/status panel **so that** I get immediate feedback without opening additional dialogs. | [x] |

---
## X · XPath Smart Suggestions in XSLT Editor (2026-04-11)

> **Epic:** Intelligent XPath auto-completion inside the XSLT workbench editor, driven by the currently loaded XML file structure.

| ID | User Story | Status |
|----|-----------|--------|
| F-206 | **As a** XSLT developer **I want** to get XPath suggestions while typing inside `select`, `match`, or `test` attributes **so that** I can write correct XPath expressions quickly without memorizing the XML structure. | [x] |
| F-207 | **As a** developer **I want** XPath suggestions to be generated based on the currently loaded XML file **so that** I only see valid and relevant paths (full paths, relative paths, attributes). | [x] |
| F-208 | **As a** developer **I want** different suggestions based on where I type (`select`, `match`, `test`) **so that** I get context-relevant XPath expressions detected automatically. | [x] |
| F-209 | **As a** developer **I want** suggestions to be filtered dynamically as I type **so that** I can quickly find the correct XPath via partial input (e.g. `//ti` → `//title`). | [x] |
| F-210 | **As a** developer **I want** to see how many nodes match a suggested XPath **so that** I can verify correctness before using it, with an option to preview matching nodes. | [x] |
| F-211 | **As a** developer **I want** XPath suggestions to integrate with the editor's auto-completion system **so that** it behaves like a modern IDE (dropdown near cursor, keyboard navigation, Enter to insert). | [x] |
| F-212 | **As a** developer **I want** suggestions to appear within 200ms without UI freezing **so that** my coding experience remains smooth (XML parsed once and cached). | [x] |
| F-213 | **As a** developer **I want** the system to handle invalid XML or XPath gracefully **so that** I am not blocked while working (warning shown, editor remains usable). | [x] |

---

## Progress Summary

| Category | Total | Done | Remaining |
|----------|-------|------|-----------|
| A · Canvas & Visual Editing | 20 | 20 | 0 |
| B · Undo / Redo | 5 | 5 | 0 |
| C · Topic Type Management | 15 | 15 | 0 |
| D · Element Management | 12 | 10 | 2 |
| E · Attribute Management | 12 | 12 | 0 |
| F · Domain Management | 8 | 8 | 0 |
| G · Project Management | 12 | 12 | 0 |
| H · DITA Library Integration | 8 | 4 | 4 |
| I · DTD Generation | 10 | 10 | 0 |
| J · XSD Generation | 8 | 8 | 0 |
| K · XML Catalog | 5 | 3 | 2 |
| L · Validation & Quality | 10 | 10 | 0 |
| M · Search & Navigation | 5 | 4 | 1 |
| N · UI & Preferences | 10 | 10 | 0 |
| O · Documentation & Export | 7 | 7 | 0 |
| P · Element & Attribute In-Place Editing | 6 | 6 | 0 |
| Q · Bug Fixes | 13 | 13 | 0 |
| R · Project File Explorer & Live File Sync | 10 | 10 | 0 |
| S · Namespace & Tooling Improvements | 2 | 2 | 0 |
| T · Project Startup, Import & Content Model | 9 | 9 | 0 |
| U · XSLT Development Environment | 12 | 12 | 0 |
| V · Topic Type Import & Extend | 3 | 3 | 0 |
| W � XPath Checker Enhancements | 3 | 3 | 0 |
| X · XPath Smart Suggestions in XSLT Editor | 8 | 8 | 0 |
| **TOTAL** | **213** | **210** | **9** |

---

_Last updated: 2026-04-11 (X section: F-206 to F-213 XPath Smart Suggestions in XSLT Editor) — � U section: F-188 to F-199 (XSLT Workbench), V section: F-200 to F-202 (Import & Extend), W section: F-203 to F-205 (XPath syntax check + XML pretty print)._
