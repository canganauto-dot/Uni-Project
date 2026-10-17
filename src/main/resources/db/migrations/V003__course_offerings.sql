CREATE TABLE course_offerings (
    id INTEGER PRIMARY KEY,
    course_id INTEGER NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    academic_term_id INTEGER NOT NULL REFERENCES academic_terms(id) ON DELETE RESTRICT,
    section TEXT NOT NULL COLLATE NOCASE CHECK (length(trim(section)) BETWEEN 1 AND 40),
    notes TEXT NOT NULL DEFAULT '' CHECK (length(notes) <= 5000),
    status TEXT NOT NULL CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
    created_at TEXT NOT NULL CHECK (julianday(created_at) IS NOT NULL),
    updated_at TEXT NOT NULL CHECK (julianday(updated_at) IS NOT NULL AND julianday(updated_at) >= julianday(created_at)),
    UNIQUE (course_id, academic_term_id, section)
);
-- @statement
CREATE INDEX idx_offerings_term ON course_offerings(academic_term_id);
