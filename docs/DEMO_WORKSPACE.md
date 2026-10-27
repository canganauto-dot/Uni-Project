# Academic demo workspace

This is an explicit developer tool, never an application startup action. All demo people, academic records and email addresses are fictional. The user-authorized operation replaces academic records, preserving database DDL, schema_version rows/checksums and appearance preferences. Do not use it to merge demo data into a real academic workspace.

## Contents and useful routes

The compact-v2 reference date for this installation is **2026-10-27**. An explicit ISO date controls the teaching calendar for future runs. The single Fall term remains active for result review and a possible resit; its twelve completed Mon/Thu sessions and four graded assessments are in the past. Historical migrations are never edited.

- Exactly **2 courses**: CS201 Object-Oriented Programming and CS202 Data Structures and Algorithms.
- Exactly **1 active term** and **2 active offerings**, section A, one per course.
- **70 unique students**, partitioned into two disjoint rosters of **35**. All are fictional Iraqi three-part given/father/grandfather names, curated as 35 women and 35 men. All 70 third names are distinct. The existing first/last-name model stores the given name in first_name and father plus grandfather in last_name; no schema change is needed. IDs remain text with leading zeros.
- **24 completed lectures**, twelve per offering, with **840 attendance rows**: every student in every session has Present, Late, Absent or Excused. Nothing is accidentally unmarked. Attendance profiles vary with realistic student performance; the normal saved-attendance policy remains authoritative.
- **40 questions**, twenty per course: four relevant topics, each with all five question types. MCQs have topic-specific distractors and varied correct-choice positions; model answers explain solutions and boundary cases. There are 32 bank-choice rows.
- **6 saved exams**, three per offering: finalized midterm (50 marks), finalized final (100 marks), and a draft resit/practice paper (50 marks). Their immutable snapshots contain 80 question rows and 64 choice rows, with matched intended/calculated totals.
- **8 categories, 8 assessments, 2 scales and 280 scores**. Each offering has laboratory portfolio, quiz, midterm and final assessments, all normalized to 100 marks and weighted **20/10/30/40**. Every assessment has all 35 grades. Final percentages span 25–96, including 49/50/51 and 59/60/61. Weighted offsets vary individual assessment scores while preserving exact final boundaries. Every result is complete; excellent, average, borderline and failing students are represented.

The verified existing demo settings are retained: CS201 passes at **50%**, CS202 at **60%**. Letter thresholds stay 90/80/70/60 in both. Pass/fail and letter thresholds are independent settings in the application, so CS201 can show an F letter with PASS at 50–59. This uses the existing configurable scale, not a grading-rule change; the application's default remains 60%.

Start with **CS201 / Fall / A**. Gradebook shows complete scores and saved statistics, Attendance shows fully recorded sessions, and Question Bank/Exam Builder provide realistic papers for generation and PDF export. Switch to CS202 to inspect its separate 35-student cohort. No completed/planned extra offerings are needed to demonstrate populated screens. Fictional markers remain on all courses, students and offerings.

## Backup, staging and installation

Close every Professor OS instance. The maintenance CLI requires `--app-closed`; this is an operator declaration, not process detection. It also refuses SQLite sidecars and locked databases. Never bypass these checks or run concurrently with the app. Python 3 (standard library only), JDK 25 and the existing Maven/Gradle wrappers are required for this optional developer workflow.

Run the documented backend/Compose tests first; Gradle prepares the Java backend and runtime libraries. From the repository root in PowerShell:

```powershell
python tools/demo_workspace.py prepare data/professor-os.db --app-closed
# Use the exact manifest path printed above:
$demoManifest = 'C:\path\printed\by\prepare\manifest.json'
$demoPlan = Get-Content -Raw $demoManifest | ConvertFrom-Json
& "$env:JAVA_HOME\bin\java.exe" --enable-native-access=ALL-UNNAMED -cp 'target/classes;target/compose-libs/*' com.professoros.demo.DemoDataSeeder $demoPlan.stage '2026-10-27' 'generated/demo-imports-NEW-RUN'
# Only after the generator exits successfully and review/tests pass:
python tools/demo_workspace.py install $demoManifest --app-closed
```

For configured storage, replace `data/professor-os.db` with the actual `PROFESSOR_OS_DATA_DIR/professor-os.db` path. The runtime default is repository-root data, as documented by AppPaths. Unix classpaths use `:` instead of `;`.

