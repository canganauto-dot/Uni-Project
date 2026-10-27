# Compact academic demo verification — 2026-10-27

## Target, backup and recovery

The exact target is `C:/Users/HP/Desktop/Uni Project/data/professor-os.db`. Before replacement it had the known legacy demo's six course identities, 180 fictional-marked students and twelve DEMO-marked offerings. There were no non-demo parent records. The old demo included user edits to grade/attendance records and the CS201 pass threshold; the complete backup preserves those edits too.

- Original target SHA-256: `2d000ad10f2704b81c3fc77d88f899e9e3f414e3d56de24d36d1c1738649b637`.
- New verified backup: `data/backups/demo-20261027T145444975012Z/professor-os.db`.
- Backup manifest: `data/backups/demo-20261027T145444975012Z/manifest.json`.
- Backup SHA-256: `2e96fc9c1840fb67512a56d4727fc9d489d88a8d8f2dc105f66736a3e94b9bff`.
- Installed compact demo SHA-256: `962720b308d198a5951e56dbfd9cee0adc5aae14977d02adf9c7e88c5a17e76a`.
- Previous backup directory `data/backups/demo-20261025T165057050499Z/` is preserved unchanged.

Preparation used the existing SQLite backup API, with integrity, foreign keys, every table's logical contents, DDL and complete migration history verified against the source. A SQLite backup's physical hash can differ from its source; logical equality was verified. The original remained unchanged throughout generation/tests. Only the newly prepared `.demo-stage-20261027T145444975012Z.db` was seeded. Install used the existing receipt, digest, unchanged-source, schema/history, offline-lock and atomic-replacement checks, plus the new compact-data validation. No historical migration or stored checksum was edited.

To recover the complete preceding demo, close all app instances and run from the repository root:

```powershell
python tools/demo_workspace.py restore data/backups/demo-20261027T145444975012Z/manifest.json --app-closed
```

Restore verifies the backup and history and creates another safety backup before replacement. The real install/restore CLI round trip was verified on disposable copies, including all original table contents. An incomplete candidate with a matching receipt was rejected without changing its disposable target. The installed demo was not rolled back after successful validation.

## Exact installed counts

| Table | Rows | Coverage |
| --- | ---: | --- |
| courses | 2 | CS201 Object-Oriented Programming; CS202 Data Structures and Algorithms |
| academic_terms | 1 | Active Fall 2026–2027 |
| course_offerings | 2 | One active section A per course |
| students | 70 | Unique three-part Iraqi names; 35 women and 35 men in curated fixture metadata |
| enrollments | 70 | Disjoint 35-student active rosters; every student enrolled once |
| lectures | 24 | Twelve completed sessions per offering |
| questions | 40 | Twenty per course; four topics × all five types |
| question_choices | 32 | Four ordered choices for each of eight MCQs |
| exams | 6 | Two finalized and one draft per course |
| exam_questions | 80 | Saved snapshots, correct course relationships and ordered selections |
| exam_question_choices | 64 | Ordered saved choices with correct flags |
| assessment_categories | 8 | Four per offering; weights 20/10/30/40 total 100% |
| assessments | 8 | Four graded assessments per offering |
| student_grades | 280 | 35 × 4 × 2, with no missing scores |
| grading_scales | 2 | Existing CS201 pass 50%, CS202 pass 60%; letter thresholds unchanged |
| attendance | 840 | 35 × 12 × 2, with no unmarked student/session pairs |
| schema_version | 10 | Original resources, checksums and timestamps preserved |

All 70 full names have exactly three alphabetic components and are distinct; all 70 third names are distinct. Gender assignments are curated in demo metadata and tested for consistent given-name/parent-name combinations; no gender column or other schema change was introduced. First/last-name storage remains compatible: one given name followed by father and grandfather names.

Both gradebooks have **35 complete, zero incomplete results**, with final percentages 25–96. The 49/50/51 and 59/60/61 boundaries are exact, not rounding approximations. Four excellent students reach 90% or higher in each cohort. Assessment scores vary while their weighted offsets sum to zero. CS201 has 28 passing and seven failing students; CS202 has 22 passing and thirteen failing students. The standard calculation code is unchanged. Independent letter and pass thresholds explain the existing CS201 F/PASS result at 50–59; the application default pass mark remains 60%.

Attendance totals are **598 Present, 91 Late, 107 Absent and 44 Excused**. CS201 has 298/48/54/20 respectively; CS202 has 300/43/53/24. Saved percentages continue to use the existing policy, excluding Excused and treating Late as attended. No cancelled or future session was premarked; all recorded sessions are completed and in the past.

## Verification performed

- JDK 25, sequential Windows Maven `cmd /d /c` with `-DforkCount=0 clean verify`: **209 passed**, zero failures/errors/skips.
- Compose test suite: **38 passed**, zero failures/errors/skips. The demo scenario renders every route and verifies both complete active-offering gradebooks. Gradebook and Attendance renderer captures were visually inspected.
- Python backup/restore safety suite: **8 passed**. Coverage includes corrupted backups, changed source/schema/history, incomplete receipts/populations and non-demo refusal.
- Disposable-copy CLI install → restore: complete logical round trip passed. Invalid-stage rejection left its target unchanged.
- Native Windows app launched twice through Compose Gradle `run`, explicitly targeting the identified demo directory. Inspected Overview counts, complete CS201 grades, saved attendance, the two-offering picker, CS202 roster/complete grades after restart, and its three correctly finalized/draft exams. Both runs closed normally with BUILD SUCCESSFUL.
- Actual saved data exported through unchanged application services to **12 student/answer-key PDFs (52 pages)** and **two four-sheet Excel reports**. PDFs parsed and previewed, saved snapshot fields matched their bank sources, all 280 exported grade cells were numeric and all 840 exported attendance cells were populated. Generation returned ten distinct course-valid questions for each course without saving another exam.
- `integrity_check = ok`; no foreign-key violations. Every schema object and all ten migration-history rows matched the pre-install manifest. After both native runs and document/generation checks, the installed database was byte-identical and all table contents were unchanged.
- All 18 protected backup/manifest, migration and test-resource files matched the hashes captured after backup preparation. Old backups and fixtures were retained. Graphify was consulted and rebuilt; its existing SQL-parser and partial Kotlin extraction limitations remain, with actual source/tests authoritative.

Independent validation JSON, new import fixtures, PDFs and workbooks are under ignored `generated/demo-compact-imports-20261027T145444975012Z/`. Stage files, databases and backups remain outside Git. Only demo code, relevant tests and documentation are committed. The pre-existing AGENTS.md and local tooling changes remain separate.
