# Professor OS maintainer handoff

**Snapshot: 2026-11-10.** The latest implementation commit inspected for this handoff is `098cf15` on `main`. Start with [AGENTS.md](AGENTS.md) for project rules and [README.md](README.md) for setup and the user workflow. [PROJECT_NOTES.md](PROJECT_NOTES.md) is the detailed decision and verification history.

## What is ready

Professor OS is an offline desktop workspace for university professors. Its single application UI is Kotlin/JVM Compose Desktop, backed by Java 25 services and local SQLite. It manages courses, academic terms, offerings, student rosters, lectures, attendance, grades, a question bank, and exams. Apache POI handles student import and grade/attendance export; PDFBox generates exam and answer-key PDFs. No server, account, or network connection is needed to use the installed app.

- **Teaching context:** a course is a catalog item; a course offering is a term/section instance. Enrollments, lectures, grades, attendance, and exams belong to an offering. Bank questions belong to the course.
- **Gradebook and attendance:** edits remain drafts until saved. Gradebook has spreadsheet keyboard navigation, selected-student bulk entry with overwrite confirmation, and a shared calculator for on-screen results and Excel. E starts at the configured Pass minimum; F is a failing grade; incomplete results stay incomplete. Blank grades differ from zero. Unmarked attendance differs from absence.
- **Exam Builder:** saved exams retain question snapshots, including exam-specific marks. Editing a draft's marks does not update the Question Bank. Finalized exams are locked. PDF export reads the saved snapshots.
- **Appearance:** the shared design system defines the navy/violet palette and semantic actions. Gradebook freezes student identity and headings; Attendance uses a vertical roster. The minimum native window is 880×660.

## Where to work

| Area | Starting point |
| --- | --- |
| Desktop startup and navigation | [Main.kt](prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt), [Dashboard.kt](prototype-compose/src/main/kotlin/com/professoros/compose/Dashboard.kt) |
| UI coordination and read models | [WorkspaceController.kt](prototype-compose/src/main/kotlin/com/professoros/compose/WorkspaceController.kt), [DashboardBridge.kt](prototype-compose/src/main/kotlin/com/professoros/compose/DashboardBridge.kt) |
| Shared UI and appearance | [DesignSystem.kt](prototype-compose/src/main/kotlin/com/professoros/compose/DesignSystem.kt), [WorkspaceControls.kt](prototype-compose/src/main/kotlin/com/professoros/compose/WorkspaceControls.kt), [theme notes](docs/THEMES.md) |
| Business rules and service composition | [Java services](src/main/java/com/professoros/service), starting with `AcademicServices`, `GradeCalculator`, `AttendanceCalculator`, and `ExamService` |
| Persistence | [Repositories](src/main/java/com/professoros/repository), [Database.java](src/main/java/com/professoros/database/Database.java), [migrations](src/main/resources/db/migrations) |
| Imports and exports | [Excel code](src/main/java/com/professoros/excel), [PDF code](src/main/java/com/professoros/pdf), [document workflow UI](prototype-compose/src/main/kotlin/com/professoros/compose/DocumentWorkflows.kt) |
| Demo and maintenance | [Demo seeder](src/main/java/com/professoros/demo), [backup and staging tool](tools/demo_workspace.py), [demo workspace guide](docs/DEMO_WORKSPACE.md) |

Keep the dependency direction **Compose → Java services → repositories → SQLite**. Put calculations and validation in services and SQL in repositories. Reuse the existing unsaved-change guards and request handling when adding UI operations. See [audit improvements](docs/AUDIT_IMPROVEMENTS.md) for the rationale behind targeted loading and batched queries. When a local Graphify graph exists, query it first as described in AGENTS.md, then verify findings against source.

## Build, run, and verify

Set `JAVA_HOME` to a JDK 25 installation. Use the pinned wrappers; the first build needs dependency downloads. From the repository root on Windows:

```powershell
.\mvnw.cmd clean verify
.\prototype-compose\gradlew.bat -p prototype-compose test
.\prototype-compose\gradlew.bat -p prototype-compose run
python -m unittest discover -s tools -p 'test_*.py'
```

