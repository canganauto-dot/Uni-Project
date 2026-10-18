# Professor OS / Professor Dashboard

An offline-first desktop workspace for university professors, built as an academic software engineering project with an emphasis on clarity, maintainability and explainable design.

**Status: Phase 6 exam builder.** Academic records, lectures, reusable questions and offering-specific exams are persisted locally. Dashboard course count and active offering are real; remaining dashboard metrics and activities are clearly labelled demo data.

## Implemented

- JavaFX shell, catalog Course CRUD, term management under Settings and student directory management.
- Course offerings by term/section, an active offering selector and enrollment rosters with withdrawal/completion/reactivation.
- Offering-specific lecture CRUD and search, chronological timeline, completion counts and basic schedule status.
- Catalog question bank with five question types, MCQ choices, difficulty/marks, optional topic/image path, details, search and course/type/difficulty filters.
- Manual exam building, ordered question snapshots, mark-total warnings and deterministic generation with exact count/distribution constraints. Finalization locks content and records bank usage; archived exams preserve history.
- Real course count and active offering on Dashboard; explicitly demo student/exam/grading cards, schedule, deadlines, progress and activity.
- Dedicated CSS theme, keyboard-focus styling and scrollable content.
- Versioned SQLite migrations, unique identifiers, foreign-key deletion restrictions and configurable storage. Application startup never seeds demo records.
- Maven wrapper, JUnit tests, optional desktop smoke test and permanent AI development rules.

## Planned

Excel student import, broader semester planning, advanced generation constraints, multiple exam versions, **PDF exams and answer keys**, grades and **Excel grade export**, attendance, learning outcomes, question quality/performance analytics, forecasting, backup and restore.

## Academic workflow

1. Create an academic term in **Settings**.
2. Create/select a catalog course in **Courses**, then choose **Create Offering** and select a term and section.
3. Add students in **Students**.
4. Open an offering through Course Details, All Offerings or the top-bar selector; use its **Students** tab to enroll existing students.
5. Select **Lectures** to organize the active offering's lectures, or **Timeline** to see them chronologically. Switching the top-bar offering updates either page. Progress covers all lectures in that offering, independent of search.
6. Use **Exam Bank** to create reusable questions for a catalog course. No term or active offering is required. Choose 2–10 distinct choices and one correct answer for MCQs, or a True/False answer when applicable. Optional image files are referenced by absolute local path, not copied into the database.
7. Select an offering and open **Exams**. Create a draft, add questions from its course's bank, and use the Selected tab to remove/reorder them. Generate Selection supports exact difficulty/type/topic counts; leaving a whole distribution blank makes it unrestricted. Review calculated versus intended marks, then Save. Finalize only when content is ready: finalized exams cannot be edited or deleted.

Course is a reusable catalog definition. Enrollment links a student to a specific offering, never directly to the catalog course. Records with dependent academic history cannot be deleted. Active offering selection lasts for the current application session.

## Technology stack

- Java 25 LTS; JavaFX Controls 25.0.4 (includes JavaFX Base/Graphics).
- Maven 3.9.11 via the wrapper; JavaFX Maven plugin 0.0.8.
- SQLite JDBC 3.53.4.0; JDBC and `java.util.logging` from the JDK.
- JUnit Jupiter 5.14.0 for tests.
- Apache POI and PDFBox/OpenPDF will be evaluated when Excel/PDF implementation begins; they are not dependencies yet.

## Prerequisites

- JDK 25, with `JAVA_HOME` pointing to the JDK directory (not its `bin` directory).
- A graphical desktop for launching JavaFX or the optional UI test.
- Internet access for the first Maven/dependency download. Runtime application behavior is offline; subsequent builds can use cached dependencies.
- Git for version control. A separate Maven installation is optional because the wrapper downloads its pinned Maven version.

Initial Windows inspection found JDK 25.0.1 at `C:\Users\HP\.jdks\openjdk-25.0.1`, but Java and Maven were absent from PATH. No global environment settings were changed. In PowerShell on this machine:

