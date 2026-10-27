# Professor OS codebase cleanup and architecture audit

Date: 2026-10-27 (Asia/Baghdad)  
Source baseline: `f59530d3f77a50465b54a2afeed4f6adccc42e9f`  
Status: **Findings for review only. No cleanup implemented.**

All source paths below are relative to `C:/Users/HP/Desktop/Uni Project/`. A suffix such as `:27` identifies a one-based source line at the audited baseline. Line numbers refer to the working-tree source, not Graphify's inferred relationships.

## Executive summary

**Overall assessment: well-structured, with localized cleanup and maintainability debt.** Professor OS is neither significantly overengineered nor broadly bloated. Its 105 production Java files include 49 small domain-model files; that count is not evidence of excessive abstraction. The backend has 3,717 physical lines, the 21 production Kotlin files have 2,101, and the Java/Kotlin test sources have 3,647. Many lines contain several statements, so physical line counts understate reading complexity.

The main architecture is recognizable and appropriate for the project: Compose screens coordinate Java services, repositories own JDBC, and domain models do not depend on JDBC or UI toolkits. Calculations, constrained generation, document rendering, transactional writes and migration verification have substantial tests. There is no active JavaFX frontend, dependency, launch configuration, CSS or FXML in the tracked application sources.

The confirmed removal opportunities are small: three imports, an unused icon branch, a redundant empty placeholder, an uncalled workbook-writer overload, and a session helper that is unused by the current application but deliberately retained with a test. One contrast test duplicates coverage in a newer test. None justifies a broad rewrite or dependency purge.

The more valuable improvements concern duplicated application composition, repeated full-data loads, per-record queries, a service/Excel package cycle, concentrated controller state, and tests that reconstruct shell wiring instead of exercising the production orchestration. These are maintenance and scaling concerns; no measured latency or memory improvement is claimed. A small PDF export input-validation inconsistency also deserves a regression test.

**Recommended approach:** approve the narrowly proven cleanup first, then address correctness and orchestration tests before behavior-sensitive restructuring. Preserve all historical migrations, fixtures, demo material and backups.

## Scope, method and limits

- Inventoried all **222 tracked files**, including 137 Java files, 33 Kotlin files, two Python files, both build definitions/wrappers, documentation and resources. Ran declaration/reference, import, resource-loading, legacy-toolkit and dependency scans over the tracked source corpus. Manually inspected the implementation and callers relevant to every finding below; this was not a formal proof of every execution path.
- Read `.codex/skills/graphify/SKILL.md` and its `references/query.md` before source searches. Attempted the required `graphify query`; the executable was unavailable and `graphify-out/.graphify_python` did not exist. Used the skill's inline Python/NetworkX fallback against the existing graph, without installing tools or creating graph metadata.
- The graph contains **1,409 nodes and 4,536 edges**, reports Graphify **0.9.96**, and records the same commit as HEAD. All 172 tracked Java/Kotlin/Python source files have graph source entries. There is no `graphify-out/wiki/index.md` or existing reflections lesson file. Coverage and matching commit metadata do not prove graph completeness or correct call resolution.
- Expanded the query using graph vocabulary: `compose`, `controller`, `service`, `repository`, `database`, `test`, `resource`, `demo`, `migration`, `main`, `gradle`. The earlier displayed candidate `dependency` was not present in the vocabulary and matched no seed; it contributed no relationship evidence. Traversed component neighborhoods and inspected focused neighborhoods for `SessionContext`, `GradeWorkbookWriter` and `AcademicServices`.
- The graph is **undirected**. Its `WorkspaceController → WorkspaceController.kt → Database.java → Database` path is a navigation relationship, not proof of a runtime call chain. Source imports, constructors and method bodies established the actual direction. A separate source-token dependency scan found the Java package cycle discussed under A3 and no multi-class Java strongly connected component; that heuristic is not a compiler-resolved dependency analysis.
- Checked reflection, explicit entry points, string-selected icons/routes, resource paths, Kotlin callbacks/method references, JUnit discovery and the SQLite driver's service-provider registration. Missing graph edges or low textual occurrence counts were never treated as sufficient removal evidence.
- Only this document was created. No source, dependency, migration, fixture, demo, backup, database or existing graph file was changed. No database connection, seeding, migration, repair, restore or application startup was performed. Existing generated report metadata and runtime JAR contents were inspected read-only.
- No new Maven, Gradle or Python test run was performed. A build would regenerate outputs, and several tests create or replace QA databases/screenshots. This documentation-only audit intentionally leaves those artifacts intact. No new snapshot inspection or native-startup verification is claimed.
- Existing reports contain **204 backend tests across 30 suites** and **31 Compose tests across 11 suites**, with zero failures, errors or skips. Evidence: `target/surefire-reports/TEST-*.xml` and `prototype-compose/build/test-results/test/TEST-*.xml`. Latest report modification times are 2026-10-27 13:47:33 and 13:49:32 Baghdad time respectively. These are prior-run results, not fresh validation of this audit.
- Existing user state included modified `AGENTS.md` and untracked `.codex/` and `graphify-out/`; those were preserved. No graph rebuild was needed because no application code changed. The graph skill's write-back/reflection steps were omitted to honor the read-only request. Graph traversal used no external extraction API; no new extraction token cost was incurred.

### Evidence and risk terminology