The CLI first refuses unrecognized, mixed or non-demo workspaces using the known course identities, parent counts and fictional markers. The Java seeder independently refuses non-demo academic rows even if a file is renamed as a stage. Empty, newly initialized test stages are allowed. Prepare uses SQLite's backup API into a unique UTC-timestamped directory under the database's `backups/`. It verifies integrity, foreign keys, all table contents, DDL, counts and migration history, records SHA-256 hashes, and creates a sibling `.demo-stage-*.db` copy. Java refuses ordinary live-database filenames, verifies migrations, clears only known academic tables in foreign-key order, and populates the stage through existing services. Unknown tables cause refusal. A failed generation leaves the original intact; repeating a completed generation replaces the stage instead of appending duplicates. Preserve an interrupted stage and prepare a fresh copy if its partial contents fail the safety gate.

Successful Java verification writes a SHA-256 completion receipt beside the stage; interrupted or subsequently changed stages cannot be installed. CLI install independently checks exact compact-v2 counts, name uniqueness/third-name diversity, one roster per student, grade/attendance coverage, weights, exam totals and course relationships. It then verifies the original has not changed, the backup digest still matches, the candidate has valid integrity/relationships, and DDL/migration history/user_version exactly match the original. It copies to a temporary sibling then atomically replaces the database; it never edits stored checksums. Retain the verified manifest and backup together. Intermediate staging copies are intentionally retained for inspection. Generated data, backups, samples and screenshots are ignored by Git.

## Restore

With the application closed, use the same manifest:

```powershell
python tools/demo_workspace.py restore $demoManifest --app-closed
```

Restore refuses to downgrade a database with different current schema/migration history. It checks the original backup's digest/integrity/history and makes a fresh verified backup of the current workspace before replacement. This restores academic records/schema, not appearance preferences or external image/export files (none were changed by seeding). Restore is regression-tested on isolated databases; the real original is not restored during demo installation.

## Excel import fixtures

Existing fixture files/directories are preserved. The Java fixture writer uses the application's pinned POI dependency so the files can be regenerated offline. Choose a **new** output directory; existing directories are refused.

- `students-english.xlsx`: 24 new profiles; standard English ID/first/last/email/section columns.
- `students-arabic.xlsx`: 24 new profiles; recognized Arabic ID/full-name/email/section headers.
- `students-manual-mapping.xlsx`: 2 new profiles with intentionally nonstandard headers.
- `students-validation-cases.xlsx`: existing and duplicate IDs, missing ID/name, invalid email, numeric leading-zero formatting and a formula cell. The formula is only `1+1`, has no external link, and must be rejected by import validation.

The valid files use text IDs. Preview before confirming. Existing students must never be overwritten; imports can target the global directory or active/planned offering section A. Successful imported profiles should be skipped on repeat, while enrollment import can add the corresponding relationship. Fixture profiles are not pre-imported into the installed demo dataset. Do not treat the validation fixture as a clean roster.

## Tests and limitations

Run Python safety tests with `python -m unittest discover -s tools -p test_demo_workspace.py`, then the standard Maven and Compose suites sequentially. Java tests seed/reseed isolated databases, verify status/grade outcomes, reopen data, check immutable migration history and validate sample workbooks through the actual import reader/preview service. Compose renders every route using the same generator in an isolated database and checks both fully populated active-offering gradebooks.

Optional images are not seeded; question images remain an existing supported workflow. This is a realistic teaching demonstration, not a university's actual curriculum or assessment policy. No source database, backup or generated workbook is committed. Native Excel opening and full physical-keyboard/accessibility certification are not claimed.

## Verified installation on 2026-10-26

Active database: `C:\Users\HP\Desktop\Uni Project\data\professor-os.db` (no storage override). The original was protected in `data/backups/demo-20261025T165057050499Z/professor-os.db`; its manifest remains beside it. Backup SHA-256: `ea70ca48851260a8e81e9f0598ec55b9fb9aaa89083fe5cbbbddfc6d309399f8`. The original remained unchanged between backup and installation. SQLite backup may change physical bytes; all logical contents were independently compared before replacement.

To restore this particular original, close the app and run from the repository root:

```powershell
python tools/demo_workspace.py restore data/backups/demo-20261025T165057050499Z/manifest.json --app-closed
```

The installed database has 1,318 saved grades and 2,616 attendance records. Integrity and foreign-key checks pass; all ten migration history rows and schema are unchanged. After native close/restart, its SHA-256 remains `517042879a34c0eb91eb8a183209fb720c2714f2e4b5877226bb32369488f635`, identical to the verified stage.

Verification passed: 200 Maven tests, 31 Compose tests and 6 Python safety tests. Reviewed all 13 populated routes and the historical gradebook renderer capture. Native Windows checks covered dashboard, Gradebook, Attendance and reopening the default database; screenshots are under ignored `generated/demo-native-*.png`. Import workbooks are in `generated/demo-imports-20261026/`. Native Excel opening was not tested. The existing lecture summary can display an unrounded percentage; this data-only task does not change UI formatting.
