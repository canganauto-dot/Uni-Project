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

`ProfessorOsApplication` is the composition root: it initializes/migrates the database before showing the window and constructs CourseService plus AcademicServices. MainController wires entity controllers, navigation, the dashboard and session offering context. Views construct layouts; controllers coordinate interactions; services own validation and timestamps; repositories own JDBC and row mapping. `DemoDashboard` supplies only clearly labelled synthetic content.

Database initialization runs in JavaFX's `init()` lifecycle method, off the JavaFX application thread. Each entity controller submits service operations through a serial background worker; JavaFX Task callbacks update controls on the JavaFX thread. Writes disable the form until completion and close it only after success. Each database operation owns a short-lived connection and closes it with try-with-resources; no pool is needed yet. Expected validation failures have specific exception types; unexpected storage failures retain their causes, are logged and show concise messages.

## Directory Structure

Standard Maven `src/main/java`, `src/main/resources` and `src/test/java` layouts, under package `com.professoros`. Packages: `app`, `controller`, `view`, `demo`, `model`, `service`, `repository`, `database`, `util`. Resources: `css`, `fxml`, `images`, `db/migrations`. Migration failure fixtures live separately in `src/test/resources/db/migrations`.

The requested structure gains `view` to keep layout construction out of controllers and `demo` to isolate sample content from domain/persistence code. Reserved packages/resources use `.gitkeep` rather than empty placeholder classes. No FXML dependency is needed until an FXML file is actually introduced.

Local development database files live in ignored `data/`. Future generated PDFs/Excel workbooks belong in ignored `exports/` or `generated/`; build/test output belongs in `target/`. Reference documents, fixtures and SQL migrations remain trackable rather than globally ignoring all PDF/Excel/SQL files.

## Database Design

**Courses, academic terms, offerings, students, enrollments and lectures are implemented.** Initialization creates the parent directory, opens/validates SQLite and applies missing migrations. It never resets the database or inserts sample records.

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

Deletion requires explicit UI confirmation; Cancel is the default action. Courses with offerings cannot be deleted. Foreign-key restrictions preserve the academic history described below.

Tests use temporary SQLite files, never the user's database. This exercises independent connections and persistence across reopening without the connection-lifetime traps of SQLite `:memory:` databases.

### Phase 3 academic relationship model

```text
Course       AcademicTerm
   1              1
   |              |
   +------* CourseOffering
                  1
                  |
                  * Enrollment *-------1 Student
```

A Course is the reusable catalog definition (CS201, Object Oriented Programming). A CourseOffering delivers that course in a specific term and section. Students belong to offerings through Enrollment, a many-to-many join entity with its own status and enrollment date. No student is permanently attached to a catalog course, and no student/catalog details are copied into relationship tables.

| Migration | Table and important constraints |
| --- | --- |
| V001 (unchanged) | Existing catalog courses |
| V002 | `academic_terms`: name, academic_year, start_date, end_date, status, timestamps; unique name/year |
| V003 | `course_offerings`: course_id, academic_term_id, section, notes, status, timestamps; unique course/term/section |
| V004 | `students`: student_number, first_name, last_name, email, notes, timestamps; globally unique student number |
| V005 | `enrollments`: student_id, course_offering_id, enrolled_at, status; unique student/offering |

All tables use integer primary keys. Foreign keys use `ON DELETE RESTRICT`: offerings protect courses and terms; any enrollment history protects students and offerings. Withdrawal does not remove a row or release the uniqueness constraint; reactivation updates that row. An offering with enrollments cannot be reassigned to another course or term, enforced both by service validation and a small V005 trigger. Section labels, notes and status may still be corrected. Foreign-key lookup indexes cover term-to-offerings and offering-to-enrollments; composite unique indexes cover course-to-offerings and student-to-enrollments.

Terms use `LocalDate` and ISO `yyyy-MM-dd` storage, with an end date strictly after the start date and years 0001–9999. Names are required (80 characters maximum); academic year is a required free-form label (40 characters maximum), allowing labels such as 2026–2027. Names/years are trimmed. Term and offering statuses are PLANNED, ACTIVE, COMPLETED or ARCHIVED.

