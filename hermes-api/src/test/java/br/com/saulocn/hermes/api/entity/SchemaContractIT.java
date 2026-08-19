package br.com.saulocn.hermes.api.entity;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import br.com.saulocn.hermes.api.admin.ApiTestProfile;
import br.com.saulocn.hermes.api.admin.InfraTestResource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema contract tests for Recipient and Message entities.
 *
 * <p>Verifies that the actual database schema matches the cumulative result of applying
 * all db/*.sql files in order. The db/*.sql files are only run on Postgres volume
 * initialization; this test catches divergence between the actual schema and what the
 * SQL files declare (not between files and a pre-existing environment).
 *
 * <p>Handles both CREATE TABLE and ALTER TABLE ADD COLUMN statements, which may
 * introduce columns incrementally (e.g., db/2recipient.sql creates 6 columns,
 * db/4attempts.sql adds recipient_attempts, db/5rates.sql adds published_on and claimed_on).
 */
@QuarkusTest
@TestProfile(ApiTestProfile.class)
@WithTestResource(InfraTestResource.class)
public class SchemaContractIT {

    @Inject
    EntityManager em;

    /**
     * Parses all SQL files in db/ (in filename order) to extract columns for the given table.
     * Handles CREATE TABLE and ALTER TABLE ... ADD COLUMN IF NOT EXISTS.
     */
    private Set<String> parseColumnsFromSqlFiles(String tableName) throws Exception {
        Path dbDir = Path.of("..").resolve("db").toAbsolutePath().normalize();
        if (!Files.exists(dbDir)) {
            dbDir = Path.of("db").toAbsolutePath().normalize();
        }
        if (!Files.exists(dbDir)) {
            dbDir = Path.of("hermes-api").resolve("..").resolve("db").toAbsolutePath().normalize();
        }

        File[] files = dbDir.toFile().listFiles();
        if (files == null) {
            throw new IllegalArgumentException("Could not list db/ directory at " + dbDir);
        }

        // Sort by filename to apply in order (0schema, 1message, 2recipient, 4attempts, 5rates, etc).
        List<File> sortedFiles = Arrays.stream(files)
                .filter(f -> f.isFile() && f.getName().endsWith(".sql"))
                .sorted()
                .collect(Collectors.toList());

        Set<String> columns = new HashSet<>();

        for (File sqlFile : sortedFiles) {
            String sql = Files.readString(sqlFile.toPath());

            // Parse CREATE TABLE
            Set<String> createTableColumns = parseCreateTable(sql, tableName);
            columns.addAll(createTableColumns);

            // Parse ALTER TABLE ... ADD COLUMN
            Set<String> alterTableColumns = parseAlterTable(sql, tableName);
            columns.addAll(alterTableColumns);
        }

        return columns;
    }

    /**
     * Extracts columns from a CREATE TABLE statement. Returns empty set if the table is not found
     * in this file. Uses paren-counting to handle nested parentheses in constraints.
     */
    private Set<String> parseCreateTable(String sql, String tableName) {
        Set<String> columns = new HashSet<>();

        // Find: CREATE TABLE hermes.<table> (
        String pattern = "CREATE TABLE\\s+hermes\\." + tableName + "\\s*\\(";
        Pattern tableStart = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher startMatcher = tableStart.matcher(sql);

        if (!startMatcher.find()) {
            return columns; // Table not in this file.
        }

        int openParen = startMatcher.end() - 1; // Position of the (
        int parenDepth = 1;
        int pos = openParen + 1;

        // Find matching ); by counting parentheses.
        while (pos < sql.length() && parenDepth > 0) {
            char c = sql.charAt(pos);
            if (c == '(') {
                parenDepth++;
            } else if (c == ')') {
                parenDepth--;
                if (parenDepth == 0 && pos + 1 < sql.length() && sql.charAt(pos + 1) == ';') {
                    // Found the end of the table definition.
                    String tableContent = sql.substring(openParen + 1, pos);
                    return parseTableContent(tableContent);
                }
            }
            pos++;
        }

        return columns; // Could not find matching );
    }

