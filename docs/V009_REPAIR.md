# V009 startup incident and guarded repair

## Evidence (2026-10-19)

The affected database is `C:\Users\HP\Desktop\Uni Project\data\professor-os.db`.
No process, user or machine `PROFESSOR_OS_DATA_DIR` override was set during investigation; `AppPaths` resolves the default `data` directory relative to the launch directory.

V001–V008 stored checksums match the resources. V009 differs:

| Version of SQL | Normalized UTF-8 SHA-256 |
| --- | --- |
| Applied local V009 | `c87cf9d20865be53637f01358a1846aaec42d582e0dd6a55815f30fdded9f6e0` |
| Original commit `ca2dc74` and HEAD | `399b1f398657d88d76a147807aebdb241bc4bf46654d010eb320cbecc0a75e65` |

The Git blobs at `ca2dc74` and HEAD are identical. MigrationRunner already normalized CRLF to LF before hashing; the algorithm was not responsible. Removing exactly `date(assessment_date,'+0 days') IS NOT NULL AND ` from the committed V009 reproduces the stored hash. The database's `sqlite_schema` confirms that precise earlier assessment-date CHECK, with all other objects matching the reconstructed legacy reference.

V009's stored application time is `2026-10-18T13:15:27.800081100Z`; the first V009 commit was at `2026-10-18T14:14:01Z`. The database therefore retained the earlier, pre-commit migration variant, while the first committed file contained the tightened constraint. Comparing only committed revisions cannot reveal that local development history. SQLite CHECK accepts NULL results: the earlier predicate could accept a ten-character invalid date for which `date(...)` returns NULL. This is a real schema difference and must not be repaired by only replacing a checksum.

Initial integrity check returned `ok`; foreign-key check had no violations. The database contained one course, term, offering and student, and no assessments or grades. The repair is nevertheless tested with populated grades, including an actual zero score.

## Backup and repair policy

Before any database modification, SQLite's backup API produced:

`data/backups/professor-os-before-v009-repair-20261019-174556.db`

SHA-256: `ccadc24366be822705ea77d0fbf0b1737cf3ebce13e58eb4a64bfdecafa3baf1`.
Its integrity check passed. Local backups contain user records and are ignored by Git; retain them outside build output.

`LegacyGradeMigrationRepair` is an explicit offline maintenance command, not a startup bypass. It:

1. Requires an existing database, exactly nine recognized history rows, and the exact known legacy hash.
2. Builds independent in-memory databases from the canonical and reconstructed legacy migrations, checking both pinned V009 hashes. Compares every schema object, including indexes, triggers and implicit indexes. Any additional/missing/changed schema object is refused.
3. Rejects integrity/FK violations or dates that cannot satisfy the canonical constraint. It never guesses replacement dates or drops invalid rows.
4. Reserves a new backup filename and uses SQLite `VACUUM INTO` for a consistent snapshot. Existing backup files are never overwritten.
5. Acquires a write lock and repeats preflight. Compares all rows/counts and schema with the backup to reject changes between snapshot and lock acquisition.
6. Rebuilds only `assessments` within one transaction, retaining every column/ID. Restores its index and all affected triggers. FK enforcement is temporarily off only on this maintenance connection for the table rebuild; full FK/integrity checks run before commit and enforcement is restored.
7. Proves full schema equality with the canonical migrated reference and bidirectional row equality against the backup before changing V009's checksum. Keeps its original application timestamp. Other history rows are untouched.
8. Commits atomically and runs the ordinary strict MigrationRunner again. Failures inside the transaction roll back. Unknown hashes, modified schemas, newer histories and already repaired databases are refused.

Historical migrations and checksum policy remain unchanged. No V010 is needed: this narrowly identified pre-commit schema is brought to the already released V009 definition. New domain changes must still use new migration versions.

## Explicit invocation

Close Professor OS first. Build with the README's verified Windows workflow, then generate a runtime classpath:

```powershell
& $env:ComSpec /d /c '.\mvnw.cmd -B -ntp dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt'
$repairClasspath = 'target/classes;' + (Get-Content target/runtime-classpath.txt -Raw).Trim()
& "$env:JAVA_HOME/bin/java.exe" --enable-native-access=ALL-UNNAMED -cp $repairClasspath `
  com.professoros.database.LegacyGradeMigrationRepair `
  'C:\Users\HP\Desktop\Uni Project\data\professor-os.db' `
  'C:\Users\HP\Desktop\Uni Project\data\backups\choose-a-new-backup-name.db'
```

Do not run this as a general checksum-reset command. An unfamiliar failure requires a separate investigation. Normal startup diagnostics now include the resolved database path, resource names and stored/computed hashes without displaying record contents.

The backup approach follows [SQLite VACUUM INTO documentation](https://www.sqlite.org/lang_vacuum.html). Keep the incident backup until the repaired application and records have been reviewed.


## Completed local verification

The guarded command additionally saved `data/backups/professor-os-v009-guarded-repair-20261019.db`. Repair succeeded on the affected database. Subsequent comparison against the first backup found every row identical across all 15 user tables. History differed only in V009's checksum; its timestamp and all V001-V008 rows were preserved. Integrity was `ok`, with no FK violations, and the first backup's file hash was unchanged.

The full Windows build and desktop smoke workflow passed 192 tests with no failures, errors or skips. Five new repair regression tests include populated grade history with a real zero score and rejection of unsafe cases. A separate JavaFX process initialized the actual repaired database and opened the real application Dashboard, showing one saved course, student and offering. Its screenshot was reviewed at `target/startup-repaired.png`. This startup harness used the classpath, producing JavaFX's unnamed-module warning; the application itself started successfully and closed normally.
