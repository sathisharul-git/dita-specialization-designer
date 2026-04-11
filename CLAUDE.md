# CLAUDE.md — DITA Specialization Designer

Project guidance for Claude Code when working in this repository.

---

## Project Identity

A modular Java desktop application (JavaFX + Saxon HE) for DITA architects and XML/XSLT developers.
Key modules: DITA specialization design, XSLT workbench, XPath checker, XML editing/validation.

See `AGENTS.md` for full architecture overview.
See `FEATURES.md` for all implemented and planned user stories (sections A–Y).

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Java 17+ |
| Build | Gradle |
| UI | JavaFX |
| XSLT / XPath engine | Saxon HE |
| Persistence | `.ddp` JSON via `repository/ProjectRepository` |

---

## Package Conventions

```
model/          domain entities (topic types, elements, attributes, domains)
transformer/    model validation + DITA-specific computed values (class, etc.)
generator/      output generation (DTD, XSD, catalog, HTML docs)
parser/         XML parsing and validation services
importer/       import existing XSD/DTD into editable model structures
repository/     project persistence
xml/            XML parsing / validation / pretty-print
xpath/          XPath checker UI + evaluation engine
xslt/           XSLT workbench UI + execution
ui/             JavaFX controllers and canvas
util/ service/  shared utilities and app-level services
```

- UI and business logic must remain separated.
- Keep module boundaries clear and coupling low.
- No hardcoded paths or environment assumptions.
- Generation must be deterministic and testable.

---

## Coding Rules

- Add comments only where behavior is non-obvious.
- Do not add docstrings, error handling, or fallbacks beyond what the task requires.
- Do not create new helpers or abstractions for one-time operations.
- Validate only at system boundaries (user input, file I/O).
- Keep JavaFX UI responsive: use background workers (`Task`, `Service`) for heavy operations — never block the FX Application Thread.
- Re-parse XML once and cache; do not re-parse on every keystroke.

---

## Completed: XPath Smart Suggestions (section X, F-206–F-213) ✓

All eight user stories implemented. Key components:
- `XmlStructureModel` — JAXP DOM walker; builds select/match/test suggestion banks
- `XPathSuggestionService` — background XML parsing, Saxon match-count evaluation
- `XPathSuggestionPopup` — JavaFX Popup with ListView, keyboard nav, async match label

## Completed: XSL IDE Smart Completion (section Y, F-214–F-223) ✓

Ten user stories implemented. Key components:
- `XslKnowledgeBase` — 30 XSL 2.0 elements with typed attributes and ready-to-use snippets
- `XslVariableScanner` — regex scan of XSLT source to discover `$var`, `$param`, and named-template names
- `XPathBuilderDialog` — visual axis / node-test / predicate composer dialog (Ctrl+Shift+P)
- `XsltUIController` — 4-priority completion dispatch: `$var` → `<xsl:` → `call-template name` → XPath attribute

## No Active Epic

No epic is currently in progress. See `FEATURES.md` for remaining items (sections D, H, K, M).

---

## UI Layout

```
Left    Project Explorer
Center  Canvas / editors
Right   Properties and contextual tooling
Bottom  Output and logs
```

---

## Performance Rules

- Avoid repeated full-model recomputation; use incremental updates.
- Never re-parse XML on every keystroke — cache the parsed model.
- Keep the FX Application Thread unblocked; use `Task` / `Service` for I/O and evaluation.

---

## Feature Tracking

All features are tracked in `FEATURES.md` using the format:

```
| F-NNN | User story | [x] / [ ] |
```

Update the Progress Summary table and `_Last updated_` footer when adding new sections.