**Confirmed unused** means the current repository application/test/build entry points do not use the identified declaration, or the item is explicitly qualified as unused by the application but used by tests. It is not a guarantee about unknown consumers of a public backend JAR. **Potential** means a compatibility decision, behavioral verification or deeper analysis is still required. **Architectural finding** identifies a real structure or behavior, not necessarily a defect.

Removal/change risk: **Low** = isolated, mechanically verifiable cleanup; **Medium** = API, test coverage, UI behavior or cross-feature coordination can change; **High** = transactional consistency, persistence rules or broad lifecycle behavior is involved. Priority is independent of risk: P1 is the first substantive follow-up, P2 is useful maintenance, P3 is optional polish. Nothing below authorizes implementation.

## Confirmed unnecessary code and files

| ID | Finding and exact source evidence | Proposed action after approval | Risk / priority |
| --- | --- | --- | --- |
| C1 | `src/main/java/com/professoros/util/.gitkeep` is a tracked **zero-byte** placeholder. The same directory already contains tracked `src/main/java/com/professoros/util/PercentageFormatter.java`. No build configuration references the placeholder. | Remove the placeholder only. Keep the formatter and its tests. | Low / P3 |
| C2 | Three explicit imports have no use in their file bodies: `androidx.compose.ui.draw.clip` at `prototype-compose/src/main/kotlin/com/professoros/compose/OverviewPanels.kt:12`; `androidx.compose.ui.graphics.Color` at `prototype-compose/src/test/kotlin/com/professoros/compose/BridgeTest.kt:8`; `com.professoros.pdf.PdfKind` at `prototype-compose/src/test/kotlin/com/professoros/compose/DocumentWorkflowUiTest.kt:12`. The complete bodies were checked, including extension calls and type references. | Remove these three import lines. This does not establish that wildcard imports or all local variables are minimal. | Low / P3 |
| C3 | `prototype-compose/src/main/kotlin/com/professoros/compose/DesignSystem.kt:119` contains a `Mark` branch for `"external"`. It is the only matching icon-key literal in production source. `Mark` is internal, and its callers select keys from fixed arguments, the route lists at `prototype-compose/src/main/kotlin/com/professoros/compose/Dashboard.kt:20–22`, or fixed icon choices such as `prototype-compose/src/main/kotlin/com/professoros/compose/OverviewPanels.kt:77–78,119,135`; no user-configured icon loader was found. Other icon branches have callers. | Remove only the `"external"` branch. Retain the default arrow and all other icons. | Low / P3 |
| C4 | `src/main/java/com/professoros/service/SessionContext.java:7–11` has **no current application caller**. Its sole code consumer is `sessionContextIsPerInstanceAndCanBeCleared` at `src/test/java/com/professoros/service/AcademicServiceTest.java:103–108`. Production selection lives in `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:18,24–25,47` and `prototype-compose/src/main/kotlin/com/professoros/compose/WorkspaceController.kt:34,79–86`. `docs/COMPOSE_PARITY.md:23` explicitly says this helper was retained although Compose owns selection directly. No reflection, service registration or CLI entry point for it was found. | Decide whether to retire this intentionally retained public API. If approved, remove the 12-line class and only its focused seven-line test, and update the current parity statement. Do not describe the class as wholly unreferenced or delete the rest of `AcademicServiceTest`. | Medium: documented/public compatibility decision / P2 |
| C5 | `src/main/java/com/professoros/excel/GradeWorkbookWriter.java:16` declares `write(Gradebook, String)`, which only delegates with null attendance. The sole in-repository writer construction/call is `src/main/java/com/professoros/service/ExcelExportService.java:15`, and it calls the **three-argument** overload. Tests use `ExcelExportService`, not the writer overload; no method-reference, reflective or registration consumer was found. | Remove the two-argument writer overload if the backend is not required to preserve it for outside consumers. Keep the three-argument implementation and all export behavior. This is separate from the still-tested two-argument `ExcelExportService` constructor in S2. | Low locally; Medium if public binary compatibility matters / P3 |

No additional whole production class was proved removable. In particular, short records, inputs, joined read models and draft adapters were not classified as dead merely because they have few references. No additional unused local variable was confirmed by the lexical/manual review; compiler/IDE inspection remains advisable before claiming an exhaustive unused-symbol result.

## Potentially unnecessary code requiring further verification

