package com.professoros.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Explicit offline maintenance for one proven pre-commit V009; never invoked by startup. */
public final class LegacyGradeMigrationRepair {
    static final String LEGACY_HASH="c87cf9d20865be53637f01358a1846aaec42d582e0dd6a55815f30fdded9f6e0";
    static final String CURRENT_HASH="399b1f398657d88d76a147807aebdb241bc4bf46654d010eb320cbecc0a75e65";
    static final String DATE_GUARD="date(assessment_date,'+0 days') IS NOT NULL AND ";
    private LegacyGradeMigrationRepair() { }

    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Close Professor OS. Usage: LegacyGradeMigrationRepair <existing database> <new backup file>");
        repair(Path.of(args[0]),Path.of(args[1]));
        System.out.println("V009 schema repaired and verified. Backup: "+Path.of(args[1]).toAbsolutePath());
    }

    public static void repair(Path database,Path backup) throws SQLException,IOException {
        database=database.toAbsolutePath().normalize(); backup=backup.toAbsolutePath().normalize();
        if(!Files.isRegularFile(database)) throw new IOException("Existing database file required: "+database);
        if(Files.exists(backup)) throw new IOException("Backup destination already exists; choose a new filename: "+backup);
        try(var reference=DriverManager.getConnection("jdbc:sqlite::memory:");
            var legacy=DriverManager.getConnection("jdbc:sqlite::memory:");
            var connection=new Database(database).openConnection()) {
            String script=MigrationRunner.readScript(MigrationRunner.RESOURCES.get(8));
            String oldScript=script.replace(DATE_GUARD,"");
            require(CURRENT_HASH.equals(MigrationRunner.checksum(script)) && LEGACY_HASH.equals(MigrationRunner.checksum(oldScript)),
                    "Repair does not recognize the bundled V009 SQL; no changes made");
            new MigrationRunner().migrate(reference);
            new MigrationRunner(MigrationRunner.RESOURCES.subList(0,8)).migrate(legacy);
            try(var statement=legacy.createStatement()) {
                for(String sql:oldScript.split("(?m)^-- @statement\\s*$")) if(!sql.isBlank()) statement.execute(sql);
            }
            var expected=schema(reference); var expectedLegacy=schema(legacy);
            preflight(connection,expectedLegacy);
            Files.createDirectories(backup.getParent());
            Files.createFile(backup); // Reserve the name without ever replacing an existing file.
            try(var vacuum=connection.prepareStatement("VACUUM main INTO ?")) {
                vacuum.setString(1,backup.toString()); vacuum.execute();
            }
            try(var attach=connection.prepareStatement("ATTACH DATABASE ? AS repair_backup")) {
                attach.setString(1,backup.toString()); attach.execute();
            }
            try(var statement=connection.createStatement()) {
                // Foreign keys must be off before BEGIN for SQLite's documented table-rebuild procedure.
                // They are checked explicitly before COMMIT and restored in finally.
                statement.execute("PRAGMA foreign_keys=OFF");
                statement.execute("BEGIN IMMEDIATE");
                try {
                    preflight(connection,expectedLegacy);
                    require(schema(connection,"repair_backup").equals(expectedLegacy),"Backup schema differs; repair refused");
                    sameRows(connection,expectedLegacy); // Reject any writes between snapshot and lock acquisition.
                    statement.execute("CREATE TEMP TABLE repair_assessments AS SELECT * FROM assessments");
                    statement.execute("DROP TRIGGER grade_range_insert");
                    statement.execute("DROP TRIGGER grade_range_update");
                    statement.execute("DROP TRIGGER assessment_maximum_update");
                    statement.execute("DROP TABLE assessments");
                    statement.execute(expected.get("table:assessments"));
                    statement.execute("INSERT INTO assessments(id,course_offering_id,category_id,title,maximum_units,assessment_date,created_at,updated_at) "
                            +"SELECT id,course_offering_id,category_id,title,maximum_units,assessment_date,created_at,updated_at FROM repair_assessments");
                    statement.execute("DROP TABLE temp.repair_assessments");
                    for(String key:List.of("index:assessment_offering_date","trigger:grade_range_insert","trigger:grade_range_update","trigger:assessment_maximum_update"))
                        statement.execute(expected.get(key));
                    require(schema(connection).equals(expected),"Rebuilt schema differs from independently migrated reference; rolling back");
                    checkIntegrity(connection);
                    sameRows(connection,expected); // Prove every persisted value is unchanged, including history so far.
                    try(var update=connection.prepareStatement("UPDATE schema_version SET checksum=? WHERE version=9 AND checksum=?")) {
                        update.setString(1,CURRENT_HASH); update.setString(2,LEGACY_HASH);
                        require(update.executeUpdate()==1,"V009 history changed concurrently; rolling back");
                    }
                    checkHistory(connection,CURRENT_HASH);
                    statement.execute("COMMIT");
                } catch(SQLException | RuntimeException failure) {
                    try { statement.execute("ROLLBACK"); } catch(SQLException rollback) { failure.addSuppressed(rollback); }
                    throw failure;
                } finally { statement.execute("PRAGMA foreign_keys=ON"); }
            }
            new MigrationRunner().migrate(connection); // Normal strict startup verification, unchanged policy.
        }
    }

    private static void preflight(Connection c,Map<String,String> expected) throws SQLException {
        checkHistory(c,LEGACY_HASH);
        require(schema(c).equals(expected),"Database is not the exact recognized legacy schema; repair refused");
        checkIntegrity(c);
        try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM assessments WHERE length(assessment_date)<>10 OR date(assessment_date,'+0 days') IS NULL OR date(assessment_date,'+0 days')<>assessment_date")) {
            r.next(); require(r.getInt(1)==0,"Invalid assessment dates exist; repair refused. Correct them explicitly after review; no rows were changed.");
        }
    }

    private static void checkHistory(Connection c,String ninthHash) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT version,resource,checksum FROM schema_version ORDER BY version")) {
            int count=0;
            while(r.next()) {
                require(count<9 && r.getInt(1)==count+1,"Unsupported migration history; repair refused");
                String resource=MigrationRunner.RESOURCES.get(count);
                String hash=count==8?ninthHash:MigrationRunner.checksum(MigrationRunner.readScript(resource));
                require(resource.equals(r.getString(2)) && hash.equals(r.getString(3)),"Unrecognized migration history at version "+(count+1)+"; repair refused");
                count++;
            }
            require(count==9,"Repair requires exactly nine applied migrations");
        }
    }

    private static void checkIntegrity(Connection c) throws SQLException {
        try(var s=c.createStatement()) {
            try(var r=s.executeQuery("PRAGMA integrity_check")) { require(r.next() && "ok".equals(r.getString(1)) && !r.next(),"Database integrity check failed; repair refused"); }
            try(var r=s.executeQuery("PRAGMA foreign_key_check")) { require(!r.next(),"Foreign key violations exist; repair refused"); }
        }
    }

    static Map<String,String> schema(Connection c) throws SQLException { return schema(c,"main"); }
    private static Map<String,String> schema(Connection c,String database) throws SQLException {
        Map<String,String> result=new TreeMap<>();
        // Database is one of the two internal literal names, never user input.
        try(var s=c.createStatement();var r=s.executeQuery("SELECT type,name,sql FROM "+database+".sqlite_schema ORDER BY type,name")) {
            while(r.next()) result.put(r.getString(1)+":"+r.getString(2),r.getString(3));
        }
        return result;
    }

    private static void sameRows(Connection c,Map<String,String> expected) throws SQLException {
        for(String key:expected.keySet()) if(key.startsWith("table:")) {
            String table='"'+key.substring(6).replace("\"","\"\"")+'"';
            try(var s=c.createStatement()) {
                for(String sql:List.of("SELECT * FROM main."+table+" EXCEPT SELECT * FROM repair_backup."+table,
                        "SELECT * FROM repair_backup."+table+" EXCEPT SELECT * FROM main."+table))
                    try(var r=s.executeQuery(sql)) { require(!r.next(),"Data differs from backup in "+key+"; repair refused"); }
                try(var r=s.executeQuery("SELECT (SELECT count(*) FROM main."+table+")=(SELECT count(*) FROM repair_backup."+table+")")) {
                    require(r.next() && r.getBoolean(1),"Row counts differ from backup in "+key+"; repair refused");
                }
            }
        }
    }
    private static void require(boolean condition,String message) throws SQLException { if(!condition) throw new SQLException(message); }
}
