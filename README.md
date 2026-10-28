# Professor OS / Professor Dashboard

An offline-first desktop workspace for university professors, built as an academic software engineering project with an emphasis on clarity, maintainability and explainable design.

**Status: Compose Desktop is the only application UI.** Courses, students, terms, offerings/enrollment, lectures, Question Bank, Exam Builder, Gradebook, Attendance and Settings now have Compose screens, including student Excel import, grade/attendance Excel export and exam PDF preview/export. Academic records are persisted locally; dashboard metrics use saved records.

## Implemented

- Lecture attendance for enrolled students, individual/bulk marking, saved history, offering summaries and an Attendance Excel sheet.

- Excel student import with Arabic/English mapping, validation preview and optional offering enrollment; formatted gradebook export with saved calculations.
- Student Exam and Answer Key PDF preview/export from saved snapshots, with A4 pagination, embedded fonts and basic Arabic/RTL support.
- Offering-scoped gradebook with editable cells, selected-student bulk score entry, weighted categories, assessment CRUD, configurable letter/pass thresholds, incomplete results and basic class statistics.
- Compose shell, catalog Course CRUD, academic Terms and student directory management.
- Course offerings by term/section, an active offering selector and enrollment rosters with withdrawal/completion/reactivation.
- Offering-specific lecture CRUD and search, completion counts and basic schedule status.
- Catalog question bank with five question types, MCQ choices, difficulty/marks, optional topic/image path, details, search and course/type/difficulty filters.
- Manual exam building, ordered question snapshots, mark-total warnings and deterministic generation with exact count/distribution constraints. Finalization locks content and records bank usage; archived exams preserve history.
- Grouped, collapsible icon sidebar; real dashboard counts and active-offering lecture progress, upcoming activities and recently updated records. Refresh overview reloads saved data.
- Persistent Light, Dark and System appearance modes, shared Compose component styling, keyboard focus and scrollable content.
- Consistent feature headers, labelled filters, wrapping action bars, resizable forms, exam composition panels and spreadsheet-style grade cells with a visible unsaved state.
- Versioned SQLite migrations, unique identifiers, foreign-key deletion restrictions and configurable storage. Application startup never seeds demo records.
- Maven/Gradle wrappers, backend and Compose UI tests, and permanent AI development rules.

## Desktop application

The native Kotlin/JVM workspace calls the existing Java services for catalog, enrollment, lecture, question, exam and Gradebook workflows. It retains the dark navy dashboard, muted violet accents, Arabic fonts and fixed, restrained animations with Light/Dark/System appearance in Settings. Timeline remains removed; lecture records, algorithms and migration history are preserved.

With JDK 25 configured, run from the repository root: `cmd /d /c ".\prototype-compose\gradlew.bat -p prototype-compose run"`. See [desktop setup, verification and limitations](prototype-compose/README.md). Close the application before rebuilding. Maven builds/tests the Java backend library; Gradle builds/tests/launches the Compose desktop. The existing `prototype-compose` folder name is retained to avoid an unrelated path migration.

## Planned

Broader semester planning, advanced generation constraints, multiple exam versions, learning outcomes, question quality/performance analytics, forecasting, backup and restore.

## Academic workflow

1. Create an academic term in **Terms** (also linked from Settings).
2. Create/select a catalog course in **Courses**, then choose **Create Offering** and select a term and section.
3. Add students in **Students**.
4. Open an offering from **Offerings** or the top-bar selector; **Teaching context** manages its enrolled roster.
5. Select **Lectures** to organize the active offering's lectures. Switching the top-bar offering updates the page. Progress covers all lectures in that offering, independent of search.
6. Use **Question Bank** to create reusable questions for a catalog course. No term or active offering is required. Choose 2–10 distinct choices and one correct answer for MCQs, or a True/False answer when applicable. Optional image files are referenced by absolute local path, not copied into the database.
7. Select an offering and open **Exam Builder**. Create a draft, add questions from its course's bank, and use the composition area to remove/reorder them and edit exam-specific marks without changing Question Bank defaults. Generate selection supports exact difficulty/type/topic counts; leaving a whole distribution blank makes it unrestricted. Review calculated versus intended marks, then Save. Finalize only when content is ready: finalized exams cannot be edited or deleted.

Course is a reusable catalog definition. Enrollment links a student to a specific offering, never directly to the catalog course. Records with dependent academic history cannot be deleted. Active offering selection lasts for the current application session.