| ID | Evidence and reason for uncertainty | Verification before any change | Risk / priority |
| --- | --- | --- | --- |
| S1 | A dashboard-only mode remains in `prototype-compose/src/main/kotlin/com/professoros/compose/Dashboard.kt:20,27,41,89–95,117–118`: nullable `featureContent`, a smaller destination list, and a fallback teaching-context panel. Production `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:47–58` always supplies feature content. However, `prototype-compose/src/test/kotlin/com/professoros/compose/DashboardUiTest.kt:29–32` and other shell tests deliberately exercise the fallback. | Decide whether an independently reusable dashboard shell remains a requirement. If retired, adapt shell tests to the real workspace and retain their keyboard, selection, animation and empty-state assertions. The whole Dashboard is active; this is only a conditional mode. | Medium / P2 |
| S2 | `src/main/java/com/professoros/service/ExcelExportService.java:11` supports reports without Attendance; production `prototype-compose/src/main/kotlin/com/professoros/compose/GradebookScreen.kt:204` supplies all three services. Several `ExcelIntegrationTest.java` cases at lines 130,151,166,191,222 still use the two-service constructor. `src/main/java/com/professoros/service/GradebookDraft.java:11` exposes `book()` chiefly for `src/test/java/com/professoros/service/GradebookTest.java:127`, whereas `prototype-compose/src/main/kotlin/com/professoros/compose/GradebookEditor.kt:9–10` retains its own book. | Decide whether these small convenience APIs remain useful for backend callers/tests. Keep the real grade draft, no-attendance rendering semantics and export tests. The savings are too small to justify automatic deletion. | Medium / P3 |
| S3 | Public search facades in `src/main/java/com/professoros/service/CourseService.java:62–64`, `src/main/java/com/professoros/service/StudentService.java:33`, `src/main/java/com/professoros/service/LectureService.java:39–42` and `src/main/java/com/professoros/service/QuestionService.java:32–34` are bypassed by the current Compose list/filter path (`prototype-compose/src/main/kotlin/com/professoros/compose/CatalogScreens.kt:28,42`, `prototype-compose/src/main/kotlin/com/professoros/compose/LectureScreen.kt:49`, `prototype-compose/src/main/kotlin/com/professoros/compose/QuestionScreen.kt:107`). Repository searches are still exercised by tests; `QuestionRepository.search` also serves `ExamService.bank` at line 45. | Resolve whether filtering should stay local, move behind services or remain available as a backend API. Trace each overload independently. Do not delete the question-repository search or domain predicates. | Medium / P3 |
| S4 | Course-specific compatibility types remain: `src/main/java/com/professoros/repository/CourseStorageException.java:4` merely extends `StorageException`; `CourseService` uses `CourseValidationException` and `DuplicateCourseCodeException`, while newer services use `DomainValidationException`. `prototype-compose/src/main/kotlin/com/professoros/compose/WorkspaceController.kt:58` already handles both validation families. | Audit test assertions, caller catches and exact error messages before merging exception types. These classes are actively thrown, not dead. Little size reduction is expected. | Medium / P3 |

## JavaFX, prototypes, assets and tracked artifacts

### JavaFX removal is already complete in active sources

- `pom.xml:22–48,51–66` contains no OpenJFX dependency, JavaFX plugin or `ui-smoke` profile. `prototype-compose/build.gradle.kts:20–29` supplies Compose and launches `com.professoros.compose.MainKt`.
- No production `javafx`/`com.sun.javafx` import, FXML file or CSS resource exists in `src/` or `prototype-compose/src/`. `prototype-compose/src/test/kotlin/com/professoros/compose/BridgeTest.kt:13–25` intentionally refers to missing legacy classes and CSS to prevent their return. Keep that test.
- `PROJECT_NOTES.md:698–706` documents the removal. Earlier notebook entries and `docs/V009_REPAIR.md` describe historical JavaFX verification, not current functionality. Preserve that history; do not delete every textual match.
- Historical ignored files still exist at `prototype-compose/build/javafx-removal-before.json`, `prototype-compose/build/screenshots/migration-native-javafx-fallback.png` and `prototype-compose/build/screenshots/native-javafx.png`. They are audit/capture artifacts, not application inputs or tracked bloat. Preserve them under this request, especially where they support prior verification.
- AWT usage is active desktop/document infrastructure, not leftover JavaFX: `prototype-compose/src/main/kotlin/com/professoros/compose/DocumentWorkflows.kt:14–26` owns native file dialogs, `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:42` sets the window minimum size, and `src/main/java/com/professoros/pdf/PdfImages.java:4–5,18–30` uses ImageIO/BufferedImage. Do not remove `java.desktop` support.

### Assets are accounted for

| Exact resources | Source evidence | Assessment |
| --- | --- | --- |
| `prototype-compose/src/main/resources/professor-os.svg` | `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:39` calls `painterResource("professor-os.svg")`. | Required window icon. |
| `prototype-compose/src/main/resources/fonts/Manrope.ttf`, `IBMPlexSansArabic-Regular.ttf`, `IBMPlexSansArabic-SemiBold.ttf` | `prototype-compose/src/main/kotlin/com/professoros/compose/DesignSystem.kt:44–50` loads all three, including variable-font weights and Arabic selection. | Required; no unused bundled UI font confirmed. |
| `prototype-compose/src/main/resources/fonts/Manrope-OFL.txt`, `IBM-Plex-Sans-Arabic-OFL.txt`, `README.md` | Font provenance/licensing; `prototype-compose/src/test/kotlin/com/professoros/compose/BridgeTest.kt:41–45` checks font and license resources. | Keep licenses and provenance even though they are not rendered. |
| `src/main/resources/fonts/NotoSans-Regular.ttf`, `NotoSans-Bold.ttf`, `NotoSansMono-Regular.ttf`, `NotoNaskhArabic-Regular.ttf` | `src/main/java/com/professoros/pdf/PdfFonts.java:14–27` loads all four via `/fonts/` plus a filename. Code, heading and Arabic fallback paths are real. | Required PDF resources; not duplicates of Compose's font stack. |
| `src/main/resources/fonts/LICENSE`, `README.md` | License/provenance for bundled PDF fonts; renderer coverage at `src/test/java/com/professoros/pdf/ExamPdfRendererTest.java:104–117`. | Keep. |

