CREATE TABLE students (
    id INTEGER PRIMARY KEY,
    student_number TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(trim(student_number)) BETWEEN 1 AND 40),
    first_name TEXT NOT NULL CHECK (length(trim(first_name)) BETWEEN 1 AND 100),
    last_name TEXT NOT NULL CHECK (length(trim(last_name)) BETWEEN 1 AND 100),
    email TEXT NOT NULL DEFAULT '' CHECK (length(email) <= 254),
    notes TEXT NOT NULL DEFAULT '' CHECK (length(notes) <= 5000),
    created_at TEXT NOT NULL CHECK (julianday(created_at) IS NOT NULL),
    updated_at TEXT NOT NULL CHECK (julianday(updated_at) IS NOT NULL AND julianday(updated_at) >= julianday(created_at))
);