```powershell
cd 'C:\Users\HP\Desktop\Uni Project'
$env:JAVA_HOME = 'C:\Users\HP\.jdks\openjdk-25.0.1'
.\mvnw.cmd -version
```

On another machine, set `JAVA_HOME` to your actual JDK 25 installation. IntelliJ users should select JDK 25 as both the project SDK and Maven runner JDK, and import `pom.xml`.

In console-less automation on this machine, a pre-existing Windows `cmd` AutoRun color setting causes misleading exit codes, including for Maven's forked test JVM. Use the local workaround below; no registry change is required.

## Build and test

```powershell
.\mvnw.cmd clean verify
```

**Verified workaround for this machine's shell setting:**

```powershell
$env:MAVEN_OPTS = '--enable-native-access=ALL-UNNAMED'
cmd /d /c ".\mvnw.cmd -DforkCount=0 clean verify"
```

This runs the same tests in Maven's JVM instead of spawning a test JVM through the affected Windows shell. Keep normal forking on unaffected machines; in-process execution provides less test isolation. The `/d` flag also bypasses AutoRun for the outer wrapper invocation.

On Linux/macOS: `./mvnw clean verify` after setting `JAVA_HOME`. The normal build runs database and path tests without requiring a display. The generated JAR is a development artifact, not a bundled executable installer; use the JavaFX Maven goal to launch.

Optional desktop check (opens and closes a window, exercises all navigation destinations and writes snapshots under `target/ui-smoke/`):

```powershell
.\mvnw.cmd -Pui-smoke verify
```

For this machine's workaround, also set the smoke data location explicitly because in-process tests cannot receive Surefire's fork-only environment variables:

```powershell
$env:PROFESSOR_OS_DATA_DIR = Join-Path (Get-Location) 'target\ui-smoke-data'
cmd /d /c ".\mvnw.cmd -DforkCount=0 -Pui-smoke verify"
Remove-Item Env:PROFESSOR_OS_DATA_DIR
```

The smoke profile uses `target/ui-smoke-data/`, while unit tests use temporary directories. The smoke test checks its storage location before initialization and refuses to touch the normal application data directory.

## Run

From the repository root, with `JAVA_HOME` configured:

```powershell
.\mvnw.cmd javafx:run
```

On Linux/macOS: `./mvnw javafx:run`. Close the window to stop the application.

The development database is `data/professor-os.db`, relative to the working directory. Set the following environment variable **before launching** to choose another directory:

```powershell
$env:PROFESSOR_OS_DATA_DIR = Join-Path $env:LOCALAPPDATA 'ProfessorOS\data'
.\mvnw.cmd javafx:run
```

Changing the directory selects a different database; it does not migrate or copy existing files. Preserve existing data. Startup diagnostics are logged to the launching terminal; users receive a concise dialog if database initialization fails.

## Directory overview

```text
AGENTS.md                     Permanent AI development rules
PROJECT_NOTES.md              Technical notebook and decisions
pom.xml                      Dependencies, compiler, launch and test configuration
mvnw / mvnw.cmd              Portable Maven entry points
.mvn/wrapper/                Pinned Maven distribution configuration
src/main/java/com/professoros/
  app/                       Startup and storage-path policy
  controller/                Navigation coordination
  view/                      JavaFX layout composition
  demo/                      Explicitly synthetic dashboard data
  model/                     Academic entities, inputs and joined read models
  service/                   Validation, academic operations and session context
  repository/                Prepared JDBC queries and row mapping
  database/                  Connections and versioned migration runner
  util/                      Reserved for focused shared utilities
src/main/resources/
  css/                       Theme stylesheet
  db/migrations/             Append-only schema migrations V001–V008
  fxml/                      Reserved; current layouts are Java code
  images/                    Reserved for application assets
src/test/java/com/professoros/ Database, path and optional desktop tests
data/                        Local database (ignored)
exports/                     Future generated PDF/Excel exports (ignored)
target/                      Build output and test reports (ignored)
```

Read [AGENTS.md](AGENTS.md) before changing code and record significant decisions in [PROJECT_NOTES.md](PROJECT_NOTES.md). Future teaching features should attach to CourseOffering or Enrollment as appropriate.
