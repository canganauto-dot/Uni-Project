CREATE TABLE courses (
    id INTEGER PRIMARY KEY,
    course_code TEXT NOT NULL COLLATE NOCASE UNIQUE
        CHECK (length(course_code) BETWEEN 1 AND 20 AND course_code = trim(course_code)
               AND course_code NOT GLOB '*[^A-Z0-9 ._-]*' AND course_code GLOB '[A-Z0-9]*'),
    course_name TEXT NOT NULL
        CHECK (length(trim(course_name)) BETWEEN 1 AND 200 AND course_name = trim(course_name)),
    department TEXT NOT NULL DEFAULT '' CHECK (length(department) <= 120),
    credit_hours INTEGER NOT NULL CHECK (typeof(credit_hours) = 'integer' AND credit_hours BETWEEN 1 AND 30),
    description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 5000),
    created_at TEXT NOT NULL CHECK (julianday(created_at) IS NOT NULL),
    updated_at TEXT NOT NULL CHECK (julianday(updated_at) IS NOT NULL AND julianday(updated_at) >= julianday(created_at))
);
