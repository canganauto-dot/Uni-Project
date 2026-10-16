# Professor OS / Professor Dashboard

An offline-first desktop workspace for university professors, built as an academic software engineering project with an emphasis on clarity, maintainability and explainable design.

**Status: foundation preview.** The dashboard displays clearly labeled sample data. Academic workflows are planned and are not yet implemented.

## Implemented

- JavaFX application shell with eleven navigation destinations and honest placeholder pages.
- Demo dashboard: Courses (4), Students (126), Upcoming Exams (2), Pending Grading (18), schedule, deadlines, course progress and recent activity.
- Dedicated CSS theme, keyboard-focus styling and scrollable content.
- SQLite connection infrastructure, foreign key enforcement, configurable storage and startup error reporting; no domain tables or demo database records.
- Maven wrapper, JUnit tests, optional desktop smoke test and permanent AI development rules.

## Planned

Course/student management, Excel student import, lecture organization, semester timeline, question bank, constraint-based exam generation, multiple exam versions, **PDF exams and answer keys**, grades and **Excel grade export**, attendance, learning outcomes, question quality/performance analytics, forecasting, backup and restore.

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
  model/                     Reserved for domain models
  service/                   Reserved for business operations
  repository/                Reserved for persistence operations
  database/                  JDBC connection infrastructure
  util/                      Reserved for focused shared utilities
src/main/resources/
  css/                       Theme stylesheet
  fxml/                      Reserved; current layouts are Java code
  images/                    Reserved for application assets
src/test/java/com/professoros/ Database, path and optional desktop tests
data/                        Local database (ignored)
exports/                     Future generated PDF/Excel exports (ignored)
target/                      Build output and test reports (ignored)
```

Read [AGENTS.md](AGENTS.md) before changing code and record significant decisions in [PROJECT_NOTES.md](PROJECT_NOTES.md). Start the next increment by defining course fields, a migration strategy, and a small course-management use case.
