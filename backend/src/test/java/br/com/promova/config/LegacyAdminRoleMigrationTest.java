package br.com.promova.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class LegacyAdminRoleMigrationTest {
  @Test
  void convertsStoredAdminUsersAndRejectsTheLegacyRoleAfterMigration() throws Exception {
    String url =
        "jdbc:h2:mem:promova-admin-migration-"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    Flyway.configure()
        .dataSource(url, "sa", "")
        .target(MigrationVersion.fromVersion("2"))
        .load()
        .migrate();

    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.prepareStatement(
            "INSERT INTO users (name, email, password_hash, role, created_at) "
                + "VALUES (?, ?, ?, 'ADMIN', CURRENT_TIMESTAMP)")) {
      statement.setString(1, "Legacy Admin");
      statement.setString(2, "legacy-admin@example.com");
      statement.setString(3, "legacy-hash");
      statement.executeUpdate();
    }

    Flyway.configure().dataSource(url, "sa", "").load().migrate();

    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.prepareStatement("SELECT role FROM users WHERE email = ?")) {
      statement.setString(1, "legacy-admin@example.com");
      try (var resultSet = statement.executeQuery()) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString("role")).isEqualTo("MANAGER");
      }
    }

    assertThatThrownBy(
            () -> {
              try (var connection = DriverManager.getConnection(url, "sa", "");
                  var statement = connection.prepareStatement(
                      "INSERT INTO users (name, email, password_hash, role, created_at) "
                          + "VALUES (?, ?, ?, 'ADMIN', CURRENT_TIMESTAMP)")) {
                statement.setString(1, "Unsupported Admin");
                statement.setString(2, "unsupported-admin@example.com");
                statement.setString(3, "hash");
                statement.executeUpdate();
              }
            })
        .isInstanceOf(SQLException.class);
  }
}
