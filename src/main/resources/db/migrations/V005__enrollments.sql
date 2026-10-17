CREATE TABLE enrollments (
    id INTEGER PRIMARY KEY,
    student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    course_offering_id INTEGER NOT NULL REFERENCES course_offerings(id) ON DELETE RESTRICT,
    enrolled_at TEXT NOT NULL CHECK (julianday(enrolled_at) IS NOT NULL),
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'WITHDRAWN', 'COMPLETED')),
    UNIQUE (student_id, course_offering_id)
);
-- @statement
CREATE INDEX idx_enrollments_offering ON enrollments(course_offering_id);
-- @statement
CREATE TRIGGER preserve_offering_enrollment_context
BEFORE UPDATE OF course_id, academic_term_id ON course_offerings
WHEN (NEW.course_id <> OLD.course_id OR NEW.academic_term_id <> OLD.academic_term_id)
     AND EXISTS (SELECT 1 FROM enrollments WHERE course_offering_id = OLD.id)
BEGIN
    SELECT RAISE(ABORT, 'Offering with enrollment history cannot change course or term');
END;
