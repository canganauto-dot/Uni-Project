# Compose feature-parity audit

Reviewed against the final JavaFX implementation in commit `72dc145` before removing its sources. Parity here means preserving implemented academic operations, validations and persistence, not identical widget layouts. The former Analytics route was a planned-feature placeholder; it contained no implemented analytics workflow to retain. Timeline had already been removed by request.

| Implemented operations | Compose entry point / implementation | Regression evidence |
| --- | --- | --- |
| Real counts, offering progress, upcoming/recent activity, refresh | Overview / Activity; DashboardBridge, OverviewPanels | BridgeTest, DashboardUiTest, Java DashboardServiceTest |
| Course CRUD/search/details, create offering | Courses; CatalogScreens, RecordForms | WorkspaceUiTest, WorkspaceLayoutTest, Java CourseService/Repository tests |
| Student CRUD/search/details/enrollment history | Students; CatalogScreens, RecordForms | WorkspaceUiTest, Java AcademicService/Repository tests |
| Term/offering CRUD, constraints, selection and roster | Terms / Offerings / Teaching context; RecordForms, WorkspaceController | WorkspaceUiTest, WorkspaceLayoutTest, Java AcademicService/Repository tests |
| Enroll, withdraw, complete, reactivate, preserve history | Teaching context; RosterScreen | WorkspaceUiTest, Java AcademicService/Repository tests |
| Lecture CRUD/search/status, offering scope, progress | Lectures; LectureScreen | WorkspaceUiTest, Java LectureService/Repository tests |
| Five question types, choices/answers, image path, filters and details | Question Bank; QuestionScreen, QuestionDraft | AssessmentUiTest, Java QuestionService/Repository tests |
| Manual ordering, snapshot preservation, mark warnings, constrained generation, finalize/archive/delete | Exam Builder; ExamScreen, ExamDraft | AssessmentUiTest, Java ExamService/Generator tests |
| Student/answer-key PDFs, print profile/logo, pagination preview, safe saving | Exam Builder → Preview / export PDF; DocumentScreens/Workflows | DocumentWorkflowUiTest, Java ExamPdfService/Renderer tests |
| Categories/assessments/scale, weighted totals, individual/bulk entry, blank versus zero, incomplete results | Gradebook; GradebookScreen/Editor | GradebookUiTest, Java Gradebook/GradeCalculator tests |
| Lecture roster, all five marking states, bulk marking/clearing, saved history/summaries, percentage policy | Attendance; AttendanceScreen/Editor | AttendanceUiTest, Java AttendanceTest |
| Global/offering student XLSX import, bilingual/manual mapping, preview/counts, duplicate protection | Students / Teaching context; DocumentScreens/Workflows | DocumentWorkflowUiTest, Java ExcelIntegrationTest |
| Grades, breakdown, attendance and statistics XLSX export | Gradebook / Attendance; existing ExcelExportService | DocumentWorkflowUiTest, Java ExcelIntegrationTest |
| Light/Dark/System, persistent appearance, term access, storage policy | Settings; SettingsScreen, Java ThemePreferences/AppPaths | SettingsUiTest, Java ThemePreferencesTest/AppPathsTest |
| Shared selection, keyboard navigation, dirty-state guards, responsive layouts | Main, Dashboard, WorkspaceController/Controls | DashboardUiTest, WorkspaceLayoutTest and feature UI suites |

All Java business services, repositories, domain models, database infrastructure, V001–V010 migrations, PDFBox/ICU/fonts, Apache POI and their backend tests are retained. The toolkit-independent SessionContext helper and its Java test are also retained; Compose owns its current offering directly in its existing window/controller state.

Removed only the JavaFX launcher, view/controller/theme packages, CSS, unused FXML/image placeholders and JavaFX-specific smoke/CSS tests. The Maven project produces the backend library; Compose is the only launch target. `prepareBackend` refreshes generated classes/runtime libraries to prevent deleted code or dependencies surviving incremental builds. BridgeTest explicitly verifies the absence of legacy toolkit classes, launchers and CSS on the actual desktop test classpath.

Run the backend and desktop tests sequentially as documented in README. Automated workflows use isolated databases and verify saved values after reconstruction. Native checks supplement them; they do not claim exhaustive physical-keyboard, screen-reader, cross-platform or multi-monitor certification. The dated removal entry in PROJECT_NOTES records final test counts, native startup and production database integrity evidence.
