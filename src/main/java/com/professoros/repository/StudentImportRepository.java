package com.professoros.repository;
import com.professoros.database.Database;
import com.professoros.model.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** One transaction for all confirmed valid rows, including their enrollments. Never updates students. */
public final class StudentImportRepository {
    private final JdbcOperations jdbc;
    public StudentImportRepository(Database database) { jdbc=new JdbcOperations(database,"Student data changed since preview. Nothing was imported; generate a new preview."); }
    public StudentImportResult commit(StudentImportPreview preview,Instant now) {
        return jdbc.transaction(c->{
            if(preview.offeringId()!=null) {
                try(var s=c.prepareStatement("""
                        SELECT o.section,o.status,t.status AS term_status FROM course_offerings o
                        JOIN academic_terms t ON t.id=o.academic_term_id WHERE o.id=?
                        """)) {
                    s.setLong(1,preview.offeringId());
                    try(var r=s.executeQuery()) {
                        if(!r.next() || !r.getString("section").equals(preview.section())
                                || !open(r.getString("status")) || !open(r.getString("term_status"))) {
                            throw new DomainValidationException("Offering changed or is closed. Nothing was imported; generate a new preview.");
                        }
                    }
                }
            }
            int created=0,enrolled=0,skipped=0,failed=0;
            for(var row:preview.rows()) {
                if(row.action()==StudentImportRow.Action.ERROR) { failed++; continue; }
                if(row.action()==StudentImportRow.Action.SKIP) { skipped++; continue; }
                Student existing=null;
                try(var s=c.prepareStatement("SELECT * FROM students WHERE student_number=? COLLATE NOCASE")) {
                    s.setString(1,row.input().studentNumber()); try(var r=s.executeQuery()) { if(r.next()) { existing=StudentRepository.map(r); } }
                }
                if(!Objects.equals(existing,row.existing())) { throw new DomainValidationException("Student data changed since preview. Nothing was imported; generate a new preview."); }
                long studentId;
                if(row.action()==StudentImportRow.Action.CREATE) {
                    if(existing!=null) { throw new DomainValidationException("Student already exists; regenerate preview."); }
                    try(var s=c.prepareStatement("INSERT INTO students(student_number,first_name,last_name,email,notes,created_at,updated_at) VALUES(?,?,?,?,?,?,?) RETURNING id")) {
                        var input=row.input(); JdbcOperations.bind(s,new Object[]{input.studentNumber(),input.firstName(),input.lastName(),input.email(),input.notes(),now,now});
                        try(var r=s.executeQuery()) { r.next(); studentId=r.getLong(1); } created++;
                    }
                } else { studentId=Objects.requireNonNull(existing).id(); }
                if(preview.offeringId()!=null) {
                    try(var s=c.prepareStatement("SELECT 1 FROM enrollments WHERE student_id=? AND course_offering_id=?")) {
                        JdbcOperations.bind(s,new Object[]{studentId,preview.offeringId()});
                        try(var r=s.executeQuery()) { if(r.next()) { throw new DomainValidationException("Enrollment changed since preview. Nothing was imported; regenerate preview."); } }
                    }
                    try(var s=c.prepareStatement("INSERT INTO enrollments(student_id,course_offering_id,enrolled_at,status) VALUES(?,?,?,'ACTIVE')")) {
                        JdbcOperations.bind(s,new Object[]{studentId,preview.offeringId(),now}); s.executeUpdate(); enrolled++;
                    }
                }
            }
            return new StudentImportResult(created,enrolled,skipped,failed);
        });
    }
    private static boolean open(String status) { return status.equals("ACTIVE") || status.equals("PLANNED"); }
}
