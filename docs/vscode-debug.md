# VS Code — Run & Debug Guide

Developer guide for running, debugging, and testing the **DITA Specialization Designer** inside Visual Studio Code.

---

## Prerequisites

### 1. Install VS Code Extensions

Install all four extensions from the VS Code Marketplace:

| Extension | Publisher | Purpose |
|-----------|-----------|---------|
| **Extension Pack for Java** | Microsoft | Java language support, debugger, test runner |
| **Gradle for Java** | Microsoft | Gradle task browser and build integration |
| **Test Runner for Java** | Microsoft | Run/debug JUnit 5 tests with a GUI |
| **Language Support for Java** | Red Hat | Code completion, navigation, refactoring |

> **Quickest install:** Press `Ctrl+Shift+X`, search "Extension Pack for Java", install the pack — it bundles all four.

### 2. Java 17

The project requires **Java 17** (LTS).

Verify your installation:
```bash
java -version
# java version "17.x.x"
```

If you have multiple JDKs, tell VS Code which one to use:
1. `Ctrl+Shift+P` → **Java: Configure Java Runtime**
2. Set the JDK to a Java 17 build.

### 3. Open the Project

```bash
code c:/Users/aruls/dita-specialization-designer
```

When VS Code opens, it will detect the Gradle project and import it automatically. Wait for the status bar message **"Java: Ready"** before running anything.

---

## Running the Application

### Option A — Gradle Task (recommended)

Use the Gradle side panel or the terminal:

```bash
./gradlew run
```

**Via VS Code tasks** (`Ctrl+Shift+B` or `Terminal → Run Task`):
- Select **Gradle: Run**

The JavaFX window opens. All classpath and module-path wiring is handled by Gradle.

---

### Option B — Launch Configuration

1. Open the **Run and Debug** panel (`Ctrl+Shift+D`).
2. Select **"Run App (Gradle)"** from the drop-down.
3. Press `F5` or click the green play button.

> This uses VS Code's Java debugger with the same JVM args as `./gradlew run`.

---

## Debugging the Application

### Step 1 — Start the app in debug mode

Run the Gradle task that opens a debug port:

```bash
./gradlew run --debug-jvm
```

**Via VS Code task** (`Ctrl+Shift+P` → **Tasks: Run Task**):
- Select **Gradle: Run (Debug mode)**

The terminal will pause and print:
```
Listening for transport dt_socket at address: 5005
```

### Step 2 — Attach the VS Code debugger

1. Open the **Run and Debug** panel (`Ctrl+Shift+D`).
2. Select **"Debug App (Gradle attach)"** from the drop-down.
3. Press `F5`.

VS Code connects to port **5005** and the application starts.

### Step 3 — Set breakpoints

- Click in the left gutter next to any line number to set a breakpoint (red dot).
- The debugger pauses there when execution reaches it.
- Use the **Debug toolbar** to step over (`F10`), step into (`F11`), continue (`F5`), or stop (`Shift+F5`).

### Useful debug targets

| File | What to debug |
|------|--------------|
| [App.java](../src/main/java/com/ditadesigner/App.java) | JavaFX startup, stage initialization |
| [MainController.java](../src/main/java/com/ditadesigner/ui/MainController.java) | Canvas events, menu actions, model changes |
| [XsltUIController.java](../src/main/java/com/ditadesigner/xslt/XsltUIController.java) | XSLT workbench, completion dispatch, XPath builder |
| [XPathEvaluationService.java](../src/main/java/com/ditadesigner/xpath/XPathEvaluationService.java) | XPath evaluation and error handling |
| [DtdGenerator.java](../src/main/java/com/ditadesigner/generator/DtdGenerator.java) | DTD/XSD generation logic |

---

## Running Tests

### All tests — GUI

1. Open the **Testing** panel from the Activity Bar (flask icon, or `Ctrl+Shift+P` → **Testing: Focus on Test Explorer View**).
2. Click the **Run All Tests** button (play icon at the top of the panel).
3. Results appear inline with green checkmarks / red crosses.

### All tests — terminal

```bash
./gradlew test
```

HTML report is written to:
```
build/reports/tests/test/index.html
```

### Single test class — terminal

```bash
./gradlew test --tests "com.ditadesigner.XsltTest"
./gradlew test --tests "com.ditadesigner.ModelTest"
```

