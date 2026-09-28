package br.com.promova.config;

import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.analysis.service.AnalysisRunRecorder;
import br.com.promova.analysis.review.ReviewStatus;
import br.com.promova.analysis.review.persistence.SavedAnalysisReview;
import br.com.promova.analysis.review.persistence.SavedAnalysisReviewRepository;
import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.github.metrics.GithubPullRequestFact;
import br.com.promova.github.metrics.GithubPullRequestFactRepository;
import br.com.promova.github.metrics.GithubPullReview;
import br.com.promova.github.metrics.GithubPullReviewRepository;
import br.com.promova.organization.JobRole;
import br.com.promova.organization.JobRoleRepository;
import br.com.promova.profile.CareerObjective;
import br.com.promova.profile.CareerObjectiveEvent;
import br.com.promova.profile.CareerObjectiveEventRepository;
import br.com.promova.profile.CareerObjectiveRepository;
import br.com.promova.profile.CareerProfile;
import br.com.promova.profile.CareerProfileRepository;
import br.com.promova.source.NormalizedEvidence;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Small, traceable dashboard fixture for fresh local development databases only. */
@Component
@Profile("dev")
@Order(3)
@ConditionalOnProperty(name = "promova.demo-data.enabled", havingValue = "true")
public class DemoDashboardDataSeeder implements CommandLineRunner {
  private final UserRepository users;
  private final CareerProfileRepository profiles;
  private final CareerObjectiveRepository objectives;
  private final CareerObjectiveEventRepository objectiveEvents;
  private final EvidenceRepository evidences;
  private final SavedAnalysisRepository analyses;
  private final SavedAnalysisReviewRepository reviews;
  private final GithubPullRequestFactRepository pullRequests;
  private final GithubPullReviewRepository pullReviews;
  private final AnalysisRunRecorder runRecorder;
  private final JobRoleRepository jobRoles;