Sections and student numbers are trimmed, uppercased using `Locale.ROOT`, required and limited to 40 ASCII identifier characters (letters, digits, spaces, dots, hyphens, underscores; first character alphanumeric). Student first/last names are required, Unicode-capable and limited to 100 characters each. Optional email is trimmed, limited to 254 characters and checked for a reasonable address structure; it is not an RFC-complete parser or proof of deliverability. Notes are optional, limited to 5000 characters. Null characters are rejected; optional null text becomes empty. Timestamps remain UTC ISO-8601 instants, with creation time preserved and update time monotonic.

Enrollment statuses are ACTIVE, WITHDRAWN and COMPLETED. New and reactivated enrollment requires an existing student and a PLANNED or ACTIVE offering and term. Completed/archived terms and offerings remain readable; status changes never cascade into enrollment rows. Changing a term's dates or status is an explicit administrative action, not a background scheduling algorithm.

### Phase 3 layers and session context

Each entity has a concrete repository and service. Repositories use the existing short-lived JDBC connection/parameter binding pattern. `JdbcOperations` only shares resource ownership, parameter binding and exception translation between the four new repositories; it does not generate SQL or introduce an ORM. Joined read records (`OfferingSummary`, `EnrolledStudent`, `StudentOffering`) provide labels and roster/history data without denormalizing persistence.

Service validation handles input normalization, relationships, duplicates and deletion restrictions; database constraints remain authoritative for races. `DomainValidationException` covers expected academic failures and is now the base of the existing course validation exception. Unexpected failures retain causes in `StorageException` and are logged with concise UI messages. Academic services are explicitly composed by `AcademicServices`; no dependency injection framework was added.

`SessionContext` is a plain instance owned by `MainController`, with no static state or JavaFX dependency. It holds the selected offering for this session. The top-bar selector opens the offering, and Open reopens the same selection after navigating elsewhere. Refresh reconciles the selection by ID against current summaries, updates labels and clears missing selections. Future screens should obtain their offering identity from this context, never infer it from a catalog course.

Students has its own controller/view and an in-memory filtered/sorted list. Settings hosts Terms. Course details shows only that course's offerings; All Offerings and Create Offering actions keep the catalog table uncluttered. Offering details contains Overview and Students tabs. The roster displays real active/total enrollment counts. Dashboard shows the selected offering and real course count; student/exam/grading metric cards remain explicitly demo values.

New controllers use `UiTaskRunner` for the established JavaFX Task/background-worker pattern. Shared form code only handles layout, busy/error state and Save/Cancel behavior; each entity form explicitly declares its fields. These small helpers remove actual duplication without introducing generic CRUD frameworks. No new third-party dependencies were added.

Lectures reference CourseOffering. Future Assessment and Exam records should also reference CourseOffering; attendance should reference offering/enrollment; grades should relate Enrollment and Assessment.

### Phase 4 lecture organizer and timeline

Migration `V006__lectures.sql` adds `CourseOffering 1 → * Lecture`; V001–V005 remain unchanged. The existing transactional migration runner applies it and records its checksum. An upgrade test starts from V005 with academic data and verifies preservation and repeated initialization.

| Column | Storage and constraints |
| --- | --- |
| `id` | INTEGER PRIMARY KEY |
| `course_offering_id` | Required foreign key, ON DELETE RESTRICT |
| `lecture_number` | Required integer 1–9999; unique within its offering |
| `title` | Required trimmed text, maximum 200 characters |
| `planned_date` | Required ISO date; Java LocalDate, years 0001–9999 |
| `topics` | Optional text, maximum 5000 characters |
| `description`, `professor_notes` | Optional text, maximum 10000 characters each |
| `duration_minutes` | Required integer 1–1440 |
| `status` | PLANNED, COMPLETED, PARTIALLY_COMPLETED or CANCELLED |
| `created_at`, `updated_at` | ISO UTC instants; creation preserved and updates monotonic |