Run Maven and Gradle sequentially: Compose packages and consumes the Maven backend output. Close the development app before rebuilding. Launch via the Gradle `run` task; the Maven JAR is a backend library. On Linux/macOS use `./mvnw` and `./prototype-compose/gradlew` in place of the Windows wrappers. On the original Windows development machine, use the [documented shell AutoRun workaround](README.md#build-and-test) if the wrapper commands misreport exit codes.

Backend tests are in [src/test](src/test); Compose tests are in [prototype-compose/src/test](prototype-compose/src/test). For UI changes, review snapshots and run the native app when possible. For storage or export changes, use isolated databases and verify outputs and persistence. The last recorded packaging run passed **226 Maven and 43 Compose tests**; the prior publication cleanup also recorded **8 Python maintenance tests**. These are historical results, not a fresh test run for this handoff.

## Database, backups, and demo data

| Launch mode | Default database |
| --- | --- |
| Gradle development run | `data/professor-os.db` at the repository root |
| Installed Windows app | `%LOCALAPPDATA%\ProfessorOS\data\professor-os.db`, separate from the installation |
| Explicit override | `PROFESSOR_OS_DATA_DIR` selects the directory containing `professor-os.db` |

The [storage policy](src/main/java/com/professoros/app/AppPaths.java) also determines where appearance settings live. Fresh workspaces start empty. Installation and updates do not copy a development database, seed a demo, or ship records.

Close the app before maintenance and use a verified backup before any repair or replacement. Enable SQLite foreign keys on every connection. Never edit, rename, or delete applied migrations (currently V001–V010), or change their stored checksums; add a new version for future schema changes. For the historical V009 mismatch only, follow the guarded [V009 repair guide](docs/V009_REPAIR.md).

The last recorded verified local backup is `data/backups/demo-20261028T143332838944Z/professor-os.db`, with a manifest alongside it. Treat it as a dated snapshot and take a fresh backup before maintenance. For reproducible demos use the [backup → staging → validation → install workflow](docs/DEMO_WORKSPACE.md). The compact seed creates two offerings with 35 students each, 12 lectures per offering, and complete grades and attendance. Existing local workspaces can differ from the seed. Keep databases, backups, exports, and generated files outside Git.

## Windows release and what remains

The [Windows packaging guide](docs/WINDOWS_PACKAGING.md) is the release runbook. On Windows with JDK 25, run [tools/package-windows.ps1](tools/package-windows.ps1) to test and build. It uses the Compose wrapper and a pinned portable WiX toolset. Increment the installer version for updates and preserve the upgrade UUID in [build.gradle.kts](prototype-compose/build.gradle.kts). The official PNG is the icon source; [tools/build_windows_icon.py](tools/build_windows_icon.py) regenerates the window icons and seven-resolution ICO.

The verified local installer is `prototype-compose/build/compose/binaries/main/exe/Professor OS-1.0.1.exe` (**141,851,136 bytes; 135.28 MiB**). It is Windows x64 and bundles Java, fonts, and dependencies. Verification covered desktop and Start Menu shortcuts, the official icon, direct launch without Java or Gradle on PATH, the 1.0.0 → 1.0.1 upgrade, and restart persistence. PDF generation and the four-sheet Excel export were checked against the packaged runtime and installed dependencies on a test copy.

Before distributing publicly, test the installer on a clean Windows x64 machine and arrange code signing. The current installer is unsigned and ARM64 was not verified. Follow the [publication cleanup record](docs/PUBLICATION_CLEANUP.md), check what is staged, and wait for explicit publication approval. No Git remote was configured in this checkout when inspected.

## Local work to preserve

At handoff, `AGENTS.md` already had a local modification. The untracked `secure-academic-demo/` is Campus Secure, a separate Spring Boot assignment demo. It has its own README, run instructions, and database. It is not part of Professor OS, its installer, or its test commands. Keep that folder and the local AGENTS.md changes intact; review them separately before staging anything.
