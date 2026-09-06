package br.com.promova.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.main.web-application-type=none")
@ActiveProfiles("test")
class MigrationStartupSmokeTest {
  @Autowired private Environment environment;
  @Autowired private Flyway flyway;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void startsWithTheLatestMigrationAndValidatedAcceptedSchema() {
    MigrationInfo current = flyway.info().current();

    assertThat(current).isNotNull();
    assertThat(current.getVersion().getVersion()).isEqualTo("6");
    assertThat(current.getDescription()).isEqualTo("expand user career plans");
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(environment.getProperty("spring.datasource.url"))
        .startsWith("jdbc:h2:mem:")
        .doesNotContain("backend/data")
        .doesNotContain("promova.");

    Set<String> tables =
        jdbcTemplate
            .queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class)
            .stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    assertThat(tables)
        .contains(
            "users",
            "auth_sessions",
            "career_profiles",
            "evidences",
            "github_connection_settings",
            "terminology_settings",
            "job_roles",
            "job_role_levels",
            "career_profile_characteristics",
            "career_objectives",
            "saved_analyses",
            "saved_analysis_reviews");

    Set<String> evidenceColumns =
        jdbcTemplate
            .queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'EVIDENCES'",
                String.class)
            .stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    assertThat(evidenceColumns).contains("content", "occurred_at").doesNotContain("evidence");
  }
}
