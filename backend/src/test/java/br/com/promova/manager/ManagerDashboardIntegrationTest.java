package br.com.promova.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.analysis.service.AnalysisRunRecorder;
import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.github.metrics.GithubPullRequestFact;
import br.com.promova.github.metrics.GithubPullRequestFactRepository;
import br.com.promova.github.metrics.GithubPullReview;
import br.com.promova.github.metrics.GithubPullReviewRepository;
import br.com.promova.manager.service.ManagerDashboardService;
import br.com.promova.manager.service.ManagerDashboardService.DashboardFilter;
import br.com.promova.manager.team.ManagerTeamService;
import br.com.promova.source.NormalizedEvidence;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManagerDashboardIntegrationTest {
  @Autowired private UserRepository users;
  @Autowired private EvidenceRepository evidences;
  @Autowired private SavedAnalysisRepository analyses;
  @Autowired private GithubPullRequestFactRepository pullRequests;
  @Autowired private GithubPullReviewRepository pullReviews;
  @Autowired private ManagerTeamService teams;
  @Autowired private ManagerDashboardService dashboard;
  @Autowired private AnalysisRunRecorder runRecorder;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void aggregatesScopedRecordsAndKeepsPaginationOutOfTotals() {
    User manager = users.findByEmailIgnoreCase("manager@promova.com").orElseThrow();
    User employee = users.findByEmailIgnoreCase("joao.silva@empresa.com").orElseThrow();
    Instant now = Instant.now();
    LocalDate today = LocalDate.now();
    var team = teams.create(manager, "Equipe de teste");
    teams.assign(manager, team.id(), employee.getId());

    Evidence evidence = evidences.saveAndFlush(new Evidence(employee, "GitHub", "github:sample/demo#2026",
        "PR #2026", "Entrega demonstrativa de testes", "https://github.com/sample/demo/pull/2026",
        now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)));
    evidence.markAnalyzed();
    evidences.saveAndFlush(evidence);
    SavedAnalysis analysis = analyses.saveAndFlush(new SavedAnalysis(evidence, "L3", "L4", null,
        "Melhoria de testes", "L3", "medium", "A entrega é verificável.",
        "[\"Ownership\"]", "[\"Detalhar impacto.\"]", "Em desenvolvimento", now));
    NormalizedEvidence normalized = new NormalizedEvidence("GitHub", evidence.getExternalId(),
        evidence.getSourceMeta(), evidence.getContent(), evidence.getSourceUrl(), "joao", now,
        Map.of("repository", "sample/demo", "number", "2026", "title", "Melhoria de testes",
            "author", "joao", "state", "closed", "createdAt", now.minus(2, ChronoUnit.DAYS).toString(),
            "mergedAt", now.toString(), "commitCount", "4"));
    GithubPullRequestFact fact = new GithubPullRequestFact(employee, "sample/demo", 2026);
    fact.update(normalized, evidence.getId());
    fact.recordReviewRequest(now.minus(1, ChronoUnit.DAYS));
    pullRequests.saveAndFlush(fact);
    pullReviews.saveAndFlush(new GithubPullReview(fact, 2026L, "reviewer", "APPROVED", now));
    runRecorder.succeeded(evidence.getId(), analysis.getId(), now.minusSeconds(8), now,
        List.of("Detalhar impacto."));

    var all = dashboard.summary(manager, new DashboardFilter(today.minusDays(30), today,
        null, null, null, "custom"));
    assertThat(all.peopleCount()).isEqualTo(3);
    assertThat(all.evidenceCount()).isEqualTo(1);
    assertThat(all.analysisCount()).isEqualTo(1);
    assertThat(all.highlights()).hasSize(1);
    assertThat(all.highlights().get(0).analysisId()).isEqualTo(analysis.getId());
    assertThat(all.highlights().get(0).employeeId()).isEqualTo(employee.getId());
    assertThat(all.highlights().get(0).title()).isEqualTo("Melhoria de testes");
    assertThat(all.unreviewedCount()).isEqualTo(1);
    assertThat(all.pullRequests().registered()).isEqualTo(1);
    assertThat(all.pullRequests().withHumanReview()).isEqualTo(1);
    assertThat(all.pullRequests().merged()).isEqualTo(1);
    assertThat(all.pullRequests().commitsOnKnownPulls()).isEqualTo(4);
    assertThat(all.pullRequests().commitCountSample()).isEqualTo(1);
    assertThat(all.pullRequests().firstRequestSample()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_runs", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analysis_suggestions", Integer.class)).isEqualTo(1);
    assertThat(dashboard.people(manager, new DashboardFilter(null, null, null, null, null, "30"),
        1, 1).total()).isEqualTo(3);

    var one = dashboard.summary(manager, new DashboardFilter(today.minusDays(30), today,
        team.id(), null, employee.getId(), "custom"));
    assertThat(one.peopleCount()).isEqualTo(1);
    assertThat(one.evidenceCount()).isEqualTo(1);
    assertThat(one.highlights()).hasSize(1);
    var outside = dashboard.summary(manager, new DashboardFilter(today.minusDays(30), today,
        team.id(), null, users.findByEmailIgnoreCase("maria.santos@empresa.com").orElseThrow().getId(), "custom"));
    assertThat(outside.peopleCount()).isZero();
    assertThat(outside.evidenceCount()).isZero();
    assertThat(outside.highlights()).isEmpty();
    var lastWeek = dashboard.summary(manager, new DashboardFilter(null, null,
        team.id(), null, employee.getId(), "7"));
    assertThat(lastWeek.from()).isEqualTo(lastWeek.to().minusDays(6));
  }

  @Test
  void preventsManagerFromReadingAnotherManagersTeamOrPerson() {
    User manager = users.findByEmailIgnoreCase("manager@promova.com").orElseThrow();
    User other = users.saveAndFlush(new User("Outro gestor", "other-manager-dashboard@example.com",
        "test", UserRole.MANAGER));
    var privateTeam = teams.create(other, "Equipe reservada");
    User assigned = users.findByEmailIgnoreCase("joao.silva@empresa.com").orElseThrow();
    assertThatThrownBy(() -> teams.assign(other, privateTeam.id(), assigned.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT));
    assertThatThrownBy(() -> dashboard.summary(manager,
        new DashboardFilter(null, null, privateTeam.id(), null, null, "30")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND));
    var unassigned = users.saveAndFlush(new User("Sem equipe", "unassigned-dashboard@example.com",
        "test", UserRole.EMPLOYEE));
    assertThatThrownBy(() -> dashboard.summary(manager,
        new DashboardFilter(null, null, null, null, unassigned.getId(), "30")))
        .isInstanceOf(ResponseStatusException.class);
  }
}
