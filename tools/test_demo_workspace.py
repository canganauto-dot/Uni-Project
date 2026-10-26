import contextlib
import io
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import demo_workspace as demo


class DemoSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.database = Path(self.temp.name) / 'professor-os.db'
        with demo.connection(self.database) as db:
            db.executescript("CREATE TABLE schema_version(version INTEGER, checksum TEXT);"
                             "INSERT INTO schema_version VALUES(1,'original');"
                             "CREATE TABLE students(id INTEGER PRIMARY KEY,name TEXT);"
                             "INSERT INTO students VALUES(1,'Original profile');")
        self.original = demo.inspect(self.database)
        self.manifest = demo.prepare(self.database)
        self.data = json.loads(self.manifest.read_text(encoding='utf-8'))

    def populate_stage(self):
        with demo.connection(self.data['stage']) as db:
            db.executemany('INSERT INTO students VALUES(?,?)', [(i, 'Demo') for i in range(2,151)])
        self.receipt()

    def receipt(self):
        Path(self.data['stage']+'.ready.sha256').write_text(demo.digest(self.data['stage']),encoding='utf-8')

    def test_install_and_restore_preserve_every_original_row_and_history(self):
        self.populate_stage()
        with contextlib.redirect_stdout(io.StringIO()):
            demo.install(self.manifest)
            self.assertEqual(150, demo.inspect(self.database)['counts']['students'])
            demo.install(self.manifest, restore=True)
        self.assertEqual(demo.canonical(self.original), demo.canonical(demo.inspect(self.database)))
        self.assertEqual(2, len(list((self.database.parent/'backups').glob('*/manifest.json'))))

    def test_changed_original_is_not_overwritten(self):
        self.populate_stage()
        with demo.connection(self.database) as db:
            db.execute("UPDATE students SET name='New user edit'")
        with self.assertRaisesRegex(ValueError, 'changed since'):
            demo.install(self.manifest)
        with demo.connection(self.database) as db:
            self.assertEqual('New user edit', db.execute('SELECT name FROM students').fetchone()[0])

    def test_modified_schema_is_rejected(self):
        self.populate_stage()
        with demo.connection(self.data['stage']) as db:
            db.execute('CREATE TABLE unexpected(id INTEGER)')
        self.receipt()
        with self.assertRaisesRegex(ValueError, 'Schema or migration'):
            demo.install(self.manifest)
        self.assertEqual(demo.canonical(self.original), demo.canonical(demo.inspect(self.database)))

    def test_corrupt_backup_is_rejected(self):
        self.populate_stage()
        Path(self.data['backup']).write_bytes(b'not a database')
        with self.assertRaisesRegex(ValueError, 'Backup checksum'):
            demo.install(self.manifest)

    def test_restore_refuses_newer_migration_history(self):
        with demo.connection(self.database) as db:
            db.execute("INSERT INTO schema_version VALUES(2,'new migration')")
        with self.assertRaisesRegex(ValueError, 'do not downgrade'):
            demo.install(self.manifest, restore=True)

    def test_partial_seed_without_matching_receipt_cannot_install(self):
        self.populate_stage()
        with demo.connection(self.data['stage']) as db:
            db.execute("UPDATE students SET name='Interrupted change'")
        with self.assertRaisesRegex(ValueError, 'completion receipt'):
            demo.install(self.manifest)


if __name__ == '__main__':
    unittest.main()
