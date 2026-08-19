package br.com.saulocn.hermes.mailer.entity;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import br.com.saulocn.hermes.mailer.broker.InfraTestResource;
import br.com.saulocn.hermes.mailer.service.ContentTypeTestProfile;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the database schema is aligned across the codebase. The three modules
 * (api, enqueuer, mailer) maintain identical copies of the recipient and message tables,
 * aligned by hand to a shared definition in the {@code db/} directory SQL files.
 *
 * <p>This test queries the actual test database schema and compares it against the SQL files.
 * A column rename in one file compiles everywhere (neither JPA nor tests call the name),
 * but delivery silently produces wrong data. This test is the circuit breaker.
 *
 * <p>The SQL parser is minimal and tolerant: it extracts the first identifier from each line
 * (between parentheses and whitespace) and ignores everything that looks like a constraint or
 * table-level keyword. It processes all .sql files in the db/ directory in alphabetical order,
 * so future migrations are automatically included without needing to update the test.
 */
@QuarkusTest
@TestProfile(ContentTypeTestProfile.class)
@WithTestResource(InfraTestResource.class)
class SchemaContractIT {

    @Inject
    DataSource dataSource;

    @Test
    @Transactional
    void messageTableColumnsMatchContract() throws Exception {
        Set<String> dbColumns = getTableColumnsFromDatabase("message");
        Set<String> sqlColumns = parseAllSqlFilesForTable("message");

        assertEquals(sqlColumns, dbColumns,
                "message table columns must match all db/*.sql files");
    }

    @Test
    @Transactional
    void recipientTableColumnsMatchContract() throws Exception {
        Set<String> dbColumns = getTableColumnsFromDatabase("recipient");
        Set<String> sqlColumns = parseAllSqlFilesForTable("recipient");

        assertEquals(sqlColumns, dbColumns,
                "recipient table columns must match all db/*.sql files");
    }

    /**
     * Query information_schema.columns to get the actual column names from the live database.
     */
    private Set<String> getTableColumnsFromDatabase(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            String query = "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema = 'hermes' AND table_name = '" + tableName + "' " +
                    "ORDER BY ordinal_position";
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                columns.add(rs.getString("column_name"));
            }
        }

        return columns;
    }

    /**
     * Parse all SQL files in the db/ directory and extract columns for the given table.
     * Processes files in alphabetical order, automatically including new migrations without
     * needing to update this test.
     */
    private Set<String> parseAllSqlFilesForTable(String tableName) throws Exception {
        Set<String> allColumns = new HashSet<>();
        Path dbDir = Path.of("..", "db");

        if (!Files.exists(dbDir)) {
            throw new AssertionError(
                    "Database schema directory not found at: " + dbDir.toAbsolutePath() +
                    "\nThis test must run from the hermes-mailer module directory");
        }

        // Get all .sql files in alphabetical order
        List<Path> sqlFiles = Files.list(dbDir)
                .filter(p -> p.getFileName().toString().endsWith(".sql"))
                .sorted()
                .toList();

        for (Path sqlFile : sqlFiles) {
            allColumns.addAll(parseColumnsFromFile(sqlFile, tableName));
        }

        return allColumns;
    }

    /**
     * Parse a single SQL file for CREATE TABLE and ALTER TABLE statements affecting the table.
     * Ignores CREATE INDEX, CREATE SEQUENCE, CREATE SCHEMA, and comment lines.
     */
    private Set<String> parseColumnsFromFile(Path sqlFile, String tableName) throws IOException {
        Set<String> columns = new HashSet<>();
        String content = Files.readString(sqlFile);

        // Parse CREATE TABLE
        Pattern createTablePattern = Pattern.compile(
                "CREATE TABLE hermes\\." + tableName + "\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        Matcher createMatcher = createTablePattern.matcher(content);

        if (createMatcher.find()) {
            int tableStart = createMatcher.end();
            int tableEnd = content.indexOf(");", tableStart);
            if (tableEnd != -1) {
                String tableBody = content.substring(tableStart, tableEnd);
                columns.addAll(extractColumnNames(tableBody));
            }
        }

        // Parse ALTER TABLE ADD COLUMN
        Pattern alterPattern = Pattern.compile(
                "ALTER TABLE hermes\\." + tableName + "\\s+ADD COLUMN",
                Pattern.CASE_INSENSITIVE);
        Matcher alterMatcher = alterPattern.matcher(content);

        if (alterMatcher.find()) {
            // Find all ADD COLUMN IF NOT EXISTS lines for this table
            Pattern addColumnPattern = Pattern.compile(
                    "ADD COLUMN\\s+IF NOT EXISTS\\s+(\\w+)",
                    Pattern.CASE_INSENSITIVE);

            // Extract the ALTER block
            int alterStart = alterMatcher.start();
            int alterEnd = content.indexOf(";", alterStart);
            if (alterEnd != -1) {
                String alterBlock = content.substring(alterStart, alterEnd);
                Matcher addMatcher = addColumnPattern.matcher(alterBlock);
                while (addMatcher.find()) {
                    columns.add(addMatcher.group(1));
                }
            }
        }

        return columns;
    }

    /**
     * Extract column names from a CREATE TABLE body (the part between parentheses).
     * Split by commas, skip constraints and keywords, extract the first identifier.
     */
    private Set<String> extractColumnNames(String tableBody) {
        Set<String> columns = new HashSet<>();

        // Split by commas, each chunk may be on multiple lines
        String[] parts = tableBody.split(",");

        for (String part : parts) {
            String trimmed = part.trim();

            // Skip constraint lines and empty lines
            if (trimmed.isEmpty()) continue;
            if (trimmed.toUpperCase().startsWith("CONSTRAINT")) continue;
            if (trimmed.toUpperCase().startsWith("FOREIGN KEY")) continue;
            if (trimmed.toUpperCase().startsWith("PRIMARY KEY")) continue;

            // Extract the first word as the column name
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length > 0 && !tokens[0].isEmpty()) {
                String columnName = tokens[0];
                // Remove any leading/trailing characters that might be left
                columnName = columnName.replaceAll("[^a-zA-Z0-9_]", "");
                if (!columnName.isEmpty()) {
                    columns.add(columnName);
                }
            }
        }

        return columns;
    }
}
