# Professor OS

## Project Goal

Build an all-in-one offline desktop workspace for university professors: courses, students, lectures, exams, grades, attendance and semester planning. The software should reduce fragmented administration while remaining understandable and defensible as an academic Java software engineering project. PDF exam generation and Excel grade export are implemented.

## Technology Stack

| Technology | Version / status | Purpose |
| --- | --- | --- |
| Java | 25 LTS; verified local JDK 25.0.1 | Language, standard logging, JDBC |
| Maven | Wrapper pins 3.9.11 | Repeatable build and dependency resolution |
| Maven Compiler / Surefire | 3.14.1 / 3.6.0 | Java 25 compilation and JUnit execution |
| SQLite JDBC | 3.53.4.0 | Local storage engine and JDBC driver |
| JUnit Jupiter | 5.14.0, test scope | Meaningful foundation tests |
| Kotlin / Compose Desktop / Gradle | 2.3.20 / 1.10.3 / 9.3.1 | Sole desktop frontend, calling the Java backend |
| Apache POI | 5.5.1 | Excel student import and grade export |
| Apache PDFBox / ICU4J | 3.0.8 / 78.3 | PDF generation/preview and Arabic shaping/bidi |

Version availability was checked against Maven Central. Setup guidance: [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/), [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc), [JUnit 5 release notes](https://docs.junit.org/5.14.0/release-notes/).

## Architecture

Dependency direction: **Compose UI → Java services → repositories → database**. Domain models remain independent of UI toolkits and JDBC. Main.kt composes DashboardBridge, WorkspaceServices and WorkspaceController; screen files build layouts, controllers coordinate background IO, services own business validation/calculations, and repositories own JDBC. There is no JavaFX dependency or alternate frontend.

Database initialization and service operations run off the Compose thread. Writes retain local edits until success; expected validation failures are actionable and unexpected failures are logged. Each database operation owns a short-lived connection and closes it with try-with-resources. No pool, backend server or generic CRUD framework is needed.

## Directory Structure

The root Maven project contains Java `app` (storage policy), `model`, `service`, `repository`, `database`, `pdf` and `excel` packages, tests and resources. PDF fonts and immutable migration resources remain in `src/main/resources`; migration failure fixtures remain in `src/test/resources`. JavaFX view/controller/theme packages, launcher, CSS and unused FXML/image placeholder directories were removed.

`prototype-compose/` is now the sole desktop frontend; its historical folder name is intentionally retained to avoid unrelated build-path changes. It uses the existing Gradle/Kotlin configuration, consumes the Maven backend JAR and copied runtime libraries, and owns UI tests, desktop fonts and icons. Backend preparation removes stale generated classes/libraries before packaging, without deleting test reports or user data.

Local development data lives in ignored `data/`; generated exports belong in `exports/` or `generated/`. Maven output belongs in `target/`, Compose output in `prototype-compose/build/`. Reference documents, fixtures and SQL migrations remain version controlled.

## Database Design

**Courses, academic terms, offerings, students, enrollments, lectures, the question bank, exams, grades and attendance are implemented.** Initialization creates the parent directory, opens/validates SQLite and applies missing migrations. It never resets the database or inserts sample records.

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

`SessionContext` is the original toolkit-independent Java session helper, retained with its backend tests. Compose owns active offering identity in Main/WorkspaceController without global state. Refresh reconciles the selection by ID against current summaries and clears missing selections. Offering-scoped screens obtain their identity from that selection, never infer it from a catalog course.

Compose provides separate Students, Terms, Offerings and Teaching context routes. Course details links to offerings, and the teaching-context roster exposes enrollment history and status changes. Dashboard counts and offering progress use saved records. The Phase 3 JavaFX controller/view implementations were removed after Compose feature migration.

Compose controllers coordinate the same services through background coroutine tasks. Shared controls handle layout, busy/error state and Save/Cancel behavior; each entity form explicitly declares its fields. The former JavaFX UiTaskRunner has been removed.

Lectures, Exams and Assessments reference CourseOffering. Grades relate Enrollment and Assessment within that same offering; attendance relates Enrollment and Lecture within the same offering.

### Phase 4 lecture organizer and timeline

The description below records the Phase 4 design. The separate Timeline view/navigation was subsequently removed during the Compose evaluation; the Lectures table, persisted records, ordering, calculations and migration remain unchanged.

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

### Phase 5 question bank

Migration `V007__question_bank.sql` adds `questions` and `question_choices`. V001–V006 remain unchanged. A question belongs to the reusable catalog Course, not a CourseOffering, so it can be reused across semesters and sections without duplication.

```text
Course 1 ── * Question 1 ── * AnswerChoice
                   |
                    ExamQuestion ── Exam ── CourseOffering
```

| Question column | Storage and rules |
| --- | --- |
| id / course_id | Integer primary key / required Course foreign key, ON DELETE RESTRICT |
| question_type | MCQ, TRUE_FALSE, SHORT_ANSWER, ESSAY, CODING |
| question_text | Required trimmed Unicode text, maximum 20000 characters |
| topic | Optional trimmed text label, maximum 200 characters |
| difficulty | EASY, MEDIUM, HARD |
| marks_hundredths | Integer 1–100000; Java BigDecimal marks 0.01–1000, at most two decimal places |
| answer / explanation | Optional text up to 20000 characters each; True/False requires TRUE or FALSE |
| image_path | Optional absolute local path, at most 2000 characters |
| correct_choice_position | Required only for MCQ; deferred composite foreign key to a choice of this question |
| usage_count / last_used_date | Nonnegative integer default 0 / nullable ISO LocalDate, initially null |
| created_at / updated_at | ISO UTC instants; preserved creation time and monotonic update time |

Choices have composite key `(question_id, position)`, positions 1–10, required text up to 2000 characters and ON DELETE CASCADE to their owning question. Only these owned choice rows cascade; courses with bank questions cannot be deleted. A question has one authoritative correct-choice position, so a correct answer cannot point into another question. The composite foreign key is deferred until commit to allow atomic insertion and replacement of the question/choices. CHECK constraints enforce enum values, marks and answer shape; triggers disallow choices on non-MCQ questions. Service validation additionally requires 2–10 nonblank, case-insensitively distinct choices and exactly one selected correct answer. No separate free-text MCQ answer competes with that selection.

`Question`, `QuestionInput`, `AnswerChoice`, `QuestionType` and `Difficulty` are independent domain types. `QuestionRepository` uses prepared SQL and a small transaction callback added to the existing `JdbcOperations` helper. Every aggregate write commits question and choices together; failures roll back both, preserving rollback exceptions as suppressed diagnostics. Aggregate reads share one transaction for a consistent question/choice snapshot. The current list loads all questions and their choices; pagination/bulk reads should be considered if measured bank size warrants them. An index covers course/type/difficulty.

`QuestionService` owns validation, normalization, course existence and timestamps. Null optional text becomes empty; null characters and excess lengths are rejected. Marks are stored as integer hundredths to avoid binary floating-point rounding. True/False answers normalize to uppercase. Short-answer, essay and coding model answers are optional because professors may build prompts before writing a marking guide. Editing preserves usage metadata; input models and forms cannot alter it. No usage is fabricated on viewing or editing questions.

`QuestionController`, `QuestionsView` and `QuestionFormDialog` follow the existing background-worker/form conventions. Exam Bank provides CRUD, fresh details, confirmed deletion, immediate literal search over question/topic/answer/explanation/choices, and combined course/type/difficulty filters. Filters do not depend on session offering. Save clears filters and selects the saved question. The form shares create/edit logic with tabs for question, MCQ choices, answer/explanation and image path. Empty states distinguish missing courses, an empty bank, no matches and load errors.

Topic association is an optional free-text label at this stage, not an invented topic-management subsystem; a later taxonomy can migrate labels to entities. Images are local path references only: the file chooser is a convenience, missing files are allowed so moving files does not prevent editing question text, and there is no image copying, preview, execution or export. Paths must be absolute and syntactically valid; portability and managed attachments remain future work.

Phase 6 implements the planned ExamQuestion relationship and frozen question/choice content described below. Published exams do not change when the bank is edited. No third-party dependencies were added.

### Phase 6 exam builder

Migration `V008__exam_builder.sql` adds three tables; V001–V007 remain unchanged:

- `exams`: integer primary key, required CourseOffering foreign key (ON DELETE RESTRICT), title (1–200 characters), ISO exam date, duration (1–1440 whole minutes), intended marks in integer hundredths (0.01–100000 marks), instructions (up to 20000 characters), DRAFT/FINALIZED/ARCHIVED status and ISO UTC timestamps.
- `exam_questions`: composite primary key (exam_id, question_id), unique ordered position 1–100 per exam, source question foreign key (ON DELETE RESTRICT), and saved type/text/topic/difficulty/marks/answer/explanation/image path. An exam owns these rows (ON DELETE CASCADE). A bank question can occur once in a given exam.
- `exam_question_choices`: ordered copied answer choices with correctness flags, owned by the composite exam-question relationship. At most one correct choice is enforced by a partial unique index; copying validated MCQs preserves the full choice configuration.

Indexes support offering/date lookup and finding exams that reference a bank question. Triggers reject cross-course exam-question links, moving an exam to another offering, moving a referenced bank question to another course, and moving an offering with exams to another course or term. Services also provide useful errors. Referenced bank questions cannot be deleted; offerings with exams are protected even when drafts are empty.

`Exam`, `ExamInput`, `ExamQuestion`, `ExamStatus` and `GenerationRequest` remain independent domain types. `ExamRepository` saves metadata and the complete ordered selection in one transaction. It rechecks offering/course identity and draft status in that transaction, snapshots newly selected bank questions, preserves existing snapshots when reordering/editing an exam, and rolls back all changes on failure. `ExamService` validates required fields, ISO dates, limits, decimal marks and unique selections. Empty drafts are allowed; a finalized exam must contain at least one question.

Marks use BigDecimal in Java and integer hundredths in SQLite. Calculated total is the exact sum of saved question marks; intended marks are a separate professor-entered target. A mismatch is a visible warning, not an implicit rescaling or a prohibition on saving/finalizing. There is no automatic mark-budget constraint in this phase.

Finalize is an explicit confirmed DRAFT → FINALIZED transition. It makes content read-only in application workflows and increments each selected bank question's usage count once, recording the local finalization date as last-used (never moving it backwards). Usage here means inclusion in a finalized exam, not proof that an exam was administered. Creating/editing drafts or generation previews does not record usage. FINALIZED → ARCHIVED preserves everything and does not count again. Only drafts can be deleted; there is no unfinalize action. Image paths are snapshotted strings, not copied image bytes.

`ExamController`, `ExamsView`, `ExamBuilderDialog` and `ExamGenerationDialog` extend the existing view/controller/background-service pattern. Exams follow the session offering and ignore stale load callbacks after a context change. The builder keeps a local selection until Save succeeds, supports adding/removing/reordering, filters bank questions by type/difficulty/topic/text, and displays live totals. The course is fixed by the offering and labelled rather than exposed as an unsafe cross-course selector. Generation replaces the local selection only after success, with confirmation when replacing a nonempty selection. Details display saved snapshots, including answers.

The current exam represents a single paper. Future A/B/C/D support should introduce an ExamVersion child (label/seed/version-specific ordering), then move the existing ordered snapshot collection under a default version in a new migration. Source question IDs, independent content snapshots and explicit order are already separated from exam metadata; no choice randomization or speculative version-generation UI is introduced now.

## Major Technical Decisions

### Decision
Use Java 25 LTS and Maven for backend services, with Kotlin/JVM Compose Desktop and Gradle for the UI.

### Reason
Business logic remains Java for the academic requirement; Compose provides the approved desktop interface without a web runtime. Both build tools have pinned wrappers. The original JavaFX foundation was superseded after feature migration and parity verification.

### Alternatives Considered
Retaining JavaFX, Swing, a web frontend and Java 21 LTS. The current project retains Java 25 and avoids an unrelated language/build reorganization.

### Decision
Keep the non-modular Java backend and the existing Compose Gradle frontend with shared presentation tokens.

### Reason
Explicit service construction keeps dependencies explainable. Compose shared components and semantic palette tokens keep presentation separate from academic behavior. No dependency-injection framework or JPMS configuration is needed.

### Alternatives Considered
Dependency injection and JPMS runtime packaging. Revisit distribution packaging for an installer; the Maven JAR is a backend library, not a self-contained desktop executable.

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

### Basic exam generation

`ExamGenerator` is a pure, deterministic, bounded backtracking selector. Input is the offering's course bank (using existing saved snapshots for questions already in the edited exam), required question count 1–100, and optional exact distributions by difficulty, type and topic. A whole empty distribution is unrestricted; otherwise its nonnegative integer counts must sum to the total and omitted categories mean zero. Topic matching normalizes surrounding whitespace and case; the empty topic represents untagged questions.

Candidates outside positive quotas are removed, then ordered by ascending usage count and stable question ID. Depth-first selection tries each distinct question at most once per branch. Remaining eligible counts prune branches that cannot meet the total or any quota. Failed choices backtrack rather than silently relaxing a constraint or relying on random retries. All quotas hold when the requested count is reached, since each constrained distribution started with that total and cannot go negative. Fixed inputs always produce the same result.

Too few candidates or exhausted search reports that constraints cannot be satisfied; incompatible overlapping distributions receive an explicit joint-feasibility message. A 100000-node search budget and 1000 eligible-question limit bound basic generation. Reaching the search budget reports feasibility as **unknown**, not impossible, and suggests simplifying constraints/manual building. No partial solution is returned or saved. Tests compare small distributions with an independent exhaustive-subset oracle, cover backtracking past an early dead end, and distinguish search limits from infeasibility.

This is a selection preview, not automatic persistence. The UI subsequently saves the professor-reviewed ordered IDs. Retained exam questions keep their snapshots; newly selected questions are read again at save time. Concurrent bank editing by another application instance can therefore change new-question content between preview and save; multi-instance version-conflict detection remains future work. Exact target marks, learning outcomes and A/B/C/D generation are not constraints in this first algorithm.

Search is a literal, case-insensitive scan over loaded records. Lecture progress reports total, completed, partial and cancelled counts. Completion percentage is fully completed / (total minus cancelled), or zero when that denominator is zero; partial lectures receive no invented fractional weight. Counts always cover the full offering, not just search matches.

Basic schedule status compares unfinished lectures (planned or partial) with today's local date: dates before today are overdue; today's lectures are due today; otherwise show the next planned date. Empty, all-cancelled and fully completed plans have distinct messages. Counts and schedule share one loaded snapshot and update on refresh/navigation or a successful edit, not a midnight timer. These are descriptive rules, not advanced analytics or forecasts.

Planned algorithm documentation: advanced exam constraints, question difficulty analysis, performance risk scoring and semester progress forecasting. Record inputs, assumptions, method, edge cases and validation when each is introduced.

## Implemented Features

- Compose Desktop shell with Overview, Courses, Students, Terms, Offerings, Teaching Context, Lectures, Question Bank, Exam Builder, Gradebook, Attendance, Activity and Settings.
- Real persisted dashboard counts, offering progress, upcoming/recent activity and saved attendance summaries. No demo academic data is seeded.
- Catalog/course/student/term/offering CRUD, search, details and protected deletion; session teaching-context selection and enrollment status/history.
- Offering-scoped lecture organization and progress, five question types with MCQ validation, manual/constraint-based exam composition and saved snapshots, finalization/archive.
- Separate Student Exam and Answer Key PDF preview/export with safe new-file selection, pagination, images, coding text and basic Arabic support.
- Weighted categories/assessments, spreadsheet grades, bulk entry, configurable scales, incomplete results and class statistics. Blank and zero remain distinct.
- Lecture attendance marking/clearing, whole-roster actions, saved history/percentages and academic-history restrictions.
- Bilingual Excel student import with mapping/preview and optional enrollment; four-sheet grade/attendance/statistics reports.
- Persisted Light/Dark/System appearance, storage configuration guidance, fixed restrained animations, shared controls and unsaved-change guards.
- Transactional SQLite migration/checksum validation, pinned Maven/Gradle wrappers, backend and Compose workflow tests. JavaFX is removed.

## Planned Features

- Broader semester planning, advanced generation constraints and multiple exam versions.
- Further PDF customization, learning outcomes and advanced question/student/course analytics.
- Backup/restore and production packaging.

## Known Limitations

- No advanced analytics yet; search is scoped to individual feature screens. Excel supports student import and saved grade/attendance export; see Phase 9 and Phase 11 limits.
- Question usage counts finalized exams, not draft selections or previews. Image paths are external references and are not bundled, previewed or checked for existence. Topic labels are free text rather than managed entities.
- Exam generation has explicit count/bank/search limits and no exact-mark optimization. Version variants remain future work. Snapshot locks are enforced by the application repository/service workflows, not protection against arbitrary external SQL edits.
- Course codes, student numbers and sections use the documented ASCII identifier format. Localized identifier formats need an explicit future decision and migration.
- Active offering selection lasts for the running session only. Academic statuses are explicit user choices, not automatic date-driven transitions; multiple active terms are allowed.
- Lists are loaded in full and changes from other app instances require Refresh or navigation. Multi-user editing/conflict resolution is outside this phase.
- No installer or packaged runtime; a compatible JDK and Maven/Gradle dependency caches are needed for development launch.
- Only local Windows desktop validation is available at initialization; other desktop platforms need their own checks.
- Database path is currently relative to the launch directory unless overridden. Selecting a different location does not migrate existing data.
- The dashboard uses a desktop minimum size of 880 × 660 and scrolls vertically; it is not a mobile layout.
- Java/Maven were initially absent from PATH. An existing JDK was found under the user profile; Maven was bootstrapped outside the repository. Set `JAVA_HOME` per README; no machine-wide settings were changed.
- Maven 3.9.11 may print a JDK 25 deprecation warning from its bundled Guice dependency. This is build-tool output, not an application compilation failure.
- This machine has a pre-existing Command Processor `AutoRun` setting (`color 02`) that returns a nonzero status in console-less shells. Normal forked testing reports a process failure despite passing assertions, including with Surefire 3.6.0. Local verification uses `cmd /d /c` for the wrapper and `-DforkCount=0` to run tests in Maven's JVM; default isolated testing is retained for unaffected machines. This workaround has less process isolation. No registry settings were modified.

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

### 2026-10-17 — Phase 5: exam question bank

- Added V007 without changing previous migrations. Implemented Course-owned questions, ordered MCQ choices and atomic aggregate persistence, with service/controller/view layers matching existing conventions. Added no dependencies.
- Added 26 normal test cases (109 total): V006 upgrade/idempotence/foreign-key integrity, all question types, aggregate CRUD and reopening, failed-write rollback, course/correct-choice constraints, type transitions, preserved usage metadata, search/combined filters, marks limits, required fields, invalid MCQ configurations, Unicode normalization, True/False answers and timestamp preservation.
- Extended the desktop workflow to submit all five question types, reject missing courses and invalid MCQ configuration, edit a question, view details, combine/reset filters, exercise empty/no-match states, cancel/confirm deletion and reconstruct the workspace against the saved temporary database. No user database was touched.
- Inspected form, details and bank snapshots, including the minimum window size. Corrected cramped MCQ action labels and missing all-filter labels found during visual inspection.
- Final clean verification succeeded with the documented Windows workflow: `cmd /d /c ".\mvnw.cmd -B -ntp -DforkCount=0 -Pui-smoke clean verify"`, with JAVA_HOME/MAVEN_OPTS and isolated smoke storage set per README. All 110 tests passed with zero failures, errors or skips; whitespace checks passed.
- Desktop verification exercised actual JavaFX controls and inspected generated screenshots. Persistence was checked using new database connections and a reconstructed workspace; separate OS-process restart and physical keyboard/file-chooser interaction were not manually reverified. The existing Windows AutoRun workaround remains unchanged.
- Updated README and this notebook. Image references, free-text topic labels and future exam snapshot/usage policy are documented; automatic exam generation and PDF export remain unimplemented as requested.

### 2026-10-18 — Phase 6: exam builder

- Added V008 for exams, ordered exam-question snapshots and saved choices. Previous migrations and Git history are preserved; no dependencies were added.
- Implemented offering-scoped draft building, add/remove/reorder, course-safe bank filtering, mark-total warnings, saved details, explicit finalization/usage accounting and archiving. The generator validates exact count/distribution constraints, deterministically backtracks, reports unsatisfied requests and distinguishes search-budget exhaustion from proven infeasibility.
- Added 19 non-UI tests (128 total): migration upgrade/idempotence, manual ordering/removal/reopening, exact decimal totals, snapshot preservation after bank edits, cross-course and wrong-offering rejection, rollback, history restrictions, finalization exactly once, required/range validation, deterministic selection, joint infeasibility, backtracking, search limits and comparison with an independent exhaustive-subset oracle over small distributions.
- Extended the desktop scenario with empty/no-offering states, real form submission, adding/filtering/removing/reordering, mismatch warnings, failed and successful generation, offering switching, reopening persisted exams, finalization, details and cancelled/confirmed draft deletion. Screenshots of builder, generation, saved details and the minimum-size Exams page were inspected. Fixed asynchronous test waits, the generation dialog's action-button initialization and truncated instructions found during verification.
- Final clean build passed using the documented Windows workflow: `cmd /d /c ".\mvnw.cmd -B -ntp -DforkCount=0 -Pui-smoke clean verify"`, with JAVA_HOME/MAVEN_OPTS and isolated smoke storage configured per README. All 129 tests passed with zero failures, errors or skips.
- Verification used automated JavaFX controls and visual snapshot inspection. Persistence was checked through new connections and workspace reconstruction, not a separately automated OS-process restart. Physical keyboard/file-picker behavior was not manually reverified. The existing Windows AutoRun workaround is unchanged.
- README and technical notes now describe the generation limits, finalization semantics, snapshot policy and future ExamVersion migration direction. PDF generation and A/B/C/D variants remain outside this phase.

### 2026-10-18 — Phase 7: PDF exam generation

- Added PDFBox 3.0.8 and ICU4J 78.3, pinned after checking Maven Central and official API documentation. PDFBox provides both writing and preview rasterization; ICU supplies Arabic shaping and bidirectional ordering absent from the low-level PDF text API. OpenPDF was considered; PDFBox keeps generation and preview in one library. No Excel or unrelated dependencies were added.
- Bundled unmodified Noto Sans regular/bold, Sans Mono and Naskh Arabic static fonts, subset-embedded into each PDF. The fonts README records the pinned upstream commit; the accompanying SIL OFL 1.1 license is included. Rendering does not depend on locally installed fonts.
- Added the pdf package: ExamPdfRenderer, PdfFonts, PdfImages, PdfKind, ExamPrintSource, ExamPrintProfile and RenderedExamPdf. ExamPdfService loads saved exams, renders previews and writes files; ExamPdfController coordinates background work and ExamPdfDialog builds the UI. Existing exam business logic and repositories remain separate from rendering.
- No migration was necessary: V001–V008 are unchanged. Questions, order, marks, choices, answers, explanations and image references come only from saved exam snapshots. Current course/offering labels supply the header; university/department/professor/logo are export-session inputs rather than persisted settings. Drafts are explicitly labelled DRAFT.
- Implemented A4 layout with institutional and course headers, date/duration/instructions, actual marks, student name/ID fields, numbered questions, per-question marks, automatic wrapping/pagination and Page X of N. All five types render; MCQ choices retain saved order, coding text uses shaded monospace blocks with preserved line breaks and four-space tabs. Written responses have answer space. Heading layout reserves room for following text.
- Student output never emits stored model answers, explanations or correct-choice indicators; Answer Key is generated independently and includes them. Both documents naturally include every MCQ option. Professors remain responsible for answers manually embedded in prompts or image pixels. Images are decoded/reencoded without source metadata; PDF metadata includes document identity, not answer fields.
- PNG/JPEG logos and question images are proportionally scaled. Missing/invalid images fail export with an actionable message. Limits are 10 MB/20 megapixels per image and 500 pages. Snapshot image paths refer to external files, not frozen image bytes: preserve those files for exact future reproduction.
- Arabic uses ICU contextual forms/bidi and embedded Naskh glyphs, RTL line alignment and mixed Latin support. This is basic Arabic/RTL support, not full OpenType typography or a localized interface. Complex diacritics, other scripts, math typesetting and emoji are not guaranteed; unsupported glyphs fail explicitly. Copy/extraction order for shaped Arabic may differ from logical input. No PDF/A or tagged-accessibility claim is made.
- Preview renders one page at a time with Previous/Next and invalidates when document type or print details change. Save writes the exact reviewed bytes. Student Exam and Answer Key are previewed and saved separately. Mark mismatches display both totals, require explicit acknowledgement and never change exam content.
- Export stages a complete file in the destination directory and moves it without REPLACE_EXISTING; existing files are rejected even when selected through the native chooser. Failures retain diagnostic causes, show concise messages and clean up staging files where possible.
- Added eight PDF tests: all question types, student confidentiality including metadata/annotations, saved snapshots after bank edits, independent student/key files, A4 margins/long-text pagination, images/missing/corrupt images, embedded fonts, mixed Arabic, unsupported glyphs, mismatch acknowledgement and overwrite prevention. Extended the existing desktop workflow to open export, generate both previews and verify stale-preview invalidation.
- Clean verification passed using the documented Windows workaround with isolated smoke storage: cmd /d /c ".\mvnw.cmd -B -ntp -DforkCount=0 -Pui-smoke clean verify". All 137 tests passed, zero failures/errors/skips (136 non-UI plus the desktop workflow). Student/key/Arabic PDFs and desktop snapshots were visually inspected; an orphaned answer heading found during review was corrected before the final build.
- Native file-picker/physical keyboard interaction was not manually exercised; file writing, preview decoding and overwrite rejection are covered by isolated tests. No known build/test failures remain. A/B/C/D versions, grades, Excel and advanced analytics remain outside scope.

### 2026-10-18 — Phase 8: grade management

#### Schema and relationships
V009__grade_management.sql adds assessment_categories, assessments, student_grades and grading_scales. V001–V008 are unchanged. Categories and assessments belong to CourseOffering. Each grade identifies an Enrollment and Assessment; composite foreign keys require both to belong to the recorded offering. Student/catalog details are never copied into grade cells. A unique enrollment/assessment key permits exactly one score.

Weights, maximum marks, score values and scale thresholds are stored as integer hundredths, avoiding SQLite floating-point drift; domain APIs use BigDecimal. Scores and weights accept at most two decimal places. Categories require unique names within an offering, 0–100% weight; assessment titles are required, dates are ISO LocalDate, maxima are 0.01–100000. Scores range from zero through the assessment maximum. Database triggers reject excessive scores and maximum reductions that invalidate existing grades. Timestamped categories, assessments and grades preserve creation time and use fixed-millisecond UTC ISO text with monotonic updates.

Category deletion is restricted while assessments use it. Offering deletion/reassignment is restricted by grading history. Enrollment deletion is restricted by grades. Explicitly confirmed assessment deletion cascades only to that assessment's grade cells; the confirmation states that those grades are permanently removed. Blank entries delete a grade cell, while a numeric zero remains a saved score. No speculative Exam-to-Assessment coupling was introduced.

#### Calculation policy
For each positive-weight category: sum earned marks / sum maximum marks across its assessments × category weight. Thus a 20-point and an 80-point assessment contribute in a 1:4 ratio within their category. Category contributions sum to weighted points out of 100, equivalent to percentage when weights total 100%. Weights are never silently normalized. Individual weights must be valid; the UI warns when their total differs from 100%.

Missing scores contribute no earned points to the displayed provisional subtotal, but remain missing rather than becoming zero records. Final percentage classification requires weights totaling exactly 100%, an assessment in every positive-weight category, and all such assessments graded. Otherwise the result is INCOMPLETE, displays graded/required counts, and withholds letter/pass/fail. Empty positive-weight categories are named in a warning. Zero-weight categories are optional practice and do not affect totals or completeness.

The default per-offering grading scale is A ≥90, B ≥80, C ≥70, D ≥60, F below D; pass ≥60. All five minimum thresholds are configurable to hundredths; A > B > C > D and each threshold lies in 0–100. Pass is intentionally an independent threshold. Letter labels remain A/B/C/D/F in this phase. Reduced rational arithmetic retains exact category fractions and compares thresholds before two-decimal HALF_UP display rounding, avoiding boundary errors from repeating decimals. A displayed 90.00 can therefore still be B if the exact result is below 90.

Basic class statistics show complete/incomplete counts and, for complete results, mean/min/max and pass count. They cover the whole offering regardless of search. Withdrawn enrollments are visible for historical correction but excluded from class statistics; ACTIVE and COMPLETED enrollments are included. Statistics use the displayed two-decimal results. There is no forecasting, ranking, curve, extra credit or missing-as-zero finalization policy.

#### Implementation and workflow
GradebookRepository follows existing prepared-JDBC conventions. GradebookService validates inputs/relationships and owns operations. GradeCalculator is pure Java arithmetic with independent tests. GradebookDraft keeps unsaved cell strings, including invalid input, outside persistence. These domain/service APIs expose IDs and decimal values that future Excel adapters can reuse without depending on JavaFX.

GradebookController coordinates background loads/writes and GradebookView/GradeForms compose the interface. Grades uses the active offering with Gradebook, Assessments and Categories & Scale tabs. Students are rows; assessments are editable columns with maxima. Blank is labelled Ungraded. Search matches student number/full name, table headers sort, and wide gradebooks scroll horizontally. Totals/statistics update as valid edits are typed; invalid input remains editable with a useful error.

Bulk Entry accepts plain text with student number TAB score per line for one selected assessment. It validates every line, rejects unknown/duplicate students, then stages all changes together. This is a text-entry convenience, not Excel/CSV import. Save Grades validates the whole change set and persists it in one transaction; an invalid write rolls the batch back. Discard/Reload, navigation, offering changes and window close protect unsaved edits with a cancellation-first confirmation. Structural settings require existing grade edits to be saved/discarded first.

No new dependencies were added. No Excel, advanced analytics, attendance or unrelated modules were implemented. The existing dashboard's non-course metrics remain explicitly demo data.

#### Verification and limitations
Added 23 normal tests covering V008→V009 preservation/idempotence, grade CRUD/reopening, actual-zero versus missing, range/date/name/scale validation, weighted points, partial/empty categories, threshold boundaries and repeating fractions, relationship isolation, repository constraints, atomic rollback, assessment deletion, bulk parsing, draft behavior, statistics and monotonic timestamps. Extended the existing desktop test with category/assessment forms, inline grades, invalid-score rejection, bulk entry, search/sorting, scale changes, confirmed/cancelled deletion, cancelled navigation/offering/window-close requests, discard/reload and workspace reconstruction from saved data.

The verified Windows workflow uses the existing cmd /d /c and -DforkCount=0 workaround, without changing machine settings. UI tests use isolated storage and run the real JavaFX application. Gradebook snapshots were inspected at default and minimum window sizes. Automated control interaction does not claim manual physical-keyboard testing or a separate OS-process restart.

Current limits: fixed A/B/C/D/F labels with configurable cutoffs, points-proportional weighting inside categories, no per-assessment exemption or dropped-lowest policy, no grade audit history, no multi-instance optimistic conflict detection, and no crash recovery for unsaved drafts. Assessment deletion is irreversible after confirmation. These are deliberate scope boundaries to revisit before broader grading/import features.

Final Phase 8 verification: cmd /d /c ".\mvnw.cmd -B -ntp -DforkCount=0 -Pui-smoke clean verify" passed all 160 tests (159 normal cases plus the desktop workflow), with zero failures, errors or skips. Previous migration files and Git history were preserved; no dependencies or Windows settings changed.


### 2026-10-19 — Phase 9: Excel integration

#### Architecture and dependency
Added Apache POI poi-ooxml 5.5.1, pinned after checking the [official release page](https://poi.apache.org/download.cgi) and [spreadsheet guide](https://poi.apache.org/components/spreadsheet/quick-guide.html). POI provides Java-native XLSX reading/writing without Microsoft Excel or external services. No migration is required: existing students, enrollments and grading tables are reused; V001–V009 are unchanged.

The excel package owns bounded workbook reading, header detection and report formatting. StudentImportService owns validation/preview decisions; StudentImportRepository owns the atomic JDBC transaction. Detached import records contain no POI or JavaFX objects. StudentImportController and StudentImportDialog are reused by the student directory and offering roster. ExcelExportService loads saved data and writes files; GradeWorkbookWriter uses GradeCalculator for totals and statistics. GradeStatistics exposes the existing calculation result without parsing UI text. No competing grading formula was introduced.

#### Import policy
Import accepts XLSX, selects a worksheet/header and detects Arabic/English aliases for student ID, full name or first/last names, email and section. Manual mapping resolves unknown/ambiguous headers. Arabic/Persian decimal digits normalize to ASCII identifiers. Full-name-only input splits the first word from the remainder, explicitly noted in preview; it is not a linguistic name parser. StudentService's existing validation is reused.

Text IDs retain leading zeros; numeric IDs use Excel's displayed number format and show a warning. Precision or leading zeros already discarded by Excel cannot be recovered. Unsafe numeric values, formula/error/boolean cells in mapped fields, invalid emails and missing required names/IDs produce visible row errors. Formulas are never evaluated, and external links are not resolved. All repeated IDs within the sheet are rejected, rather than choosing an arbitrary winner.

Preview shows create/enroll/skip/error decisions and selected-row explanations. Confirmation explicitly imports valid rows; validation failures are counted separately. Exact matching existing students are skipped globally or enrolled into the selected offering. Conflicting names or supplied emails are rejected; no existing profile is updated. Already-enrolled students are skipped, including withdrawn enrollments (no implicit reactivation). A supplied section must match the target offering; global import retains section in newly created students' notes and does not invent offerings.

All actionable creates/enrollments commit in one transaction with prepared statements. Existing student snapshots and target offering/term eligibility are checked again at confirmation. A concurrent change or database failure rolls the actionable batch back; it does not leave a partial import. Created/enrolled counts are separate and may overlap for new students enrolled immediately. Skipped and failed-validation counts describe untouched rows. File/sheet/header/mapping changes invalidate confirmation until a new preview is generated.

Bounds: 20 MB compressed, 64 MB expanded ZIP content, 2,000 ZIP parts, 20 sheets, last row no later than 10,001 (header included), 100 columns, 200,000 visited cells and 5,000 characters per imported cell. Disguised macro workbooks are rejected. Malformed/encrypted files produce a concise error with diagnostic cause retained.

#### Export policy and formatting
Export uses saved grades only; pending edits must first be saved or discarded. Three sheets are generated: Grades, Assessment Breakdown and Statistics. Attendance is not implemented, so no fictitious Attendance sheet is emitted. IDs are literal text cells with text number format; names beginning with '=' remain literal strings. Missing scores are true blank cells; actual zeros remain numeric. Final percentage/letter stay blank until complete; provisional weighted points and incomplete status remain explicit.

Category weights, grading scales, threshold comparisons, incomplete handling and withdrawn-enrollment statistics follow GradeCalculator unchanged. Breakdown means include actual zeros and exclude missing/withdrawn scores. Class mean/pass rate cover complete ACTIVE/COMPLETED results. Reports are static snapshots, clearly labelled as such; editing Excel cells does not recalculate totals. There are no generated formulas or executable external links.

Formatting includes restrained headers, typed dates/numbers/percentages, wrapped text, column widths, row sizing, frozen identifiers/headers, filters, A4 landscape print setup and page-number footers. Limits are 1,000 assessments, 10,000 students and 200,000 grade cells. Export stages the file in the destination folder and moves it without replacement; existing destinations are never silently overwritten.

#### Verification and limitations
Added 14 integration tests covering bilingual/manual mapping, full names, Arabic digits, leading zeros and formatted numeric IDs, duplicate/conflict/section policies, formula/error cells, malformed/empty/oversized/macro files, transactional rollback, stale previews, withdrawn enrollment preservation, blank versus zero, literal formula-like text, nonzero weighted results/class statistics, empty reports and overwrite protection. Desktop workflows exercise global and offering imports, mapping invalidation, validation previews, counts, persistence through service reconstruction, export and unsaved-edit protection. A pre-existing desktop-test race was corrected by waiting for failed-save completion before testing navigation protection.

Workbook layouts and JavaFX snapshots are visually inspected. The auxiliary workbook renderer coerces numeric-looking text IDs in its own preview; POI round-trip assertions verify the actual XLSX retains string IDs and leading zeros. Native Excel rendering, physical keyboard interaction and OS file-picker interaction are not claimed; tests enter file paths through the actual dialogs. No XLS/CSV/XLSM, formula evaluation, grade import, attendance or advanced analytics were added. Full-name splitting and locale-specific headers remain reviewable through manual mapping and preview.

Final Phase 9 verification: the documented cmd /d /c workflow with -DforkCount=0 -Pui-smoke clean verify passed all 174 tests (173 normal tests plus the desktop workflow), zero failures/errors/skips. The real JavaFX windows ran against isolated temporary databases; import/export snapshots and all three workbook sheet layouts were inspected. No known build failures remain. Windows settings and previous migration files were preserved.

### 2026-10-19 — Phase 9.1: Excel QA and hardening

Confirmed a data-integrity bug using a real XLSX numeric cell: stored value 123000 with custom format `0,` displayed 123 and was previously accepted as a new student ID. A failing regression test reproduced it. The reader now accepts numeric displays only when they contain digits and represent the same underlying safe integer; zero-padding formats remain supported. Scaled, abbreviated and scientific displays require the original identifier to be re-entered as Text. This changes input safety, not student uniqueness, enrollment or grading rules.

Confirmed a second bug: drive-root paths caused NullPointerException before useful validation in import/export. Root/null destinations now receive actionable filename guidance. Missing files and folders named `.xlsx` also receive a direct Browse-to-file message. Numeric-ID errors, row warnings and the import dialog now explain that changing a column to Text cannot restore digits Excel has already discarded; the original ID must be re-entered.

Added four tests (18 Excel tests total): numeric scaling regression, root-path regression, missing-file/folder errors, and a realistic bilingual roster with 36 valid students, two duplicate-ID rows, a missing ID, blank emails and an empty row. The latter imports/enrolls the 36 valid students in isolation, exports an Arabic assessment title and verifies every text ID, 18 Arabic names, one numeric zero and 35 true missing scores/final percentages. Generated synthetic XLSX files and an isolated QA database are available under ignored `target/excel-qa/` after tests. No migration/dependency changes, new grade formulas, attendance or unrelated features were introduced.

Verification: documented Windows workflow `cmd /d /c ".\mvnw.cmd -B -ntp -DforkCount=0 -Pui-smoke clean verify"` passed 178 tests, zero failures/errors/skips. This includes 177 non-UI tests plus the existing desktop workflow. The updated import dialog snapshot was visually inspected; the added guidance and controls are visible. Earlier regression runs failed for the two confirmed defects and passed after the fixes.

Microsoft Excel was detected and launched; its native Open dialog was opened and cancelled with injected keys. Further native testing was stopped after the Windows Computer Use helper returned stale control references, invalid window bounds and an incorrect application screenshot despite refreshing window selection. No successful workbook opening in Microsoft Excel, end-to-end native Professor OS file-picker interaction or physical-keyboard testing is claimed. These remain manual verification items, not application failures. See [Excel QA checklist](docs/EXCEL_QA.md) for reproducible steps, expected counts and safe isolated storage. POI reopening and JavaFX control tests do not substitute for those native checks.

### 2026-10-19 — Phase 10A: modern UI theme foundation

Introduced a restrained Professor OS visual foundation: warm paper Light surfaces, charcoal Dark surfaces and an indigo accent. Existing page layouts and business behavior are preserved. Top-bar appearance cycling and Settings → Appearance share one per-window ThemeManager. The mode persists as LIGHT/DARK/SYSTEM in appearance.properties beside the configurable application database. Preference writes stage a complete file and atomically replace the old preference where supported; save failures leave the current choice unchanged and show an actionable error. Missing/invalid settings default to System, with invalid reads logged. No database migration or dependency was added.

ThemeMode and ThemePreferences are plain Java model/service code. ThemeManager is a presentation coordinator, instantiated by MainView and closed by MainController. It updates existing scene-root and PopupControl style classes, including owned dialogs, dropdowns and tooltips, instead of recreating nodes or calling navigation. Form text/caret position and unsaved grade drafts survive changes. Owned windows are discovered when shown; weak tracking and explicit listener cleanup avoid retaining closed dialogs or sessions. JavaFX's Platform.Preferences.colorSchemeProperty provides live OS appearance notifications; System resolves to Light when no dark preference is reported. Explicit Light/Dark choices override subsequent OS notifications. The API was checked against official OpenJFX 25 documentation linked in docs/THEMES.md.

Colors are semantic looked-up tokens in css/palette.css. Typography, spacing, radii and component rules remain centralized in css/theme.css with a documented sizing scale. JavaFX does not support browser CSS var() or reusable numeric custom properties, so sizing uses shared component rules, not invalid CSS variables. Existing Java layout gaps remain unchanged. Styled primitives include sidebar/navigation, buttons, fields, tables, selection/focus, tabs, dates, dialogs, tooltips, scrollbars and four chart-series colors. Chart screenshots are test specimens only, not new application features. Native OS title bars/file pickers remain OS-controlled.

Five new normal tests cover persistence/reconstruction, invalid settings, failed writes, mode resolution and 4.5:1 contrast for semantic normal-text pairs. The desktop workflow now verifies top-bar/Settings synchronization, live simulated system changes, explicit overrides, unchanged Settings view identity, open-dialog text/caret preservation, unsaved grade field identity/content, popup styling and responsive root layouts. Visual review caught a tooltip that remained light despite a themed scene root; PopupControl's separate style bridge now receives the theme class and a computed-background regression assertion verifies the actual dark fill. The smoke-test shutdown now runs on the FX thread, matching the real Application lifecycle.

Reviewed screenshot sets under target/ui-smoke include Light/Dark dashboards at normal and minimum sizes, Settings, course dialogs, chart/input specimens, a dark tooltip and the dark Gradebook with an unsaved score. The Settings captures wait for academic-term loading to finish. Minimum-theme specimens use root resize/layout; the broader smoke suite also exercises native minimum-window resizing. Actual Windows appearance changes were not made: live notifications were tested with an injected observable. Physical keyboard/screen-reader audits, multi-monitor/high-DPI behavior and other platforms remain manual checks. This is not a full High Contrast theme or accessibility certification. See docs/THEMES.md for extension conventions and limits.

Final Phase 10A verification: the documented cmd /d /c workflow with -DforkCount=0 -Pui-smoke clean verify passed 183 tests (182 normal tests plus the expanded desktop workflow), with zero failures/errors/skips. Final reviewed images include the corrected dark tooltip and fully loaded Settings in both palettes. No known build or CSS parse failures remain.


### 2026-10-19 — Phase 10B: dashboard and navigation redesign

The main shell now uses grouped navigation (Workspace, Teaching, Assessment, Insights, Preferences), original SVG line icons, an accent selection marker and an icon-only collapsible sidebar. Links retain accessible names and tooltips when collapsed. The sidebar scrolls at shorter window heights. The top bar exposes page context, the active course offering, Open and the existing theme toggle; the nonfunctional global search placeholder is removed. Complete offering labels remain available in a tooltip. Collapse is session-only and changes existing node geometry/content display, without navigating, rebuilding forms or changing the active offering. Re-selecting the current page preserves its state; Courses remains a route back to the catalog from offering details.

DashboardOverview is a detached read model; DashboardService composes existing academic read services without SQL, schema changes, new dependencies or changed academic calculations. MainController loads it on the existing background-task pattern, discarding stale responses using a request revision. Refresh overview and entering Dashboard reload saved records. The existing course count callback remains shared with Course CRUD. Counts cover all catalog courses, all saved students, all offerings and ACTIVE enrollments in the selected offering. No selection/unavailable data is shown as unknown rather than zero. The unused DemoDashboard and its constructor dependency were removed; the dashboard contains no demo metrics.

Teaching progress reuses LectureProgress: completed lectures divided by non-cancelled lectures; partial lectures are reported separately and do not count as completed. Schedule status uses the existing lecture rules. Coming up shows at most five dated, unfinished lectures and non-archived exams on/after today for the selected offering, ordered by date/kind/title. Draft exams retain an explicit draft label. Recently updated lists the four most recently modified lecture/exam records, explicitly not an audit/activity log. These are read snapshots across existing services, not an atomic reporting transaction or advanced analytics. No fictitious grading backlog, deadlines or attendance statistics are presented.

Scoped shell/dashboard CSS extends the existing semantic Light/Dark palette and leaves individual feature layouts intact. Cards, teaching progress, chronological activities and latest updates share consistent spacing and type hierarchy. No theme persistence behavior changed.

Four added isolated-database tests verify real counts, unselected state, active enrollment filtering, offering isolation, chronological upcoming activities, draft labels, unchanged progress calculations, bounded lists and immutable result lists. Desktop coverage adds populated-dashboard captures, selected navigation/node identity during collapse, unsaved grade preservation when collapsing and reselecting Grades, and open-dialog field/caret preservation. Existing CRUD, enrollment, lectures, question bank, exams, PDFs, gradebook and Excel workflows remain in the smoke suite.

Reviewed Light/Dark screenshots: shell-dashboard-* at 1360x900, shell-minimum-* at 1100x740, and shell-collapsed-* under ignored target/ui-smoke. Short windows deliberately scroll content/navigation instead of compressing controls. The suite also exercises actual native-window minimum sizing; the themed comparison images use root resize/layout. Physical keyboard/screen-reader audits, high-DPI/multi-monitor behavior and other operating systems remain manual checks. Sidebar collapse is not remembered across restarts. Overview freshness requires navigation or Refresh; it does not monitor external database edits.

Final Phase 10B verification: the documented cmd /d /c workflow with -DforkCount=0 -Pui-smoke clean verify passed 187 tests (186 normal tests plus the expanded desktop workflow), zero failures/errors/skips. The real JavaFX application ran with isolated test storage. No known compilation or CSS parsing failures remain; previous migrations and Git history are unchanged.


### 2026-10-19 — V009 startup mismatch: investigation and repair

The actual default database was `C:\Users\HP\Desktop\Uni Project\data\professor-os.db`, with no environment override. Its V001-V008 hashes matched. V009 stored `c87cf9d20865be53637f01358a1846aaec42d582e0dd6a55815f30fdded9f6e0`; both the original ca2dc74 Git blob and HEAD compute `399b1f398657d88d76a147807aebdb241bc4bf46654d010eb320cbecc0a75e65`. MigrationRunner already normalized CRLF before SHA-256. The exact stored hash is reproduced by removing only `date(assessment_date,'+0 days') IS NOT NULL AND ` from committed V009. The real assessments schema independently confirmed that weaker CHECK. SQLite accepts NULL CHECK results, so this difference is meaningful. V009 was applied at 13:15:27 UTC on October 18, before its first commit at 14:14:01 UTC: the database retained a pre-commit variant. No post-commit migration edit or hashing regression caused this incident.

Backed up the existing database through SQLite's backup API before modifying it; integrity passed. Added an explicit offline LegacyGradeMigrationRepair command, never called from normal startup. It accepts only the known legacy history and exact full schema, compares against independently migrated in-memory references, refuses invalid dates/FK/integrity failures, creates a second non-overwriting SQLite snapshot, and locks/rechecks against that backup. A transaction rebuilds assessments with the canonical constraint and restores affected indexes/triggers. Full schema and every table's rows must match the canonical reference and backup respectively before V009's checksum is updated. The original applied_at and other history rows remain unchanged. Normal checksum verification remains strict. Unknown/modified/newer databases are refused; this is not a general migration-reset feature. No historical migration or dependency was changed.

The existing database was successfully repaired. Post-repair comparison against the original backup confirmed identical rows in all 15 user tables; only V009's checksum changed in history. Integrity returned ok and foreign_key_check returned no rows. The original backup SHA-256 remained unchanged. A separate JavaFX process invoked the real ProfessorOsApplication lifecycle against that exact database, opened Dashboard, verified saved course/student counts of one and closed normally. Reviewed target/startup-repaired.png. The temporary startup harness used a classpath and emitted JavaFX's unnamed-module warning; it did not affect startup. No production records were created for verification.

Five regression tests cover exact legacy-checksum reproduction, reproduction of the original startup failure, populated grade/zero preservation, canonical schema equivalence, restored date/range/FK enforcement, backup preservation, repeated startup, repeat-repair refusal, unknown hashes, unexpected schema changes and invalid legacy dates. The documented Windows clean verify workflow with -DforkCount=0 -Pui-smoke passed 192 tests (191 normal plus the desktop workflow), zero failures/errors/skips. Startup diagnostics now include the actual path and stored/computed hashes while preserving the original exception cause. See [V009 incident evidence and maintenance instructions](docs/V009_REPAIR.md) for backup paths, checksums and the guarded procedure.

### 2026-10-20 — Phase 10C: feature screen UI redesign

Courses, Students, Lectures, Exam Bank, Exams and Gradebook now share clearer page headings, descriptive context, labelled search/filter controls, wrapping action bars, restrained primary actions and more readable tables. Shared AcademicControls helpers provide these presentation conventions, full-text table-cell tooltips and page-scoped Ctrl/Command+F search. Enter activates a selected record action only when the table itself owns focus. Existing services, repositories, calculations, navigation guards and data remain unchanged; no dependency or migration was added.

AcademicFormDialog now provides a resizable, scrollable form body with persistent validation and Save/Cancel controls. Question content uses the available form width. Exam Builder groups metadata above separate Question bank, Composition and Instructions tabs. The bank has compact labelled filters; composition shows question order and count with move/remove actions. Calculated/intended marks remain visible below the scrollable body. Saved snapshot handling, generation constraints and finalization rules are preserved.

Gradebook uses aligned numeric editors, clearer grid boundaries, distinct ungraded placeholders and a Saved/Unsaved changes badge. Editor accessibility labels identify the student, assessment and maximum score. Blank and zero remain distinct; grading and export logic are unchanged. Wide gradebooks retain horizontal scrolling; frozen student columns and a full spreadsheet keyboard model are outside this phase.

All new component styling uses the existing semantic Light/Dark palettes and centralized theme.css. Theme changes update existing nodes, including open forms and grade drafts. Minimum-size toolbars wrap rather than hiding actions. AGENTS.md now permanently prohibits altering applied migrations, requires new versions for schema changes, preserves checksum verification and requires verified backups/root-cause investigation before repairs.

The expanded desktop workflow seeds isolated academic data, visits all six screens, checks table space/action visibility at 1360×900 and 1100×740 in both themes, verifies search focus, unchanged form fields and unsaved grade values, and captures feature/dialog snapshots under target/ui-smoke. Exam composition checks preserve saved question IDs and mark totals. Visual review caught clipped builder actions and a collapsed order column; compact bank controls and a fixed minimum order width address those defects. It also exposed a late asynchronous course-count callback refreshing offering choices after shutdown; MainController now ignores callbacks after close. The smoke test now fails on uncaught JavaFX-thread exceptions.

Verification uses the documented Windows cmd /d /c, -DforkCount=0 and -Pui-smoke clean verify workflow. Feature screenshots were inspected in Light and Dark, including normal/minimum page sizes, editable forms, bank selection, composition and gradebook. Physical keyboard, screen-reader, high-DPI/multi-monitor and native file-picker audits remain manual checks. Programmatic JavaFX focus/control tests and snapshots do not claim those checks. Production database contents and historical migrations were not changed.

Final Phase 10C result: 192 tests passed (191 normal tests plus the expanded desktop workflow), zero failures/errors/skips, BUILD SUCCESS. The final composition screenshot confirms visible question numbers and both builder tabs show their action buttons and persistent totals.

### 2026-10-20 — Phase 11: attendance management

Attendance links an Enrollment to a Lecture within one CourseOffering. V010__attendance.sql adds the attendance table and supporting indexes without changing V001–V009. Its composite primary key (enrollment_id, lecture_id) prevents duplicates. Composite foreign keys (enrollment_id, course_offering_id) and (lecture_id, course_offering_id), backed by unique parent indexes, enforce matching offering relationships even for direct SQL. Both deletion and relationship updates are RESTRICT, preserving history. Status is constrained to PRESENT, ABSENT, LATE or EXCUSED; timestamps are ISO Instant text. Unmarked has no row. Clearing a mark explicitly deletes that one attendance row; bulk saves use one transaction and roll back on failure.

AttendanceRepository uses existing JdbcOperations, prepared statements and transactional upserts. AttendanceService validates offering, lecture and enrollment relationships and rejects editing cancelled lectures. AttendanceDraft holds only changed cells until Save, including explicit null clears. AttendanceCalculator is independent of JavaFX and provides the same student/course calculations used by UI and Excel. AttendanceBook contains immutable saved records, lectures and the roster. The small AttendanceController coordinates existing background tasks; AttendanceView uses shared controls and Light/Dark/System styling.

Percentage = 100 × (PRESENT + LATE) / (PRESENT + LATE + ABSENT), rounded HALF_UP to two decimals. LATE receives full credit but is counted separately. EXCUSED, unmarked and CANCELLED lectures do not contribute to the denominator. A zero denominator displays Not yet rated (blank in Excel), not 0%. Counts of absence and lateness are always separate. Course statistics aggregate student-lecture observations for ACTIVE/COMPLETED enrollments; withdrawn students remain available for historical editing and individual history but are excluded from course statistics. Unmarked coverage includes all non-cancelled lectures, including future planned lectures: enrollment timing is not inferred from a record's creation timestamp. The UI makes saved summaries distinct from unsaved edits.

Attendance navigation follows the active offering. The lecture selector shows date, number and title; roster rows show student identifiers, names, enrollment status, editable attendance and saved percentage. Search, confirmed bulk marking (including hidden/withdrawn rows), individual corrections, clear-to-unmarked, student history and course summaries are supported. Changing lecture/offering/page or closing the main window requires discarding or retaining pending changes. Save disables editing until completion; errors retain the draft. Cancelled lecture history remains visible and read-only. No new dependencies were introduced.

Excel export from Gradebook now supplies AttendanceBook to the existing POI writer, adding an Attendance matrix alongside the three existing sheets. It preserves string student IDs, Arabic names, blank unmarked cells, explicit statuses and numeric percentages/counts. Cancelled columns retain history but do not affect totals. The sheet uses existing header/number/date styles, filters, frozen identifiers, print settings and clear calculation notes. Attendance exports are limited to 1,000 lectures and 200,000 cells. Existing grading logic, blank-vs-zero semantics, static reports and non-overwrite safeguards remain unchanged.

The legacy V009 repair command's independently migrated reference and final verification are now explicitly pinned to V001–V009. The repair still accepts exactly the known nine-version legacy state and does not apply V010 itself; ordinary startup applies later migrations. This prevents future schema additions from expanding that narrowly guarded repair. No repair was run and no production database was modified during this phase.

Added isolated tests for V009→V010 upgrade/history preservation/idempotence; save/edit/clear/restart; cross-offering and cancelled-lecture rejection; transactional rollback; uniqueness/status constraints and restricted deletion/parent moves; percentage/excused/late/unmarked/withdrawn policies; draft isolation/reversion; and four-sheet Excel content with Arabic names, leading-zero IDs, missing attendance and actual zero grades. The desktop workflow covers individual/bulk/save, cancelled navigation/lecture changes, theme preservation, saved history and close/reopen persistence, plus Light/Dark normal/minimum screenshots. Physical keyboard, screen-reader, high-DPI and Microsoft Excel rendering remain manual checks. Attendance has no session time beyond its lecture date, automatic absence marking, configurable late penalties or attendance import in this phase.

Final Phase 11 verification: documented Windows cmd /d /c workflow with -DforkCount=0 -Pui-smoke clean verify passed 199 tests (198 normal plus the expanded desktop workflow), zero failures/errors/skips. Reviewed attendance-Light/Dark-1360/1100 snapshots. UI tests also cancel offering switches and window close while retaining edits, and verify bulk marking includes filtered-out rows. Persistence was verified by rebuilding services and closing/reopening JavaFX windows on isolated databases. All V001–V009 files are unchanged; no production database was modified.

### 2026-10-21 — Native Compose dashboard evaluation and Timeline removal

#### Decision and scope

Keep JavaFX as the complete working application and evaluate a separate Compose Multiplatform Desktop frontend under prototype-compose/. Kotlin/compiler plugin 2.3.20, Compose Desktop 1.10.3 and Gradle 9.3.1 are pinned. Gradle is confined to the prototype because it is the supported Compose build path; converting the existing Maven application would add unnecessary risk. The wrapper distribution checksum is pinned. No WebView, Electron, web frontend, backend server or new persistence style was introduced. Existing feature screens remain in JavaFX.

Timeline was removed from JavaFX navigation, routing, icons, the LecturesView variant and its CSS. The retained Lectures table still supports offering context, CRUD, search, date ordering, progress and schedule status. Its desktop regression scenario now tests the table; the shell asserts that Timeline navigation is absent. No lecture records, repository/service rules or historical migrations were removed. Historical Phase 4 notes remain as history rather than being rewritten to imply Timeline never existed.

#### Java integration and state

DashboardBridge calls CourseService, AcademicServices, DashboardService and AttendanceCalculator directly across the JVM language boundary. DashboardSnapshot is a detached presentation value; calculations and data ownership stay in the established Java layers. Initial loading/refresh runs on Dispatchers.IO, with coroutine cancellation propagated rather than shown as a storage error. The bridge uses AppPaths and Database.initialize, preserving strict migration/checksum checks. No database migration was added or changed.

The prototype shows real catalog/student/offering counts, selected-offering progress, recorded attendance and upcoming/recently updated academic records. Empty databases show honest guidance. Synthetic Arabic/English fixtures are test-only. The first available offering is selected initially; session selection can be changed through Teaching context. JavaFX's separate session remains independent. The Open JavaFX action starts the original application in a separate JVM with the same data-directory environment, avoiding mixed toolkit lifecycle ownership. Refresh reads changes made in the complete application; no live synchronization protocol or CRUD replacement was added.

#### Visual identity and accessibility

The custom Foundation/Canvas UI uses midnight navy, charcoal, violet, cyan, layered gradient panels, original geometric icons, consistent rounded surfaces and Manrope typography. No Material widgets, external icon pack or animation library was added. IBM Plex Sans Arabic renders Arabic text with content-based bidi direction. Both font families are bundled unmodified with complete SIL OFL 1.1 licenses and provenance. Visual review caught Manrope selecting its ExtraLight default variable axis; explicit 400/600/700 axes restore readable weights.

Navigation crossfades, hover colors, changed-value counters/progress and an eight-second low-opacity ambient cycle use Compose animation APIs. The ambient value is read in a graphics layer instead of recomposing the whole screen. System/Reduced/Full motion is persisted in Java Preferences independently of academic data. System reads Windows ClientAreaAnimation off the UI thread with a five-second timeout; other platforms/failed detection conservatively reduce motion. The environment override PROFESSOR_OS_REDUCED_MOTION supplies the System value; Full is an explicit override. Unfocused windows reduce motion. Reduced mode removes animated transitions while preserving feedback and current session state.

Responsive breakpoints collapse the sidebar below 1100 logical pixels and stack panels below 1000. Window minimum is 880×660; the content scrolls. Text direction/shaping is supported, but the English shell is not fully localized or mirrored RTL. Semantic text-color pairs on the dark backgrounds meet the tested 4.5:1 contrast threshold. Cyan focus outlines and keyboard activation remain visible.

#### Verification and findings

The verified Windows cmd /d /c and -DforkCount=0 workflow runs all 199 Maven tests including the graphical desktop smoke workflow. The separate Gradle suite has seven tests: persisted Java integration/reconstruction and read-only bytes, empty data, motion policy, bundled fonts/licenses, dark contrast, rendering/interactions/motion, and empty-workspace guidance. UI coverage includes Enter activation, offering selection/navigation, refresh/handoff callbacks, Arabic labels, responsive layout, full-motion pixel changes and exact idle-pixel stability under Reduced. Tests use isolated temporary databases. Test-only internal/experimental Compose rendering APIs are pinned to this Compose version; they are not used by production code.

Reviewed Compose renderer snapshots at 1360×960, 900×720, 864×621 (minimum client-area specimen) and 1700×1050, plus Arabic offering choices and empty state. Corrected the initial test harness's 1024×768 capture surface so larger views are genuinely captured without cropping. Ten warmed full-size screenshot captures took 33 ms in the recorded run; this includes readback/synchronization and is not FPS or sustained performance evidence. Reduced motion stability and bounded animation scope are verified; GPU/frame-time profiling remains an evaluation task.

Launched the actual Compose application against C:\Users\HP\Desktop\Uni Project\data\professor-os.db, inspected the saved counts, captured its native window, and used Open JavaFX to launch and inspect the original dashboard against the same database. Both initialize successfully with migration checks enabled. Native Tab focus and Enter motion activation were checked; Full persisted across process restart, then System was restored. Native maximization resized the content correctly. Captures are in ignored prototype-compose/build/screenshots/: native-dashboard.png, native-dashboard-full-motion.png, native-dashboard-maximized.png and native-javafx.png. JavaFX smoke captures remain under target/ui-smoke/.

The existing database SHA-256 before and after the native checks was unchanged: E749E8C7DE93BB70E771F938BED99AF719B2BD4BE2C319FBFB56D58AB8CD2C52. No repair, seeding, checksum rewrite or production-data modification occurred. V001–V010 are unchanged in Git.

One development-only interruption occurred when Gradle recompiled output files while the prototype was running from that same classpath, causing a missing generated Kotlin class on a later action. Closing, rebuilding and restarting resolved it; the clean native rerun passed. README now requires closing the development window before rebuilding. This is not a hot-reload or packaged distribution setup. The JavaFX handoff also reports its known unnamed-module warning; normal Maven JavaFX launch remains available.

Remaining manual evaluation: Windows edge-drag/minimum-window behavior (renderer minimum-size checks passed; native maximization passed), physical keyboard use beyond injected key checks, high-DPI/multi-monitor behavior, other operating systems, full RTL localization, sustained performance profiling and screen-reader accessibility. Windows UI Automation exposed only the outer Compose window during this check, so no screen-reader compatibility claim is made. No installer, other-screen migration or unrelated feature was added. See prototype-compose/README.md for launch, architecture, fonts and motion details.

Final verification: Maven clean verify with the desktop profile passed 199 tests; the final Gradle test run passed seven tests, both with zero failures/errors/skips. The final warmed capture observation was 25 ms for ten captures (not an FPS benchmark). Native startup/handoff/restart logs and screenshots were inspected. No known build failures remain.

### 2026-10-22 — Compose workspace refinement

Refined the existing dashboard evaluation around obsidian, charcoal and muted violet. The oversized hero, decorative gradients and ambient loop give way to a compact workspace heading, teaching-focus card, library summary, chronological activities and offering attendance. Counts and calculations still come directly from the existing Java services. Empty states explain what to do next; an offering without lectures does not show a misleading completion percentage. No academic model, JavaFX feature screen, service, database file or migration was changed. Dependencies and bundled OFL fonts are unchanged.

Presentation is split into Dashboard (shell/navigation), DesignSystem (tokens, original icons, controls and typography), OverviewPanels (read-only cards) and OfferingPicker (shared searchable context selection). Manrope now explicitly includes the medium 500 weight alongside 400/600/700, with quieter heading sizes, consistent line heights and restrained borders. IBM Plex Sans Arabic remains responsible for Arabic labels and now also the picker input. The top bar provides page context, quick offering selection, motion preferences and refresh. The original window icon/title were refined while native window controls remain available; OS title-bar styling is not overridden.

The sidebar uses one gliding selection indicator. Critically damped springs with stiffness 420 drive selection, hover and changed-value counters/progress; page transitions combine a 12-pixel reveal and brief fades. Reduced motion snaps transitions without clearing selection or navigation. Both Full and Reduced modes now have stable idle pixels. There is no perpetual rendering effect or external animation package. Existing persisted System/Reduced/Full policy remains unchanged and was restored to System after native checks.

Ctrl/Command+K opens the focused context picker; Ctrl/Command+R refreshes. Filtering matches course name/code, term/year and section. Up/Down and Enter select an offering; Tab focus is synchronized with the selection cursor so Enter cannot activate a stale row. Verification found that the popup's default Escape handling did not reliably dismiss an empty search result; an explicit popup key handler fixes this, with regression coverage. Escape guidance is shown only in the dismissible popup. These are session-only UI actions, not new academic write paths.

Default size is 1360×860, minimum 880×660, with an icon sidebar below 1050 and stacked cards below 1120 logical pixels. Larger content is centered and bounded at 1340 pixels; smaller windows retain vertical scrolling. Optional Gradle previewWidth/previewHeight properties provide repeatable initial native sizing without saving window preferences.

Reviewed renderer captures at 1360×860, 900×720, 864×621 and 1700×1050, plus Arabic teaching contexts and an empty database. Native Windows captures at default size, minimum size (including scrolling) and maximized size are saved under ignored prototype-compose/build/screenshots/refined-native-*.png. Native Full navigation start/settled frames, the context popup, Ctrl+K, Escape and motion preference changes were inspected. The capture tool's blue cursor halo is not part of the application. Arabic glyph joining and bidi labels were visually reviewed; this remains an English shell rather than a localized RTL application.

The native prototype read the existing database successfully and its full-workspace action launched JavaFX with normal database initialization and the existing unnamed-module warning. The complete JavaFX desktop smoke workflow also passed and its dashboard screenshot was inspected. The production database SHA-256 remains E749E8C7DE93BB70E771F938BED99AF719B2BD4BE2C319FBFB56D58AB8CD2C52, identical before and after native verification. No data was seeded or repaired and V001–V010 remain untouched.

The first Maven invocation omitted the documented smoke-data environment variable; the safety assertion rejected the normal database path before initialization. Re-running the documented cmd /d /c workflow with PROFESSOR_OS_DATA_DIR set to target/ui-smoke-data and -DforkCount=0 -Pui-smoke clean verify passed all 199 tests, zero failures/errors/skips. The final Gradle test run also passed all eight Compose tests with zero failures/errors/skips, including real-data isolation, font licenses, 4.5:1 text contrast, keyboard navigation/search, focused-row selection, Escape, responsive rendering, spring movement/settling and idle stability. Ten warmed screenshot captures took 20 ms in this run, including readback; this is not a frame-rate measurement. Synthetic records remain confined to temporary test databases.

Remaining evaluation: high-DPI/multi-monitor and other operating systems, native edge-drag behavior, full screen-reader compatibility, physical keyboard testing and sustained frame-time profiling. Automated spring-position and screenshot-readback checks are not an FPS benchmark. Windows UI Automation still exposes limited Compose internals. No installer, production packaging, additional screen migration or new persistence functionality was introduced.

### 2026-10-22 — Compose migration, checkpoint 1: academic workspace

Added Courses, Students, Terms, Offerings and the selected offering's enrollment roster to Compose navigation. Shared searchable selectors, sortable tables, forms, details and confirmation surfaces retain the charcoal/violet tokens. WorkspaceController schedules existing Java service calls on Dispatchers.IO and keeps form values local until successful Save; expected domain validation is shown in the form, diagnostic exceptions are logged, and failed writes retain edits. Closing an edited form or the application requires explicit discard confirmation. Enrollment status changes preserve history, and deletion restrictions are still enforced by the original Java services. JavaFX remains available through Open full workspace. No database migrations or business rules changed.

Checkpoint build: nine Compose tests pass, including an isolated workflow covering Arabic course creation, required-field errors, cancel/discard protection, leading-zero student identifiers, enrollment, restricted course deletion and reopened-database persistence. Course/form renderer screenshots are saved under build/screenshots/migration-*. Native workflow verification and the complete Maven regression run follow after the remaining feature groups.

Checkpoint 2 adds offering-scoped lecture create/edit/delete/details, search and status filtering, professor notes and the existing LectureProgress summary. All validation, duplicate-number enforcement, scoped access and history restrictions use LectureService. Ten Compose tests pass, including a minimum-size lecture screen, duplicate-number rejection with retained edits, progress after status changes, deletion and switching to an empty offering. Timeline is not reintroduced.

Checkpoint 3 adds the complete Question Bank and Exam Builder workflows. QuestionDraft holds editable fields and MCQ choices; existing QuestionService owns required fields, marks, choice and relationship validation. All five types, topic/type/difficulty/course filters, answer/explanation, image path/browse and usage details are supported. The local AWT image picker references files without copying them. Gradebook, attendance editing, PDF and Excel workflows remain in JavaFX.

ExamDraft is a local composition buffer with metadata, ordered question snapshots and instructions. The bank is scoped through ExamService.bankForExam, which substitutes saved exam snapshots for later live-bank edits. Multiple selected rows can be added together; questions can be removed or explicitly moved. The calculated total uses the same saved marks and warns on mismatch without changing content. Save, finalize, archive and draft deletion call the original Java services; finalization remains the sole usage-recording event. Details display saved snapshots and their answers/explanations.

Generation passes exact question-count, difficulty, type and topic distributions to the existing ExamService/ExamGenerator. The deterministic, bounded backtracking solver, usage/id ordering, feasibility checks and node limit are unchanged. Generating over a selection requires confirmation; replacement occurs only on success, and failed constraints retain the previous selection. No SQL, migration or business algorithm was copied or changed.

Shared controls provide keyboard focus, searchable selectors, sortable tables, confirmed deletion, responsive toolbars and scrollable modal forms with persistent Save/error footers. Ctrl/Command+Enter submits, Escape requests close, and edits require explicit discard confirmation before window closure. Form values are snapshotted before background writes; controls are disabled while a write is active. A successful write followed by a failed refresh is explicitly reported as saved, preventing an accidental duplicate retry. Closing/navigation/offering changes use the same guard. The selected context is session-only. Concurrent edits from separate JavaFX/Compose processes retain existing service semantics; no new optimistic-locking protocol was introduced.

The existing spring/fade shell is retained. New controls inherit reduced-motion policy; compact sidebar icons now distinguish academic terms, offerings, lectures, questions and exams. Minimum-size tests found a tooltip intercepting the next sidebar row; placing hints beside the icon resolves it, and the integrated navigation test covers all eight migrated routes at 1360×860 and 864×621 client sizes. Renderer snapshots cover normal/minimum screens, Arabic forms, MCQ choices and exam composition under prototype-compose/build/screenshots/migration-*.

Final automated verification: 13 Compose tests pass, zero failures/errors/skips. New UI scenarios cover course/student/enrollment writes and restart reads, required-field/duplicate failures with retained edits, discard cancellation, lecture progress and offering isolation, all five question types, MCQ/range validation, manual exam totals/reordering, generation success/failure, cross-course rejection, saved-snapshot preservation after a bank edit, finalization usage and archive persistence. The original Maven Windows workaround with explicit isolated smoke storage and -DforkCount=0 -Pui-smoke clean verify passed all 199 tests. No Java source or historical migration was modified.

Native Windows verification used an explicitly isolated fixture at prototype-compose/build/native-migration-qa/professor-os.db, created only when absent. Created QA202 with an Arabic course name through the actual Compose form, verified Tab focus and Ctrl+Enter save, closed/restarted the application at 880×660, and confirmed the saved course and dashboard count. Inspected the full native exam editor and its real course-scoped bank, persistent totals/warning, Escape dismissal and minimum navigation. Captures include migration-native-courses.png, migration-native-exam-builder.png and migration-native-minimum-restart.png. These are actual Windows screenshots; richer automated CRUD/generation scenarios use temporary databases and renderer tests rather than being claimed as exhaustive native mouse testing.

Open full workspace also launched JavaFX successfully against that same isolated database; its dashboard showed the two persisted courses and students, and one offering. Reviewed migration-native-javafx-fallback.png and closed both verification windows normally. The user's original data/professor-os.db retains SHA-256 E749E8C7DE93BB70E771F938BED99AF719B2BD4BE2C319FBFB56D58AB8CD2C52. Historical migrations and Java backend sources remain unchanged.

Remaining manual checks: native question-image picker selection/cancellation across file types, high-DPI/multi-monitor behavior, other operating systems, full screen-reader compatibility, physical keyboard auditing and sustained animation profiling. Arabic shaping/bidi are supported within the English shell, which is not fully RTL-localized. No installer or production packaging was added. JavaFX remains the fallback for the whole application, and particularly for workflows deliberately excluded from this migration.

### 2026-10-23 — Compose Gradebook migration

Added Gradebook to Compose navigation using the approved charcoal/violet surfaces, Manrope and IBM Plex Sans Arabic. The selected offering owns the sheet, categories, assessments and scale. GradebookEditor is an observable adapter around the existing Java GradebookDraft; GradebookService, GradeCalculator and ExcelExportService remain authoritative. No Java source, migration, dependency or database schema changed. JavaFX retains the complete fallback and its original Gradebook.

The screen provides assessment/category create, edit and confirmed deletion, configurable grading thresholds, searchable enrollment rows, numeric score/result sorting, class statistics and direct score editing. Column widths expand to use available space, with horizontal scrolling for wider sheets and virtualized vertical rows. Header/filter actions explicitly sort; typed scores do not reorder the focused row. Enter/Shift+Enter and Up/Down move between students, Tab/Shift+Tab move between assessment cells, and Ctrl/Command+S saves. Movement scrolls off-screen editors into view and respects reduced motion. Existing shared modal forms retain validation errors and Ctrl/Command+Enter submission.

Blank clears a persisted score; zero remains an actual score. All weighted calculations, exact threshold comparisons and rounding are unchanged. Final letters/pass-fail require weights totaling exactly 100%, assessments in every positive-weight category, at least one required assessment and complete entries in those categories. Zero-weight categories do not contribute; withdrawn enrollments remain visible but are excluded from class statistics. Incomplete weighted points are explicitly provisional. Native verification showed 16/20 at 40% plus 80/80 at 60% = 92% / A / PASS.

Bulk student-number TAB score input uses the original parser. Invalid batches stage nothing; valid rows enter the draft and require Save grades. Save copies the change map before IO and clears the draft only after a successful transaction. Validation failures retain all edits; a committed save followed by a failed refresh is identified as saved rather than inviting duplicate retries. Refresh, page navigation, offering selection and window close share explicit discard protection. Closing a bulk form does not discard existing grid edits. Configuration changes and Excel export require saving/discarding grades first. Changing offerings clears the old sheet before loading, preventing stale scores under a different context after a read failure.

Excel export is retained through the existing report generator and native Windows save dialog. It exports saved grades, breakdown, statistics and attendance where available, warns about incomplete configuration, and never replaces an existing file. The exported fixture workbook was reopened with POI: four sheets, leading-zero student ID preserved, numeric zero preserved and missing grade blank. No import or attendance-editing workflow was migrated.

Verification: all 16 Compose tests and all 199 Maven tests passed with zero failures/errors/skips. Commands used the documented cmd /d /c workflow, JDK 25 and -DforkCount=0 -Pui-smoke clean verify with PROFESSOR_OS_DATA_DIR set to target/ui-smoke-data for Maven. Three new Compose tests cover real forms/configuration CRUD, invalid weights/scales, final-result suppression at 120% weights despite complete scores, blank/zero save and reconstruction, atomic invalid batches, bulk staging, search, discard/context guards and keyboard traversal through 14 students and six assessment columns. Existing Java calculation, persistence and JavaFX desktop smoke checks remain passing; the JavaFX Gradebook screenshot was reviewed.

Native Windows verification used only prototype-compose/build/native-grade-qa/professor-os.db. Edited a grade through the running Compose grid, saved with Ctrl+S, exported through the native file picker, closed and restarted at 880×660, and verified the saved 80.00 score and 92% / A / PASS result. Arabic names, leading-zero IDs, incomplete rows, normal/minimum layouts and minimum-window scrolling were visually inspected. Screenshots in ignored prototype-compose/build/screenshots include gradebook-compose-normal.png, gradebook-compose-minimum.png, gradebook-native-saved.png, gradebook-native-minimum-restart.png and gradebook-native-minimum-scrolled.png. Native checks use injected keyboard/mouse input; the blue cursor halo is from the capture tool. The original data/professor-os.db SHA-256 remains E749E8C7DE93BB70E771F938BED99AF719B2BD4BE2C319FBFB56D58AB8CD2C52.

Limitations: the English shell supports Arabic shaping rather than full RTL localization. High-DPI/multi-monitor, other operating systems, screen-reader certification and sustained large-roster performance profiling remain manual evaluation tasks. The native export artifact was validated with POI, not opened in Microsoft Excel during this migration. Production packaging and unrelated workflows remain outside scope.

### 2026-10-23 — Compose Attendance migration

Added Attendance to the existing Compose navigation using the approved charcoal/violet components, typography, context selection and motion policy. The selected CourseOffering supplies its lecture/date sessions and enrolled roster. Present, Absent, Late, Excused and Unmarked are directly selectable; cancelled lectures remain visible as read-only history. Search matches student number/name, including Arabic, and sorting supports student number/name. The roster has alternating surfaces, selected/focused row borders, explicit status selections, vertical virtualization and horizontal scrolling at narrow widths. No JavaFX screen, Java service, dependency or database migration changed.

`AttendanceEditor` is an observable adapter around the existing Java `AttendanceDraft`, with no duplicated validation or persistence. `WorkspaceController` loads through `AttendanceService` on IO threads and saves the copied change set through its existing transactional method. Null/Unmarked clears a record on save; it is distinct from Absent. Duplicate prevention, relationship checks and cancellation validation stay in Java. Successful writes retain the selected lecture; failed writes retain edits, including when a lecture is cancelled concurrently. If a successful write's subsequent refresh fails, the existing controller reports that it saved and asks for refresh rather than implying the write failed.

The shared dirty-state guard now covers both Gradebook and Attendance for navigation, offering selection, refresh and window closure. Lecture changes also use this guard. Context changes clear old editors before loading, so an old roster cannot appear under a new offering after a read failure. Closing a history dialog does not discard sheet edits. Bulk marking confirms its entire-roster scope, including hidden and withdrawn rows, matching JavaFX behavior; edits remain staged until Save attendance.

Calculations and saved history use the unchanged `AttendanceCalculator`: percentage = (Present + Late) / (Present + Late + Absent). Late counts fully and is reported separately. Excused, Unmarked and cancelled lectures are excluded from the denominator; no eligible marks means Not rated. Withdrawn enrollments are excluded from the course summary. Percentages and history deliberately show saved data until Save refreshes them. Existing Gradebook Excel export continues to include saved attendance through the established Java report generator.

Keyboard interaction: with a roster row focused, P/A/L/E/U selects its status; Delete/Backspace also selects Unmarked. Up/Down moves focus and scrolls through virtualized rows. Ctrl/Command+S saves while focus is within Attendance. Buttons retain Tab/Enter activation and shared focus feedback. No new keyboard framework or animation dependency was introduced.

Verification: all 19 Compose tests passed, including three new Attendance scenarios covering individual marking, clearing, saved-only calculations, history, Arabic search, reconstructed database persistence, duplicate-record count, whole-roster bulk changes, cancelled-session read-only behavior, failed-save edit retention, context/lecture/discard guards and keyboard traversal through 14 students. Normal/minimum rendering captures were reviewed. The documented Windows JDK 25, cmd /d /c, -DforkCount=0 -Pui-smoke clean verify workflow passed all 199 Maven tests with zero failures/errors/skips, using PROFESSOR_OS_DATA_DIR=target/ui-smoke-data. The JavaFX Attendance smoke screenshot was reviewed; its original functionality remains available.

Native Windows QA used only the separately seeded prototype-compose/build/native-attendance-qa/professor-os.db. In the running application, used the A shortcut to change a sample student's first session to Absent, checked navigation's keep-editing confirmation, then saved with Ctrl+S. Closed and restarted at 880×660; the persisted Absent mark and 50.00% student result remained, and the saved course summary was 33.33%. Arabic names, leading-zero IDs, withdrawn labels, the five status controls, compact navigation and minimum-size scrolling were visually inspected. Captures under ignored prototype-compose/build/screenshots/: attendance-compose-normal.png, attendance-compose-minimum.png, attendance-native-saved.png, attendance-native-minimum-restart.png and attendance-native-minimum-summary.png. Native input was injected through the Windows tool; its blue cursor halo is not application styling. The existing Full motion preference was left unchanged.

The original data/professor-os.db SHA-256 remained E749E8C7DE93BB70E771F938BED99AF719B2BD4BE2C319FBFB56D58AB8CD2C52. No production records were seeded, repaired or modified. Migration files and stored checksum verification remain untouched. No known build/test failures remain. High-DPI/multi-monitor behavior, physical-keyboard use, other operating systems, screen-reader certification and sustained large-roster profiling remain manual evaluation tasks; Arabic support is shaping within the English shell, not full RTL localization.

### 2026-10-24 — Compose PDF and Excel workflow integration

Completed the remaining document workflows in Compose while retaining the JavaFX fallback. Exam Builder now opens a saved-exam PDF dialog with university/department/professor details, optional native logo selection, Student Exam/Answer Key selection, paginated preview and separate native Save As actions. Changing print settings invalidates the preview. Mark-total mismatches require acknowledgement; exports never change exam content or overwrite an existing file. ExamPdfService remains authoritative and renders saved question snapshots through the existing PDFBox/ICU/embedded-font implementation. No rendering algorithm or question-bank logic was duplicated.

Students now supports global Excel import; Teaching Context supports import and enrollment into the selected offering. DocumentWorkflows provides observable form state and background coordination; DocumentScreens uses the existing shared tables, selectors, modal forms and charcoal/violet design. The existing StudentWorkbookReader, StudentColumns and StudentImportService perform bounded parsing, Arabic/English header detection, manual mapping validation, row preview and transactional confirmation. Preview shows actionable/skipped/invalid counts and selectable row messages. Mapping/header/sheet changes invalidate previous review; existing profiles are never overwritten. Guidance explains numeric-ID warnings and irrecoverable digits already lost in Excel. Formula cells remain rejected. Closing an uncommitted import uses the shared discard guard.

Gradebook and Attendance share the existing ExcelExportService and native file-selection helper. Reports include Grades, Assessment Breakdown, Attendance and Statistics, retaining saved calculations, text identifiers, blank versus zero and unmarked attendance. Export blocks unsaved grade or attendance changes. The native AWT picker runs on its event thread; parsing, rendering and writes run on background IO with busy/error feedback. The established ownerless dialog avoids focus conflicts between Compose popups and owned AWT dialogs. Existing service errors are shown concisely, with diagnostics logged. No Java backend, historical migration, schema or dependency version changed; the Gradle test classpath now exposes the already-packaged Maven libraries for POI/PDFBox assertions.

Verification: all 22 Compose tests passed with zero failures/errors/skips. Three new document scenarios cover global/offering import, bilingual/manual mapping, preview invalidation, duplicates, formula cells, numeric/leading-zero IDs, malformed files, discarded edits and reconstructed persistence; saved PDF snapshots for all five question types, pagination, confidential-answer exclusion, separate answer keys, mark acknowledgement, missing images and refusal to overwrite; and four-sheet report integrity, saved-only guards, blank versus zero and attendance. The documented JDK 25 Windows cmd /d /c Maven workflow with isolated smoke storage and -DforkCount=0 -Pui-smoke clean verify passed all 199 tests. Reviewed normal/minimum Compose import/PDF renderer captures and the JavaFX import smoke screenshot. No known build or test failures remain.

Native Windows verification used only prototype-compose/build/native-document-qa/professor-os.db. Imported an Arabic-header workbook through the native picker: two students created and enrolled, one existing row skipped. Restart confirmed all three students and enrollments persisted. Exported a combined report through the native save dialog and independently reopened it with POI: all four sheets, Arabic names, leading-zero text IDs, numeric zero versus blank and absence of formulas verified. The native import screenshot is documents-native-import.png; renderer captures are documents-import-preview.png, documents-import-minimum.png, documents-pdf-preview.png and documents-pdf-minimum.png under ignored build/screenshots. Native PDF verification was interrupted by the user's Escape input; the user subsequently confirmed PDF export works. This confirmation is separate from the automated PDFBox content/pagination/confidentiality checks, which passed.

The production database SHA-256 remains E749E8C7DE93BB70E771F938BED99AF719B2BD4BE2C319FBFB56D58AB8CD2C52. No production data was seeded, repaired or modified. Existing JavaFX fallback and migration verification remain intact. Remaining evaluation: opening generated reports in Microsoft Excel itself, other operating systems, high-DPI/multi-monitor behavior, full screen-reader/physical-keyboard auditing and complex Arabic typography. PDF support retains existing shaping/bidi limitations rather than promising full RTL localization, PDF/A or tagged accessibility. POI's existing missing Log4j-provider diagnostic remains nonfatal; no logging dependency was added for this UI migration.

### 2026-10-24 — Compose Settings and fixed animations

Migrated the remaining Settings responsibilities into a dedicated Compose Settings route. Appearance offers Light, Dark and System through AppearanceSettings, a small observable adapter around the existing Java ThemePreferences service. It uses the same atomically saved appearance.properties file beside the active database; a fresh JavaFX launch reads the same preference. Failed writes retain the previous mode and show an actionable error. Window closure waits for an active preference write. Theme selection changes composition-local palette tokens in place, preserving controller/editor state, navigation and teaching context rather than reconstructing screens. System follows Compose desktop appearance detection. Dark retains the approved charcoal/violet palette; Light adds coordinated neutral/violet surfaces. Manrope and IBM Plex Sans Arabic remain unchanged. Theme controls expose their selected state and retain normal Tab/Enter behavior.

Settings shows selectable active database and appearance-file paths and documents the existing PROFESSOR_OS_DATA_DIR launch configuration. Storage policy is unchanged: the default is data/professor-os.db, and another directory is selected before launch. There was no existing live relocation workflow to migrate; no new database-switch, copy, repair or recreation behavior was introduced. Manage terms opens the complete existing Terms screen. Settings can also render when dashboard loading fails, so storage information remains reachable.

Removed MotionPreferences.kt, its enum, stored-preference reads/writes, environment override, Windows accessibility subprocess, focus-based animation overrides, reduced-motion composition local and Boolean plumbing throughout controls, picker, dashboard and Gradebook scrolling. Standard springs now use a fixed damping ratio of 1 and stiffness 420, with the existing restrained page fades/reveal. There is no animation intensity or reduced-motion setting or runtime policy. Old registry values are inert; no preference-cleanup code remains. Tests now exercise fixed transition movement, settling and idle stability.

Removed the sidebar/full-workspace button, its callback, the separate JavaFX process launcher and LegacyLauncher bridge. The overview empty state now opens Compose Offerings or Lectures directly. All implemented workflows remain reachable through Compose navigation, including document workflows through their feature screens. The JavaFX codebase, Maven launch command and smoke suite remain intact for the separately requested future cleanup. No Java service, database migration, dependency or academic calculation changed.

Verification: 24 Compose tests passed with zero failures/errors/skips. Added Settings tests for persisted Light/Dark/System selection, retained unsaved course-form data, failed-save behavior, system-mode resolution, 4.5:1 text contrast in both palettes, storage path display, compact navigation and the Terms link. Existing CRUD, generation, grading, attendance, PDF/Excel and persistence scenarios pass with fixed animations. The documented JDK 25 Windows cmd /d /c, -DforkCount=0 -Pui-smoke clean verify workflow passed all 199 Maven tests. An initial Maven clean attempt overlapped the still-finishing Gradle test runner and encountered its locked backend JAR; the sequential rerun completed successfully. No known build/test failure remains. Reviewed the JavaFX Settings smoke capture as well as Compose light/dark/system and minimum-size renderer screenshots.

Native Windows QA used only the isolated prototype-compose/build/native-settings-qa directory. Opened Settings in the running app, changed System (dark on this desktop) to Light, observed the saved appearance.properties value, closed the app normally, and restarted at 880×660. Light reloaded at startup; compact sidebar scrolling reached Settings and showed Light selected. Captures are settings-native-light.png and settings-native-minimum-restart.png under ignored prototype-compose/build/screenshots; renderer captures include settings-dark.png, settings-light.png, settings-system.png and settings-system-light-minimum.png. Native input was injected, not a physical-keyboard certification. No OS theme or accessibility setting was changed.

The production data/professor-os.db SHA-256 remains E749E8C7DE93BB70E771F938BED99AF719B2BD4BE2C319FBFB56D58AB8CD2C52. Historical migrations and checksum verification are untouched. Remaining evaluation: OS appearance changes while running on other platforms, high-DPI/multi-monitor and full screen-reader/physical-keyboard coverage. Storage changes still require a configured launch/restart; this phase deliberately preserves that existing behavior. JavaFX removal is deferred to a separate feature-parity cleanup commit.


### 2026-10-25 — Remove JavaFX; Compose is the sole desktop frontend

Removed the obsolete JavaFX launcher, controller/view/theme packages, CSS, unused FXML/image placeholder directories, OpenJFX dependency/plugin and Maven ui-smoke profile. Removed only the JavaFX smoke/workflow helpers and CSS contrast test; all Java backend services, repositories, models, storage/migration infrastructure and relevant tests remain unchanged. PDFBox, ICU, embedded PDF fonts, Apache POI and their document workflows are retained. No schema change, migration repair or preference/data conversion was needed.

The root Maven artifact is a backend library. The existing prototype-compose directory remains the desktop project to avoid unrelated path changes. Its prepareBackend task now refreshes generated target/classes and target/compose-libs before packaging, preventing removed classes/JARs from surviving incremental builds while retaining test reports and user data. Backend test execution remains a separate required step. BridgeTest now rejects JavaFX classes, the obsolete launcher/controllers/views/theme manager, CSS and JavaFX JARs on the actual Compose test runtime.

The operation-by-operation parity audit is recorded in docs/COMPOSE_PARITY.md against baseline 72dc145. Existing Compose workflows cover catalog/academic management, lectures, questions, exam composition/generation, PDF preview/export, grades, attendance, Excel import/export, settings and unsaved-change protection. The former Analytics placeholder had no implemented feature to preserve. Historical notebook entries retain their original JavaFX verification context; README, theme guidance and current architecture now describe Compose only.

Verification on the local Windows machine completed October 24, with the final documentation/integrity review October 25: cmd /d /c with JDK 25 and the documented shell workaround ran Maven -DforkCount=0 clean verify (197 tests) followed by Compose test (25 tests), all passing with zero failures/errors/skips. The two removed runnable tests were toolkit-specific; the new Compose runtime-absence regression passed. Inspection of the packaged backend JAR and copied runtime libraries also found no obsolete UI classes/resources or JavaFX libraries.

Native Compose run initialized the existing data/professor-os.db, displayed its saved course/student/offering, navigated from Overview to Gradebook, and closed normally with BUILD SUCCESSFUL. Actual screenshots compose-only-native-dashboard.png and compose-only-native-gradebook.png under ignored prototype-compose/build/screenshots were inspected. Native checks were read-only; persistence, CRUD, document content and dirty-state behavior were exercised by the isolated regression suites. This pass does not claim exhaustive physical-keyboard, screen-reader, Microsoft Excel, cross-platform or multi-monitor testing.

Before/after read-only database checks returned integrity_check = ok, no foreign_key_check rows, unchanged counts across all 17 application/history tables and identical ten-row schema_version history. The database SHA-256 stayed E749E8C7DE93BB70E771F938BED99AF719B2BD4BE2C319FBFB56D58AB8CD2C52. All ten migration Git contents are unchanged. A raw-byte comparison initially flagged V010's pre-existing CRLF working-tree endings versus Git LF; normalized SQL matches exactly, consistent with the existing MigrationRunner checksum policy. No migration files, stored checksums or user data were altered.


### 2026-10-25 — Final Compose UI and performance audit

Reviewed all 13 Compose routes and shared tables/forms/selectors, question/exam editors and document overlays. Findings, regression evidence and remaining limits are recorded in docs/UI_PERFORMANCE_AUDIT.md. Preserved the approved layout, colors, typography and fixed restrained animation policy; no new dependency, feature, Java business-rule change or migration was introduced.

Fixed shared selector keyboard navigation (Up/Down/Enter/Escape), virtual-option scrolling, focus synchronization and no-match feedback. A new destination now starts at the top instead of inheriting the previous page's offset; theme changes and same-page edits retain scroll. Sortable record headers have visible keyboard focus and respect disabled state. Gradebook's offering label uses bounded remaining width so save status stays readable. Empty offering guidance now refers to Compose Offerings rather than the removed full workspace.

Performance changes are narrow: DashboardBridge uses the existing joined summaries service once instead of an offering-list read plus one summary read per offering, then sorts by ID to preserve old ordering and fallback selection. GradebookEditor caches its unchanged Java calculation results using draft-revision-derived state; score changes, validation errors and discard invalidate the cache. Attendance remembers its saved course summary by book. Sidebar indicator offset is read during placement instead of composition on each spring frame. No arithmetic or persistence policy was duplicated or rewritten.

Verification: the documented JDK 25 cmd /d /c workflow ran Maven -DforkCount=0 clean verify (197 tests) followed sequentially by Compose test (30 tests); zero failures/errors/skips. Five new audit tests cover 50-option keyboard selection/search/no-match behavior, navigation versus theme scroll behavior, cached grade result invalidation including zero/invalid/discard, batch offering output equivalence, and 13 routes in both palettes at minimum content size and densities 1.0/1.5. All existing CRUD, dirty-state, grading, attendance, PDF/Excel, persistence, animation-settling and idle-pixel tests pass. The renderer produced 52 audit captures; route contact sheets, populated question/exam views and existing minimum-size PDF/Excel/MCQ editor captures were inspected. Ten warmed screenshot readbacks took 21 ms on this run; this is a synchronization/readback observation, not an FPS or GPU benchmark.

Native Windows startup at 880×660 used only the synthetic prototype-compose/build/native-ui-audit database. Verified lecture status dropdown Down/Enter selects PLANNED, Arabic lecture text, wheel scrolling, navigation back to a top-aligned Courses page, compact-sidebar scrolling to Settings, and saved Light appearance without replacing the page. Inspected audit-native-keyboard.png, audit-native-courses.png and audit-native-light-minimum.png under ignored build/screenshots. Closed normally; Gradle run reported BUILD SUCCESSFUL. Inputs were injected, not physical keyboard certification.

Production data/professor-os.db retained SHA-256 B94B45FC93BE2A9783A0FB0DCE1DCAD8741215AF014AC141DF10F79D89DB108E throughout this audit. Read-only integrity_check returned ok, foreign_key_check returned no errors, and all ten stored migration-history rows remained unchanged. The entire Java source/resource/test tree, including historical migration files, has no changes in this audit. This baseline reflects the user's current database at the start of this task, rather than the previous task's older hash.

Remaining checks: physical keyboard/screen-reader coverage, real multi-monitor DPI transitions, other operating systems and large-dataset/document stress profiling. Density 1.5 renderer coverage does not claim OS DPI certification. Refresh still intentionally loads all modules, and bounded tables retain nested scrolling. These were documented instead of introducing speculative architecture or layout changes.


### 2026-10-26 — Verified interconnected academic demo workspace

Replaced the user-authorized gibberish academic records in the active default data/professor-os.db with fictional bilingual academic data. No application behavior, dependency, schema or historical migration changed. Existing services perform domain creation, enrollment, exam snapshot generation, grading and attendance; demo fixtures remain in com.professoros.demo and are never invoked on application startup. DemoCurriculum supplies meaningful subject/topic content, DemoDataSeeder builds an isolated stage, and DemoImportSamples generates four POI import fixtures.

The installed workspace contains 3 terms, 6 courses, 12 offerings, 180 Arabic/English student profiles, 540 enrollments, 144 lectures, 120 questions across all five types, 24 saved exams, 48 categories, 48 assessments, 12 grading scales, 1,318 grades and 2,616 attendance records. Scenario dates are anchored to 2026-10-26. Text student IDs preserve leading zeros. Past/current/future offerings include all lecture and attendance states, withdrawn enrollments, complete/incomplete and passing/failing grades, ten real zero scores and intentional missing grades. Future assessments and sessions remain ungraded/unmarked. Category weights total 100%; exam intended and calculated marks match. Dashboard activity comes from these persisted records. Samples include Arabic/English automatic mapping, manual mapping, duplicates, blanks, malformed email, numeric ID formatting and a safely rejected formula.

Safety: tools/demo_workspace.py creates and verifies a timestamped SQLite backup and staging copy, checks original/backup hashes, verifies full logical table contents, DDL, migration history and integrity, and installs only a completed stage with its matching SHA-256 receipt. Restore validates history to prevent downgrades and first backs up the current workspace. The operator must close every app instance; --app-closed is an explicit declaration, not process detection. Sidecars/locks are refused. No database, backup, workbook or screenshot is committed.

The original verified backup is data/backups/demo-20261025T165057050499Z/professor-os.db, with manifest.json beside it. Its SHA-256 is ea70ca48851260a8e81e9f0598ec55b9fb9aaa89083fe5cbbbddfc6d309399f8. The original source hash remained b94b45fc93be2a9783a0fb0dce1dcad8741215af014ac141df10f79d89db108e until replacement. Backup physical bytes differ because SQLite's backup API was used; logical contents were independently equal. Installed SHA-256 after native restart: 517042879a34c0eb91eb8a183209fb720c2714f2e4b5877226bb32369488f635. All ten migration records, DDL and user_version match the backup; integrity_check is ok and foreign_key_check has no rows. See docs/DEMO_WORKSPACE.md for exact restore instructions.

Verification: sequential documented JDK 25 Windows Maven -DforkCount=0 clean verify and Compose test workflows passed 200 and 31 tests respectively, with zero failures/errors/skips; Python maintenance tests passed all 6. Added seed/reseed, immutable-history, reopened persistence, realistic outcome and workbook-reader/preview tests, plus all-route demo UI coverage and backup/install/restore refusal regressions. Reviewed all 13 populated Compose routes and historical grades. Native default-storage startup displayed the persisted dashboard, saved attendance and zero versus ungraded scores; normal close/restart retained the data and unchanged installed bytes. Native captures are generated/demo-native-dashboard.png, demo-native-gradebook.png, demo-native-attendance.png and demo-native-restarted.png. Sample workbooks are under generated/demo-imports-20261026.

Limits: no native Microsoft Excel open check or physical-keyboard/accessibility certification; optional question images are not seeded. Restore was tested on isolated databases, not by reverting the installed demo. The existing lecture summary's unrounded percentage formatting was observed and left unchanged to keep this task focused on data.


### 2026-10-27 — Consistent percentage presentation

Added toolkit-independent PercentageFormatter for percentage points: HALF_UP rounding to at most two decimal places, no trailing zeros or scientific notation, and a stable decimal point. Dashboard progress/attendance, lecture progress, attendance rows/history/course summaries, Gradebook results/statistics/category weights/scale labels and category picker labels share this presentation. Examples: 36.363636 → 36.36%, 82.50 → 82.5%, 100.00 → 100%. Editable percentage fields remove only redundant trailing zeros on initialization; active text input is unchanged.

Excel percentage cells select one of three cached display styles (0%, 0.0%, 0.00%) according to the displayed precision. Their underlying numeric values remain unchanged; blank/ungraded cells remain blank and zero remains numeric zero. Rounding is confined to presentation. Existing exact grading threshold comparisons, calculation algorithms, validation, stored scores and migration history are untouched.

Regression coverage includes repeating ratios, HALF_UP ties/carry, zero, negative rounded zero, plain notation, locale independence, unchanged high-precision inputs, null/unrated attendance, category labels, Excel display versus numeric values, and actual Compose Dashboard/Lectures/Attendance text. Verification: documented sequential Windows Maven -DforkCount=0 clean verify passed 204 tests; Compose test passed 31 tests, with zero failures/errors/skips. Reviewed updated Overview, Lectures, Attendance and historical Gradebook screenshots, and native Windows startup showing 36.36% lecture progress and 86.67% saved attendance. Closed normally; native run completed successfully. Screenshot: generated/percentage-native-dashboard.png (ignored). The active database retained SHA-256 8a9961cba678f8569b42ac2af37cb6b981895a049544da9ffe5cc265b98a673a throughout; read-only integrity/FK checks passed and no migration changed.


### 2026-10-27 — Audit stage 1: PDF boundaries and authoritative workspace selection

Reproduced two selection defects with explicitly queued IO continuations (no sleeps): requests made during a load were dropped, and direct selection bypassed unsaved course forms. WorkspaceController now owns the dashboard snapshot as well as feature state. Main no longer maintains a second independent selection/load effect; it renders the controller snapshot. Header, active offering and loaded sheets are published together. Pending selections are coalesced to the latest valid request after an active task finishes, including writes; the existing full dirty-state guard runs before accepting the request. Navigation callbacks run only after an approved load succeeds. Explicit context clearing stays distinct from initial first-offering selection. Failed reads retain a consistently labelled previous workspace and allow retry. Public constructor/select signatures, SessionContext and document compatibility overloads are preserved.

ExamPdfService now validates null, root-only, empty, wrong-extension and directory destinations before staging. Missing parent folders retain the established actionable StorageException. Tests verify that rejected destinations leave no staging artifacts; mismatch acknowledgement and refusal to overwrite are unchanged.

Before changing loading behavior, a test-only JDBC driver interceptor measured successful SELECT executions on an isolated fixture with three offerings, twelve enrolled students, five lectures and twenty two-choice MCQs. The original question-bank read executed 21 SELECTs; startup executed 55; the original settled explicit-refresh path (workspace refresh, dashboard reload, selection-driven second workspace refresh) executed 96. PRAGMAs, transaction control, migration DDL and fixture creation are excluded. These are statement counts, not timings. The correctness change already removes the competing Main load path; stage 2 will remove remaining duplicated reads and add permanent count assertions.

Maven clean verify passed 206 tests using the documented Windows cmd /d /c and -DforkCount=0 workaround. Python backup/restore safety tests passed all six cases. Deterministic controller tests cover latest/invalid requests, unsaved form and sheet protection, queued requests during work, failure/retry and explicit clearing. Final stage verification will include the native application against a newly created isolated QA database. No historical migration, fixture, backup, production/demo database or calculation rule changed.

Compose verification for stage 1 passed all 37 tests (31 existing, five new controller tests and one measurement test), with zero failures/errors/skips. An intermediate run exposed accidental first-offering fallback after explicit clearing; that regression was corrected and the full suite rerun successfully. Graphify AST update completed; its Kotlin parser reports partial extraction for five files and SQL extraction is unavailable without tree_sitter_sql, so source and compiler/test results remain authoritative.


### 2026-10-27 — Audit stage 2: bounded question reads and one workspace load

QuestionRepository.findAll now reads ordered choices in one batch and question rows in a second query, both inside its existing JDBC transaction. The single-question read and all public overloads remain unchanged. Correct-answer flags, choice order, immutable aggregates, usage metadata, literal Unicode filtering and snapshot behavior are preserved. A deterministic WAL-mode test commits a changed prompt/choices/marks on another connection after the first SELECT; the batch reader still returns the complete original snapshot. Backend tests compare batch aggregates against independent single-record reads and check integrity_check, foreign_key_check and all ten migration-history rows on an isolated database.

WorkspaceServices now owns one academic-service composition and a DashboardService. The controller supplies its loaded catalog, roster, lectures, exams and attendance to the existing summary/calculation code instead of constructing a second bridge and rereading them. DashboardService.load remains compatible; its new pure summarize method is verified against the established loading API. The roster is reused from the loaded gradebook. Each WorkspaceServices instance verifies migrations once at successful startup; failed initialization is retryable. Ordinary refreshes no longer acquire migration write locks. A new process/service composition still verifies all migration checksums.

Offering switches reuse the controller's existing global catalog/question list; explicit refresh and every committed mutation reload global data. This is a small invalidation policy using existing observable state, not a cache framework. Writes remain serialized and are never cancelled to satisfy a newer read. Scope cancellation clears pending selections instead of starting another load. Post-write reload failure still reports that the write committed and must not be repeated. No domain write transaction or schema changed. Composite workspace read models still consist of separate existing service transactions; a cross-module snapshot against simultaneous external writers is not newly guaranteed. The question/choice aggregate does have one verified SQLite snapshot.

Successful SELECT counts (PRAGMAs, transaction control, DDL and fixture creation excluded), on the same synthetic three-offering/twelve-student/five-lecture/twenty-MCQ fixture:

| Operation | Original audit baseline | After stage 1 correctness | After stage 2 |
| --- | ---: | ---: | ---: |
| Question bank | 21 | 21 | 2 |
| Startup | 55 | 54 | 21 |
| Settled explicit refresh | 96 | 54 | 20 |
| Offering switch | Not separately captured | 54 | 14 |
| Course creation plus refresh | Not captured | Not captured | 21 |

The stage-1 switch measurement included one additional fixture lookup to obtain the target ID (55 including that lookup; 54 for selection itself). Final regression tests obtain the ID before counting. Question-bank reads remain two SELECTs after growing the bank to forty MCQs. Count budgets verify initialization only once, no question-bank queries during selection, explicit global invalidation, and dashboard equality with the public bridge. No elapsed-time/FPS/memory speedup is claimed.

Verification: Maven clean verify passed 208 tests. The complete Compose run passed 38 tests; after adding the concurrent-commit assertion, the focused loading/query suites passed all eight tests. The final cleanup stage will rerun the complete suites. All 48 protected files under data/, generated/, historical migrations and test resources matched their initial SHA-256 values, including production/demo databases, backups and manifests. No dependency or calculation rule changed.


### 2026-10-27 — Audit stage 3: approved cleanup and final verification

Removed the zero-byte util/.gitkeep, the three audited unused imports, the uncalled external Canvas icon branch, and BridgeTest's duplicate dark contrast test plus its now-unused luminance import. SettingsUiTest retains the complete light/dark contrast/mode coverage. Renamed the lecture repository's timeline-named test to describe chronological offering lectures, retaining every assertion. SessionContext, all GradeWorkbookWriter/ExcelExportService overloads, demo generators, backups, fixtures, font/license resources and historical documentation remain intact. The original CODEBASE_AUDIT.md is retained as the approved historical baseline; AUDIT_IMPROVEMENTS.md records the implementation and measured impact. Updated the desktop README's test counts and loading description.

Final verification, sequentially on JDK 25: cmd /d /c Maven -DforkCount=0 clean verify passed 208 tests; Compose test passed 38 tests; python -m unittest discover -s tools -p test_demo_workspace.py passed six tests. All suites had zero failures/errors/skips. Final query measurements remain question bank 2, startup 21, explicit refresh 20, offering switch 14 and course mutation/refresh 21 successful SELECTs under the documented fixture/counting scope. Reviewed generated dark Attendance and light Gradebook minimum-size snapshots; animation/idle/keyboard, document contents, persistence and discard guards pass their existing tests.

Native Windows verification used only a new QA copy at target/native-audit-8a9a05d35b/professor-os.db, copied from the freshly generated UI-audit fixture. Started through the Compose Gradle run task with PROFESSOR_OS_DATA_DIR explicitly pointing there. Inspected Overview, Gradebook, Attendance, the bilingual Question Bank and Exam Builder. Switched to CS302 then back to populated CS301 and verified header/context/roster agreement. Entered synthetic QA University/QA Professor print details, generated the saved-exam Student PDF preview (two pages), and verified the unsaved-settings guard: Keep retained settings/preview; closing the window prompted again and discarded only the agent-created transient print draft. No academic write or native Save As was performed. The native run closed normally and Gradle reported BUILD SUCCESSFUL.

The QA database retained SHA-256 574a956faf1c3a0b2b7ea3ab2db748b7101ba29fda176331aa840c2a27a653ff before/after native verification; read-only integrity_check returned ok, foreign_key_check returned no rows, and all ten migration-history rows (including checksums/timestamps) were identical. All 48 protected files under data/, generated/, historical migrations and test resources matched their starting hashes; no new protected file appeared. The active production/demo database retained SHA-256 2d000ad10f2704b81c3fc77d88f899e9e3f414e3d56de24d36d1c1738649b637. No production/demo database was opened by the app or connected for checks; only file hashes were read.

Graphify code updates completed after each stage with no extraction API cost. Its existing Kotlin partial-extraction and missing SQL-parser warnings are recorded; passing compiler/tests and source review are authoritative. Git diffs were checked for whitespace, migration/resource changes, retained compatibility classes and intended file scope. The user's pre-existing AGENTS.md modification and untracked .codex/ remain outside these commits. Graphify's rebuilt outputs remain in its pre-existing untracked graphify-out/ directory. No build output or QA database is committed.

Remaining limits: composite workspace data still comes from separate existing service transactions, while the question/choice aggregate is transaction-consistent and tested against a concurrent commit. Explicit/post-write refresh still loads all modules once; more granular write invalidation is deferred. No wall-clock speedup, other-platform/high-DPI/screen-reader certification, or native Excel/file-picker certification is claimed. Existing JDK/toolchain/POI diagnostic warnings are unchanged and nonfatal.


### 2026-10-27 — Compact, fully populated Iraqi academic demo

Replaced only the verified fictional demo at data/professor-os.db using the existing backup/stage/install workflow. Its legacy source SHA-256 was 2d000ad10f2704b81c3fc77d88f899e9e3f414e3d56de24d36d1c1738649b637. A complete verified backup and manifest are preserved in data/backups/demo-20261027T145444975012Z/, alongside the untouched previous demo backup. The original stayed unchanged until all staging, coverage and safety checks passed. Restoring the manifest recovers every old demo row, including user edits. No real/non-demo records, historical migration or stored checksum were overwritten.

The repeatable compact-v2 seeder now creates two courses (OOP and Algorithms), one active term, two active offerings, seventy unique Iraqi three-part names and two disjoint thirty-five-student cohorts. DemoStudents holds curated given/father/grandfather names for thirty-five women and thirty-five men; all seventy third names are distinct. It uses the existing first/last fields, with the last field containing father and grandfather names. DemoCurriculum supplies four topics per course, specific MCQ distractors, conceptual/essay/coding prompts and worked answer guidance. No domain model, calculation rule, service, UI, dependency or schema changed.

Each offering has twelve completed past lectures, four assessments weighted 20/10/30/40, every score and every attendance mark. Totals: 24 lectures, 40 questions, 32 bank choices, six exams (four finalized/two drafts), 80 question snapshots, 64 snapshot choices, eight categories, eight assessments, 280 scores, two scales and 840 attendance rows. All seventy results are complete. Grade profiles include exact 49/50/51 and 59/60/61 boundaries and range from 25 to 96; weighted score offsets give varied assessments without changing those final percentages. The verified CS201 pass setting of 50 and CS202 setting of 60 are retained, with the existing 90/80/70/60 letter thresholds. Therefore CS201's independent letter/pass settings can yield F/PASS at 50–59, as before. Default rules remain untouched. CS201 has 28 PASS/7 FAIL, CS202 22 PASS/13 FAIL. Attendance has 598 Present, 91 Late, 107 Absent and 44 Excused; zero student/session pairs are unmarked.

The Java stage seeder refuses populated non-demo data before clearing. CLI prepare/install recognize only the established demo parent counts/course identities/fictional markers. CLI install independently checks exact compact counts, name components/uniqueness, third-name diversity, rosters, complete grade/attendance coverage, weights, exam totals and course relationships. Existing digest/receipt/source/schema/history/offline checks and recoverable atomic replacement remain active. The connection helper now enables foreign keys consistently. Existing backups, fixtures and generated samples remain intact; new samples update only the intentional existing-student preview row to the new name.

Verification: Maven clean verify passed 209 tests using the documented cmd /d /c and -DforkCount=0 workaround; Compose passed 38 tests; Python safety tests passed eight. A real CLI install/restore round trip on disposable copies recovered all original rows, and an invalid candidate with a matching receipt was rejected without changing the target. Native Compose startup was inspected twice against the exact installed demo, including both gradebooks, attendance, roster and saved exams after restart. Both runs closed normally. Twelve PDFs (52 pages), two Excel reports and two generation selections were checked through unchanged application services against the saved data. All exported grade and attendance cells were populated.

Final demo SHA-256 is 962720b308d198a5951e56dbfd9cee0adc5aae14977d02adf9c7e88c5a17e76a, unchanged by both native runs and export/generation checks. Integrity is ok, foreign-key checks return no errors, and DDL plus all ten migration-history rows match the original manifest. All eighteen protected backup/manifest, historical migration and test-resource files retained their hashes. See docs/COMPACT_DEMO_VERIFICATION.md for exact counts, hashes and recovery; docs/DEMO_WORKSPACE.md describes future generation. Databases/backups/reports remain ignored. No unrelated application cleanup was performed.


### 2026-10-27 — Bounded Gradebook and Attendance layouts

Only the Gradebook and Attendance routes opt out of the dashboard's width cap and outer page scrolling. Their existing page reveal animation remains; compact headers leave a bounded, expanding content area. Other routes keep their existing dashboard layout. Gradebook uses one vertically virtualized list, with a frozen student identity column and a shared horizontal scroll position for assessment headers and score rows. Headers stay outside the vertical list. Assessment widths expand with the window and retain a 136dp minimum before horizontal scrolling. Configuration tabs retain their own scrolling at small sizes.

Attendance uses one student per vertical roster row, fixed aligned saved percentages, and responsive equal-width status controls. Lecture/bulk selectors, saved summary, search, sorting and actions stay in the compact header; the existing policy text is available through Policy. Both sheets have vertical scrollbars. Editing models, controllers, calculations, guards, persistence, schema, dependencies, theme tokens and animation specifications are unchanged.

Verification: the complete Compose suite passed 39 tests, including a new temporary-demo layout test at 880x660 and 1920x1080; the final Attendance width adjustment also passed the four focused attendance/layout tests. Assertions cover actual horizontal scrolling with frozen student identities, fixed headers during vertical scrolling, roster alignment, last-student access and expansion in both dimensions. Existing keyboard, saving, bulk validation and unsaved-change tests passed. Reviewed both screens' minimum/maximized renderer captures and native Windows layouts using only target/native-sheet-layout/professor-os.db. Native Gradebook horizontal scrolling kept names fixed; resizing and Attendance navigation worked, and the app closed normally. Both the isolated copy and data/professor-os.db retained SHA-256 962720b308d198a5951e56dbfd9cee0adc5aae14977d02adf9c7e88c5a17e76a. No academic edits or database writes were performed. One earlier renderer run stalled in Compose focus/snapshot locking; restarting its test worker resolved it, and subsequent full and focused runs passed. No backend tests were rerun for this presentation-only change. Graphify was consulted and updated; its existing parser limitations remain.


### 2026-10-28 — E grade shares the configured Pass minimum

GradeCalculator now classifies complete results below the existing Pass minimum as F/FAIL before considering passing letters A through E. Passing results below D receive E; E's inclusive floor is the existing pass field. This removes F/PASS and also handles existing independent Pass thresholds above D without changing saved scales or rejecting legacy settings. The default Pass minimum remains 60, so E has no separate band when Pass equals D. No schema, model/API, default threshold, migration, assessment, score or database record changed. Incomplete results still show an em dash and INCOMPLETE, and exact rational threshold comparisons still precede display rounding.

Gradebook and Excel continue to use the same calculator. The grading form now labels the existing field E / Pass minimum %, the configuration summary shows E / Pass, and Excel's Statistics threshold and explanation reflect E and F. No additional threshold or duplicated classification logic was introduced.

Verification: sequential Maven clean verify on JDK 25 with the documented cmd /d /c and -DforkCount=0 workaround passed 223 tests; the complete Compose suite passed 40 tests, with zero failures/errors/skips. New tests cover 49.99/F, 50/E, 59.99/E and 60/D at Pass=50, exact values just below thresholds that round up for display, configured Pass changes, zero-width E bands, legacy Pass-above-D scales, incomplete grades, and matching Excel classifications. Compose exercises the actual grade cells and editing/saving the relabeled Pass field in a temporary test database. Native startup, 50% E/PASS versus 49% F/FAIL, the E / Pass summary and the E / Pass minimum % configuration field were visually verified using only the isolated target/native-e-grade/professor-os.db copy. The scale form was cancelled without edits and the app closed normally. Graphify was consulted and rebuilt; existing parser limitations remain. Historical migrations, backups, fixtures and existing database files are preserved.

All 24 protected existing database/backup, migration and fixture files retained their captured hashes. The existing data/professor-os.db and isolated native copy both retained SHA-256 45127a5bc03651587d12ebe9df9f411ddf83a7e93d04a41ac0f4af9436e7ff76 after native verification. No database or generated file is included in this change.
