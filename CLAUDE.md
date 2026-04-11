# CLAUDE.md — DITA Specialization Designer

Project guidance for Claude Code when working in this repository.

---

## Project Identity

A modular Java desktop application (JavaFX + Saxon HE) for DITA architects and XML/XSLT developers.
Key modules: DITA specialization design, XSLT workbench, XPath checker, XML editing/validation.

See `AGENTS.md` for full architecture overview.
See `FEATURES.md` for all implemented and planned user stories (sections A–X).

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

## Active Development: XPath Smart Suggestions (section X, F-206–F-213)

Eight user stories for XPath auto-completion inside the XSLT workbench editor:

| ID | Summary | Status |
|----|---------|--------|
| F-206 | Suggestions in `select` / `match` / `test` attributes | [ ] |
| F-207 | XML-derived suggestions (full paths, relative, attributes) | [ ] |
| F-208 | Context-aware sets per attribute type | [ ] |
| F-209 | Dynamic filtering as user types | [ ] |
| F-210 | Match-count preview per suggestion | [ ] |
| F-211 | Dropdown auto-completion with keyboard navigation | [ ] |
| F-212 | XML parsed once and cached; suggestions within 200 ms | [ ] |
| F-213 | Graceful handling of invalid XML / XPath | [ ] |

Design notes:
- Parse the loaded XML into a structure model (element paths + attribute names) once on load/change.
- The XSLT editor caret position determines which attribute is active (detect via text scan or token model).
- Suggestion context: `select` → value paths; `match` → template match patterns; `test` → boolean-friendly forms.
- Deliver completions via a JavaFX `ContextMenu` or `ListView` popup anchored near the caret.
- Off-load XML parsing and XPath evaluation to a background thread; post results back on the FX thread.

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
