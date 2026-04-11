# Agent & Sub-Agent Guide — DITA Specialization Designer

This guide explains the Claude Code agent system for this project: one **Senior Solution Architect** orchestrator and six **Module Developer** sub-agents, one per major module.

---

## Agent Overview

| Agent file | Role | When to use |
|---|---|---|
| `solution-architect.md` | Orchestrator / architect | New epics, cross-module decisions, interface design, feature planning |
| `xml-core-dev.md` | XML Core developer | `xml/` package: parsing, validation, pretty-print, Saxon Processor |
| `xslt-dev.md` | XSLT Workbench developer | `xslt/` package: workbench editor, transformation, XPath suggestions |
| `xpath-dev.md` | XPath Checker developer | `xpath/` package: XPath evaluation, checker UI, syntax validation |
| `dita-spec-dev.md` | DITA Spec core developer | `model/`, `transformer/`, `generator/`, `parser/`, `importer/`, `repository/` |
| `ui-dev.md` | UI / Canvas developer | `ui/` package: canvas, properties panel, dialogs, keyboard shortcuts |
| `generator-dev.md` | Output generator developer | `generator/` package: DTD, XSD, catalog, HTML doc generation |

All agent files live in `.claude/agents/`.

---

## How to Invoke an Agent

### From the Claude Code CLI

```bash
# Start a task with the solution architect (orchestrator)
claude --agent solution-architect "Plan the implementation of section Y: DITA Relationship Tables"

# Invoke a module sub-agent directly for a scoped task
claude --agent xslt-dev "Add a Format menu to the XSLT Workbench that wraps xsl:output options"
claude --agent xpath-dev "Add namespace prefix auto-detection from the loaded XML"
claude --agent generator-dev "Make CatalogGenerator skip write when output file is already byte-identical"
```

### From within a Claude Code session

Use the Agent tool with `subagent_type` pointing to the agent name, or use the `/agent` slash command:

```
/agent solution-architect Plan section Y: DITA Relationship Tables
/agent xslt-dev Add keyboard shortcut Ctrl+Space to force-trigger suggestions
/agent ui-dev Add a dark-mode toggle to the Preferences dialog
```

---

## Recommended Workflow

### 1. Start with the Solution Architect for new features

Before writing any code for a significant new feature, engage the **solution-architect** agent:

```
/agent solution-architect
I want to add DITA Relationship Table authoring (reltable, relrow, relcell).
Analyse the impact on model/, generator/, and ui/ packages.
Define the interfaces and produce an implementation plan.
```

The architect will:
- Identify which modules are affected
- Define data contracts and service interfaces
- Assign work to specific sub-agents with precise prompts
- Update FEATURES.md with the new section

### 2. Use module sub-agents for scoped implementation

Once the architect has defined the scope, engage the relevant sub-agent:

```
/agent dita-spec-dev
Add RelationshipTable, RelRow, and RelCell to model/.
Follow the interface defined by the solution architect: [paste contract here].
```

### 3. Return to the architect for integration review

After module work is done:

```
/agent solution-architect
The dita-spec-dev and generator-dev sub-agents have completed their work.
Review the integration points and confirm the interfaces align.
```

---

## Agent Specialisation Details

### solution-architect

**Triggers:** new epic, cross-module design question, architectural trade-off, performance budget concern, interface contract definition.

**Does NOT:** write module implementation code. It defines what to build and delegates.

**Outputs:** implementation plans, interface contracts, FEATURES.md updates, coordination prompts for sub-agents.

---

### xml-core-dev

**Owns:** `XmlCoreService`, `XmlParseResult`.

**Key rule:** Never throw from public API — return result objects. `parse()` is not thread-safe for concurrent calls on the same instance.

**Example tasks:**
- Add streaming XML parsing for large files
- Expose a `lint()` method for XML best-practice checks
- Add namespace-aware pretty-print

---

### xslt-dev

**Owns:** `XsltEditor`, `XsltUIController`, `XsltExecutionService`, `XsltValidationService`, `XmlStructureModel`, `XPathSuggestionService`, `XPathSuggestionPopup`.

**Key rule:** Transformation, validation, XML model building, and XPath evaluation always on the background `executor`. Suggestion context detection and list filtering run on the FX thread (fast string ops).

**Example tasks:**
- Tune suggestion popup position for HiDPI screens
- Add XSLT parameter editor dialog
- Extend `XmlStructureModel` to suggest namespace prefixes

---

### xpath-dev

**Owns:** `XPathEvaluationService`, `XPathUIController`, `XPathModule`.

**Key rule:** Always use `XPathModule.getXmlCoreService()` — do not create a second Saxon `Processor`.

**Example tasks:**
- Add result-node highlighting in the XML editor
- Export XPath evaluation results to CSV
- Add XPath 3.1 map/array expression support

---

### dita-spec-dev

**Owns:** `model/`, `transformer/`, `parser/`, `importer/`, `repository/`.

**Key rule:** `ModelTransformer` must reject circular inheritance. Generation is deterministic. `.ddp` JSON must survive round-trip.

**Example tasks:**
- Add DITA 2.0 base types to the base-type ComboBox
- Implement relationship table model entities
- Add DTD import for OASIS DITA 1.3 specializations

---

### ui-dev

**Owns:** `ui/` (canvas, properties panel, dialogs, explorer).

**Key rule:** Never block the FX Application Thread. All model mutations go through the undo stack.

**Example tasks:**
- Add an alignment toolbar (align left/right/top/bottom)
- Implement node minimap for large diagrams
- Add theme switching (light/dark)

---

### generator-dev

**Owns:** `generator/` (DTD, XSD, catalog, HTML doc).

**Key rule:** Output must be deterministic and idempotent. Validate generated files before reporting success.

**Example tasks:**
- Add RNG (RELAX NG) output format
- Make catalog entries use relative paths
- Add a generation dry-run mode (report what would change without writing)

---

## Adding a New Sub-Agent

To add a sub-agent for a new module:

1. Create `.claude/agents/<module-name>.md`
2. Add frontmatter with `name` and `description`
3. Document: package ownership, key contracts, threading rules, design constraints
4. Add an entry to the table at the top of this guide
5. Update `AGENTS.md` with the new module section

---

## File Locations

```
.claude/agents/
  solution-architect.md   — orchestrator
  xml-core-dev.md         — xml/ package
  xslt-dev.md             — xslt/ package
  xpath-dev.md            — xpath/ package
  dita-spec-dev.md        — model/ transformer/ generator/ parser/ importer/ repository/
  ui-dev.md               — ui/ package
  generator-dev.md        — generator/ package (output formats)

AGENTS.md                 — project architecture reference (for all agents)
CLAUDE.md                 — project coding rules and active epic status
FEATURES.md               — complete feature checklist (F-001 to F-213+)
AGENTS_GUIDE.md           — this file
```
