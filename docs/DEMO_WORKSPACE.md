# Academic demo workspace

This is an explicit developer tool, never an application startup action. All demo people, academic records and email addresses are fictional. The user-authorized operation replaces academic records, preserving database DDL, schema_version rows/checksums and appearance preferences. Do not use it to merge demo data into a real academic workspace.

## Contents and useful routes

The reference date for this installation is 2026-10-26. A repeat can use another explicit ISO date; teaching dates move relative to it. Existing historical migration scripts are never edited.

- 3 terms: completed Spring, active Fall and planned next Spring.
- 6 catalog courses: OOP, Algorithms, Databases, Software Engineering, Networks and HCI.
- 12 offerings: 8 current, 2 completed, 2 planned; 45 enrollments each (540 total).
- 180 distinct student profiles with Arabic/English names, text identifiers retaining leading zeros and synthetic email addresses. Some optional emails are blank.
- 144 lectures with planned/completed/partial/cancelled states; past non-cancelled sessions contain varied Present/Absent/Late/Excused marks plus intentional unmarked cells. Future sessions remain unmarked.
- 120 questions: four topics × all five types per course, mixed difficulties, explanations, valid MCQ choices with varied correct-answer positions and both true/false answers.
- 24 saved exams with immutable question snapshots, matched calculated/intended marks, drafts/finalized/archived examples and recorded question usage.
- 48 weighted categories and 48 assessments, 12 grading scales, actual zero marks, late/ungraded work and future assessments without grades. Weights are 20/10/30/40. Current offerings remain incomplete until their future final assessment; completed offerings demonstrate passing/failing final grades and a late submission.

Start with the default **CS201 / Fall / A**. Explore Teaching context, Lectures, Attendance and Gradebook. Select **CS201 / Spring / A (completed)** in the context picker to inspect final grades. Question Bank has every type; Exam Builder includes saved papers ready for PDF preview/export. Records are marked fictional in course descriptions, student notes and offering notes. Creation timestamps reflect when the demo records were inserted; teaching dates represent the scenario, and enrollment dates precede teaching.

## Backup, staging and installation

Close every Professor OS instance. The maintenance CLI requires `--app-closed`; this is an operator declaration, not process detection. It also refuses SQLite sidecars and locked databases. Never bypass these checks or run concurrently with the app. Python 3 (standard library only), JDK 25 and the existing Maven/Gradle wrappers are required for this optional developer workflow.

Run the documented backend/Compose tests first; Gradle prepares the Java backend and runtime libraries. From the repository root in PowerShell:

```powershell
python tools/demo_workspace.py prepare data/professor-os.db --app-closed
# Use the exact manifest path printed above:
$demoManifest = 'C:\path\printed\by\prepare\manifest.json'
$demoPlan = Get-Content -Raw $demoManifest | ConvertFrom-Json
& "$env:JAVA_HOME\bin\java.exe" --enable-native-access=ALL-UNNAMED -cp 'target/classes;target/compose-libs/*' com.professoros.demo.DemoDataSeeder $demoPlan.stage '2026-10-26' 'generated/demo-imports-NEW-RUN'
# Only after the generator exits successfully and review/tests pass:
python tools/demo_workspace.py install $demoManifest --app-closed
```

For configured storage, replace `data/professor-os.db` with the actual `PROFESSOR_OS_DATA_DIR/professor-os.db` path. The runtime default is repository-root data, as documented by AppPaths. Unix classpaths use `:` instead of `;`.

Prepare uses SQLite's backup API into a unique UTC-timestamped directory under the database's `backups/`. It verifies integrity, foreign keys, all table contents, DDL, counts and migration history, records SHA-256 hashes, and creates a sibling `.demo-stage-*.db` copy. Java refuses ordinary live-database filenames, verifies migrations, clears only known academic tables in foreign-key order, and populates the stage through existing services. Unknown tables cause refusal. A failed generation leaves the original intact; repeat generation replaces the stage instead of appending duplicates.