There are seven tracked TTF files, totaling **2,481,412 bytes**, and one tracked SVG. No removable bitmap, font, CSS or unused asset directory was confirmed. Optional question images are external absolute paths (`src/main/java/com/professoros/service/QuestionService.java:77–81`, `src/main/java/com/professoros/pdf/PdfImages.java:11–30`); deleting files based on repository references would not establish whether persisted questions or exam snapshots use them. External images were not inventoried from the database.

### Prototypes and generated files

The directory `prototype-compose/` is the production desktop project. `README.md:29` explicitly retains the old folder name to avoid an unrelated migration. `prototype-compose/settings.gradle.kts:3` still uses `professor-os-compose-prototype` as the Gradle project name. Renaming these is optional naming cleanup, **Medium risk / P3**, because paths appear in wrappers, documentation, scripts and tooling; no runtime benefit is expected. Do not remove the project as an abandoned prototype.

`git ls-files` found no tracked `target/`, `build/`, `.gradle/`, `.kotlin/`, database, log, class or screenshot output. The tracked Gradle wrapper JAR at `prototype-compose/gradle/wrapper/gradle-wrapper.jar` is required bootstrap infrastructure, not accidental compiled application output. Maven and Gradle wrappers and their configuration are all active.

`.gitignore:1–19,48–54` excludes generated outputs, local data, exports and Python caches. Existing ignored build folders also contain QA databases and test artifacts; **do not recommend a blanket directory purge** under this request. `data/`, its backups/stages, `generated/` and fixture outputs are explicitly preserved. Untracked `.codex/` and `graphify-out/` are current tooling, not confirmed garbage. Graph rebuilds can legitimately rewrite the complete graph.

## Dependency audit

**No redundant direct Maven or Gradle dependency was confirmed.** Declared versions below describe the repository, not an assessment of the latest available release or a vulnerability scan.

| Declaration | Version/scope | Verified use and decision |
| --- | --- | --- |
| `pom.xml:23–27` — `org.apache.poi:poi-ooxml` | 5.5.1, compile | `src/main/java/com/professoros/excel/StudentWorkbookReader.java:4–6,29`, `src/main/java/com/professoros/excel/GradeWorkbookWriter.java:4–6,19`, and demo workbook fixtures. Keep. |
| `pom.xml:28–32` — `org.apache.pdfbox:pdfbox` | 3.0.8, compile | `ExamPdfRenderer.java` imports/rendering and `src/main/java/com/professoros/service/ExamPdfService.java:6–7,33–34` preview. Keep. |
| `pom.xml:33–37` — `com.ibm.icu:icu4j` | 78.3, compile | `src/main/java/com/professoros/pdf/PdfFonts.java:6,35–40,57` uses ArabicShaping, Bidi and BreakIterator. Keep; it is not redundant with JDK text utilities for the implemented behavior. |
| `pom.xml:38–42` — `org.xerial:sqlite-jdbc` | 3.53.4.0, compile/runtime | `src/main/java/com/professoros/database/Database.java:41` uses DriverManager, while repositories use SQLite exception codes. The existing JAR's `META-INF/services/java.sql.Driver` names `org.sqlite.JDBC`. Keep despite the lack of an explicit driver-constructor call. |
| `pom.xml:43–48` — `org.junit.jupiter:junit-jupiter` | 5.14.0, test | Backend JUnit 5 tests, parameterized annotations and engine discovery. Keep. |
| `prototype-compose/build.gradle.kts:21` — `compose.desktop.currentOs` | Compose 1.10.3 plugin | Desktop runtime, Foundation, UI, graphics and platform support. Keep; the absence of Material widgets is not evidence that the entire desktop dependency can be removed. |
| `prototype-compose/build.gradle.kts:22–23` — backend JAR/runtime file tree | Local Maven outputs | Supplies Java services and their runtime dependencies to Compose. Keep. The generated JAR itself is not tracked. |
| `prototype-compose/build.gradle.kts:24–25` — `kotlin("test-junit")`, `compose.desktop.uiTestJUnit4` | Test only | Kotlin assertions and Compose/JUnit 4 tests such as `prototype-compose/src/test/kotlin/com/professoros/compose/BridgeTest.kt:3–6` and `prototype-compose/src/test/kotlin/com/professoros/compose/DashboardUiTest.kt:23`. JUnit 4 here and JUnit 5 in Maven serve different suites; neither is redundant just because two generations exist. |
| `prototype-compose/build.gradle.kts:27` — test dependency file tree | Test compile/runtime | `prototype-compose/src/test/kotlin/com/professoros/compose/DocumentWorkflowUiTest.kt:13–16` directly imports POI/PDFBox. `runtimeOnly` at line 23 alone does not provide these to test compilation. Keep unless replaced by an equivalent deliberately scoped dependency declaration. |

The 19 existing JARs in `target/compose-libs/` contain no JavaFX JAR and no duplicate artifact/version filename. POI/PDFBox bring supporting libraries such as XMLBeans, FontBox, Commons utilities and logging APIs; no transitive exclusion is justified merely because Professor OS does not import each library directly. This was a local configuration/JAR inspection, not a newly resolved Gradle/Maven dependency tree or full binary dependency minimization. **Risk of speculative exclusions: High. Expected proven dependency savings: zero.**

## Architectural problems and improvement opportunities

### A1 — Whole-workspace reloads and duplicated composition

