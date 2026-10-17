CREATE TABLE academic_terms (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL COLLATE NOCASE CHECK (length(trim(name)) BETWEEN 1 AND 80),
    academic_year TEXT NOT NULL COLLATE NOCASE CHECK (length(trim(academic_year)) BETWEEN 1 AND 40),
    start_date TEXT NOT NULL CHECK (length(start_date) = 10 AND date(start_date) IS NOT NULL AND date(start_date) = start_date),
    end_date TEXT NOT NULL CHECK (length(end_date) = 10 AND date(end_date) IS NOT NULL AND date(end_date) = end_date AND end_date > start_date),
    status TEXT NOT NULL CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
    created_at TEXT NOT NULL CHECK (julianday(created_at) IS NOT NULL),
    updated_at TEXT NOT NULL CHECK (julianday(updated_at) IS NOT NULL AND julianday(updated_at) >= julianday(created_at)),
    UNIQUE (name, academic_year)
);