Optional text is trimmed and normalized to empty; null characters are rejected. Database CHECK and unique constraints complement useful service validation messages. Cancelled lectures retain their numbers. An offering/date/number index supports chronological retrieval. Lectures prevent offering deletion and reassignment to another course or term; service checks and a V006 trigger protect history, including direct database writes. Confirmed lecture deletion is permanent.

`Lecture`, `LectureInput`, `LectureStatus`, `LectureProgress` and `LectureOverview` are independent domain records/enum. `LectureRepository` uses the existing prepared JDBC helper and scopes reads, updates and deletion to offering ID. `LectureService` validates input, relationships and duplicates, owns timestamps, and computes simple progress with an injectable Clock. Lecture input cannot move a lecture to another offering. No new dependencies or generic framework were introduced.

`LectureController` coordinates the Lectures table and chronological Timeline using `LecturesView` and one reusable `LectureFormDialog`. Both use the current session offering, provide immediate literal search over number/title/topics/description/notes, and support create, edit, confirmed deletion, empty/error states and refresh. Timeline ordering is planned date, lecture number, then ID. Switching offerings keeps these pages open and reloads their context; request revisions ignore stale asynchronous results. Form operations capture their offering ID when opened.

Planned dates outside the term and corrections to lectures in completed/archived offerings are allowed: historical correction is useful, and no term-bound restriction was requested. The timeline is an offering-specific chronological list, not a calendar or forecasting module. No automatic status transitions are performed.

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

Search is a literal, case-insensitive scan over loaded records. Lecture progress reports total, completed, partial and cancelled counts. Completion percentage is fully completed / (total minus cancelled), or zero when that denominator is zero; partial lectures receive no invented fractional weight. Counts always cover the full offering, not just search matches.

Basic schedule status compares unfinished lectures (planned or partial) with today's local date: dates before today are overdue; today's lectures are due today; otherwise show the next planned date. Empty, all-cancelled and fully completed plans have distinct messages. Counts and schedule share one loaded snapshot and update on refresh/navigation or a successful edit, not a midnight timer. These are descriptive rules, not advanced analytics or forecasts.

Planned documentation: exam constraint generation, question difficulty analysis, performance risk scoring and semester progress forecasting. Record inputs, assumptions, method, edge cases and validation when each is introduced.

## Implemented Features

- Main window with Dashboard, Courses, Students, Lectures, Exam Bank, Exams, Grades, Attendance, Analytics, Timeline and Settings navigation.
- Header with application title, session offering selector, Open action and disabled global search placeholder.
- Persisted course count on the dashboard, labelled “Courses · saved”; other cards explicitly remain demo values (126 students, 2 upcoming exams, 18 pending grading items). An unavailable count is shown as unknown, not zero.
- Course creation, editing, details, confirmed deletion and immediate code/name/department search. Table sorting, empty/no-match/error states, selected saved rows and double-click editing are supported. Create/edit share a form with inline validation, default Save and Escape/Cancel behavior.
- Sample schedule, deadlines, lecture completion and recent activity. Sample dates are relative illustrative labels, not a live calendar.
- Students provides directory management; Settings provides academic term management. Courses includes offering creation, course-specific offering lists and access to each offering's roster.
- Enrollment supports selecting an existing student, duplicate prevention, withdrawal, completion and reactivation while retaining history.
- Lectures and Timeline provide persisted offering-specific lecture organization, search, chronological ordering, completion counts and basic schedule status.
- All other destinations explicitly identify themselves as planned.
- Transactional versioned SQLite migrations, CSS theme, Maven wrapper, repository/service tests and an opt-in desktop workflow check.

## Planned Features

- Excel student import.
- Broader semester planning beyond the offering-specific lecture timeline.
- Question bank, constraint-based exam generation and multiple exam versions.
- Required PDF exam generation and PDF answer keys.
- Grade tracking and required Excel grade export.
- Attendance, learning outcomes, question quality analysis, student performance analysis and course analytics.
- Backup and restore.

## Known Limitations

