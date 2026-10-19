package com.professoros.database;

import com.professoros.model.*;
import com.professoros.repository.CourseRepository;
import com.professoros.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LegacyGradeMigrationRepairTest {
    @TempDir Path directory;
    Path file() { return directory.resolve("legacy.db"); }
    Path backup() { return directory.resolve("backup.db"); }
    Database fixture() throws Exception {
        var db=new Database(file());
        try(var c=db.openConnection();var s=c.createStatement()) {
            new MigrationRunner(MigrationRunner.RESOURCES.subList(0,8)).migrate(c);
            String sql=MigrationRunner.readScript("/db/migrations/V009__legacy_grade_management.sql");
            assertEquals(LegacyGradeMigrationRepair.LEGACY_HASH,MigrationRunner.checksum(sql));
            for(String part:sql.split("(?m)^-- @statement\\s*$")) if(!part.isBlank()) s.execute(part);
            try(var insert=c.prepareStatement("INSERT INTO schema_version VALUES(9,?,?,?)")) {
                insert.setString(1,MigrationRunner.RESOURCES.get(8)); insert.setString(2,LegacyGradeMigrationRepair.LEGACY_HASH);
                insert.setString(3,"2026-10-18T13:15:27.800081100Z"); insert.executeUpdate();
            }
        }
        var academic=AcademicServices.create(db);
        var course=new CourseService(new CourseRepository(db)).create(new CourseInput("CS201","OOP","",3,""));
        var term=academic.terms().create(new AcademicTermInput("Fall","2026",LocalDate.of(2026,9,1),LocalDate.of(2027,1,1),AcademicStatus.ACTIVE));
        var offering=academic.offerings().create(new CourseOfferingInput(course.id(),term.id(),"A","",AcademicStatus.ACTIVE));
        var student=academic.students().create(new StudentInput("001","Ahmed","Ali","",""));
        var enrollment=academic.enrollments().enroll(student.id(),offering.id());
        var category=academic.grades().saveCategory(offering.id(),null,"Assignments",new BigDecimal("100"));
        var assessment=academic.grades().saveAssessment(offering.id(),null,category.id(),"Project",new BigDecimal("20"),LocalDate.of(2026,10,19));
        academic.grades().saveGrades(offering.id(),Map.of(new GradeKey(enrollment.id(),assessment.id()),"0"));
        return db;
    }
    @Test void provesOriginalMismatchThenRepairsSchemaPreservingGradesAndHistory() throws Exception {
        var db=fixture(); var before=AcademicServices.create(db).grades().load(1);
        SQLException error=assertThrows(SQLException.class,db::initialize);
        assertTrue(error.getMessage().contains(LegacyGradeMigrationRepair.LEGACY_HASH));
        assertTrue(error.getMessage().contains(LegacyGradeMigrationRepair.CURRENT_HASH));
        LegacyGradeMigrationRepair.repair(file(),backup());
        db.initialize(); db.initialize();
        assertEquals(before,AcademicServices.create(db).grades().load(1));
        assertEquals(before,AcademicServices.create(new Database(backup())).grades().load(1));
        try(var c=db.openConnection();var reference=DriverManager.getConnection("jdbc:sqlite::memory:");var s=c.createStatement()) {
            new MigrationRunner().migrate(reference);
            assertEquals(LegacyGradeMigrationRepair.schema(reference),LegacyGradeMigrationRepair.schema(c));
            try(var r=s.executeQuery("SELECT checksum,applied_at FROM schema_version WHERE version=9")) {
                assertTrue(r.next()); assertEquals(LegacyGradeMigrationRepair.CURRENT_HASH,r.getString(1));
                assertEquals("2026-10-18T13:15:27.800081100Z",r.getString(2));
            }
            assertThrows(SQLException.class,()->s.execute("UPDATE assessments SET assessment_date='not-a-date'"));
            assertThrows(SQLException.class,()->s.execute("UPDATE student_grades SET score_units=2001"));
            assertThrows(SQLException.class,()->s.execute("DELETE FROM enrollments"));
        }
        assertThrows(SQLException.class,new Database(backup())::initialize);
        assertThrows(SQLException.class,()->LegacyGradeMigrationRepair.repair(file(),directory.resolve("repeat.db")));
        assertFalse(Files.exists(directory.resolve("repeat.db")));
    }
    @Test void refusesSchemaDriftEvenWithRecognizedChecksum() throws Exception {
        var db=fixture();
        try(var c=db.openConnection();var s=c.createStatement()) { s.execute("DROP TRIGGER grade_range_update"); }
        byte[] before=Files.readAllBytes(file());
        assertThrows(SQLException.class,()->LegacyGradeMigrationRepair.repair(file(),backup()));
        assertArrayEquals(before,Files.readAllBytes(file())); assertFalse(Files.exists(backup()));
    }
    @Test void refusesInvalidDatesWithoutDiscardingRecords() throws Exception {
        var db=fixture();
        try(var c=db.openConnection();var s=c.createStatement()) { s.execute("UPDATE assessments SET assessment_date='not-a-date'"); }
        byte[] before=Files.readAllBytes(file());
        var error=assertThrows(SQLException.class,()->LegacyGradeMigrationRepair.repair(file(),backup()));
        assertTrue(error.getMessage().contains("Invalid assessment dates")); assertArrayEquals(before,Files.readAllBytes(file()));
    }
    @Test void refusesUnknownHistoryAndNeverOverwritesBackup() throws Exception {
        var db=fixture(); Files.writeString(backup(),"Keep this backup");
        assertThrows(java.io.IOException.class,()->LegacyGradeMigrationRepair.repair(file(),backup()));
        assertEquals("Keep this backup",Files.readString(backup()));
        try(var c=db.openConnection();var s=c.createStatement()) { s.execute("UPDATE schema_version SET checksum='unknown' WHERE version=9"); }
        byte[] before=Files.readAllBytes(file());
        assertThrows(SQLException.class,()->LegacyGradeMigrationRepair.repair(file(),directory.resolve("new.db")));
        assertArrayEquals(before,Files.readAllBytes(file()));
    }
    @Test void reconstructsExactPreCommitChecksumFromIndependentLegacyFixture() throws Exception {
        String current=MigrationRunner.readScript(MigrationRunner.RESOURCES.get(8));
        assertEquals(LegacyGradeMigrationRepair.CURRENT_HASH,MigrationRunner.checksum(current));
        assertEquals(MigrationRunner.readScript("/db/migrations/V009__legacy_grade_management.sql"),current.replace(LegacyGradeMigrationRepair.DATE_GUARD,""));
        assertNotEquals(MigrationRunner.checksum(current),MigrationRunner.checksum(current.replace(LegacyGradeMigrationRepair.DATE_GUARD,"")));
    }
}