For PDFs, select a saved exam and choose **Preview / export PDF**. Enter university, department, professor and an optional PNG/JPEG logo. Generate and review Student Exam, then save to a new filename. Switch to Answer Key, generate its preview and save separately. A mark mismatch requires acknowledgement; existing files are never replaced. Print details are entered per export session. Missing images or unsupported glyphs produce an actionable error rather than silently disappearing.

For grades, select an offering and open **Gradebook**. Create weighted categories under **Categories & Scale**, then dated assessments under **Assessments**. Enter scores directly or select students and use **Bulk Entry** to apply one validated score to a chosen assessment, with confirmation before overwriting, then **Save Grades**. Blank means ungraded; zero is a real score. Final results require weights totaling 100% and complete scores in all positive-weight categories. E uses the configured Pass minimum inclusively; F always means failure. The default Pass minimum equals D at 60, so E has no interval until Pass is set below D. Configure thresholds per offering. Deleting an assessment also deletes its grades after confirmation.

For Excel, choose **Import from Excel** in Students or **Teaching context → Import & enroll**. Load an `.xlsx`, select its sheet/header, review automatic Arabic/English column mapping (or map manually), then preview and confirm valid rows. Existing profiles are never overwritten. Repeated IDs and conflicting profiles are errors; matching students can be enrolled without duplication. Section must match the selected offering when supplied. Set student ID columns to Text before entering values. For existing numeric IDs, re-enter the original ID after formatting; formatting alone cannot restore lost zeros or rounded digits. Numeric formats that scale or abbreviate IDs are rejected. See [Excel QA and manual verification](docs/EXCEL_QA.md) for realistic fixtures and pending native checks.

In Gradebook or Attendance, save or discard pending edits, then choose **Export to Excel** and a new filename. Grades, Assessment Breakdown, Attendance and Statistics contain a saved report, not recalculating formulas. Missing scores remain blank and zero stays numeric zero. Attendance includes saved lecture marks and percentages; blank attendance cells mean unmarked. Imports do not evaluate formulas; replace formula cells with reviewed values first. `.xls`, `.xlsm`, CSV and grade import are not supported. No Microsoft Excel installation is required.

In Compose, select Light, Dark or System in **Settings → Appearance**. Your choice is saved beside the local database and applies without resetting open forms or unsaved grades. System follows the OS appearance reported by the active UI toolkit. See [theme architecture and verification](docs/THEMES.md).

Ctrl/Command+K opens teaching context; Ctrl/Command+R refreshes. With a record table focused, Enter opens its selected edit/build action; in the exam bank selector, Enter adds selected questions. Ctrl/Command+Enter submits forms. Forms retain Save/Cancel behavior and scroll when their content exceeds the available height.

For attendance, select an offering and open **Attendance**. Choose a lecture/date, mark individual students or confirm **Mark roster**, then **Save attendance**. **Unmarked** clears a saved mark; it never means Absent. Search filters the display, but bulk marking explicitly applies to the entire roster. Navigation, lecture/offering changes and window close protect unsaved edits. Select a student for saved history. Present and Late count fully; Absent counts against attendance; Excused and unmarked are excluded. Cancelled lectures are read-only and excluded from percentages. Course summaries exclude withdrawn enrollments; their history remains available. Export the saved Attendance sheet from Gradebook or Attendance → Export to Excel.

## Technology stack

- Java 25 LTS backend; Kotlin 2.3.20 and Compose Desktop 1.10.3 UI.
- Maven 3.9.11 and Gradle 9.3.1 via pinned wrappers.
- SQLite JDBC 3.53.4.0; JDBC and `java.util.logging` from the JDK.
- JUnit Jupiter 5.14.0 for tests.
- Apache PDFBox 3.0.8; ICU4J 78.3 for Arabic shaping/bidi; bundled Noto fonts under SIL OFL 1.1.
- Apache POI 5.5.1 for .xlsx student import and gradebook export.

## Prerequisites

- JDK 25, with `JAVA_HOME` pointing to the JDK directory (not its `bin` directory).
- A graphical desktop for native startup and UI verification.
- Internet access for the first Maven/Gradle/dependency download. Runtime application behavior is offline; subsequent builds can use cached dependencies.
- Git for version control. A separate Maven installation is optional because the wrapper downloads its pinned Maven version.

Initial Windows inspection found JDK 25.0.1 at `C:\Users\HP\.jdks\openjdk-25.0.1`, but Java and Maven were absent from PATH. No global environment settings were changed. In PowerShell on this machine:

```powershell
cd 'C:\Users\HP\Desktop\Uni Project'
$env:JAVA_HOME = 'C:\Users\HP\.jdks\openjdk-25.0.1'
.\mvnw.cmd -version
```

