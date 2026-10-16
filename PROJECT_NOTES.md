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

`ProfessorOsApplication` is the composition root: it initializes/migrates the database before showing the window and constructs `CourseRepository` and `CourseService`. `MainController` handles navigation and wires `CourseController` to `CoursesView` and the dashboard course count. Views construct layouts; controllers coordinate interactions; services own validation and timestamps; repositories own JDBC and row mapping. `DemoDashboard` supplies only clearly labelled synthetic content.

Database initialization runs in JavaFX's `init()` lifecycle method, off the JavaFX application thread. Course operations use JavaFX `Task` on a single background worker; controls update on the JavaFX thread. Writes disable the form until completion and close it only after success. Each database operation owns a short-lived connection and closes it with try-with-resources; no pool is needed yet. Expected validation failures have specific exception types; unexpected storage failures retain their causes, are logged and show concise messages.

## Directory Structure

Standard Maven `src/main/java`, `src/main/resources` and `src/test/java` layouts, under package `com.professoros`. Packages: `app`, `controller`, `view`, `demo`, `model`, `service`, `repository`, `database`, `util`. Resources: `css`, `fxml`, `images`, `db/migrations`. Migration failure fixtures live separately in `src/test/resources/db/migrations`.

The requested structure gains `view` to keep layout construction out of controllers and `demo` to isolate sample content from domain/persistence code. Reserved packages/resources use `.gitkeep` rather than empty placeholder classes. No FXML dependency is needed until an FXML file is actually introduced.

Local development database files live in ignored `data/`. Future generated PDFs/Excel workbooks belong in ignored `exports/` or `generated/`; build/test output belongs in `target/`. Reference documents, fixtures and SQL migrations remain trackable rather than globally ignoring all PDF/Excel/SQL files.

## Database Design

**Course schema implemented; relationships to future domains remain preliminary.** Initialization creates the parent directory, opens/validates SQLite and applies missing migrations. It never resets the database or inserts sample records.

- Every connection enables and verifies `PRAGMA foreign_keys = ON` and sets a five-second busy timeout.
- All variable SQL values use prepared statements.
- Storage defaults to `./data/professor-os.db`; `PROFESSOR_OS_DATA_DIR` overrides the directory. `AppPaths` owns this policy; `Database` accepts a `Path` independently.
- `initialize()` owns/closes its connection; callers own connections returned by `openConnection()`.
- SQL/file errors propagate with context. Startup logs details through `java.util.logging` and shows a short actionable message without a stack trace.

### Migration mechanism

`MigrationRunner` has an explicit append-only resource registry. Entry 1 is `db/migrations/V001__create_courses.sql`. Versions start at 1, must be contiguous and are stored in `schema_version(version, resource, checksum, applied_at)`. SHA-256 checksums use SQL with CRLF normalized to LF. A changed applied script, missing resource, incomplete history or newer unsupported version fails startup instead of silently proceeding.

The runner obtains a SQLite write lock with `BEGIN IMMEDIATE` before inspecting history. It applies all pending scripts and records their versions in one transaction; any failure rolls back pending schema/data changes and history. Already applied versions are verified but never re-executed. Existing Phase 1 databases have no domain tables/history and upgrade normally.

To add a migration, create the next numbered SQL resource and append its path to the registry. Never reorder entries or edit an applied migration. Each script block contains exactly one JDBC SQL statement; separate multiple blocks with a standalone `-- @statement` line. This explicit delimiter avoids splitting semicolons in literals or trigger bodies. Do not put transaction-control statements in scripts; the runner owns the transaction. SQL resources are version controlled with LF endings.

### Course domain and table

`Course` is an immutable Java record with `long id`, string fields, `int creditHours`, and `Instant createdAt/updatedAt`. `CourseInput` represents untrusted create/edit input without IDs or timestamps.

| Column | Storage and constraints |
| --- | --- |
| `id` | INTEGER PRIMARY KEY |
| `course_code` | TEXT NOT NULL, globally UNIQUE with NOCASE collation, 1–20 characters |
| `course_name` | TEXT NOT NULL, trimmed, 1–200 characters |
| `department` | TEXT NOT NULL, default empty, at most 120 characters |
| `credit_hours` | INTEGER NOT NULL, whole number from 1 to 30 |
| `description` | TEXT NOT NULL, default empty, at most 5000 characters |
| `created_at` | TEXT NOT NULL, valid ISO-8601 UTC instant |
| `updated_at` | TEXT NOT NULL, valid instant no earlier than creation |

The unique course-code constraint provides the lookup/order index; no speculative secondary indexes are needed. CHECK constraints protect required lengths, code format, credit range and timestamp validity. Service validation provides useful messages before these constraints are reached.

`CourseRepository` supports create, update, delete, find-by-ID, find-by-code, list and search. All variable values are bound parameters. INSERT/UPDATE RETURNING maps the saved row without a separate lookup. SQLite uniqueness failures are translated to `DuplicateCourseCodeException`, including a concurrent writer winning after the service pre-check.

`CourseService` strips surrounding whitespace and uppercases codes with `Locale.ROOT`. Codes start with an ASCII letter/digit and then permit A–Z, digits, spaces, dots, hyphens and underscores. Names, department and description support Unicode; null optional text becomes empty. Null characters are rejected. Required code/name, field limits and integer credits 1–30 are validated here, not in controllers. Limits count Unicode code points. Missing records return an actionable validation error.

