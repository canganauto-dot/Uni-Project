# GitHub publication preparation — 2026-10-28

This cleanup changes ignore rules and documentation only. No source, test, migration, application resource, dependency or wrapper was removed. The pre-existing AGENTS.md edit and local .codex/ tooling are preserved and excluded from this commit. No push was performed.

## Backup verification

Active database: `data/professor-os.db`. Storage override was unset and no Professor OS window was open. Existing `tools/demo_workspace.py` prepare/inspect functions created and independently reopened the backup, checked integrity and foreign keys, compared every table's contents and schema/migration history, and verified the source hash stayed unchanged. The backup-only operation's redundant staging copy was retired too.

Retained backup: `data/backups/demo-20261028T143332838944Z/professor-os.db` and its `manifest.json`.

Active SHA-256: `c7ad8b740adc348e31ca9531082cbd4e979ec71f89b6c376dd3fcab9f0cefe62`. Backup SHA-256: `e69c23f573209538db3604280a8b3d66d91d6cd4180c3a0fa7114e9c495b7d1b`.

Record counts: schema_version=10, courses=2, academic_terms=1, course_offerings=2, students=90, enrollments=70, lectures=24, questions=41, question_choices=32, exams=7, exam_questions=85, exam_question_choices=64, assessment_categories=8, student_grades=280, grading_scales=2, assessments=8, attendance=840. Integrity: `ok`. Foreign-key violations: `0`.

## Files retired from the project

All files below were moved recoverably to sibling directory `Professor OS publication archive 20261028T143332838944Z`, preserving relative paths. Its `retired-files.json` records SHA-256 checksums; every archived file was checked after moving. None was permanently deleted.

- `data/backups/demo-20261025T165057050499Z/manifest.json`
- `data/backups/demo-20261025T165057050499Z/professor-os.db`
- `data/backups/demo-20261027T145444975012Z/manifest.json`
- `data/backups/demo-20261027T145444975012Z/professor-os.db`
- `data/backups/demo-20261028T131349886828Z/manifest.json`
- `data/backups/demo-20261028T131349886828Z/professor-os.db`
- `data/backups/professor-os-before-v009-repair-20261019-174556.db`
- `data/backups/professor-os-v009-guarded-repair-20261019.db`
- `data/.demo-stage-20261025T165057050499Z.db`
- `data/.demo-stage-20261027T145444975012Z.db`
- `data/.demo-stage-20261028T131349886828Z.db`
- `data/.demo-stage-20261028T143332838944Z.db`
- `generated/audit-final-0.png`
- `generated/audit-final-1.png`
- `generated/audit-final-2.png`
- `generated/demo-review-0.png`
- `generated/demo-review-1.png`
- `generated/demo-review-2.png`
- `generated/demo-review-3.png`
- `generated/ui-audit-contact-0.png`
- `generated/ui-audit-contact-1.png`
- `generated/ui-audit-contact-2.png`
- `generated/ui-audit-contact-3.png`
- `generated/ui-audit-contact-4.png`
- `outputs/import-test-20261028/preview.png`

## Preserved files and review decisions

- Root README remains; its existing wrapper build/run commands were checked. Obsolete bulk-paste and letter-grade descriptions were corrected.
- `prototype-compose/README.md` is linked from the root and contains desktop-specific architecture/setup details. Both resource font READMEs provide licensing/provenance. These are useful, not redundant.
- Documentation-referenced native screenshots remain under ignored `generated/`. Source resources and test snapshots/fixtures remain intact.
- Generated import workbooks, their README instructions, verification PDFs/Excel files, remain local and ignored. They are not safe candidates for permanent removal without user review.
- Historical one-off Python scripts and QA databases beneath generated/build directories are retained for review, because deleting source-like files or fixtures is outside this cleanup's safe scope. Maven clean rebuilds its disposable target directory; Gradle manages its generated outputs. Build/Gradle caches remain ignored.
- Local Graphify output and .codex tooling stay on disk and are ignored. The import-workbook helper's node_modules link is local tooling; it is ignored and was not followed or deleted.

## Publication checks

Tracked-file inventory found no databases, backup files, images, build/cache directories or credential-key files requiring removal from the Git index. A scan of tracked text for common private-key, GitHub token, AWS access-key and OpenAI project-key signatures found no matches. This is a bounded check, not a guarantee against every possible secret format. Ignore rules preserve resource fixtures and do not exclude Gradle/Maven wrappers, SQL migrations, PDFs/Excel fixtures or documentation generally.

## Approval needed before any further permanent removal

No deletion approval is needed for this completed cleanup: retired files remain recoverable. The following source-like helpers and historical QA fixtures were intentionally retained; review and approval are required before deleting them permanently:

- `generated/refresh-project-status.py`
- `generated/remove-javafx-docs.py`
- `generated/update-architecture-docs.py`
- `outputs/import-test-20261028/build.mjs`
- Generated import fixture directories and their historical verification outputs.
- Historical native QA database directories under `prototype-compose/build/native-*-qa/` and `prototype-compose/build/native-ui-audit/`.

The previously requested `outputs/import-test-20261028/students-import-test.xlsx` was already absent when this cleanup inspected that directory; its helper and inspection sidecar were preserved. No attempt was made to recreate or delete that workbook.

## Final verification results

Maven clean verify: 225 tests passed. Compose test/build: 43 tests passed, no failures/errors/skips. Python maintenance safety suite: 8 tests passed. Both backend library and Compose application sources compiled successfully. Active database and the sole retained backup were rechecked after tests with unchanged hashes, identical contents, integrity ok and zero foreign-key violations. All expected wrappers, source/resources, fixtures and historical migrations remain tracked; no ignored file was already tracked. The cleanup commit includes only .gitignore, root README and this report. AGENTS.md remains modified locally and is not staged.