**Confirmed structure; Medium change risk; P1 investigation, P2 refactor.**

`prototype-compose/src/main/kotlin/com/professoros/compose/DashboardBridge.kt:10–15` and `prototype-compose/src/main/kotlin/com/professoros/compose/WorkspaceController.kt:14–18` independently create `Database`, `AcademicServices` and `CourseService`. `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:14,23` instantiates both. `WorkspaceController.load` at lines 65–70 always fetches catalog, roster, lectures, the entire question bank, exams, gradebook and attendance. A successful mutation loads that aggregate and then invokes `changed()` at lines 88–97; `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:23–28` responds by loading the dashboard again, and `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:24` then selects/refreshes the workspace again when dashboard state changes. Explicit refresh also starts both paths at `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:47`.

These are actual overlapping read responsibilities, not duplicated database storage. `Database` owns no persistent connection, so duplicate objects alone are inexpensive. The significant cost is repeated reads, reconstruction and migration checks: `refresh()` calls `initialize()` (`prototype-compose/src/main/kotlin/com/professoros/compose/WorkspaceController.kt:78`), `src/main/java/com/professoros/database/Database.java:27` invokes migrations, and `src/main/java/com/professoros/database/MigrationRunner.java:39–45` rereads scripts and obtains `BEGIN IMMEDIATE` even for a fully migrated database. Preserve startup checksum verification; changing refresh frequency must never bypass that verification at startup.

Prefer one clearly owned application service composition and an explicit refresh policy. First instrument query/load counts and test invalidation after mutations. Then reuse already loaded summaries or refresh affected features. Avoid adding a generic cache/event bus or a dependency-injection framework solely to remove a few constructors. Existing documentation already acknowledges whole-module loads (`docs/UI_PERFORMANCE_AUDIT.md:32`).

### A2 — Per-record queries and non-atomic composite read models

**Confirmed query structure; Medium–High change risk; P2.**

- `src/main/java/com/professoros/repository/QuestionRepository.java:55–68` loads every question before filtering. Its mapper at lines 77–85 issues a choices query per row: one question query plus N choices queries, including questions with no choices. `ExamService.bank` at line 45 filters a course through this path.
- `src/main/java/com/professoros/repository/ExamRepository.java:59–67,118–127` loads an offering's exams, then questions per exam and choices per question. `src/main/java/com/professoros/service/DashboardService.java:24–27` needs exam title/date/status/duration but receives full snapshots. These transactional reads are correct in intent, but more work than a dashboard summary requires.
- `src/main/java/com/professoros/service/AttendanceService.java:24` opens an enrollment lookup per changed student through the repository; `src/main/java/com/professoros/service/GradebookService.java:69–73` repeatedly searches student and assessment lists for each edited cell. Costs grow with batch size.
- `src/main/java/com/professoros/service/GradebookService.java:18–21` composes categories, assessments, enrollments, grades and scale through separate repository calls/connections; `src/main/java/com/professoros/service/AttendanceService.java:14–16` and `src/main/java/com/professoros/service/ExcelExportService.java:14–15` similarly assemble multiple reads. A simultaneous external writer could change records between reads. This is a consistency risk inferred from the source, not an observed corrupt report.

Use bounded query-count tests and realistic synthetic loads before optimizing. Possible targeted changes include batch choice reads, dashboard-specific exam summaries, pre-indexed validation maps, and a shared read transaction for composite reports. Preserve snapshot ordering, Unicode search, withdrawn-enrollment policy and all atomic write checks. Do not solve this by moving SQL into Compose or removing validation.

### A3 — A real package dependency cycle around Excel

**Confirmed package cycle, not a demonstrated class initialization cycle; Medium change risk; P2.**

`service → excel` is established by `src/main/java/com/professoros/service/ExcelExportService.java:2,15` and `src/main/java/com/professoros/service/StudentImportService.java:2,19`. `excel → service` is established by `src/main/java/com/professoros/excel/GradeWorkbookWriter.java:3,21,31,58,84,91,96`, which calls the grade and attendance calculators. Source dependency scanning found this package strongly connected component; the graph alone cannot establish its direction.

The writer reuses the authoritative calculators, which is good. Do not duplicate formulas to break the cycle. If this boundary becomes costly, move pure calculations into a small toolkit-independent calculation package or supply calculated export results from the service. Keep this proportional: no interfaces for every record or repository are required. The models themselves have no imports of Compose, JDBC, repositories, services or database classes.

### A4 — Controller state concentration and repeated routing

**Confirmed design weakness; Medium change risk; P2.**

`prototype-compose/src/main/kotlin/com/professoros/compose/WorkspaceController.kt:27–46` owns state for catalog, roster, lectures, questions, exams, grades, attendance, imports, PDFs and several modal types. Its guard at lines 100–108 manually enumerates their dirty-state rules. It is only 130 physical lines, but each new feature must participate correctly in loading, clearing, guarding and refreshing.

