package com.professoros.repository;
import com.professoros.database.Database;
import com.professoros.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Offering-scoped grading persistence; batch score changes commit together. */
public final class GradebookRepository {
    private final JdbcOperations jdbc;
    private static final java.time.format.DateTimeFormatter TIMESTAMP=new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    public GradebookRepository(Database database) { jdbc=new JdbcOperations(database,"A category with this name already exists in this offering."); }
    private static int units(BigDecimal value) { return value.movePointRight(2).intValueExact(); }
    private static BigDecimal decimal(ResultSet row,String name) throws SQLException { return BigDecimal.valueOf(row.getInt(name),2); }
    public List<AssessmentCategory> categories(long offering) {
        return jdbc.query("SELECT * FROM assessment_categories WHERE course_offering_id=? ORDER BY name,id",GradebookRepository::category,offering);
    }
    public AssessmentCategory saveCategory(long offering,Long id,String name,BigDecimal weight,Instant now) {
        String timestamp=TIMESTAMP.format(now);
        if(id==null) { return jdbc.one("INSERT INTO assessment_categories(course_offering_id,name,weight_units,created_at,updated_at) VALUES(?,?,?,?,?) RETURNING *",GradebookRepository::category,offering,name,units(weight),timestamp,timestamp).orElseThrow(); }
        return jdbc.one("UPDATE assessment_categories SET name=?,weight_units=?,updated_at=MAX(updated_at,?) WHERE id=? AND course_offering_id=? RETURNING *",GradebookRepository::category,name,units(weight),timestamp,id,offering).orElseThrow(()->new DomainValidationException("Category no longer exists."));
    }
    public void deleteCategory(long offering,long id) { jdbc.execute("DELETE FROM assessment_categories WHERE id=? AND course_offering_id=?",id,offering); }
    public List<Assessment> assessments(long offering) {
        return jdbc.query("SELECT * FROM assessments WHERE course_offering_id=? ORDER BY assessment_date,id",GradebookRepository::assessment,offering);
    }
    public Assessment saveAssessment(long offering,Long id,long category,String title,BigDecimal maximum,LocalDate date,Instant now) {
        String timestamp=TIMESTAMP.format(now);
        if(id==null) { return jdbc.one("INSERT INTO assessments(course_offering_id,category_id,title,maximum_units,assessment_date,created_at,updated_at) VALUES(?,?,?,?,?,?,?) RETURNING *",GradebookRepository::assessment,offering,category,title,units(maximum),date,timestamp,timestamp).orElseThrow(); }
        return jdbc.one("UPDATE assessments SET category_id=?,title=?,maximum_units=?,assessment_date=?,updated_at=MAX(updated_at,?) WHERE id=? AND course_offering_id=? RETURNING *",GradebookRepository::assessment,category,title,units(maximum),date,timestamp,id,offering).orElseThrow(()->new DomainValidationException("Assessment no longer exists."));
    }
    /** Explicitly confirmed assessment deletion also deletes its owned grade cells. */
    public void deleteAssessment(long offering,long id) { jdbc.execute("DELETE FROM assessments WHERE id=? AND course_offering_id=?",id,offering); }
    public List<StudentGrade> grades(long offering) {
        return jdbc.query("SELECT * FROM student_grades WHERE course_offering_id=?",row->new StudentGrade(
                new GradeKey(row.getLong("enrollment_id"),row.getLong("assessment_id")),decimal(row,"score_units"),
                Instant.parse(row.getString("created_at")),Instant.parse(row.getString("updated_at"))),offering);
    }
    public GradingScale scale(long offering) {
        return jdbc.one("SELECT * FROM grading_scales WHERE course_offering_id=?",row->new GradingScale(decimal(row,"a_units"),decimal(row,"b_units"),decimal(row,"c_units"),decimal(row,"d_units"),decimal(row,"pass_units")),offering).orElse(GradingScale.standard());
    }
    public void saveScale(long offering,GradingScale scale) {
        jdbc.execute("""
                INSERT INTO grading_scales VALUES(?,?,?,?,?,?)
                ON CONFLICT(course_offering_id) DO UPDATE SET a_units=excluded.a_units,b_units=excluded.b_units,
                c_units=excluded.c_units,d_units=excluded.d_units,pass_units=excluded.pass_units
                """,offering,units(scale.a()),units(scale.b()),units(scale.c()),units(scale.d()),units(scale.pass()));
    }
    public void saveGrades(long offering,Map<GradeKey,BigDecimal> changes,Instant now) {
        String timestamp=TIMESTAMP.format(now);
        jdbc.transaction(connection->{
            try(var upsert=connection.prepareStatement("""
                    INSERT INTO student_grades(enrollment_id,assessment_id,course_offering_id,score_units,created_at,updated_at)
                    VALUES(?,?,?,?,?,?) ON CONFLICT(enrollment_id,assessment_id) DO UPDATE
                    SET score_units=excluded.score_units,updated_at=MAX(student_grades.updated_at,excluded.updated_at)
                    WHERE student_grades.course_offering_id=excluded.course_offering_id
                    """); var clear=connection.prepareStatement("DELETE FROM student_grades WHERE enrollment_id=? AND assessment_id=? AND course_offering_id=?")) {
                for(var entry:changes.entrySet()) {
                    var key=entry.getKey();
                    if(entry.getValue()==null) { JdbcOperations.bind(clear,new Object[]{key.enrollmentId(),key.assessmentId(),offering}); clear.executeUpdate(); }
                    else { JdbcOperations.bind(upsert,new Object[]{key.enrollmentId(),key.assessmentId(),offering,units(entry.getValue()),timestamp,timestamp});
                        if(upsert.executeUpdate()!=1) { throw new DomainValidationException("Grade relationships do not belong to this offering."); }
                    }
                }
            }
            return null;
        });
    }
    private static AssessmentCategory category(ResultSet r) throws SQLException {
        return new AssessmentCategory(r.getLong("id"),r.getLong("course_offering_id"),r.getString("name"),decimal(r,"weight_units"),Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at")));
    }
    private static Assessment assessment(ResultSet r) throws SQLException {
        return new Assessment(r.getLong("id"),r.getLong("course_offering_id"),r.getLong("category_id"),r.getString("title"),decimal(r,"maximum_units"),LocalDate.parse(r.getString("assessment_date")),Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at")));
    }
}