Timestamps are generated from an injectable UTC `Clock`, truncated to milliseconds and serialized with `Instant.toString()`. Creation time is preserved on edits, and update time cannot go backwards when the system clock moves back. SQLite text remains readable and timezone-independent; the UI formats instants in the local timezone.

Deletion is permanent and requires explicit UI confirmation; Cancel is the default action. There are no dependent entities yet. Future migrations should enforce foreign-key restrictions and revisit archive behavior alongside actual requirements.

Tests use temporary SQLite files, never the user's database. This exercises independent connections and persistence across reopening without the connection-lifetime traps of SQLite `:memory:` databases.

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

### Decision
Use a small migration registry, concrete course repository/service and in-memory course search.

### Reason
These boundaries extend the existing architecture and keep the academic implementation explainable. A `FilteredList` updates immediately without querying SQLite on each keystroke. UI and repository search share `Course.matches`, matching literal substrings in code/name/department with locale-independent case normalization. Domain objects remain usable by future Excel/PDF services.

### Alternatives Considered
Flyway/Liquibase, ORM, generic CRUD framework, SQL search per keystroke and pagination. None is justified for the expected course-list size; revisit search/paging if measured data volume warrants it. No dependency was added for Phase 2.

## Algorithms

No academic analysis algorithms are implemented. Course search is a literal, case-insensitive scan over the loaded list. Planned documentation: exam constraint generation, question difficulty analysis, performance risk scoring and semester progress forecasting. Record inputs, assumptions, method, edge cases and validation when each is introduced.

## Implemented Features

- Main window with Dashboard, Courses, Students, Lectures, Exam Bank, Exams, Grades, Attendance, Analytics, Timeline and Settings navigation.
- Header with application title, current-course placeholder and disabled search placeholder.
- Persisted course count on the dashboard, labelled “Courses · saved”; other cards explicitly remain demo values (126 students, 2 upcoming exams, 18 pending grading items). An unavailable count is shown as unknown, not zero.
- Course creation, editing, details, confirmed deletion and immediate code/name/department search. Table sorting, empty/no-match/error states, selected saved rows and double-click editing are supported. Create/edit share a form with inline validation, default Save and Escape/Cancel behavior.
- Sample schedule, deadlines, lecture completion and recent activity. Sample dates are relative illustrative labels, not a live calendar.
- All destinations other than Dashboard and Courses explicitly identify themselves as planned.
- Transactional versioned SQLite migrations, CSS theme, Maven wrapper, repository/service tests and an opt-in desktop workflow check.

## Planned Features

- Student management and course relationships; Excel student import.
- Lecture organizer and semester timeline.
- Question bank, constraint-based exam generation and multiple exam versions.
- Required PDF exam generation and PDF answer keys.
- Grade tracking and required Excel grade export.
- Attendance, learning outcomes, question quality analysis, student performance analysis and course analytics.
- Backup and restore.

## Known Limitations

- Courses are the only persistent academic feature. No active-course selector, students, lectures, exams, grades, attendance, exports or analytics yet. Header course/search controls remain placeholders.
- Course codes are globally unique and limited to the documented ASCII format; semester-specific offerings or localized course-code formats need an explicit future decision and migration.
- Lists are loaded in full and changes from other app instances require Refresh or navigation. Multi-user editing/conflict resolution is outside this phase.
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

### 2026-10-16 — Phase 2: persistent course management

- Extended the existing layers with `Course`, `CourseInput`, `CourseRepository`, `CourseService`, `CourseController`, `CoursesView`, `CourseFormDialog` and `CourseDialogs`. Added `MigrationRunner`, version history and the V001 courses schema. No new dependencies or competing architecture.
- Added normalized, validated CRUD, unique codes, local search, details and confirmed permanent deletion. Dashboard Courses now counts persisted records; all other metrics remain explicitly demo data.
- Added 29 migration/repository/service test cases (including parameterized validation cases), bringing the normal suite to 35. Coverage includes idempotence, failed-upgrade rollback with data preservation, changed/unsupported migration history, CRUD/reopening, uniqueness, literal search, required fields, ranges, Unicode names and timestamp behavior.
- Extended the opt-in JavaFX smoke test with an isolated temporary-file workflow: empty state, required-code error, create, duplicate rejection, searches over all three fields, edit, details, cancel/confirm deletion, recreated workspace/database persistence and dashboard counts 1/0. Together with the unit tests this runs 36 tests. Screenshots are generated in `target/ui-smoke/` for review.
- Final clean build passed with zero failures/errors/skips: `cmd /d /c ".\mvnw.cmd -B -ntp -DforkCount=0 -Pui-smoke clean verify"`, with `JAVA_HOME`, `MAVEN_OPTS` and isolated `PROFESSOR_OS_DATA_DIR` set as documented. All 36 tests passed, including the full desktop workflow.
- Ran `javafx:run` against isolated `target/manual-course-qa` storage and visually checked the Courses page, empty state, form and required-code validation. Fixed a label-column sizing issue found during this check. Inspected generated populated-table, form and deletion-confirmation screenshots.
- Manual native keyboard/CRUD/restart verification remains incomplete: the desktop-control tool could click modal buttons but did not deliver text to JavaFX modal inputs reliably. The automated UI workflow verifies control-to-service persistence and recreates the entire workspace against the saved file; it does not claim to test physical keyboard events or a separate OS-process restart. No user database was used for testing.
- Build/run prerequisites and commands remain unchanged, so README was left unchanged as requested. The existing Windows shell workaround is still required on this machine; no shell or registry setting was changed.
