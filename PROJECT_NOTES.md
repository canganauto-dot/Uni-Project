# Professor OS

## Project Goal

Build an all-in-one offline desktop workspace for university professors: courses, students, lectures, exams, grades, attendance and semester planning. The software should reduce fragmented administration while remaining understandable and defensible as an academic Java software engineering project. PDF exam generation and Excel grade export are required future capabilities.

## Technology Stack

| Technology | Version / status | Purpose |
| --- | --- | --- |
| Java | 25 LTS; verified local JDK 25.0.1 | Language, standard logging, JDBC |
| JavaFX Controls | 25.0.4 | Desktop UI; Base and Graphics are transitive |
| Maven | Wrapper pins 3.9.11 | Repeatable build and dependency resolution |
| JavaFX Maven plugin | 0.0.8 | Development launch |
| Maven Compiler / Surefire | 3.14.1 / 3.6.0 | Java 25 compilation and JUnit execution |
| SQLite JDBC | 3.53.4.0 | Local storage engine and JDBC driver |
| JUnit Jupiter | 5.14.0, test scope | Meaningful foundation tests |
| Apache POI | Planned | Excel student import and grade export |
| PDFBox or OpenPDF | Choice deferred | Exam and answer-key PDFs |

Version availability was checked against Maven Central. Setup guidance: [OpenJFX](https://openjfx.io/openjfx-docs/), [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/), [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc), [JUnit 5 release notes](https://docs.junit.org/5.14.0/release-notes/).

## Architecture

Intended dependency direction: **UI → services → repositories → database**. Domain models must stay independent of JavaFX and JDBC so future Excel/PDF services can reuse them.

Currently, `ProfessorOsApplication` is the composition root: it initializes the database before showing the window and wires the main view/controller to an explicitly synthetic demo data provider. `MainController` handles only navigation. `MainView` and `DashboardView` build layouts. No academic business operations, repositories, or persistence models have been implemented.

Database initialization runs in JavaFX's `init()` lifecycle method, off the JavaFX application thread. Future potentially slow service/database operations should similarly avoid blocking the UI thread. Each database operation owns a short-lived connection and closes it with try-with-resources; no pool is needed yet.

## Directory Structure

Standard Maven `src/main/java`, `src/main/resources` and `src/test/java` layouts, under package `com.professoros`. Packages: `app`, `controller`, `view`, `demo`, `model`, `service`, `repository`, `database`, `util`. Resources: `css`, `fxml`, `images`.

The requested structure gains `view` to keep layout construction out of controllers and `demo` to isolate sample content from domain/persistence code. Reserved packages/resources use `.gitkeep` rather than empty placeholder classes. No FXML dependency is needed until an FXML file is actually introduced.

Local development database files live in ignored `data/`. Future generated PDFs/Excel workbooks belong in ignored `exports/` or `generated/`; build/test output belongs in `target/`. Reference documents, fixtures and SQL migrations remain trackable rather than globally ignoring all PDF/Excel/SQL files.

## Database Design

**Preliminary; no application tables are defined.** Initialization creates the parent directory and opens/validates the SQLite database. It is idempotent and does not delete files, drop tables or insert sample records.

- Every connection enables and verifies `PRAGMA foreign_keys = ON` and sets a five-second busy timeout.
- All future variable SQL values must use prepared statements.
- Storage defaults to `./data/professor-os.db`; `PROFESSOR_OS_DATA_DIR` overrides the directory. `AppPaths` owns this policy; `Database` accepts a `Path` independently.
- `initialize()` owns/closes its connection; callers own connections returned by `openConnection()`.
- SQL/file errors propagate with context. Startup logs details through `java.util.logging` and shows a short actionable message without a stack trace.
- Introduce versioned migrations before adding the first domain tables. Select the approach alongside course/student relationships, constraints and transaction boundaries.
- The current database has no schema-version marker because no domain schema exists. Never reset an existing schema to make tests or startup pass.

## Major Technical Decisions

### Decision
Use Java 25 LTS, JavaFX and Maven for an offline desktop application.

### Reason
This matches the academic Java requirement and offers desktop tables, forms and charts. Java 25 is an LTS baseline suitable for the selected JavaFX line. Maven pins dependencies and the wrapper makes a separate Maven installation optional.

### Alternatives Considered
Swing; a web frontend; Java 21 LTS. Java 21 would be reasonable if university machines require it, but would need a compatible JavaFX version and compiler target change.

### Decision
Use a single non-modular Maven application with code-built views and a dedicated CSS theme.

### Reason
The small foundation does not need JPMS configuration, FXML reflection or a dependency injection framework. Explicit construction keeps dependencies easy to explain. CSS named color tokens and style classes allow future theme changes without rewriting controller behavior.

### Alternatives Considered
JPMS modules, FXML and dependency injection. Revisit FXML if Scene Builder becomes useful; revisit modules/runtime packaging when building an installer. The present JAR is not a self-contained application distribution.

### Decision
Keep demo data separate and delay schema and export dependencies until real use cases exist.

### Reason
The initial UI can be reviewed without inventing academic requirements or persisting misleading sample records. Domain models and service boundaries leave room for POI Excel import/export and PDF generation without coupling them to UI controllers.

### Alternatives Considered
Seeding a demo database; speculative full schema; adding POI/PDF libraries immediately. These add maintenance and falsely imply completed functionality.

### Decision
Use short-lived JDBC connections and JDK logging.

### Reason
SQLite and current startup work do not justify a pool, ORM, custom logging system or additional logging dependencies. Resource ownership is explicit and exceptions retain diagnostic context.

### Alternatives Considered
JPA/Hibernate, pooled connections, third-party application logging. Reconsider only when concrete requirements justify them.

## Algorithms

No domain algorithms are implemented. Planned documentation: exam constraint generation, question difficulty analysis, performance risk scoring and semester progress forecasting. Record inputs, assumptions, method, edge cases and validation when each is introduced.

## Implemented Features

- Main window with Dashboard, Courses, Students, Lectures, Exam Bank, Exams, Grades, Attendance, Analytics, Timeline and Settings navigation.
- Header with application title, current-course placeholder and disabled search placeholder.
- Demo cards: 4 courses, 126 students, 2 upcoming exams and 18 pending grading items.
- Sample schedule, deadlines, lecture completion and recent activity. Sample dates are relative illustrative labels, not a live calendar.
- All other destinations explicitly identify themselves as planned.
- SQLite connection/error-handling foundation, CSS theme, Maven wrapper and tests.

## Planned Features

- Course and student management; Excel student import.
- Lecture organizer and semester timeline.
- Question bank, constraint-based exam generation and multiple exam versions.
- Required PDF exam generation and PDF answer keys.
- Grade tracking and required Excel grade export.
- Attendance, learning outcomes, question quality analysis, student performance analysis and course analytics.
- Backup and restore.

## Known Limitations

- No real academic data, domain schema, CRUD, search, course selection, exports or analytics yet.
- No installer or packaged runtime; a compatible JDK and Maven dependency cache are needed for development launch.
- Only local Windows desktop validation is available at initialization; other supported JavaFX platforms need their own checks.
- Database path is currently relative to the launch directory unless overridden. Selecting a different location does not migrate existing data.
- The dashboard uses a desktop minimum size of 1100 × 740 and scrolls vertically; it is not a mobile layout.
- Java/Maven were initially absent from PATH. An existing JDK was found under the user profile; Maven was bootstrapped outside the repository. Set `JAVA_HOME` per README; no machine-wide settings were changed.
- Maven 3.9.11 may print a JDK 25 deprecation warning from its bundled Guice dependency. This is build-tool output, not an application compilation failure.
- This machine has a pre-existing Command Processor `AutoRun` setting (`color 02`) that returns a nonzero status in console-less shells. Normal forked testing reports a process failure despite passing assertions, including with Surefire 3.6.0. Local verification uses `cmd /d /c` for the wrapper and `-DforkCount=0` to run tests in Maven's JVM; default isolated testing is retained for unaffected machines. This workaround has less process isolation. No registry settings were modified. For in-process UI tests, the isolated storage directory is set explicitly in the environment (see README).

## Development History

### 2026-10-16 — Project foundation

- Inspected the initially empty directory on Windows 11 Pro x64 (10.0.22631). No existing repository or project files were overwritten.
- Initialized Git on `main`; existing Git user identity is configured.
- Located and verified OpenJDK/javac 25.0.1 under the user's `.jdks` directory. Bootstrapped Maven 3.9.11 under `%LOCALAPPDATA%/ProfessorOS/dev-tools/` after SHA-512 verification.
- Added development rules, documentation, Maven configuration/wrapper, layered packages, demo dashboard, SQLite connection infrastructure and tests.
- Full clean Maven build passed using the documented local workaround: `cmd /d /c ".\mvnw.cmd -B -ntp -DforkCount=0 clean verify"`. Six unit tests passed with zero failures/errors.
- Desktop smoke verification passed with `-DforkCount=0 -Pui-smoke verify` and the explicit isolated data directory: seven total tests, zero failures/errors. It started the actual application class, exercised all eleven navigation destinations, returned to Dashboard and generated snapshots at the default and minimum window sizes. Both snapshots were visually inspected.
- The normal forked build was attempted and remains affected by the pre-existing Windows AutoRun issue described above; this is not reported as a clean default-build pass. Surefire 3.6.0 is pinned, but an upgrade alone did not resolve the shell issue.
- Optional UI tests load JavaFX on the test classpath and print an unnamed-module warning. The application launch uses the JavaFX Maven plugin's module-path handling.
- Verified the normal `javafx:run` goal separately: the database initialized under `data/`, the startup log confirmed the window was shown, and Windows reported the live window title `Professor OS | Dashboard`. The window was left open for review.
