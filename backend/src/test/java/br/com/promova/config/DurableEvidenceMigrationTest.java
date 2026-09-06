package br.com.promova.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class DurableEvidenceMigrationTest {
  @Test
  void preservesExistingContentAndBackfillsOccurredAt() throws Exception {
    String url =
        "jdbc:h2:mem:promova-evidence-migration-"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion("3"))
        .load()
        .migrate();

    try (var connection = DriverManager.getConnection(url, "sa", "");
        var userStatement =
            connection.prepareStatement(
                "INSERT INTO users (name, email, password_hash, role, created_at) "
                    + "VALUES (?, ?, ?, 'EMPLOYEE', CURRENT_TIMESTAMP)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
      userStatement.setString(1, "Existing Employee");
      userStatement.setString(2, "existing@example.com");
      userStatement.setString(3, "hash");
      userStatement.executeUpdate();

      try (var keys = userStatement.getGeneratedKeys()) {
        assertThat(keys.next()).isTrue();
        long userId = keys.getLong(1);
        try (var evidenceStatement =
            connection.prepareStatement(
                "INSERT INTO evidences "
                    + "(user_id, source, external_id, source_meta, evidence, captured_at, updated_at, status) "
                    + "VALUES (?, 'GitHub', 'github:acme/app#1', 'PR #1', 'Existing content', ?, ?, 'PENDING')")) {
          Instant capturedAt = Instant.parse("2026-05-12T10:00:00Z");
          evidenceStatement.setLong(1, userId);
          evidenceStatement.setObject(
              2, java.time.OffsetDateTime.ofInstant(capturedAt, java.time.ZoneOffset.UTC));
          evidenceStatement.setObject(
              3, java.time.OffsetDateTime.ofInstant(capturedAt, java.time.ZoneOffset.UTC));
          evidenceStatement.executeUpdate();
        }
      }
    }

    Flyway.configure().dataSource(url, "sa", "").load().migrate();

    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement =
            connection.prepareStatement(
                "SELECT content, occurred_at, captured_at FROM evidences WHERE external_id = ?")) {
      statement.setString(1, "github:acme/app#1");
      try (var resultSet = statement.executeQuery()) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString("content")).isEqualTo("Existing content");
        assertThat(resultSet.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant())
            .isEqualTo(resultSet.getObject("captured_at", java.time.OffsetDateTime.class).toInstant());
      }
    }
  }
}
