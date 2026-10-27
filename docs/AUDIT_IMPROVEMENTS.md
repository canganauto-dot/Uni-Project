# Professor OS audit improvements — 2026-10-27

Implemented the approved correctness, performance and mechanical cleanup stages from CODEBASE_AUDIT.md. The original audit remains an unchanged historical baseline, now committed with the verification documentation. No dependency, calculation rule, migration or persisted data conversion was introduced.

## Changes and confirmed bugs

- PDF export now rejects null, root-only, empty, wrong-extension and directory destinations with actionable validation before staging. Missing parent folders produce the established storage error. Tests also prove rejected paths leave no temporary output, while existing overwrite/mismatch checks remain active.
- Deterministic queued-IO tests reproduced lost offering-selection requests during a load and a direct-selection path that bypassed unsaved forms. One controller now owns dashboard and feature publication. Requests wait for active work, coalesce to the latest valid selection, and pass the complete unsaved-change guard. Failed reads retain a consistently labelled old context; retries work. Explicit context clearing remains distinct from startup fallback. Writes are not cancelled for newer reads, and scope cancellation does not restart pending work.
- Dashboard summaries reuse the loaded catalog, roster, lectures, exams and attendance through DashboardService.summarize. The original DashboardService.load and DashboardBridge.load APIs remain available. Startup verifies migrations once per workspace service instance; ordinary refreshes do not reacquire migration write locks.
- Offering switches reuse the existing global catalog/question list. Explicit refresh and committed mutations reload global data. Question/choice lists use two batch SELECTs in the same transaction. A deterministic concurrent-commit test verifies that a reader cannot combine old choices with a new prompt/marks.

Primary implementation files are `src/main/java/com/professoros/service/ExamPdfService.java`, `src/main/java/com/professoros/repository/QuestionRepository.java`, `src/main/java/com/professoros/service/DashboardService.java`, and `prototype-compose/src/main/kotlin/com/professoros/compose/{Main,WorkspaceController,DashboardBridge}.kt`.

## Measurements

Test-only JDBC interception counts successful application SELECT executions. PRAGMAs, transaction control, DDL and fixture creation are excluded. The fixture has three offerings, twelve enrolled students, five lectures and twenty two-choice MCQs. Measurements were taken before changing loading behavior and retained in PROJECT_NOTES.md. These are statement counts, not elapsed-time, memory or FPS claims.

| Operation | Original | After correctness stage | Final |
| --- | ---: | ---: | ---: |
| Question bank | 21 | 21 | 2 |
| Startup | 55 | 54 | 21 |
| Settled explicit refresh | 96 | 54 | 20 |
| Offering switch | Not separately measured | 54 | 14 |
| Course creation and refresh | Not measured | Not measured | 21 |

The original settled refresh follows the old workspace-refresh, dashboard-reload and selection-driven second-refresh sequence. The stage-1 switch figure excludes one fixture lookup used to obtain the destination ID. Final tests obtain IDs before counting. A forty-MCQ bank still needs two SELECTs. Tests enforce query budgets and correctness, assert no question-bank read during selection, compare the dashboard with its public loading API, and check explicit global invalidation.

## Exact cleanup

- Deleted only `src/main/java/com/professoros/util/.gitkeep` (zero bytes).
- Removed unused `clip` from `OverviewPanels.kt`, `Color` from `BridgeTest.kt`, and `PdfKind` from `DocumentWorkflowUiTest.kt` in their existing Compose main/test source directories.
- Removed the uncalled `external` icon branch from `prototype-compose/src/main/kotlin/com/professoros/compose/DesignSystem.kt`.
- Removed only `BridgeTest.primaryTextColorsHaveReadableDarkContrast` and its now-unused `luminance` import. `SettingsUiTest.lightAndDarkTokensHaveReadableContrastAndSystemResolves` retains the complete light/dark contrast and mode coverage.
- Renamed `timelineSortsByDateThenNumberAndSearchIsLiteralCaseInsensitive` to `offeringLecturesSortByDateThenNumberAndSearchIsLiteralCaseInsensitive` in `src/test/java/com/professoros/repository/LectureRepositoryTest.java`; all its assertions remain.

SessionContext, GradeWorkbookWriter overloads, ExcelExportService overloads, fixtures, demo generators, backups, historical documentation, all fonts/licenses and every migration remain intact. Compose styling and animations are unchanged.

## Verification

Final results: **208 backend tests, 38 Compose tests and six Python backup/restore safety tests passed**, with zero failures/errors/skips. Maven and Gradle ran sequentially with the development application closed, using JDK 25 and the documented Windows `cmd /d /c` and Maven `-DforkCount=0` workaround. Python safety tests ran separately.

Native Windows startup and Overview, offering switching/roster, Gradebook, Attendance, Question Bank, Exam Builder and two-page Student PDF preview were verified using a fresh isolated QA copy. The unsaved-settings Keep/close behavior was checked. The application closed normally. No academic write or native Save As was performed. Generated minimum-size dark Attendance and light Gradebook snapshots were inspected; existing animation/keyboard/document/persistence tests pass.

Before/after native checks found the QA database byte-identical, with `integrity_check = ok`, no foreign-key violations and unchanged ten-row migration history. All 48 protected production/demo, backup, generated-fixture, migration and test-resource files matched their initial SHA-256 hashes. Production/demo databases were never opened by the application during this work. See the final 2026-10-27 stage-3 entry in PROJECT_NOTES.md for paths, hashes and detailed observations.

Permanent new coverage is in `ExamPdfServiceTest`, `QuestionRepositoryTest`, `DashboardServiceTest`, Compose `WorkspaceLoadingTest` and `LoadingQueriesTest`. Test-only `SqlQueries` intercepts JDBC calls; `QueuedIo` controls coroutine continuations without sleeps or another dependency. Isolated database integrity, foreign keys and all ten migration-history rows are verified.

## Remaining limitations

Workspace feature read models still use separate existing service transactions; a single cross-module snapshot against simultaneous external writers is not guaranteed. The question/choice aggregate does have a verified consistent transaction. Explicit refresh and post-mutation reload still read all modules once; further mutation-specific invalidation should be justified with measurements. Public standalone bridge reads remain compatible and are not the production orchestration path.

Native Windows checks do not certify other operating systems, physical screen-reader behavior, multi-monitor/high-DPI transitions or native Microsoft Excel interoperability. Graphify's AST update completes, but its parser partially extracts five Kotlin files and has no SQL parser installed; source/compiler/tests remain the evidence for those areas. Existing toolchain deprecation and missing POI logging-provider diagnostics are unchanged and nonfatal.
