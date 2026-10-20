CREATE UNIQUE INDEX idx_lectures_attendance_context ON lectures(id, course_offering_id);
-- @statement
CREATE UNIQUE INDEX idx_enrollments_attendance_context ON enrollments(id, course_offering_id);
-- @statement
CREATE TABLE attendance (
    enrollment_id INTEGER NOT NULL,
    lecture_id INTEGER NOT NULL,
    course_offering_id INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PRESENT','ABSENT','LATE','EXCUSED')),
    created_at TEXT NOT NULL CHECK (julianday(created_at) IS NOT NULL),
    updated_at TEXT NOT NULL CHECK (julianday(updated_at) IS NOT NULL AND julianday(updated_at)>=julianday(created_at)),
    PRIMARY KEY (enrollment_id, lecture_id),
    FOREIGN KEY (enrollment_id, course_offering_id) REFERENCES enrollments(id, course_offering_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    FOREIGN KEY (lecture_id, course_offering_id) REFERENCES lectures(id, course_offering_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
-- @statement
CREATE INDEX idx_attendance_offering_lecture ON attendance(course_offering_id, lecture_id);