Successful Java verification writes a SHA-256 completion receipt beside the stage; interrupted or subsequently changed stages cannot be installed. Install verifies the original has not changed, the backup digest still matches, the candidate has valid integrity/relationships, and DDL/migration history/user_version exactly match the original. It copies to a temporary sibling then atomically replaces the database; it never edits stored checksums. Retain the verified manifest and backup together. Intermediate staging copies are intentionally retained for inspection. Generated data, backups, samples and screenshots are ignored by Git.

## Restore

With the application closed, use the same manifest:

```powershell
python tools/demo_workspace.py restore $demoManifest --app-closed
```

Restore refuses to downgrade a database with different current schema/migration history. It checks the original backup's digest/integrity/history and makes a fresh verified backup of the current workspace before replacement. This restores academic records/schema, not appearance preferences or external image/export files (none were changed by seeding). Restore is regression-tested on isolated databases; the real original is not restored during demo installation.

## Excel import fixtures

The Java fixture writer uses the application's pinned POI dependency so the files can be regenerated offline. Choose a **new** output directory; existing directories are refused.

- `students-english.xlsx`: 24 new profiles; standard English ID/first/last/email/section columns.
- `students-arabic.xlsx`: 24 new profiles; recognized Arabic ID/full-name/email/section headers.
- `students-manual-mapping.xlsx`: 2 new profiles with intentionally nonstandard headers.
- `students-validation-cases.xlsx`: existing and duplicate IDs, missing ID/name, invalid email, numeric leading-zero formatting and a formula cell. The formula is only `1+1`, has no external link, and must be rejected by import validation.

The valid files use text IDs. Preview before confirming. Existing students must never be overwritten; imports can target the global directory or active/planned offering section A. Successful imported profiles should be skipped on repeat, while enrollment import can add the corresponding relationship. Fixture profiles are not pre-imported into the installed demo dataset. Do not treat the validation fixture as a clean roster.

## Tests and limitations

Run Python safety tests with `python -m unittest discover -s tools -p test_demo_workspace.py`, then the standard Maven and Compose suites sequentially. Java tests seed/reseed isolated databases, verify status/grade outcomes, reopen data, check immutable migration history and validate sample workbooks through the actual import reader/preview service. Compose renders every route using the same generator in an isolated database and checks the completed-term gradebook.

Optional images are not seeded; question images remain an existing supported workflow. This is a realistic teaching demonstration, not a university's actual curriculum or assessment policy. No source database, backup or generated workbook is committed. Native Excel opening and full physical-keyboard/accessibility certification are not claimed.

## Verified installation on 2026-10-26

Active database: `C:\Users\HP\Desktop\Uni Project\data\professor-os.db` (no storage override). The original was protected in `data/backups/demo-20261025T165057050499Z/professor-os.db`; its manifest remains beside it. Backup SHA-256: `ea70ca48851260a8e81e9f0598ec55b9fb9aaa89083fe5cbbbddfc6d309399f8`. The original remained unchanged between backup and installation. SQLite backup may change physical bytes; all logical contents were independently compared before replacement.

To restore this particular original, close the app and run from the repository root:

```powershell
python tools/demo_workspace.py restore data/backups/demo-20261025T165057050499Z/manifest.json --app-closed
```

The installed database has 1,318 saved grades and 2,616 attendance records. Integrity and foreign-key checks pass; all ten migration history rows and schema are unchanged. After native close/restart, its SHA-256 remains `517042879a34c0eb91eb8a183209fb720c2714f2e4b5877226bb32369488f635`, identical to the verified stage.

Verification passed: 200 Maven tests, 31 Compose tests and 6 Python safety tests. Reviewed all 13 populated routes and the historical gradebook renderer capture. Native Windows checks covered dashboard, Gradebook, Attendance and reopening the default database; screenshots are under ignored `generated/demo-native-*.png`. Import workbooks are in `generated/demo-imports-20261026/`. Native Excel opening was not tested. The existing lecture summary can display an unrounded percentage; this data-only task does not change UI formatting.