- No exams, grades, attendance, exports or advanced analytics yet. Global header search remains a placeholder. Enrollment uses existing students; create new students in the directory first.
- Course codes, student numbers and sections use the documented ASCII identifier format. Localized identifier formats need an explicit future decision and migration.
- Active offering selection lasts for the running session only. Academic statuses are explicit user choices, not automatic date-driven transitions; multiple active terms are allowed.
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

### 2026-10-17 — Phase 3: academic offerings and enrollment

- Added migrations V002–V005 without editing V001. Existing course data survives the upgrade; history checksums and repeated initialization are tested.
- Implemented AcademicTerm, CourseOffering, Student and Enrollment records, repositories/services, relationship read models and session context. Added corresponding controllers/views/forms using the existing JavaFX/JDBC architecture. No dependencies were added.
- Added 22 normal test cases: migration upgrade/idempotence/date constraints, term CRUD, offering uniqueness/lookups, student CRUD/search/uniqueness, enrollment in both directions, retained withdrawal history, missing parents, deletion restrictions, protected offering reassignment, required fields/date validation/email validation, enrollment eligibility and independent session contexts. The normal suite now contains 57 tests.
- Extended the opt-in desktop scenario with actual term/offering/student form submissions, Ahmed Ali (2026001), Sara Hassan (2026002), two enrollments, duplicate rejection, student search, session selection and workspace reconstruction from the saved database. The desktop profile runs 58 total tests.
- Inspected generated term, offering, student and roster screenshots. Added consistent combo-box/date-picker/tab styling and a top-bar Open action to reopen the current offering after other navigation.
- Launched the actual application in a separate Java process against the generated isolated fixture `target/academic-workflow-data/professor-os.db`. After that process closed, launched it again and manually selected CS201 / Fall 2026–2027 / Section A. The course/term/offering label and roster showed both Ahmed and Sara with ACTIVE enrollments after restart. The new session correctly began without an active selection. Closed the verification window afterward.
- Form creation and duplicate checks were exercised by automated JavaFX controls; manual checks covered the launched application, selection, roster and restart persistence. Native keyboard entry into every modal was not reverified, given the desktop-tool limitation recorded in Phase 2.
- Updated README feature status/workflow and this notebook. Preserved existing Git history, local Windows shell settings, user data and future export boundaries.
- Final verification passed on 2026-10-17: `cmd /d /c ".\mvnw.cmd -B -ntp -DforkCount=0 -Pui-smoke clean verify"`, with the documented JAVA_HOME/MAVEN_OPTS and isolated smoke data directory. All 58 tests passed with zero failures, errors or skips; Git whitespace checks passed.

### 2026-10-17 — Phase 4: lecture organizer and timeline

- Added V006, Lecture domain/repository/service, shared lecture form, Lectures table, chronological Timeline and simple progress/schedule rules. Extended session offering navigation and protected offerings with lecture history. Existing migrations and Course functionality were preserved; no dependencies added.
- Added 26 normal test cases: V005 upgrade/data preservation/idempotence; repository CRUD/reopening, offering isolation, uniqueness, ordering/search, all statuses, checks and foreign keys; service normalization, invalid inputs, timestamp behavior, relationship restrictions and progress/date edge cases. The normal suite has 83 tests.
- Extended the desktop workflow with actual form submission, required-field and duplicate rejection, search, edit, cancelled/confirmed deletion, offering switching on both pages, chronological order, empty states, progress and workspace reconstruction against the saved temporary database. All test data remains isolated from user storage.
- Final clean build passed using the documented Windows workaround: `cmd /d /c ".\mvnw.cmd -B -ntp -DforkCount=0 -Pui-smoke clean verify"`. All 84 tests passed with zero failures, errors or skips. The desktop profile starts the application and exercises its controls; form, table and timeline snapshots were visually inspected, including minimum window size.
- Persistence was verified through new connections and a rebuilt workspace. This phase did not separately verify an OS-process restart or physical keyboard entry in every dialog; automated control interaction is not claimed as manual keyboard testing. The existing Windows AutoRun workaround remains necessary and no machine settings were changed.
- Updated README status/workflow and this notebook. Exams, grades, attendance, exports and advanced analytics remain outside scope.
