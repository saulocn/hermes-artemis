package br.com.saulocn.hermes.enqueuer.entity;

import br.com.saulocn.hermes.enqueuer.batch.BatchTestProfile;
import br.com.saulocn.hermes.enqueuer.batch.PostgresTestResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates that the database schema matches the schema files checked into version control.
 *
 * <p>The three modules (hermes-enqueuer, hermes-api, hermes-mailer) each run {@code drop-and-create}
 * on their own test database to stay independent. A schema divergence is invisible at build time
 * but surfaces at runtime: a query written against the real schema may fail or return wrong data
 * when run in a module whose schema is different.
 *
 * <p>This test reads all SQL files in {@code db/} (in alphabetical order by filename, which is
 * the order they are applied during database initialization) and parses CREATE TABLE and ALTER
 * TABLE statements to extract the complete set of columns for {@code hermes.message} and
 * {@code hermes.recipient}. It then queries {@code information_schema.columns} and compares
 * the column sets.
 *
 * <p>Note that {@code db/*.sql} files only run when the Postgres volume is initialized. An
 * existing volume with a stale schema will not trigger these scripts, and this test only catches
 * divergence between this module's entities and the SQL files, not divergence from the real
 * deployed schema. However, this is where agates in hermes-api and hermes-mailer perform the
 * same check over the same files, so any divergence is caught at build time in every module.
 *
 * <p>The parser extracts:
 * - CREATE TABLE: first identifier of each line between parentheses, ignoring CONSTRAINT, FOREIGN KEY, PRIMARY KEY.
 * - ALTER TABLE: all identifiers following ADD COLUMN IF NOT EXISTS.
 * Ignores CREATE INDEX, CREATE SEQUENCE, CREATE SCHEMA, and comment lines.
 */
@QuarkusTest
@TestProfile(BatchTestProfile.class)
@WithTestResource(PostgresTestResource.class)
class SchemaContractIT {

    @Inject
    EntityManager entityManager;

    @Test
    void messageTableSchemaMatches() throws Exception {
        Set<String> expectedColumns = parseColumnsFromSqlFiles("message");
        Set<String> actualColumns = queryTableColumns("message");

        assertEquals(expectedColumns, actualColumns,
                "hermes.message schema does not match db/*.sql files"
                        + ". Expected: " + expectedColumns
                        + ", actual: " + actualColumns);
    }

    @Test
    void recipientTableSchemaMatches() throws Exception {
        Set<String> expectedColumns = parseColumnsFromSqlFiles("recipient");
        Set<String> actualColumns = queryTableColumns("recipient");

        assertEquals(expectedColumns, actualColumns,
                "hermes.recipient schema does not match db/*.sql files"
                        + ". Expected: " + expectedColumns
                        + ", actual: " + actualColumns);
    }

    /**
     * Parses all SQL files in db/ (in filename order) to extract columns for the given table.
     * Handles CREATE TABLE and ALTER TABLE ... ADD COLUMN IF NOT EXISTS.
     */
    private Set<String> parseColumnsFromSqlFiles(String tableName) throws Exception {
        Path dbDir = Path.of("..", "db");
        File[] files = dbDir.toFile().listFiles();

        if (files == null) {
            throw new IllegalArgumentException("Could not list db/ directory at " + dbDir);
        }

        // Sort by filename to apply in order (0schema, 1message, 2recipient, etc).
        List<File> sortedFiles = Arrays.stream(files)
                .filter(f -> f.isFile() && f.getName().endsWith(".sql"))
                .sorted()
                .collect(Collectors.toList());

        Set<String> columns = new HashSet<>();

        for (File sqlFile : sortedFiles) {
            String sql = Files.readString(sqlFile.toPath());

            // Parse CREATE TABLE.
            Set<String> createTableColumns = parseCreateTable(sql, tableName);
            columns.addAll(createTableColumns);

            // Parse ALTER TABLE ... ADD COLUMN.
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
     * Handles multiple ADD COLUMN clauses separated by commas.
     * Returns empty set if the table is not altered in this file.
     */
    private Set<String> parseAlterTable(String sql, String tableName) {
        Set<String> columns = new HashSet<>();

        // Match: ALTER TABLE hermes.<table> ADD COLUMN IF NOT EXISTS <col> ..., ADD COLUMN IF NOT EXISTS <col> ...;
        // We'll match the entire ALTER block and extract all ADD COLUMN clauses.
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
        List<String> results = entityManager.createNativeQuery(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'hermes' AND table_name = :tableName",
                String.class)
                .setParameter("tableName", tableName)
                .getResultList();

        return results.stream().collect(Collectors.toSet());
    }
}