Navigation uses display strings across `prototype-compose/src/main/kotlin/com/professoros/compose/Dashboard.kt:20–22,89–103`, `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:49–57` and `prototype-compose/src/main/kotlin/com/professoros/compose/CatalogScreens.kt:16–63`, and test shells reproduce these switches (`prototype-compose/src/test/kotlin/com/professoros/compose/UiAuditTest.kt:102–105`, `prototype-compose/src/test/kotlin/com/professoros/compose/WorkspaceLayoutTest.kt:28–29`). This permits route/test wiring to drift. Screen files also contain controller extension functions (`prototype-compose/src/main/kotlin/com/professoros/compose/QuestionScreen.kt:31–35`, `prototype-compose/src/main/kotlin/com/professoros/compose/ExamScreen.kt:11–23`, `prototype-compose/src/main/kotlin/com/professoros/compose/LectureScreen.kt:12–23`); the responsibilities are logically distinguishable but physically mixed.

Consider a small route type and one reusable production workspace-content function. Extract feature coordination only as needed while retaining one central unsaved-change policy. `RecordForm`, `JdbcOperations`, grade/attendance draft adapters and explicit service composition are useful, bounded abstractions; replacing them with a universal framework would increase complexity.

### A5 — Repository business invariants and specialized infrastructure errors

**Confirmed boundary tension, not disposable duplicate validation; High risk if changed; P2/P3.**

`src/main/java/com/professoros/repository/ExamRepository.java:14–28,69–95` enforces draft/finalization/archive rules, preserves snapshots and updates usage inside transactions. `src/main/java/com/professoros/repository/StudentImportRepository.java:14–48` revalidates offering/student/enrollment state after preview. `src/main/java/com/professoros/repository/JdbcOperations.java:73–95`, nominally a general helper, recognizes domain-specific SQLite trigger text for grades, exams and offering history.

Some business decisions therefore reside below services. Moving checks exclusively into services would introduce time-of-check/time-of-use gaps and weaken protection against other writers. Keep transactional checks. If these classes grow, separate pure policy decisions from persistence mechanics or move feature-specific error translation nearer each repository, while leaving the atomic recheck in place. Historical trigger SQL must not be edited to make the translation cleaner.

### A6 — Small, real implementation duplication

**Confirmed overlap; Medium risk; P2/P3.**

1. Course text/required/length rules in `src/main/java/com/professoros/service/CourseService.java:73–114` overlap `src/main/java/com/professoros/service/AcademicValidation.java:11–24`, but course codes have a 20-character limit versus 40 for academic identifiers, and exception types differ. CourseRepository also implements its own resource/binding/error plumbing (`src/main/java/com/professoros/repository/CourseRepository.java:26–142`) alongside `JdbcOperations`. Share only equivalent mechanics; do not flatten distinct policies.
2. PDF/Excel non-overwriting exports repeat validation, staging, move and cleanup (`src/main/java/com/professoros/service/ExamPdfService.java:38–58`, `src/main/java/com/professoros/service/ExcelExportService.java:18–26`). A small shared file-writing helper could reduce drift, but must preserve filenames, overwrite refusal, staging cleanup and user-facing messages.
3. Question image selection embeds another AWT file dialog at `prototype-compose/src/main/kotlin/com/professoros/compose/QuestionScreen.kt:57–64`, while `prototype-compose/src/main/kotlin/com/professoros/compose/DocumentWorkflows.kt:14–26` owns a reusable chooser with filters. Reuse only after checking cancellation, file filters and popup ownership; native chooser behavior is not covered by the current semantic UI tests.
4. `prototype-compose/src/main/kotlin/com/professoros/compose/CatalogScreens.kt:28` reconstructs course code/name/department search instead of calling the existing model predicate used by `src/main/java/com/professoros/repository/CourseRepository.java:118–120`. Compare whitespace, casing and Unicode behavior before unifying it.

These are narrow consolidation opportunities. Separate PDF and Excel renderers, question and exam snapshots, and Java drafts versus observable Compose adapters have different responsibilities and should remain separate.

### A7 — Input-validation inconsistency in PDF save

**Confirmed missing guard; Low–Medium change risk; P1.**

`src/main/java/com/professoros/service/ExamPdfService.java:40–41` dereferences `destination`, then `target.getFileName()`, without checking either for null. A null destination or filesystem root therefore cannot produce the intended actionable filename validation. `src/main/java/com/professoros/service/ExcelExportService.java:19` already guards both cases, with a regression test at `src/test/java/com/professoros/excel/ExcelIntegrationTest.java:219–222`.

The normal file-dialog path supplies a selected filename, so this is an API boundary edge case rather than evidence that ordinary exports fail. Add equivalent PDF service boundary tests and a concise validation guard before consolidating export mechanics. `src/test/java/com/professoros/service/ExamPdfServiceTest.java:19–53` covers saved snapshots, overwrite refusal and mismatch acknowledgement, but not this case. No fix or new test was made in this audit.

### A8 — Build integration is practical but coupled to generated paths

**Confirmed maintainability issue; Medium change risk; P2/P3.**

`prototype-compose/build.gradle.kts:7–18,22–27` invokes Maven, names `professor-os-0.1.0-SNAPSHOT.jar` explicitly and consumes an untyped runtime JAR directory. A Maven version/artifact rename must also update Gradle. Backend dependencies do not participate as normal Gradle module metadata, so dependency conflict diagnostics are less straightforward. The Java target is shared with builds/tests; `README.md:114` correctly requires sequential Maven/Gradle execution and closing the running development app.

Keep both build tools under the current project decision. Prefer a small centralized artifact-path/version convention and clear task inputs/outputs over migrating the whole project. Preserve `prepareBackend`'s stale-output protection at lines 10–14. Do not remove generated-library refresh logic merely to shorten the build file, and do not run the application from the backend JAR.

