# Project: DITA Developer IDE

## Overview
This is a modular Java desktop application for DITA architects and developers.

The application provides tools for:
- DITA specialization (DTD, XSD, and catalog generation)
- XSLT development and execution
- XPath testing and validation
- XML editing and validation

The system should work offline and remain extensible.

---

## Architecture

The application uses modular packages with separation of concerns.

### Current Package Structure (actual repo)
- `model/` -> domain entities (topic types, elements, attributes, domains, relationships)
- `transformer/` -> model validation and DITA-specific computed values (`class`, helper derivations)
- `generator/` -> output generation (DTD, XSD, XML Catalog, HTML docs)
- `parser/` and `importer/` -> import existing XSD/DTD into editable model structures
- `repository/` -> project persistence (`.ddp` JSON)
- `xml/` -> XML parsing/validation/pretty-print services
- `xpath/` -> XPath checker UI + evaluation engine
- `xslt/` -> XSLT workbench UI + execution/validation
- `ui/` -> JavaFX controllers and canvas interactions
- `util/` and `service/` -> shared utilities and app services

---

## Modules

### 1. XML Core Module
- XML parsing and well-formedness checks
- Validation against XSD and DTD
- Namespace handling support
- XML pretty print utility

### 2. XSLT Module
- XSLT editor and workbench UI
- Transformation execution using Saxon HE
- Validation and error reporting
- Parameter handling

### 3. XPath Module
- XPath evaluation engine (Saxon HE)
- Namespace-aware expression execution
- XPath syntax check action
- XML-centric checker workflow (load, validate, evaluate)
- **XPath Smart Suggestions in XSLT Editor** (planned):
  - Trigger auto-completion inside `select`, `match`, and `test` attributes
  - Build XML structure model from loaded file; suggest full paths, relative paths, and attributes
  - Context-aware suggestion sets per attribute type (value paths, match patterns, boolean expressions)
  - Dynamic filtering as the user types (partial match, e.g. `//ti` → `//title`)
  - Match-count preview for each suggested expression
  - Dropdown integrated with editor keyboard navigation (↑ ↓ Enter)
  - XML parsed once and cached; suggestions delivered within 200 ms
  - Graceful degradation on invalid XML or XPath (warning shown, editor remains usable)

### 4. DITA Specialization Module
- Create and edit topic types, elements, attributes, and domains
- Generate DTD shell/module/entity files
- Generate XSD specialization schemas
- Generate OASIS XML Catalog (`catalog.xml`)
- Live preview and validation feedback during authoring
- Import and extend from existing XSD/DTD
- Live sync support to keep generated files aligned with model edits

### 5. DITA Processing Module
- Apply transformations on DITA content
- Generate HTML output (initial target)

---

## DITA Specialization Module (Detailed Notes)

### Primary Responsibilities
- Maintain a canonical in-memory specialization model (`DitaModel`)
- Enforce model correctness before generation
- Produce deterministic DITA artifacts from model state
- Keep generated outputs synchronized with UI edits when live sync is enabled

### Inputs
- User-authored model via canvas/properties panel
- Imported XSD/DTD files
- Project metadata (output folder, namespace, version info)

### Outputs
- `output/dtd/<topic>.dtd`
- `output/dtd/<topic>.mod`
- `output/dtd/<topic>.ent`
- `output/xsd/<topic>.xsd`
- `output/catalog.xml`
- optional project docs/preview artifacts

### Validation Rules (recommended baseline)
- Topic type names are required and unique
- Base type is required for specializations
- Circular inheritance must be rejected
- Duplicate element/attribute names in invalid contexts should be flagged
- Generated schemas must be parser-validated before success status

### Generation Expectations
- Stable file naming and predictable folder layout
- DITA-oriented attributes (`class`, `domains`) handled consistently
- Useful diagnostics for generation failures (line, file, reason)
- Regeneration should be idempotent for unchanged model state

### Integration Points
- `ui/MainController` for authoring workflow and live sync triggers
- `generator/*` for file production
- `transformer/ModelTransformer` for computed DITA metadata and checks
- `repository/ProjectRepository` for persistence and restore

### Current Scope vs Roadmap
Implemented and active:
- DITA generation (DTD/XSD/catalog)
- Import/extend workflows
- Live sync patterns and explorer integration

Planned or evolving:
- deeper lint rules for specialization quality
- richer conflict detection across multi-topic projects
- packaging profiles for target toolchains

---

## Technology Stack

- Java 17+
- Gradle
- JavaFX
- Saxon HE (XSLT and XPath)

---

## Coding Guidelines

- Keep UI and business logic separated
- Prefer clear module boundaries and low coupling
- Avoid hardcoded paths and environment assumptions
- Keep generation deterministic and testable
- Add focused comments only where behavior is non-obvious

---

## UI Layout

- Left: Project Explorer
- Center: Canvas / editors
- Right: Properties and contextual tooling
- Bottom: Output and logs

---

## Performance

- Avoid repeated full-model recomputation where incremental updates are possible
- Avoid re-parsing XML on every keystroke
- Keep JavaFX UI responsive by using background workers for heavy operations

---

## Future Extensions

- AI-assisted XML/XSLT generation
- Code indexing/search for large DITA repos
- Plugin extension system

---

## Goal

Provide a lightweight, developer-friendly alternative to heavyweight XML tooling with strong DITA specialization support, fast feedback, and practical day-to-day usability.
