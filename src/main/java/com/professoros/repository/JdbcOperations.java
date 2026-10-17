package com.professoros.repository;

import com.professoros.database.Database;
import com.professoros.model.DomainValidationException;
import org.sqlite.SQLiteException;
import org.sqlite.SQLiteErrorCode;
import java.sql.*;
import java.util.*;

/** Small resource/binding helper shared by the new repositories, not an ORM or SQL generator. */
final class JdbcOperations {
    @FunctionalInterface interface RowMapper<T> { T map(ResultSet row) throws SQLException; }
    private final Database database;
    private final String duplicateMessage;

    JdbcOperations(Database database, String duplicateMessage) {
        this.database = database;
        this.duplicateMessage = duplicateMessage;
    }

    <T> List<T> query(String sql, RowMapper<T> mapper, Object... values) {
        try (var connection = database.openConnection(); var statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (var rows = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rows.next()) { result.add(mapper.map(rows)); }
                return List.copyOf(result);
            }
        } catch (SQLException failure) { throw translate(failure); }
    }

    <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... values) {
        return query(sql, mapper, values).stream().findFirst();
    }

    int execute(String sql, Object... values) {
        try (var connection = database.openConnection(); var statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            return statement.executeUpdate();
        } catch (SQLException failure) { throw translate(failure); }
    }

    boolean exists(String sql, Object... values) {
        return one(sql, row -> row.getBoolean(1), values).orElse(false);
    }

    @FunctionalInterface interface Transaction<T> { T run(Connection connection) throws SQLException; }

    <T> T transaction(Transaction<T> work) {
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException failure) { throw translate(failure); }
    }

    static void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Enum<?> || value instanceof java.time.temporal.TemporalAccessor) {
                value = value.toString();
            }
            statement.setObject(index + 1, value);
        }
    }

    private RuntimeException translate(SQLException failure) {
        if (failure instanceof SQLiteException sqlite) {
            if (sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE) {
                return new DomainValidationException(duplicateMessage);
            }
            if (sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY
                    || (sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER
                    && failure.getMessage().contains("FOREIGN KEY"))) {
                return new DomainValidationException("This change is blocked by linked academic records. Refresh and review its relationships.");
            }
            if (sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER
                    && failure.getMessage().contains("Offering with")) {
                return new DomainValidationException("An offering with academic history cannot be moved to another course or term.");
            }
        }
        return new StorageException("The academic records could not be saved or loaded. Refresh and try again.", failure);
    }
}
