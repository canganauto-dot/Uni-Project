"""Explicit, offline demo replacement. Never invoked by application startup.

Prepare makes a verified SQLite backup and a staging copy. Java seeds only that
copy. Install verifies unchanged source/schema/history before atomic replacement.
Restore validates the backup digest and backs up the current workspace first.
"""
import argparse
from contextlib import contextmanager
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3


@contextmanager
def connection(*args, **kwargs):
    db = sqlite3.connect(*args, **kwargs)
    try:
        with db:
            yield db
    finally:
        db.close()


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def inspect(path):
    with connection(Path(path).resolve().as_uri() + '?mode=ro', uri=True) as db:
        if db.execute('PRAGMA integrity_check').fetchall() != [('ok',)]:
            raise ValueError('Database integrity check failed')
        if db.execute('PRAGMA foreign_key_check').fetchall():
            raise ValueError('Database has broken foreign keys')
        schema = db.execute("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name").fetchall()
        history = db.execute('SELECT * FROM schema_version ORDER BY version').fetchall()
        # Names are obtained from SQLite, quoted as identifiers, never user SQL.
        counts = {name: db.execute('SELECT count(*) FROM "' + name.replace('"', '""') + '"').fetchone()[0]
                  for (name,) in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        contents = {}
        for name in counts:
            rows = db.execute('SELECT * FROM "' + name.replace('"', '""') + '"').fetchall()
            encoded = sorted(json.dumps(row, ensure_ascii=False, default=lambda b: b.hex()) for row in rows)
            contents[name] = hashlib.sha256('\n'.join(encoded).encode('utf-8')).hexdigest()
        return {'schema': schema, 'history': history, 'counts': counts,
                'contents_sha256': contents,
                'user_version': db.execute('PRAGMA user_version').fetchone()[0]}


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False)


def offline(path):
    if not path.is_file():
        raise ValueError('Existing database not found: ' + str(path))
    for suffix in ('-wal', '-shm', '-journal'):
        if Path(str(path) + suffix).exists():
            raise ValueError('SQLite sidecar exists. Close the app cleanly before proceeding: ' + suffix)
    with connection(path, timeout=1) as db:
        db.execute('BEGIN EXCLUSIVE')
        db.rollback()


def prepare(path):
    path = Path(path).resolve()
    offline(path)
    before_hash = digest(path)
    before = inspect(path)
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    directory = path.parent / 'backups' / ('demo-' + stamp)
    directory.mkdir(parents=True, exist_ok=False)
    backup = directory / path.name
    with connection(path.as_uri() + '?mode=ro', uri=True) as source:
        with connection(backup) as destination:
            source.backup(destination)
    if canonical(inspect(backup)) != canonical(before) or digest(path) != before_hash:
        raise ValueError('Source changed during backup or backup verification failed')
    # Backup is the exact starting point, including schema_version and all DDL.
    stage = path.parent / ('.demo-stage-' + stamp + '.db')
    shutil.copyfile(backup, stage)
    manifest = directory / 'manifest.json'
    manifest.write_text(json.dumps({'database': str(path), 'backup': str(backup),
        'stage': str(stage), 'source_sha256': before_hash, 'backup_sha256': digest(backup),
        'before': before, 'created_utc': stamp}, indent=2, ensure_ascii=False), encoding='utf-8')
    return manifest


def install(manifest, restore=False):
    manifest = Path(manifest).resolve()
    data = json.loads(manifest.read_text(encoding='utf-8'))
    target, backup, stage = (Path(data[k]) for k in ('database', 'backup', 'stage'))
    offline(target)
    if digest(backup) != data['backup_sha256']:
        raise ValueError('Backup checksum differs from the verified manifest')
    candidate = backup if restore else stage
    if not restore:
        receipt = Path(str(stage) + '.ready.sha256')
        if not receipt.is_file() or receipt.read_text(encoding='utf-8').strip() != digest(stage):
            raise ValueError('No matching seeder completion receipt; finish generation before installing')
    checked = inspect(candidate)
    for key in ('schema', 'history', 'user_version'):
        if canonical(checked[key]) != canonical(data['before'][key]):
            raise ValueError('Schema or migration history changed: ' + key)
        if restore and canonical(inspect(target)[key]) != canonical(data['before'][key]):
            raise ValueError('Current schema or migration history differs; do not downgrade using this backup')
    if not restore and digest(target) != data['source_sha256']:
        raise ValueError('Original database changed since preparation; refusing replacement')
    if not restore and checked['counts'].get('students', 0) < 150:
        raise ValueError('Demo population is incomplete')
    if restore:
        safety = prepare(target)
        print('Pre-restore backup: ' + str(safety))
        expected = json.loads(safety.read_text(encoding='utf-8'))['source_sha256']
    else:
        expected = data['source_sha256']
    temporary = target.parent / ('.demo-install-' + data['created_utc'] + '.db')
    if temporary.exists():
        raise ValueError('Previous install file exists; investigate before retrying')
    shutil.copyfile(candidate, temporary)
    if digest(temporary) != digest(candidate) or digest(target) != expected:
        raise ValueError('Database changed before replacement')
    offline(target)
    os.replace(temporary, target)
    if canonical(inspect(target)) != canonical(checked):
        raise ValueError('Post-install verification failed; preserve the backup')
    print(('Restored: ' if restore else 'Installed: ') + str(target))
    print(json.dumps(checked['counts'], ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'install', 'restore'])
    parser.add_argument('path', help='Database for prepare; manifest.json for install/restore')
    parser.add_argument('--app-closed', action='store_true', required=True,
                        help='Confirm all Professor OS instances are closed; never run this against a live app')
    args = parser.parse_args()
    if args.action == 'prepare':
        print(prepare(args.path))
    else:
        install(args.path, args.action == 'restore')


if __name__ == '__main__':
    main()
