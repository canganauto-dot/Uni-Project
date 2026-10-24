# Compose desktop workspace

A native Kotlin/JVM workspace using Professor OS's obsidian, charcoal and muted-violet visual direction. Compose now includes Courses, Students, Terms, Course Offerings, enrollment/Teaching Context, Lectures, Question Bank, Exam Builder, Gradebook, Attendance and Settings, including student Excel import, grade/attendance Excel export and exam PDF preview/export. JavaFX remains available as the complete fallback.

## Launch

From the repository root, with JDK 25 in `JAVA_HOME`:

```powershell
.\prototype-compose\gradlew.bat -p prototype-compose run
```

On the original Windows machine, bypass its existing shell AutoRun setting:

```powershell
$env:JAVA_HOME = 'C:\Users\HP\.jdks\openjdk-25.0.1'
$env:MAVEN_OPTS = '--enable-native-access=ALL-UNNAMED'
cmd /d /c ".\prototype-compose\gradlew.bat -p prototype-compose run"
```

Linux/macOS equivalent: `./prototype-compose/gradlew -p prototype-compose run` (not verified on those platforms). First build downloads Gradle and dependencies; fonts and runtime data are local. Close the running development application before rebuilding: the development classpath uses build outputs directly and is not a hot-reload setup.

`prepareBackend` packages the existing Maven application and copies its runtime dependencies. No Maven install, backend server, Node.js, WebView or Electron is involved. Do not run Maven `clean` concurrently with Gradle tasks because both need the Maven output. The Gradle wrapper distribution has a pinned SHA-256 checksum.

## Data and architecture

- `DashboardBridge` directly calls existing Java `CourseService`, `AcademicServices`, `DashboardService` and `AttendanceCalculator`. It contains no SQL or duplicated grading/progress calculations.
- `Main.kt` owns the window/session, background loading and errors. `Dashboard.kt` coordinates the shell and navigation; `DesignSystem.kt` holds tokens, typography, original icons and controls. `OverviewPanels.kt` renders real-data cards and `OfferingPicker.kt` supplies searchable teaching-context selection in the top bar.
- `WorkspaceServices` composes the existing Java services; `WorkspaceController` schedules IO and coordinates loads, writes, errors and edit/discard state. `WorkspaceControls` supplies sortable tables, searchable selectors and scrollable modal forms. Feature files contain presentation and input conversion only. No SQL, generation solver or academic validation is copied into Kotlin.
- Default storage is the same root `data/professor-os.db`. `PROFESSOR_OS_DATA_DIR` selects another directory exactly as in JavaFX. Startup uses the existing migration runner with checksum verification; no migrations were introduced or altered.
- Dashboard reads do not write academic records. Feature Save actions do persist through the existing Java services. Fixtures exist only in tests. The first saved offering is initially selected; selection stays in this Compose session. Refresh reloads saved records. JavaFX has its own existing session selection.
- All implemented workflows are accessible from Compose navigation. The obsolete JavaFX launch button/action has been removed. The JavaFX codebase is retained and can still be launched separately with the documented Maven command.

## Migrated workflows

- **Courses / Students / Terms / Offerings:** create, edit, details, search, sorting and confirmed deletion with existing history restrictions. Courses can create offerings. Open an offering to select its teaching context and roster. Students retain enrollment history and leading-zero identifiers.
- **Teaching Context:** enroll existing students; activate, withdraw or complete enrollments with confirmation. Create new students through Students, then enroll them. The top-bar picker selects the shared session context for lectures and exams.
- **Lectures:** all fields, statuses, details, search/filtering and CRUD within the selected offering; progress and schedule status come from `LectureService`. Timeline remains removed.
- **Question Bank:** all five question types, MCQ choice editing with one correct answer, True/False answers, marks, topic, explanation and optional local image path/browse. Search and course/type/difficulty filters, details including usage, and restricted deletion use existing services.
- **Exam Builder:** local drafts with metadata/instructions, course-scoped question selection, multi-row addition, removal and explicit ordering. Calculated/intended totals stay visible. Saved snapshots are preserved when the bank changes. Exact difficulty/type/topic quotas call the existing deterministic Java generator; failure leaves the selection intact. Finalize and archive retain the established status/usage rules. Saved details show snapshots, not live bank content.

Forms keep edits after validation failures. Closing an edited form or the window asks before discarding; active writes block closing and duplicate submission. Ctrl/Command+Enter saves a form, Escape requests closing, Tab moves focus, and Enter opens a table row (or adds a bank question). Compact navigation has distinct icons and accessible hover/focus hints. Native file dialogs are used for question images, Excel import/export, university logos and PDF export.

### Gradebook

The selected offering's Gradebook includes assessment and weighted-category CRUD, configurable grading scales, student search, numeric sorting, class statistics, direct score editing and bulk plain-text entry. Enter/Shift+Enter and Up/Down move between rows; Tab/Shift+Tab move across assessment cells and scroll to off-screen editors. Ctrl/Command+S saves grades. Sort order changes on an explicit header/filter action, so editing a score does not move its row. Headers stay above the scrolling roster; wide sheets have a horizontal scrollbar.