**Via VS Code task** (`Ctrl+Shift+P` → **Tasks: Run Task** → **Gradle: Test (Single class)**):
- A prompt appears — enter the fully-qualified class name (e.g. `com.ditadesigner.XsltTest`).

### Test classes at a glance

| Test class | Module tested | Tests |
|------------|--------------|-------|
| `GeneratorTest` | generator/, model, transformer, repository | 24 |
| `ModelTest` | model/ | 24 |
| `TransformerTest` | transformer/ | 21 |
| `XmlCoreTest` | xml/ | 18 |
| `XPathTest` | xpath/ | 22 |
| `XsltTest` | xslt/ | 25 |
| `RepositoryTest` | repository/ | 11 |
| `ParserTest` | parser/ | 9 |

---

## Debugging a Single Test

### Method 1 — Test Explorer (easiest)

1. Open the **Testing** panel.
2. Expand the tree to find the test method.
3. Right-click → **Debug Test**.

Execution pauses at any breakpoints you have set inside the test or the code it calls.

### Method 2 — CodeLens

Open any test file (e.g. [XsltTest.java](../src/test/java/com/ditadesigner/XsltTest.java)).
Each `@Test` method shows a **"Debug Test"** lens directly above the annotation — click it.

### Method 3 — Launch configuration

1. Open the test file you want to debug.
2. Open Run and Debug (`Ctrl+Shift+D`).
3. Select **"Debug Current Test File"**.
4. Press `F5`.

---

## Code Coverage

After running tests, JaCoCo generates an HTML coverage report:

```bash
./gradlew test jacocoTestReport
```

Open the report:
```
build/reports/jacoco/test/html/index.html
```

---

## Build Tasks Reference

Access all tasks via `Ctrl+Shift+P` → **Tasks: Run Task**, or from the **Gradle** side panel.

| Task label | Gradle command | What it does |
|------------|---------------|--------------|
| Gradle: Build | `./gradlew build -x test` | Compile sources (skip tests) |
| Gradle: Run | `./gradlew run` | Launch the JavaFX app |
| Gradle: Run (Debug mode) | `./gradlew run --debug-jvm` | Launch with debug port 5005 open |
| Gradle: Test (All) | `./gradlew test` | Run all 154 tests + coverage |
| Gradle: Test (Single class) | `./gradlew test --tests "…"` | Run one test class (prompts for name) |
| Gradle: Clean | `./gradlew clean` | Delete build/ directory |
| Gradle: CI Pipeline | `./gradlew ci` | Full pipeline: compile → test → package |
| Gradle: Generate Sample | `./gradlew generateSample` | Write phxTask sample artefacts to sample-output/ |

---

## Common Issues

### "JavaFX runtime components are missing"

VS Code's Java debugger launches the JVM directly and may miss JavaFX module-path wiring. Use **Gradle: Run** (`./gradlew run`) instead of the bare launch configuration when this happens.

### Port 5005 already in use

```bash
# Windows
netstat -ano | findstr :5005
taskkill /PID <pid> /F
```

Then restart **Gradle: Run (Debug mode)**.

### Build not reflecting latest code changes

```bash
./gradlew clean compileJava
```

Then press `Ctrl+Shift+P` → **Java: Clean Java Language Server Workspace** → Restart.

### Tests not appearing in Test Explorer

Make sure the Gradle import finished (status bar: "Java: Ready"). If the tree is empty:
1. `Ctrl+Shift+P` → **Java: Reload Projects**.
2. Wait for the "Java: Ready" status.

---

## Keyboard Shortcuts Reference

| Action | Shortcut |
|--------|----------|
| Open Run and Debug panel | `Ctrl+Shift+D` |
| Run selected configuration | `F5` |
| Stop debugging | `Shift+F5` |
| Step over | `F10` |
| Step into | `F11` |
| Step out | `Shift+F11` |
| Toggle breakpoint | `F9` |
| Open Terminal | `` Ctrl+` `` |
| Run Task | `Ctrl+Shift+B` (default build task) |
| All Tasks menu | `Ctrl+Shift+P` → Tasks: Run Task |
| Open Test Explorer | `Ctrl+Shift+P` → Testing: Focus on Test Explorer |
