# DITA Specialization Designer

A production-quality **JavaFX + Gradle** desktop application for visually designing DITA
specializations and generating schema artefacts — DTD, XSD, and XML Catalog.

---

## Table of Contents

1. [Features](#features)
2. [Requirements](#requirements)
3. [Quick Start](#quick-start)
4. [Build Tasks Reference](#build-tasks-reference)
5. [Testing](#testing)
6. [Packaging & Deployment](#packaging--deployment)
7. [Project Structure](#project-structure)
8. [Application Usage](#application-usage)
9. [Generated Output](#generated-output)
10. [CI/CD](#cicd)
11. [Configuration Reference](#configuration-reference)

---

## Features

| Feature | Detail |
|---|---|
| Visual canvas | Drag-and-drop TopicType, Element, Domain nodes |
| Connection lines | Inheritance · Containment · Domain inclusion (colour-coded arrows) |
| Properties panel | Live-edit name, base type, namespace, public ID, elements, attributes |
| OASIS loader | Import existing DITA DTD/XSD libraries from a local directory |
| DTD generation | `.dtd` shell, `.mod` module, `.ent` entity files per topic type |
| XSD generation | `xs:complexType` with `xs:extension` from base type |
| XML Catalog | OASIS Catalog V1.1 mapping public/system IDs to generated files |
| JSON persistence | Save/load project as `.ddp` file |
| ZIP export | Bundle all generated artefacts as a ZIP from the UI |
| Sample specialization | Built-in `phxTask` (extends DITA task) with elements, attributes, domain |
| Project Explorer | Live file-tree panel with click-to-preview and Live Sync toggle |
| Import XSD / DTD | Parse an existing schema file and create a canvas node automatically |
| XPath Checker | Dedicated XML/XPath window with evaluate, syntax-check, XSD validation, and XML pretty print (Ctrl+Shift+P) |
| XSLT Workbench | Syntax-highlighted XSLT/XML editor with Saxon HE transform engine (Ctrl+Shift+X) |
| DITA → HTML | Built-in XSLT 2.0 stylesheet for converting DITA topics to styled HTML |

---

## Requirements

| Tool | Version | Notes |
|---|---|---|
| Java JDK | 17 or 21 | OpenJDK / Eclipse Temurin recommended |
| Gradle | 8.x | Wrapper (`gradlew`) included — no local install needed |
| Internet | — | Required on first build to download dependencies |
| Display | — | Required to **run** the GUI; not needed for build/test |

---

## Quick Start

```bash
# 1 — Clone or unzip the project
cd dita-specialization-designer

# 2 — Run the application (builds automatically)
./gradlew run          # Linux / macOS
gradlew.bat run        # Windows

# 3 — Run all tests
./gradlew test

# 4 — Full CI build (compile → test → jar → fat-jar → zip → javadoc)
./gradlew ci
```

The application window opens maximised.

### Windows convenience scripts

```
scripts\run.bat       — build and launch in dev mode
scripts\test.bat      — run tests and open HTML report
scripts\deploy.bat    — full build + install to deploy\
```

---

## Build Tasks Reference

Run any task with `./gradlew <task>` (or `gradlew.bat <task>` on Windows).

### Core tasks

| Task | Description |
|---|---|
| `compileJava` | Compile main sources |
| `compileTestJava` | Compile test sources |
| `processResources` | Copy FXML / CSS resources to build output |
| `classes` | All of the above |
| `run` | Launch the JavaFX application |

### Test tasks

| Task | Description |
|---|---|
| `test` | Run all JUnit 5 tests; produces HTML + XML reports |
| `jacocoTestReport` | Generate code-coverage HTML + XML (auto-run after `test`) |
| `jacocoTestCoverageVerification` | Fail if line coverage drops below 50 % |

### Package tasks

| Task | Output | Description |
|---|---|---|
| `jar` | `build/libs/*.jar` | Thin JAR with manifest classpath |
| `shadowJar` / `package` | `build/libs/*-all.jar` | Fat/uber JAR — all dependencies bundled |
| `distZip` | `build/distributions/*.zip` | Distribution ZIP with launcher scripts |
| `distTar` | `build/distributions/*.tar` | Distribution TAR |
| `installDist` | `build/install/…` | Unpacked distribution (used by `deploy`) |
| `javadocJar` | `build/libs/*-javadoc.jar` | Javadoc JAR |
| `sourcesJar` | `build/libs/*-sources.jar` | Sources JAR |

### Custom tasks

| Task | Group | Description |
|---|---|---|
| `ci` | build | Full pipeline: clean → compile → test → coverage → jar → fat-jar → distZip → distTar → javadoc |
| `deploy` | deployment | Runs `installDist` then copies result to `deploy/` directory |
| `package` | build | Alias for `shadowJar`; prints JAR size |
| `printVersion` | help | Prints `1.0.0` |

### Assemble / check / build

| Task | Description |
|---|---|
| `assemble` | Builds all artefacts (thin JAR + fat JAR) |
| `check` | Runs tests + coverage check |
| `build` | `assemble` + `check` |
| `clean` | Deletes `build/` directory |

---

## Testing

```bash
# Run tests with full output
./gradlew test

# Force rerun (skip UP-TO-DATE check)
./gradlew test --rerun-tasks

# Run tests + generate coverage report
./gradlew test jacocoTestReport

# Verify coverage threshold (≥ 50 %)
./gradlew jacocoTestCoverageVerification
```

### Test reports

| Report | Location |
|---|---|
| HTML (human-readable) | `build/reports/tests/test/index.html` |
| JUnit XML (CI) | `build/test-results/test/TEST-*.xml` |
| JaCoCo HTML coverage | `build/reports/jacoco/test/html/index.html` |
| JaCoCo XML coverage | `build/reports/jacoco/test/jacocoTestReport.xml` |

### Test suite (24 tests — GeneratorTest)

| # | Test | Validates |
|---|---|---|
| 1–6 | Model tests | TopicType, elements, attributes, domains, relationships |
| 7–9 | Transformer tests | Validation, class attribute builder, empty model |
| 10–13 | DTD generator | File existence, ELEMENT decl, ENTITY decl |
| 14–17 | XSD generator | xs:complexType, xs:extension, element declaration |
| 18–20 | Catalog generator | File existence, `<public>` entries |
| 21–22 | JSON round-trip | Save/load, element count preserved |
| 23–24 | AttributeDef | DTD fragment for required/default attributes |

---

## Packaging & Deployment

### Option A — Fat JAR (simplest)

```bash
./gradlew shadowJar
# Output: build/libs/dita-specialization-designer-1.0.0-all.jar

# Run (requires JDK 17+ with JavaFX modules on the module path)
java -jar build/libs/dita-specialization-designer-1.0.0-all.jar
```

> **Note:** JavaFX native libraries are platform-specific. The fat JAR works only
> on the same OS/arch that it was built on. Use the distribution ZIP for
> cross-platform delivery.

### Option B — Distribution ZIP (recommended)

```bash
./gradlew distZip
# Output: build/distributions/dita-specialization-designer-1.0.0.zip
```

Unzip and run:

```
dita-specialization-designer-1.0.0/
├── bin/
│   ├── dita-specialization-designer       ← Unix launcher
│   └── dita-specialization-designer.bat   ← Windows launcher
└── lib/
    └── *.jar   ← application + all dependencies
```

### Option C — Local deploy/ directory

```bash
./gradlew deploy
# Installs to deploy/ in the project root

deploy/bin/dita-specialization-designer        # Linux / macOS
deploy\bin\dita-specialization-designer.bat    # Windows
```

Or use the convenience script:

```bat
scripts\deploy.bat           (Windows)
./scripts/deploy.sh          (Linux / macOS)
```

### Full CI build (all artefacts at once)

```bash
./gradlew ci
```

Produces:

```
build/
├── libs/
│   ├── dita-specialization-designer-1.0.0.jar          ← thin JAR
│   ├── dita-specialization-designer-1.0.0-all.jar      ← fat JAR
│   ├── dita-specialization-designer-1.0.0-javadoc.jar
│   └── dita-specialization-designer-1.0.0-sources.jar
├── distributions/
│   ├── dita-specialization-designer-1.0.0.zip
│   └── dita-specialization-designer-1.0.0.tar
├── docs/javadoc/index.html
├── reports/
│   ├── tests/test/index.html                           ← test report
│   └── jacoco/test/html/index.html                     ← coverage report
└── install/dita-specialization-designer/               ← expanded distribution
```

---

## Project Structure

```
dita-specialization-designer/
├── build.gradle                         ← Full build config (see §4)
├── settings.gradle                      ← Project name
├── gradlew / gradlew.bat                ← Gradle wrapper
├── gradle/wrapper/
│   └── gradle-wrapper.properties        ← Gradle 8.14
├── Dockerfile                           ← CI build image (headless)
├── .github/workflows/ci.yml             ← GitHub Actions pipeline
├── scripts/
│   ├── deploy.bat / deploy.sh           ← Deploy to local directory
│   ├── run.bat                          ← Dev launcher (Windows)
│   └── test.bat                         ← Test runner + report (Windows)
└── src/
    ├── main/
    │   ├── java/com/ditadesigner/
    │   │   ├── App.java                 ← JavaFX Application entry point
    │   │   ├── model/                   ← Jackson-serializable domain model
    │   │   │   ├── DitaModel.java       ← Root container
    │   │   │   ├── TopicType.java       ← Topic type (+ canvas position)
    │   │   │   ├── ElementDef.java      ← Element definition
    │   │   │   ├── AttributeDef.java    ← Attribute definition
    │   │   │   ├── DomainDef.java       ← Domain module
    │   │   │   ├── Relationship.java    ← Directed relationship
    │   │   │   └── RelationshipType.java← INHERITANCE | CONTAINMENT | …
    │   │   ├── ui/
    │   │   │   ├── MainController.java  ← All FXML actions + canvas logic
    │   │   │   └── nodes/
    │   │   │       ├── DiagramNode.java ← Interface: getModelId, getCenterX/Y
    │   │   │       ├── TopicTypeNode.java ← Blue draggable card
    │   │   │       ├── ElementNode.java   ← Green draggable card
    │   │   │       └── ConnectionLine.java← Coloured arrow with label
    │   │   ├── service/
    │   │   │   ├── ProjectService.java  ← CRUD ops + phxTask sample factory
    │   │   │   └── OasisLoaderService.java ← DTD/XSD library loader
    │   │   ├── generator/
    │   │   │   ├── DtdGenerator.java    ← .dtd shell · .mod · .ent
    │   │   │   ├── XsdGenerator.java    ← xs:complexType / xs:extension
    │   │   │   └── CatalogGenerator.java← OASIS Catalog XML V1.1
    │   │   ├── parser/
    │   │   │   ├── DtdParser.java       ← Regex-based DTD element extractor
    │   │   │   └── XsdParser.java       ← DOM-based XSD complex-type parser
    │   │   ├── transformer/
    │   │   │   └── ModelTransformer.java← Validation + class attr builder
    │   │   ├── repository/
    │   │   │   └── ProjectRepository.java ← JSON save / load (Jackson)
    │   │   ├── xslt/
    │   │   │   ├── XsltModule.java      ← Entry point (lazy singleton controller)
    │   │   │   ├── XsltUIController.java← Workbench window: editors + toolbar
    │   │   │   ├── XsltEditor.java      ← Syntax-highlighted RichTextFX CodeArea
    │   │   │   ├── XsltExecutionService.java ← Saxon HE transform + temp-file helpers
    │   │   │   ├── XsltValidationService.java← Compile-time validation, line errors
    │   │   │   └── XsltValidationError.java  ← Error DTO (line, message, severity)
    │   │   └── util/
    │   │       ├── LogService.java      ← Thread-safe FX TextArea logger
    │   │       └── FileUtil.java        ← File write / ZIP / copy helpers
    │   └── resources/
    │       ├── fxml/main.fxml           ← BorderPane layout (FXML)
    │       ├── css/styles.css           ← Dark toolbox · grid canvas theme
    │       ├── css/xslt-editor.css      ← VS Code Dark+ token colours for XSLT
    │       └── xslt/dita-to-html.xsl   ← Built-in DITA→HTML XSLT 2.0 stylesheet
    └── test/
        └── java/com/ditadesigner/
            └── GeneratorTest.java       ← 24 JUnit 5 tests
```

---

## Application Usage

### Designing a Specialization

1. Click **⬛ Topic Type** in the toolbox, then click on the canvas.
2. Enter a name (e.g., `myTask`) and choose a base type (e.g., `task`).
3. Click the node to select it — its properties appear in the right panel.
4. Add **Elements** and **Attributes** from the Properties panel.
5. Click **◻ Element** to place standalone element nodes, then drag them.
6. Use **↑ Inheritance** or **↓ Containment** to connect two nodes with an arrow.

### Loading DITA Libraries

**DITA → Load DITA Libraries…** — point to a directory containing `.dtd` or
`.xsd` files from your DITA installation (e.g., DITA-OT `dtd/` folder).
This populates the base-type chooser used when creating topic types.

### Importing an Existing XSD (Import & Extend)

1. Click **⬛ Topic Type** in the toolbox, then click the canvas.
2. In the dialog, select **Import & Extend existing XSD**.
3. Click **Browse…** and choose a `.xsd` file.
4. The tool parses the schema and pre-fills the name, base type, namespace, and elements.
5. Adjust the name if needed, then click **OK** — the imported type appears on the canvas ready for further editing.

### Using the XSLT Workbench

Open the workbench from **DITA → XSLT Workbench…** (Ctrl+Shift+X) or **DITA → DITA to HTML (XSLT)…**.

| Panel | Purpose |
|---|---|
| XML Editor (top-left) | Write or load the XML/DITA source. Use **Browse XML…** to load a file. |
| XSLT Editor (bottom-left) | Write or load the XSLT stylesheet. Use **Browse XSLT…** to load a file. |
| Output (right) | Shows the transformation result or error details. |
| Console (bottom) | Captures `xsl:message` output and compile warnings. |

| Action | Button | Notes |
|---|---|---|
| Run transformation | **▶ Run XSLT** | Runs on a background thread; output appears in the right panel |
| Validate stylesheet | **✔ Validate** | Compiles the stylesheet and lists errors/warnings with line numbers |
| Load built-in template | **DITA→HTML** | Loads the bundled DITA→HTML XSLT 2.0 stylesheet |
| Save output | **Save Output…** | Writes the result panel content to a UTF-8 file |


### Using the XPath Checker

Open from **DITA ? XPath Checker...** (Ctrl+Shift+P).

| Action | Button | Notes |
|---|---|---|
| Evaluate XPath | **Evaluate** | Runs XPath 2.0/3.1 against the current XML and lists matched values |
| Check XPath syntax | **Check XPath** | Validates expression syntax (and declared namespaces) without executing |
| Pretty print XML | **Pretty Print XML** | Rewrites editor XML with consistent indentation |
| Well-formedness check | **Well-formed?** | Parses XML and shows parser errors with details |
| Validate against schema | **Validate vs XSD...** | Validates current XML against a selected `.xsd` file |

Namespace declarations can be provided as `prefix=uri` (one per line) for prefixed XPath expressions.

### Generating Artefacts

| Menu item | Keyboard | Output |
|---|---|---|
| DITA → Generate DTD | — | `output/dtd/{name}.dtd`, `.mod`, `.ent` |
| DITA → Generate XSD | — | `output/xsd/{name}.xsd` |
| DITA → Generate XML Catalog | — | `output/catalog.xml` |
| DITA → Generate All | **Ctrl+G** | All of the above |
| File → Export ZIP… | — | `output.zip` containing all artefacts |

### Save / Load

| Action | Keyboard |
|---|---|
| File → Save Project | **Ctrl+S** |
| File → Open Project… | **Ctrl+O** |
| File → New Project | **Ctrl+N** |

Projects are stored as `.ddp` (JSON) files.

---

## Generated Output

Example output for the `phxTask` sample specialization:

```
output/
├── dtd/
│   ├── phxtask.dtd        ← Shell DTD (includes .ent and .mod)
│   ├── phxtask.mod        ← <!ELEMENT> and <!ATTLIST> declarations
│   └── phxtask.ent        ← <!ENTITY> public-ID declarations
├── xsd/
│   └── phxtask.xsd        ← xs:complexType extending task.class
└── catalog.xml            ← OASIS XML Catalog V1.1
```

### phxtask.mod (excerpt)

```dtd
<!ELEMENT phxtask (title, phxRequirements?, phxSteps, phxResult?)>
<!ATTLIST phxtask
    id ID #REQUIRED
    phx-severity (critical|high|medium|low) #IMPLIED
    phx-component CDATA #IMPLIED
    class CDATA "- topic/topic task/task phxtask/phxtask "
>
```

### phxtask.xsd (excerpt)

```xml
<xs:complexType name="phxTask.class">
  <xs:complexContent>
    <xs:extension base="task.class">
      <xs:sequence>
        <xs:element ref="phxRequirements" minOccurs="0" maxOccurs="1"/>
        <xs:element ref="phxSteps"        minOccurs="1" maxOccurs="1"/>
        <xs:element ref="phxResult"       minOccurs="0" maxOccurs="1"/>
      </xs:sequence>
      <xs:attribute name="class" type="xs:string"
          default="- topic/topic task/task phxTask/phxTask "/>
    </xs:extension>
  </xs:complexContent>
</xs:complexType>
```

### catalog.xml (excerpt)

```xml
<catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog" prefer="public">
  <public publicId="-//LOCAL//ELEMENTS DITA phxTask//EN"
          uri="dtd/phxtask.mod"/>
  <system systemId="phxtask.dtd"
          uri="dtd/phxtask.dtd"/>
  <uri    name="phxtask.xsd"
          uri="xsd/phxtask.xsd"/>
</catalog>
```

---

## CI/CD

### GitHub Actions (`.github/workflows/ci.yml`)

The included workflow:

- Runs on **push** (main, develop) and **pull_request** (main)
- Matrix: Ubuntu + Windows × JDK 17 + JDK 21
- Uploads test reports and coverage as artefacts
- On **release** events: builds all artefacts and attaches them to the GitHub Release

### Docker (headless build)

```bash
# Build inside Docker (compiles + tests + packages)
docker build -t dita-designer:build .

# Extract artefacts
docker create --name dita-tmp dita-designer:build
docker cp dita-tmp:/app/build/libs/ ./dist/
docker cp dita-tmp:/app/build/distributions/ ./dist/
docker rm dita-tmp
```

---

## Configuration Reference

### build.gradle key settings

| Property | Default | Where |
|---|---|---|
| `group` | `com.ditadesigner` | `build.gradle` |
| `version` | `1.0.0` | `build.gradle` |
| JavaFX version | `21` | `javafx { version }` |
| Java compatibility | `17` | `java { sourceCompatibility }` |
| Fat JAR classifier | `all` | `shadowJar { archiveClassifier }` |
| Coverage minimum | 50 % | `jacocoTestCoverageVerification` |
| App JVM heap | `-Xms256m -Xmx512m` | `applicationDefaultJvmArgs` |

### Key dependencies

| Library | Version | Purpose |
|---|---|---|
| JavaFX | 21 | UI framework |
| Jackson Databind | 2.15.2 | JSON serialization |
| Apache Xerces | 2.12.2 | XML / XSD parsing |
| Commons IO | 2.13.0 | File utilities, ZIP |
| Commons Lang3 | 3.13.0 | String utilities |
| Saxon HE | 12.4 | XSLT 2.0/3.0 transformation engine |
| RichTextFX | 0.11.2 | Syntax-highlighted code editor for JavaFX |
| JUnit Jupiter | 5.9.3 | Unit testing |
| JaCoCo | 0.8.11 | Code coverage |
| Shadow plugin | 8.1.1 | Fat JAR creation |