`GradebookEditor` observes the existing Java `GradebookDraft`. `GradebookService` and `GradeCalculator` continue to own validation, transactional saves and all calculations. Blank clears a saved grade; zero remains a recorded zero. Final letters and pass/fail require every assessment in positive-weight categories to be graded, at least one required assessment, and weights totaling exactly 100%. Zero-weight categories are excluded from totals; withdrawn enrollments are excluded from class statistics. Incomplete weighted points are clearly provisional, never final grades.

Bulk entry accepts student-number TAB score lines through the existing parser. Invalid input stages nothing; valid input remains unsaved until Save grades. Navigation, refresh, offering changes and window closure protect unsaved edits. Assessment/category/scale changes and Excel export require saving or discarding edits first. Excel export uses the existing report generator, including saved attendance where available, and refuses existing filenames. No migration, Java business-rule change or new dependency is involved.

### Attendance

Choose a teaching context and lecture/date to mark its enrolled roster. All five states are visible: Present, Absent, Late, Excused and Unmarked. Individual changes and confirmed whole-roster changes remain staged until Save attendance. Bulk marking includes hidden and withdrawn rows, matching JavaFX; the confirmation explicitly states this scope. Selecting Unmarked clears a saved record on save, rather than recording an absence. Cancelled lectures retain read-only history.

Search student number/name, sort by number/name, or select a student to view saved history. With a roster row focused, P/A/L/E/U marks its status; Delete/Backspace also clears it, and Up/Down moves between students, including off-screen rows. Ctrl/Command+S saves while focus is within Attendance. Lecture/context changes, navigation, refresh and window closure protect unsaved entries. Failed saves retain edits; saving retains the selected lecture. The roster scrolls vertically and horizontally at narrow widths.

`AttendanceEditor` observes the existing Java `AttendanceDraft`; `AttendanceService` owns validation and transactional writes. `AttendanceCalculator` remains authoritative: Present and Late count fully; Excused, Unmarked and cancelled lectures are excluded from the percentage denominator. A zero denominator displays Not rated. Withdrawn enrollments are excluded from the course summary. Percentages and history explicitly reflect saved records, refreshing after Save. Existing Gradebook Excel export includes saved attendance. No schema, Java business-rule or dependency changes were needed.

### PDF and Excel documents

In **Exam Builder**, select a saved exam and choose **Preview / export PDF**. Enter university, department and professor details, optionally select a PNG/JPEG logo, then choose Student Exam or Answer Key. Generate preview, inspect pages with Previous/Next and save each document separately. Changing print settings or document kind invalidates the previous preview. Mark mismatches require explicit acknowledgement. PDFBox renders saved exam-question snapshots, including all question types, choices, images, coding text, pagination and embedded Arabic fonts. Drafts remain labelled DRAFT. Export never changes the exam or overwrites a file.

In **Students**, use **Import from Excel** for the global directory; in **Teaching context**, use **Import & enroll** for that offering. Choose an .xlsx file, sheet and header row; bilingual detection suggests column mappings that can be adjusted manually. Preview shows actions and validation counts; select any row for its full message/email. Confirm imports only valid rows and reports created/enrolled/skipped/failed counts. Existing profiles are never overwritten. Text IDs retain zeros; formatted numeric IDs carry warnings, and digits already lost in Excel cannot be recovered. Formula cells are rejected, never executed. Closing a loaded, uncommitted import asks before discarding it.

**Export to Excel** is available in both Gradebook and Attendance. The shared report contains Grades, Assessment Breakdown, Attendance and Statistics. Save/discard both grade and attendance edits first. Reports preserve saved calculations, blank versus zero and unmarked attendance; reports are static, without Excel formulas. Existing filenames are refused. File selection runs on the AWT event thread; file IO, parsing, rendering and service writes run off the Compose thread, with busy/error feedback and duplicate-submission protection.

The Java `StudentWorkbookReader`, `StudentColumns`, `StudentImportService`, `ExamPdfService` and `ExcelExportService` remain authoritative. PDFBox/ICU/font and POI versions are unchanged. PDF Arabic support retains the existing basic shaping/bidi limitations; complex typography, tagged accessibility and PDF/A are not promised.

### Settings

**Settings → Appearance** persists Light/Dark/System through the existing Java ThemePreferences service to `appearance.properties` beside the active database. JavaFX reads the same selection on its next launch. A failed save retains the prior mode and shows an actionable error. Theme changes preserve navigation, selected offering and unsaved editors. **Local storage** shows selectable database/preference paths and documents the existing `PROFESSOR_OS_DATA_DIR` launch configuration; it does not relocate data or switch databases while running. **Manage terms** opens the existing Terms screen.

## Visuals, fonts and animations

Compose Multiplatform Desktop 1.10.3, Kotlin/compiler plugin 2.3.20 and Gradle 9.3.1 are pinned. The independent Gradle project allows evaluation without converting the established Maven application. Custom Foundation components, Canvas icons, quiet borders and tonal panels replace the oversized hero and decorative effects. Teaching focus, library counts, upcoming activity and saved attendance have distinct hierarchies. No Material component layout or additional icon/animation package is required. The header uses a subtle alpha surface; there is no platform blur dependency. Native window controls remain intact, with a compact Professor OS title and original icon.