    /**
     * Parses the content between ( and ) in a CREATE TABLE statement.
     */
    private Set<String> parseTableContent(String tableContent) {
        Set<String> columns = new HashSet<>();
        String[] lines = tableContent.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();

            // Skip empty lines.
            if (trimmed.isEmpty()) {
                continue;
            }

            String upperTrimmed = trimmed.toUpperCase();

            // Skip constraint-related keywords.
            if (upperTrimmed.startsWith("CONSTRAINT")
                    || upperTrimmed.startsWith("FOREIGN KEY")
                    || upperTrimmed.startsWith("PRIMARY KEY")
                    || upperTrimmed.startsWith("REFERENCES")
                    || upperTrimmed.startsWith("ON ")) {
                continue;
            }

            // Extract the first identifier (the column name).
            Pattern identifierPattern = Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]*)");
            Matcher identifierMatcher = identifierPattern.matcher(trimmed);

            if (identifierMatcher.find()) {
                String columnName = identifierMatcher.group(1);
                columns.add(columnName);
            }
        }

        return columns;
    }

    /**
     * Extracts columns from ALTER TABLE ... ADD COLUMN IF NOT EXISTS statements.
     * Handles multiple ADD COLUMN clauses separated by commas, and statements that span multiple lines.
     * Returns empty set if the table is not altered in this file.
     */
    private Set<String> parseAlterTable(String sql, String tableName) {
        Set<String> columns = new HashSet<>();

        // Match: ALTER TABLE hermes.<table> ... ADD COLUMN IF NOT EXISTS <col> ..., ADD COLUMN IF NOT EXISTS <col> ...;
        // Use DOTALL to match across newlines.
        String pattern = "ALTER TABLE\\s+hermes\\." + tableName + "\\s+(.+?);";
        Pattern alterPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher alterMatcher = alterPattern.matcher(sql);

        while (alterMatcher.find()) {
            String alterContent = alterMatcher.group(1);

            // Find all ADD COLUMN IF NOT EXISTS clauses.
            Pattern addPattern = Pattern.compile(
                    "ADD COLUMN IF NOT EXISTS\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
                    Pattern.CASE_INSENSITIVE);
            Matcher addMatcher = addPattern.matcher(alterContent);

            while (addMatcher.find()) {
                String columnName = addMatcher.group(1);
                columns.add(columnName);
            }
        }

        return columns;
    }

    /**
     * Queries information_schema.columns for the given table in the hermes schema.
     */
    private Set<String> queryTableColumns(String tableName) {
        @SuppressWarnings("unchecked")
        List<String> results = em.createNativeQuery(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'hermes' AND table_name = :tableName",
                String.class)
                .setParameter("tableName", tableName)
                .getResultList();

        return results.stream().collect(Collectors.toSet());
    }

    /**
     * The hermes.recipient table columns match the cumulative schema from all db/*.sql files.
     * Recipient starts with 6 columns (2recipient.sql), gains recipient_attempts from 4attempts.sql,
     * and gains published_on and claimed_on from 5rates.sql, for a total of 9 columns.
     */
    @Test
    void recipientSchemaMatchesContract() throws Exception {
        Set<String> expectedColumns = parseColumnsFromSqlFiles("recipient");

        assertFalse(expectedColumns.isEmpty(),
                "SQL parser should have found columns in db/*.sql files for recipient");

        Set<String> actualColumns = queryTableColumns("recipient");

        assertEquals(expectedColumns, actualColumns,
                "recipient table columns should match contract. " +
                "Expected: " + expectedColumns + ", got: " + actualColumns);
    }

    /**
     * The hermes.message table columns match the cumulative schema from all db/*.sql files.
     */
    @Test
    void messageSchemaMatchesContract() throws Exception {
        Set<String> expectedColumns = parseColumnsFromSqlFiles("message");

        assertFalse(expectedColumns.isEmpty(),
                "SQL parser should have found columns in db/*.sql files for message");

        Set<String> actualColumns = queryTableColumns("message");

        assertEquals(expectedColumns, actualColumns,
                "message table columns should match contract. " +
                "Expected: " + expectedColumns + ", got: " + actualColumns);
    }

    /**
     * Both tables have schema defined in the database.
     */
    @Test
    void bothTablesExist() {
        // recipient
        Long recipientCount = (Long) em.createNativeQuery(
                "select count(*) from information_schema.tables " +
                "where table_schema = 'hermes' and table_name = 'recipient'")
                .getSingleResult();
        assertEquals(1, recipientCount, "recipient table should exist");

        // message
        Long messageCount = (Long) em.createNativeQuery(
                "select count(*) from information_schema.tables " +
                "where table_schema = 'hermes' and table_name = 'message'")
                .getSingleResult();
        assertEquals(1, messageCount, "message table should exist");
    }
}