### A9 — Dense formatting increases reading cost

**Confirmed; Low risk for isolated formatting, Medium if combined with behavior changes; P2.**

Examples include `prototype-compose/src/main/kotlin/com/professoros/compose/WorkspaceController.kt:107` (the dirty-state guard), `prototype-compose/src/main/kotlin/com/professoros/compose/CatalogScreens.kt:62` (offering filtering/table construction), `prototype-compose/src/main/kotlin/com/professoros/compose/ExamScreen.kt:42` (finalization confirmation) and `src/main/java/com/professoros/excel/GradeWorkbookWriter.java:139–150` (styles). Several statements, conditions and lambdas share single lines; class line counts conceal complexity.

Format touched files incrementally and use descriptive intermediate values where they expose decisions. Keep formatting-only changes separate from logic changes. No new formatter dependency, broad architectural rewrite or mass reformat is necessary to address this.

## Test audit

| ID | Finding and evidence | Recommendation | Risk / priority |
| --- | --- | --- | --- |
| T1 | `prototype-compose/src/test/kotlin/com/professoros/compose/BridgeTest.kt:47–54` checks the same five dark backgrounds, four foregrounds and 4.5 threshold already covered by `prototype-compose/src/test/kotlin/com/professoros/compose/SettingsUiTest.kt:77–84`, whose loop covers both palettes and system resolution. | Consolidate the overlapping dark contrast test; retain the newer test's complete palette and mode assertions. Do not remove all of BridgeTest. | Low / P3 |
| T2 | The `src/test/java/com/professoros/service/AcademicServiceTest.java:103–108` session-helper test exercises a component no longer used by production (C4). | Retire only with an explicit decision to retire SessionContext. Current offering-selection and discard-guard tests must remain. | Medium / P2 |
| T3 | `src/test/java/com/professoros/repository/LectureRepositoryTest.java:46–56` is named `timelineSortsByDateThenNumberAndSearchIsLiteralCaseInsensitive`, although Timeline UI was removed. It asserts chronological `findByOffering`, literal search and offering isolation, all still relevant to Lectures and Attendance. | Rename the test to describe current behavior; **do not delete it** as obsolete feature coverage. | Low / P3 |
| T4 | Production `prototype-compose/src/main/kotlin/com/professoros/compose/Main.kt:24–34,47` orchestrates two asynchronous load paths, while tests such as `prototype-compose/src/test/kotlin/com/professoros/compose/WorkspaceLayoutTest.kt:24–33` construct simpler callbacks. `WorkspaceController.select` at lines 79–80 returns immediately while busy. No test was found that deliberately delays the independent dashboard/workspace loads and checks that a newer selection is eventually reflected in both header and content. | Add a deterministic orchestration test for a selection/refresh arriving during an in-flight load, plus cancellation/failure ordering. A lost selection is a **potential** defect until reproduced. Reuse a production host/coordinator rather than another test-only copy of its behavior. | Medium / P1 |
| T5 | `prototype-compose/src/test/kotlin/com/professoros/compose/UiAuditTest.kt:113–118` asserts the refresh control is displayed and writes 52 captures; `prototype-compose/src/test/kotlin/com/professoros/compose/WorkspaceLayoutTest.kt:34–40` writes route captures after navigation assertions. They do not compare these captures against approved golden files. | Preserve the captures and manual QA workflow. Strengthen a few important route-specific visibility/layout assertions if useful. Do not describe screenshot creation as automated pixel-regression coverage. | Low–Medium / P2 |
| T6 | No query-count or large-data performance regression test accompanies the paths in A1/A2. Existing `prototype-compose/src/test/kotlin/com/professoros/compose/UiAuditTest.kt:61–81` checks offering-label equivalence and grade-summary caching, not the number of SQL calls during a workspace refresh. | Add bounded query/load-count checks and representative synthetic data before optimizing. Avoid elapsed-time assertions that fail on slow machines. | Medium / P2 |
| T7 | `tools/test_demo_workspace.py:33–77` has six important safety tests. Maven's plugins (`pom.xml:51–66`) and Gradle's test configuration (`prototype-compose/build.gradle.kts:30–34`) do not invoke them. `docs/DEMO_WORKSPACE.md:65` documents a separate command. | Keep this suite. Document/enforce an explicit third verification step for maintenance-tool changes; no claim that the two JVM suites cover it. A small verification script is sufficient if approved. | Low / P2 |
| T8 | PDF null/root destination validation is untested while Excel covers it (A7). Native picker interaction, high-DPI/multi-monitor, full screen-reader behavior and other platforms remain explicitly limited in `prototype-compose/README.md:102`. | Add the narrow service regression; keep native/platform limitations visible and schedule manual checks when those areas change. No platform certification is inferred from renderer tests. | Low–Medium / P1 for PDF guard; P3 for broader QA |

No disabled test annotation was found in the tracked Java/Kotlin test sources. Migration upgrade tests, schema failure fixtures, immutable-history repair tests, generation's brute-force oracle (`src/test/java/com/professoros/service/ExamGeneratorTest.java:56`), grade boundary tests, repository rollback tests and PDF confidentiality tests cover distinct risks; their overlap with UI workflows is useful defense across layers, not grounds for removal. Fixtures such as `src/test/java/com/professoros/support/LectureFixture.java`, `QuestionFixture.java` and Compose `Fixture.kt` are shared infrastructure and must be preserved.