Bundled Manrope and IBM Plex Sans Arabic are distributed unmodified under SIL OFL 1.1. See [font provenance and licenses](src/main/resources/fonts/README.md). Explicit variable-font weight axes select Manrope 400/500/600/700; leaving its default axis selected made text too thin. Arabic labels and search input use IBM Plex Sans Arabic. Labels use content-based text direction. This is Arabic shaping/bidi support within an English shell, not a fully localized or mirrored RTL application.

Animations use one fixed, restrained spring (damping ratio 1, stiffness 420), with brief page fades and a 12-pixel reveal. There are no animation-intensity or reduced-motion settings, OS motion probes, focus-based overrides or motion preference reads/writes. Legacy motion preference values are inert. Counters animate only when saved values change and transitions settle to idle; there is no ambient loop.

The default window is 1360×860 and its minimum is 880×660. Below 1050 logical pixels the sidebar becomes icon-only; below 1120 the cards stack. Content scrolls vertically and is capped at 1340 pixels on larger windows. Settings provides Light, Dark and System appearance. Dark retains the approved charcoal/violet identity; Light uses coordinated neutral/violet tokens. System follows Compose desktop appearance detection. All existing views and overlays share the palette without replacing their state.

Ctrl+K (Command+K on macOS) opens the teaching-context picker; Ctrl/Command+R refreshes saved data. Search covers course name/code, term, year and section. Up/Down moves the selection, Enter selects, and Escape dismisses the popup. Buttons support Tab/Enter and visible focus borders. The macOS shortcuts are implemented but have not been verified on macOS.

For repeatable native size checks, optional development properties set the initial window size: `run -PpreviewWidth=880 -PpreviewHeight=660`. They do not persist or affect academic data.

## Verification

```powershell
cmd /d /c ".\prototype-compose\gradlew.bat -p prototype-compose test"
```

Twenty-four tests cover direct Java persistence/reconstruction, unchanged database bytes on reads, real empty states, bundled licenses, text contrast, keyboard activation, searchable offering selection, popup dismissal, responsive rendering, intermediate/settled spring positions and idle pixel stability with fixed animations. Migrated workflows add persisted course/student/enrollment creation, dirty-form guards, lecture duplicates/progress/scoping, all question types, MCQ validation, exam ordering/totals, constrained generation success/failure, cross-course rejection, snapshot preservation and finalization/archive. Gradebook scenarios additionally cover keyboard movement across virtual rows/wide columns, blank versus zero persistence, atomic invalid batches, bulk edits, assessment/category/scale validation and CRUD, incomplete weight configurations, and discard guards. Attendance scenarios cover individual and bulk marking/clearing, saved calculations/history, Arabic search, restart persistence, hidden/withdrawn rows, cancelled sessions, failed-save edit retention, discard guards and keyboard movement through 14 students. Document scenarios additionally verify bilingual/manual mapping, preview invalidation, duplicate/formula/numeric-ID handling, global and offering imports, reconstruction after import, saved exam snapshots, PDF confidentiality/pagination, mark warnings, missing images, safe file creation, and blank/zero/attendance export integrity using POI and PDFBox. Settings tests additionally cover persisted modes, failed writes, retained unsaved drafts, system resolution, light/dark contrast and compact layout. Every migrated route is rendered at normal and minimum sizes. Rendering uses the pinned Compose test API; test-only internal/experimental opt-ins are isolated from production code. Synthetic records use temporary databases.

Snapshots in ignored `build/screenshots/` include `refined-overview-*` renderer captures at 1360×860, 900×720 and 1700×1050, `refined-minimum-content.png` at 864×621, Arabic context labels and an empty workspace. Actual running Windows captures include `refined-native-1360.png`, `refined-native-880.png`, `refined-native-880-scrolled.png`, `refined-native-maximized.png`, `refined-native-picker.png` and navigation start/settled frames. Native screenshots use the existing database; the blue pointer halo belongs to the capture tool, not the application. `render-observation.txt` measures warmed screenshot/readback time, **not FPS or a GPU benchmark**.

The existing Maven `-DforkCount=0 -Pui-smoke clean verify` workflow also passed all 199 tests. See PROJECT_NOTES for the final native verification and data-integrity evidence.

Migration screenshots use the `migration-*` prefix, including integrated normal/minimum shell views, forms, composition and native Windows captures. Native QA uses a separately seeded database under ignored `build/native-migration-qa/`; it never seeds the user's normal database. Launch normally without `PROFESSOR_OS_DATA_DIR` to use your existing saved workspace.

Remaining evaluation: native question-image picker selection/cancellation, multi-monitor/high-DPI behavior, other operating systems, comprehensive screen-reader support, fully localized RTL layout, native edge-drag behavior and sustained performance profiling. Native minimum-size launch, scrolling and maximization were checked. The OS controls title-bar appearance. Windows automation exposed only the outer Compose window to UI Automation; semantic tests and injected keyboard checks are not a screen-reader or physical-keyboard certification. No installer or production packaging was added.
