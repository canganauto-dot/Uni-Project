package com.professoros.database;
import com.professoros.model.*;
import com.professoros.repository.*;
import com.professoros.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class AttendanceMigrationTest {
    @TempDir Path directory;
    @Test void upgradesNineWithoutChangingHistoryOrCatalogAndAppliesOnce() throws Exception {
        var db=new Database(directory.resolve("upgrade.db"));String history;
        try(var c=db.openConnection()) {new MigrationRunner(MigrationRunner.RESOURCES.subList(0,9)).migrate(c);history=history(c);}
        var course=new CourseService(new CourseRepository(db)).create(new CourseInput("CS201","OOP","",3,""));
        db.initialize();db.initialize();
        try(var c=db.openConnection();var s=c.createStatement()) {
            assertEquals(history,history(c));
            try(var r=s.executeQuery("SELECT count(*),max(version) FROM schema_version")){assertTrue(r.next());assertEquals(10,r.getInt(1));assertEquals(10,r.getInt(2));}
            try(var r=s.executeQuery("PRAGMA foreign_key_check")){assertFalse(r.next());}
        }
        assertEquals(course,new CourseRepository(db).findById(course.id()).orElseThrow());
    }
    private String history(java.sql.Connection c) throws Exception {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT group_concat(version || resource || checksum || applied_at) FROM (SELECT * FROM schema_version WHERE version<=9 ORDER BY version)")){assertTrue(r.next());return r.getString(1);}
    }
}
