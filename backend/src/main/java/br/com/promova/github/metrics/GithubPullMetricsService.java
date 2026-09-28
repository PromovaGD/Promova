package br.com.promova.github.metrics;

import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.github.client.GithubApiClient;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.source.NormalizedEvidence;
import br.com.promova.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GithubPullMetricsService {
  private final GithubPullRequestFactRepository facts;
  private final GithubPullReviewRepository reviews;
  private final GithubApiClient github;
  private final GithubPullRequestService pullRequests;

  public GithubPullMetricsService(
      GithubPullRequestFactRepository facts,
      GithubPullReviewRepository reviews,
      GithubApiClient github,
      GithubPullRequestService pullRequests) {
    this.facts = facts;
    this.reviews = reviews;
    this.github = github;
    this.pullRequests = pullRequests;
  }

  @Transactional
  public void upsert(User employee, NormalizedEvidence normalized, EvidenceResponse evidence) {
    if (!"GitHub".equalsIgnoreCase(normalized.source())) return;
    Map<String, String> meta = normalized.providerMetadata();
    String repository = meta.get("repository");
    String numberText = meta.get("number");
    if (repository == null || repository.length() > 255 || numberText == null) return;
    String[] parts = repository.split("/", -1);
    if (parts.length != 2) return;
    pullRequests.validateRepository(parts[0], parts[1]);
    int number;
    try { number = Integer.parseInt(numberText); }
    catch (NumberFormatException error) { return; }
    if (number < 1) return;
    GithubPullRequestFact fact = facts
        .findByEmployeeIdAndRepositoryAndPullNumber(employee.getId(), repository, number)
        .orElseGet(() -> new GithubPullRequestFact(employee, repository, number));
    fact.update(normalized, evidence.id());
    facts.save(fact);
  }

  /** Refreshes bounded GitHub code-review events; distinct from Promova analysis reviews. */
  public RefreshResult refreshReviews(User manager, int requestedLimit) {
    if (requestedLimit < 1 || requestedLimit > 25) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limite deve estar entre 1 e 25.");
    }
    List<GithubPullRequestFact> selected =
        facts.recentVisible(manager.getId(), PageRequest.of(0, requestedLimit));
    int added = 0;
    int failed = 0;
    for (GithubPullRequestFact fact : selected) {
      try {
        added += refreshOne(fact);
      } catch (RuntimeException error) {
        failed++;
      }
    }
    return new RefreshResult(selected.size(), added, failed);
  }

  private int refreshOne(GithubPullRequestFact fact) {
    String[] repository = fact.getRepository().split("/", -1);
    pullRequests.validateRepository(repository[0], repository[1]);
    int added = 0;
    for (int page = 1; page <= 3; page++) {
      JsonNode response = github.get("/repos/" + repository[0] + "/" + repository[1]
          + "/pulls/" + fact.getPullNumber() + "/reviews?per_page=100&page=" + page);
      if (!response.isArray()) throw new IllegalStateException("GitHub reviews response is not an array");
      for (JsonNode review : response) {
        if (!review.path("id").canConvertToLong()) continue;
        long providerId = review.path("id").asLong();
        String reviewer = review.path("user").path("login").asText("");
        String state = review.path("state").asText("");
        if (providerId < 1 || reviewer.isBlank() || state.isBlank()) continue;
        if (reviews.existsByPullRequestIdAndProviderReviewId(fact.getId(), providerId)) continue;
        reviews.save(new GithubPullReview(
            fact, providerId,
            reviewer.length() <= 100 ? reviewer : reviewer.substring(0, 100),
            state.length() <= 40 ? state : state.substring(0, 40),
            instant(review.path("submitted_at").asText(null))));
        added++;
      }
      if (response.size() < 100) break;
    }
    try {
      refreshReviewRequests(fact, repository);
    } catch (RuntimeException ignored) {
      // Code review events remain useful when the issue-events API is unavailable.
    }
    return added;
  }

  private void refreshReviewRequests(GithubPullRequestFact fact, String[] repository) {
    Instant earliest = null;
    for (int page = 1; page <= 3; page++) {
      JsonNode events = github.get("/repos/" + repository[0] + "/" + repository[1]
          + "/issues/" + fact.getPullNumber() + "/events?per_page=100&page=" + page);
      if (!events.isArray()) throw new IllegalStateException("GitHub issue events response is not an array");
      for (JsonNode event : events) {
        if (!"review_requested".equals(event.path("event").asText())) continue;
        Instant requested = instant(event.path("created_at").asText(null));
        if (requested != null && (earliest == null || requested.isBefore(earliest))) {
          earliest = requested;
        }
      }
      if (events.size() < 100) break;
    }
    fact.recordReviewRequest(earliest);
    facts.save(fact);
  }

  private Instant instant(String value) {
    if (value == null || value.isBlank()) return null;
    try { return Instant.parse(value); }
    catch (DateTimeParseException error) { return null; }
  }

  public record RefreshResult(int pullRequests, int reviewsAdded, int failedPullRequests) {}
}
