CREATE TABLE lectures (
    id INTEGER PRIMARY KEY,
    course_offering_id INTEGER NOT NULL REFERENCES course_offerings(id) ON DELETE RESTRICT,
    lecture_number INTEGER NOT NULL CHECK (typeof(lecture_number) = 'integer' AND lecture_number BETWEEN 1 AND 9999),
    title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 200),
    planned_date TEXT NOT NULL CHECK (length(planned_date) = 10 AND date(planned_date) IS NOT NULL AND date(planned_date) = planned_date),
    topics TEXT NOT NULL DEFAULT '' CHECK (length(topics) <= 5000),
    description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 10000),
    duration_minutes INTEGER NOT NULL CHECK (typeof(duration_minutes) = 'integer' AND duration_minutes BETWEEN 1 AND 1440),
    status TEXT NOT NULL CHECK (status IN ('PLANNED', 'COMPLETED', 'PARTIALLY_COMPLETED', 'CANCELLED')),
    professor_notes TEXT NOT NULL DEFAULT '' CHECK (length(professor_notes) <= 10000),
    created_at TEXT NOT NULL CHECK (julianday(created_at) IS NOT NULL),
    updated_at TEXT NOT NULL CHECK (julianday(updated_at) IS NOT NULL AND julianday(updated_at) >= julianday(created_at)),
    UNIQUE (course_offering_id, lecture_number)
);
-- @statement
CREATE INDEX idx_lectures_offering_date ON lectures(course_offering_id, planned_date, lecture_number);
-- @statement
CREATE TRIGGER preserve_offering_lecture_context
BEFORE UPDATE OF course_id, academic_term_id ON course_offerings
WHEN (NEW.course_id <> OLD.course_id OR NEW.academic_term_id <> OLD.academic_term_id)
     AND EXISTS (SELECT 1 FROM lectures WHERE course_offering_id = OLD.id)
BEGIN
    SELECT RAISE(ABORT, 'Offering with lectures cannot change course or term');
END;
