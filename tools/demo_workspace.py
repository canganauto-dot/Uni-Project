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
import unicodedata


@contextmanager
def connection(*args, **kwargs):
    db = sqlite3.connect(*args, **kwargs)
    db.execute("PRAGMA foreign_keys=ON")
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


# Exact compact dataset contract. These are demo expectations, never database migrations.
COMPACT_COUNTS = {
    'courses': 2, 'academic_terms': 1, 'course_offerings': 2, 'students': 70,
    'enrollments': 70, 'lectures': 24, 'questions': 40, 'question_choices': 32,
    'exams': 6, 'exam_questions': 80, 'exam_question_choices': 64,
    'assessment_categories': 8, 'assessments': 8, 'student_grades': 280,
    'grading_scales': 2, 'attendance': 840,
}


def require_demo_source(path):
    """Refuse mixed or non-demo academic workspaces before CLI preparation/replacement."""
    info = inspect(path)
    counts = info['counts']
    parent_counts = tuple(counts.get(k) for k in ('courses', 'academic_terms', 'course_offerings', 'students'))
    if parent_counts not in ((6, 3, 12, 180), (2, 1, 2, 70)):
        raise ValueError('Not a recognized demo workspace; refusing to replace academic records')
    with connection(Path(path).resolve().as_uri() + '?mode=ro', uri=True) as db:
        for table, column, prefix in (
            ('courses', 'description', 'Fictional Professor OS demonstration curriculum.'),
            ('students', 'notes', 'Fictional demo student — not a real university record.'),
            ('course_offerings', 'notes', 'DEMO'),
        ):
            # Identifiers come only from the fixed tuples above.
            if db.execute(f'SELECT count(*) FROM {table} WHERE {column} IS NULL OR {column} NOT LIKE ?', (prefix+'%',)).fetchone()[0]:
                raise ValueError('Non-demo or mixed records detected; refusing replacement')
        codes = {r[0] for r in db.execute('SELECT course_code FROM courses')}
        expected = {'CS201', 'CS202'} if parent_counts[0] == 2 else {'CS201', 'CS202', 'CS203', 'SE301', 'CS305', 'HCI310'}
        if codes != expected:
            raise ValueError('Demo course identities differ; review the target explicitly')
    return info


def validate_compact_demo(path):
    """Read-only independent coverage checks before a CLI install; Java verifies calculation rules."""
    info = require_demo_source(path)
    if any(info['counts'].get(table) != count for table, count in COMPACT_COUNTS.items()):
        raise ValueError('Demo population is incomplete or has unexpected record counts')
    with connection(Path(path).resolve().as_uri() + '?mode=ro', uri=True) as db:
        names = [unicodedata.normalize('NFC', first+' '+last).split() for first, last in db.execute('SELECT first_name,last_name FROM students')]
        if any(len(n) != 3 or any(not all(unicodedata.category(c).startswith('L') for c in part) for part in n) for n in names):
            raise ValueError('Every demo student must have exactly three alphabetic name components')
        if len({tuple(n) for n in names}) != 70 or len({n[2] for n in names}) != 70:
            raise ValueError('Demo full names and third names must be unique')
        checks = {
            'active term': "SELECT count(*) FROM academic_terms WHERE status<>'ACTIVE'",
            'active offerings': "SELECT count(*) FROM course_offerings WHERE status<>'ACTIVE'",
            'active enrollments': "SELECT count(*) FROM enrollments WHERE status<>'ACTIVE'",
            'one offering per course': 'SELECT count(*) FROM (SELECT course_id FROM course_offerings GROUP BY course_id HAVING count(*)<>1)',
            'roster sizes': 'SELECT count(*) FROM (SELECT course_offering_id FROM enrollments GROUP BY course_offering_id HAVING count(*)<>35)',
            'student enrollment': 'SELECT count(*) FROM (SELECT student_id FROM enrollments GROUP BY student_id HAVING count(*)<>1)',
            'lecture counts': 'SELECT count(*) FROM (SELECT course_offering_id FROM lectures GROUP BY course_offering_id HAVING count(*)<>12)',
            'completed lectures': "SELECT count(*) FROM lectures WHERE status<>'COMPLETED'",
            'question counts': 'SELECT count(*) FROM (SELECT course_id FROM questions GROUP BY course_id HAVING count(*)<>20)',
            'question types': 'SELECT count(*) FROM (SELECT course_id,question_type FROM questions GROUP BY course_id,question_type HAVING count(*)<>4)',
            'exam counts': 'SELECT count(*) FROM (SELECT course_offering_id FROM exams GROUP BY course_offering_id HAVING count(*)<>3)',
            'category weights': 'SELECT count(*) FROM (SELECT course_offering_id FROM assessment_categories GROUP BY course_offering_id HAVING sum(weight_units)<>10000)',
            'category counts': 'SELECT count(*) FROM (SELECT course_offering_id FROM assessment_categories GROUP BY course_offering_id HAVING count(*)<>4)',
            'assessment counts': 'SELECT count(*) FROM (SELECT course_offering_id FROM assessments GROUP BY course_offering_id HAVING count(*)<>4)',
            'missing grades': 'SELECT count(*) FROM enrollments e JOIN assessments a ON a.course_offering_id=e.course_offering_id\n                LEFT JOIN student_grades g ON g.enrollment_id=e.id AND g.assessment_id=a.id WHERE g.enrollment_id IS NULL',
            'missing attendance': 'SELECT count(*) FROM enrollments e JOIN lectures l ON l.course_offering_id=e.course_offering_id\n                LEFT JOIN attendance a ON a.enrollment_id=e.id AND a.lecture_id=l.id WHERE a.enrollment_id IS NULL',
            'snapshot course': 'SELECT count(*) FROM exam_questions s JOIN exams e ON e.id=s.exam_id JOIN course_offerings o ON o.id=e.course_offering_id\n                JOIN questions q ON q.id=s.question_id WHERE q.course_id<>o.course_id',
            'exam totals': 'SELECT count(*) FROM exams e WHERE e.intended_marks_hundredths<>\n                (SELECT coalesce(sum(marks_hundredths),0) FROM exam_questions WHERE exam_id=e.id)',
        }
        for label, sql in checks.items():
            if db.execute(sql).fetchone()[0]:
                raise ValueError('Demo validation failed: '+label)
        statuses = dict(db.execute('SELECT status,count(*) FROM exams GROUP BY status'))
        if statuses != {'DRAFT': 2, 'FINALIZED': 4}:
            raise ValueError('Demo requires four finalized and two draft exams')
        attendance = dict(db.execute('SELECT status,count(*) FROM attendance GROUP BY status'))
        if set(attendance) != {'PRESENT', 'LATE', 'ABSENT', 'EXCUSED'}:
            raise ValueError('Attendance must include all four recorded statuses')
    return info


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
    if not restore and checked['counts'].get('students', 0) != 70:
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
        require_demo_source(args.path)
        print(prepare(args.path))
    else:
        if args.action == 'install':
            plan = json.loads(Path(args.path).read_text(encoding='utf-8'))
            require_demo_source(plan['database'])
            validate_compact_demo(plan['stage'])
        install(args.path, args.action == 'restore')


if __name__ == '__main__':
    main()