  public DemoDashboardDataSeeder(
      UserRepository users,
      CareerProfileRepository profiles,
      CareerObjectiveRepository objectives,
      CareerObjectiveEventRepository objectiveEvents,
      EvidenceRepository evidences,
      SavedAnalysisRepository analyses,
      SavedAnalysisReviewRepository reviews,
      GithubPullRequestFactRepository pullRequests,
      GithubPullReviewRepository pullReviews,
      AnalysisRunRecorder runRecorder,
      JobRoleRepository jobRoles) {
    this.users = users;
    this.profiles = profiles;
    this.objectives = objectives;
    this.objectiveEvents = objectiveEvents;
    this.evidences = evidences;
    this.analyses = analyses;
    this.reviews = reviews;
    this.pullRequests = pullRequests;
    this.pullReviews = pullReviews;
    this.runRecorder = runRecorder;
    this.jobRoles = jobRoles;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (evidences.count() > 0) return;
    User manager = users.findByRoleOrderByNameAsc(UserRole.MANAGER).stream().findFirst().orElse(null);
    List<User> employees = users.findByRoleOrderByNameAsc(UserRole.EMPLOYEE);
    if (manager == null || employees.size() < 3) return;
    JobRole quality = jobRoles.save(new JobRole("Qualidade de Software",
        "Testes, confiabilidade e qualidade das entregas.", List.of("L3", "L4", "L5")));
    JobRole platform = jobRoles.save(new JobRole("Plataforma",
        "Infraestrutura, entrega contínua e observabilidade.", List.of("L3", "L4", "L5")));
    for (int index = 0; index < 3; index++) {
      User employee = employees.get(index);
      CareerProfile profile = profiles.findByUserId(employee.getId()).orElseThrow();
      if (index > 0) {
        profile.updatePlan(index == 1 ? quality : platform,
            index == 2 ? "L4" : "L3", index == 2 ? "L5" : "L4", profile.getCharacteristics());
        profiles.save(profile);
      }
      LocalDate deadline = LocalDate.now().plusDays(index == 0 ? -3 : index == 1 ? 5 : 21);
      CareerObjective objective = objectives.save(new CareerObjective(
          profile, List.of("Ampliar impacto técnico", "Compartilhar conhecimento", "Liderar uma melhoria").get(index),
          deadline, manager));
      objectiveEvents.save(new CareerObjectiveEvent(objective, manager));
      for (int entry = 0; entry < 3; entry++) {
        int number = 501 + index * 10 + entry;
        Instant occurred = Instant.now().minus(3L + index * 4L + entry * 6L, ChronoUnit.DAYS);
        String title = List.of("Melhoria no fluxo de deploy", "Cobertura de testes", "Documentação da integração").get(entry);
        Evidence evidence = new Evidence(
            employee, "GitHub", "github:promova/demo#" + number,
            "Demo · PR #" + number + " · promova/demo",
            "Titulo: " + title + "\nDescricao/resumo: Evidência ilustrativa de trabalho técnico para o dashboard.",
            null, occurred, occurred.plus(1, ChronoUnit.HOURS));
        if (entry == 2 && index == 2) evidence.dismiss();
        if (entry != 2) evidence.markAnalyzed();
        evidences.save(evidence);
        String state = entry == 2 && index == 2 ? "open" : "closed";
        Instant mergedAt = "closed".equals(state) && entry != 2
            ? occurred.plus(2, ChronoUnit.DAYS) : null;
        NormalizedEvidence normalized = new NormalizedEvidence(
            "GitHub", evidence.getExternalId(), evidence.getSourceMeta(), evidence.getContent(),
            "https://github.com/promova/demo/pull/" + number, "demo-user", occurred,
            Map.ofEntries(
                Map.entry("repository", "promova/demo"),
                Map.entry("number", Integer.toString(number)),
                Map.entry("title", title), Map.entry("state", state),
                Map.entry("author", "demo-user"),
                Map.entry("createdAt", occurred.minus(2, ChronoUnit.DAYS).toString()),
                Map.entry("updatedAt", occurred.toString()),
                Map.entry("mergedAt", mergedAt == null ? "" : mergedAt.toString()),
                Map.entry("closedAt", "closed".equals(state) ? occurred.toString() : ""),
                Map.entry("commitCount", Integer.toString(2 + entry)),
                Map.entry("changedFilesCount", Integer.toString(1 + entry))));
        GithubPullRequestFact fact = new GithubPullRequestFact(employee, "promova/demo", number);
        fact.update(normalized, evidence.getId());
        if (entry == 0) fact.recordReviewRequest(occurred.minus(36, ChronoUnit.HOURS));
        pullRequests.save(fact);
        if (entry == 0) {
          pullReviews.save(new GithubPullReview(fact, (long) number, "demo-reviewer",
              "APPROVED", occurred.minus(1, ChronoUnit.DAYS)));
        }
        if (entry == 2) continue;
        SavedAnalysis analysis = analyses.save(new SavedAnalysis(
            evidence, profile.getCurrentLevel(), profile.getTargetLevel(), null,
            title, profile.getCurrentLevel(), "medium",
            "Exemplo de análise de evidência para desenvolvimento.",
            "[\"" + List.of("Writing code", "Testing", "Observability").get(entry) + "\"]",
            "[\"Acrescente dados do impacto.\"]",
            "A evidência precisa de mais contexto para a progressão.",
            occurred.plus(2, ChronoUnit.HOURS)));
        runRecorder.succeeded(evidence.getId(), analysis.getId(), occurred.plus(1, ChronoUnit.HOURS),
            occurred.plus(2, ChronoUnit.HOURS), List.of("Acrescente dados do impacto."));
        if (entry == 0) {
          reviews.save(new SavedAnalysisReview(analysis, manager,
              index == 2 ? ReviewStatus.NEEDS_CONTEXT : ReviewStatus.ACCEPTED,
              "Revisão demonstrativa.", occurred.plus(1, ChronoUnit.DAYS)));
        }
      }
    }
  }
}