## Explicit preservation decisions

- **All ten migrations** under `src/main/resources/db/migrations/` are registered at `src/main/java/com/professoros/database/MigrationRunner.java:18–27`. Startup verifies resource names/checksums at lines 56–70 and rolls back failures. Old versions are required to upgrade databases; none is obsolete cleanup material. Any future schema change requires a new migration.
- Preserve `src/test/resources/db/migrations/V002__broken.sql` and `V009__legacy_grade_management.sql`. They deliberately model failure/legacy states; malformed or historical SQL is their purpose.
- Preserve `src/main/java/com/professoros/database/LegacyGradeMigrationRepair.java`, its tests and `docs/V009_REPAIR.md`. Its `main` at lines 16–19 is an explicit maintenance entry point. The specialized backup/schema/history verification at lines 22–82 is not application-startup code and was not run. Its historical corrective logic must not be generalized into a checksum bypass.
- Preserve `src/main/java/com/professoros/demo/` and `tools/demo_workspace.py`, including sample generators and `tools/test_demo_workspace.py`. `src/main/java/com/professoros/demo/DemoDataSeeder.java:23` is a CLI entry point documented at `docs/DEMO_WORKSPACE.md:31`; the absence of a normal UI caller is intentional. The Python tool's CLI dispatch at lines 134–148 invokes backup/stage/install/restore workflows indirectly. Preserve demo data, completion receipts, manifests and backups as requested.
- Preserve `src/main/java/com/professoros/database/Database.java:34–60` connection configuration/resource handling and `src/main/java/com/professoros/repository/JdbcOperations.java:49–60` transaction/rollback behavior. They are necessary infrastructure, not excess abstraction.
- Preserve both frontend and PDF font licenses, all currently used fonts, runtime native/JDBC support, test fixtures and historical verification documents.

## Prioritized cleanup plan and estimated impact

This is a proposed sequence for approval, not work already performed.

| Stage | Scope | Estimated impact | Validation after approval |
| --- | --- | --- | --- |
| 1 — Mechanical cleanup | C1–C3, optionally C5; T1/T3 separately | Remove one empty file, three imports, one icon branch and optionally one forwarding method. Retire one duplicate eight-line contrast test; improve one test name. Essentially no runtime/disk improvement. | Review exact diff; Maven then Compose suites. Inspect snapshots and native startup for the icon change under project rules. |
| 2 — Compatibility decision | C4/T2; decide S1/S2/S3 independently | SessionContext retirement removes 12 production lines and seven test lines. Optional dashboard-mode simplification saves only a few dozen lines but reduces alternate wiring. Public-API/test changes require more care than their size suggests. | Search all callers again; preserve functional selection, keyboard, empty-state, animation and unsaved-edit tests. Update only current compatibility documentation. |
| 3 — Correctness and regression gaps | A7, T4, T8's PDF case | Actionable PDF path errors and confidence in asynchronous offering/refresh coordination. May add more test code than it removes; that is appropriate. | Focused boundary/orchestration tests plus required sequential suites; no production database used. |
| 4 — Measured loading improvements | A1/A2/T6 | Potentially much larger benefit than deletion. Question loading currently has 1+N data queries; a batch implementation could reduce that component to a bounded small number. No percentage speedup or memory reduction is established. | Query-count tests, output/snapshot equivalence, preservation of atomic writes and composite-read consistency. |
| 5 — Incremental maintainability | A3–A6, A8–A9; T7 | Clearer dependencies, less routing/export/chooser duplication, more readable code and repeatable verification. No new framework required. | Small independent changes, relevant regression tests, Maven/Gradle sequentially; Python suite for tool changes; native chooser checks when changed. |

The conservative C1–C5 list represents **15 production source lines, two test import lines and one empty placeholder** if all compatibility decisions are approved: 12 lines in SessionContext, one production import, one icon branch and one writer-overload line. Retiring C4's seven-line test and T1's eight-line duplicate brings the total to approximately **32 Java/Kotlin source lines**, around **0.34%** of the 9,465 Java/Kotlin lines. Formatting and future fixes can increase line counts, so this is only a rough deletion estimate, not a quality metric.

No entire implemented feature, migration, asset set or direct dependency can responsibly be removed on the evidence collected. Required runtime libraries and fonts dominate distribution size; the proven cleanup offers negligible package-size savings. Its value is removing misleading remnants and reducing small maintenance distractions. The architectural recommendations can improve clarity and loading efficiency, but must be justified and verified individually.

After approved code changes, follow `AGENTS.md`: close the development app, run Maven and Gradle sequentially (this Windows machine uses `cmd /d /c` and Maven `-DforkCount=0` as documented in `README.md:87–114`), inspect Compose snapshots and native startup for UI changes, update `PROJECT_NOTES.md` for significant decisions, and run `graphify update .`. Never edit applied migrations or replace database files as part of cleanup.

## Final assessment

**Professor OS is well-structured.** It has a small amount of confirmed dead/retained code and several concrete maintenance/scaling opportunities, but the evidence does not support calling it moderately bloated or significantly overengineered. Keep the Java/Compose/SQLite architecture, preserve its business and data-safety rules, and prefer focused cleanup and stronger orchestration tests over a rewrite.