On another machine, set `JAVA_HOME` to your actual JDK 25 installation. IntelliJ users should select JDK 25 as both the project SDK and Maven runner JDK, and import `pom.xml` for the backend and `prototype-compose` as the Gradle desktop project.

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

On Linux/macOS: `./mvnw clean verify` after setting `JAVA_HOME`. Maven tests exercise the Java backend without a display. The Maven JAR is a backend library, not an application launcher.

Run the Compose tests separately, after Maven completes:

```powershell
cmd /d /c ".\prototype-compose\gradlew.bat -p prototype-compose test"
```

Linux/macOS equivalent: `./prototype-compose/gradlew -p prototype-compose test`. Tests use temporary databases and produce renderer snapshots under `prototype-compose/build/screenshots/`. Native startup remains a separate desktop check. The retired Maven `ui-smoke` profile no longer exists. See [feature-parity verification](docs/COMPOSE_PARITY.md).

Do not run Maven and Gradle concurrently: Compose consumes generated Maven output. `prepareBackend` refreshes generated backend classes and copied runtime libraries to prevent stale classes/JARs, while retaining Maven test reports. Close the development application before rebuilding.

## Run

From the repository root, with `JAVA_HOME` configured:

```powershell
.\prototype-compose\gradlew.bat -p prototype-compose run
```

On Linux/macOS: `./prototype-compose/gradlew -p prototype-compose run`. Close the window to stop the application.

The development database is `data/professor-os.db`, relative to the working directory. Set the following environment variable **before launching** to choose another directory:

```powershell
$env:PROFESSOR_OS_DATA_DIR = Join-Path $env:LOCALAPPDATA 'ProfessorOS\data'
.\prototype-compose\gradlew.bat -p prototype-compose run
```

Changing the directory selects a different database; it does not migrate or copy existing files. Preserve existing data. Startup diagnostics are logged to the launching terminal; the application displays a concise error if database initialization fails.

## Directory overview

```text
AGENTS.md                     Permanent AI development rules
PROJECT_NOTES.md              Technical notebook and decisions
pom.xml                      Java backend dependencies, compiler and tests
mvnw / mvnw.cmd              Portable Maven entry points
src/main/java/com/professoros/
  app/                       Storage-path policy
  model/                     Academic entities, inputs and joined read models
  service/                   Validation, calculations and academic operations
  repository/                Prepared JDBC queries and row mapping
  database/                  Connections, migrations and guarded repair tool
  pdf/                       Snapshot PDF layout, fonts and image validation
  excel/                     Bounded workbook reading and formatted reports
src/main/resources/
  fonts/                     Embedded PDF fonts, license and provenance
  db/migrations/             Immutable historical schema migrations V001–V010
src/test/                    Backend tests and isolated migration fixtures
prototype-compose/
  build.gradle.kts           Desktop build and backend integration
  src/main/kotlin/           Compose screens, controllers and Java bridge
  src/main/resources/        Desktop fonts and icons
  src/test/kotlin/           Workflow, rendering and runtime-isolation tests
data/                        Local database (ignored)
exports/                     Generated PDF/Excel exports (ignored)
target/                      Maven output and test reports (ignored)
prototype-compose/build/     Compose output and screenshots (ignored)
```

Read [AGENTS.md](AGENTS.md) before changing code and record significant decisions in [PROJECT_NOTES.md](PROJECT_NOTES.md). Future teaching features should attach to CourseOffering or Enrollment as appropriate.


## Migration startup troubleshooting

If startup reports an applied migration checksum mismatch, preserve the database and investigate the stored history and actual schema; do not delete the database or reset checksums. The known pre-commit V009 assessment-date issue has an explicit, guarded repair procedure: [V009 investigation and repair](docs/V009_REPAIR.md). The affected local database has been repaired and its records verified; this command is not needed for new databases or already repaired databases.

## Optional demonstration workspace

The [demo workspace guide](docs/DEMO_WORKSPACE.md) describes the explicit backup-first seeder, fictional academic dataset, Excel import fixtures and verified restore command. The application never seeds data automatically. Close the application before using the maintenance tool; databases, backups and generated files remain outside Git.

## Repository publication

Local databases, backups, generated Graphify output, development exports, build outputs, caches and credentials are excluded from Git. A fresh checkout creates an empty workspace on first launch; demo records require the explicit maintenance workflow. Keep font provenance/licenses, wrapper files, historical migrations and test fixtures in the repository. Check `git status` and staged filenames before publishing. See [publication cleanup record](docs/PUBLICATION_CLEANUP.md).
