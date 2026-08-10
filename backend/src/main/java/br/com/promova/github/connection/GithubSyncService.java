package br.com.promova.github.connection;

import br.com.promova.evidence.service.EvidenceCaptureResult;
import br.com.promova.evidence.service.EvidenceService;
import br.com.promova.evidence.service.GithubCapturedEvidenceService;
import br.com.promova.github.connection.dto.GithubSyncResponse;
import br.com.promova.github.dto.GithubPullRequestPage;
import br.com.promova.github.dto.GithubPullSummary;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.github.support.GithubApiException;
import br.com.promova.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GithubSyncService {
  private final GithubConnectionSettingsService settingsService;
  private final GithubPullRequestService githubPullRequestService;
  private final GithubCapturedEvidenceService githubCapturedEvidenceService;
  private final EvidenceService evidenceService;
  private final Clock clock;
  private final int lookbackDays;
  private final int pageSize;
  private final int maxPages;

  public GithubSyncService(
      GithubConnectionSettingsService settingsService,
      GithubPullRequestService githubPullRequestService,
      GithubCapturedEvidenceService githubCapturedEvidenceService,
      EvidenceService evidenceService,
      Clock clock,
      @Value("${github.sync.lookback-days:90}") int lookbackDays,
      @Value("${github.sync.page-size:50}") int pageSize,
      @Value("${github.sync.max-pages:10}") int maxPages) {
    this.settingsService = settingsService;
    this.githubPullRequestService = githubPullRequestService;
    this.githubCapturedEvidenceService = githubCapturedEvidenceService;
    this.evidenceService = evidenceService;
    this.clock = clock;
    this.lookbackDays = clamp(lookbackDays, 1, 3650, 90);
    this.pageSize = clamp(pageSize, 1, 100, 50);
    this.maxPages = clamp(maxPages, 1, 100, 10);
  }

  public GithubSyncResponse sync(User user) {
    GithubConnectionSettings settings = settingsService.requireConfigured(user);
    RepositorySlug repository = parseRepository(settings.getRepoSlug());
    String authorLogin = settings.getAuthorLogin();
    Instant syncAt = clock.instant();
    Instant cutoff = syncAt.minus(Duration.ofDays(lookbackDays));
    Counters counters = new Counters();

    try {
      boolean hasMorePages = true;
      for (int page = 1; page <= maxPages && hasMorePages; page++) {
        GithubPullRequestPage response =
            githubPullRequestService.listClosedPullRequestsForSync(
                repository.owner(), repository.repo(), pageSize, page);
        counters.failed += response.malformedItems();

        for (GithubPullSummary pullRequest : response.pullRequests()) {
          if (!matchesSyncFilter(pullRequest, authorLogin, cutoff)) {
            continue;
          }

          counters.discovered++;
          captureOne(user, repository, pullRequest, counters);
        }

        hasMorePages =
            response.hasPotentialNextPage() && !pageIsOlderThanCutoff(response, cutoff);
        if (page == maxPages && hasMorePages) {
          counters.truncated = true;
        }
      }

      String outcome = counters.outcome();
      settingsService.recordSyncOutcome(user, syncAt, outcome);
      return
          new GithubSyncResponse(
              settings.getRepoSlug(),
              authorLogin,
              counters.discovered,
              counters.created,
              counters.existing,
              counters.failed,
              syncAt,
              outcome);
    } catch (GithubApiException exception) {
      recordFailure(user, syncAt, "FAILED_UPSTREAM_" + exception.statusCode());
      throw exception;
    } catch (ResponseStatusException exception) {
      recordFailure(user, syncAt, "FAILED_UPSTREAM");
      throw exception;
    } catch (RuntimeException exception) {
      recordFailure(user, syncAt, "FAILED");
      throw exception;
    }
  }

  private void captureOne(
      User user, RepositorySlug repository, GithubPullSummary pullRequest, Counters counters) {
    String externalId = externalId(repository, pullRequest.number());
    try {
      EvidenceCaptureResult result =
          githubCapturedEvidenceService.fromPullSummary(
              user, repository.owner() + "/" + repository.repo(), pullRequest);
      if (result.created()) {
        counters.created++;
      } else {
        counters.existing++;
      }
    } catch (DataIntegrityViolationException exception) {
      // A concurrent sync may win the unique-key race between the read and insert. Count the
      // committed row as existing when it is visible, and never create a second evidence row.
      classifyCaptureFailure(user, externalId, counters);
    } catch (RuntimeException exception) {
      // One malformed/failed capture must not roll back successful captures from this sync.
      // Unexpected transaction wrappers can also contain a unique-key conflict, so check the
      // natural key before reporting a failure.
      classifyCaptureFailure(user, externalId, counters);
    }
  }

  private void classifyCaptureFailure(User user, String externalId, Counters counters) {
    if (evidenceService.findByNaturalKey(user, "GitHub", externalId).isPresent()) {
      counters.existing++;
    } else {
      counters.failed++;
    }
  }

  private boolean matchesSyncFilter(
      GithubPullSummary pullRequest, String authorLogin, Instant cutoff) {
    if (pullRequest == null
        || pullRequest.number() < 1
        || !"closed".equalsIgnoreCase(pullRequest.state())
        || pullRequest.mergedAt() == null
        || pullRequest.mergedAt().isBlank()
        || pullRequest.authorLogin() == null
        || !pullRequest.authorLogin().equalsIgnoreCase(authorLogin)) {
      return false;
    }

    return parseInstant(pullRequest.updatedAt())
        .map(updatedAt -> !updatedAt.isBefore(cutoff))
        .orElse(false);
  }

  private boolean pageIsOlderThanCutoff(GithubPullRequestPage response, Instant cutoff) {
    if (response.pullRequests().isEmpty()) {
      return response.malformedItems() == 0;
    }

    GithubPullSummary last = response.pullRequests().get(response.pullRequests().size() - 1);
    return parseInstant(last.updatedAt()).map(updatedAt -> updatedAt.isBefore(cutoff)).orElse(false);
  }

  private void recordFailure(User user, Instant syncAt, String outcome) {
    try {
      settingsService.recordSyncOutcome(user, syncAt, outcome);
    } catch (RuntimeException ignored) {
      // Preserve the safe upstream error even if the status bookkeeping cannot be written.
    }
  }

  private RepositorySlug parseRepository(String repoSlug) {
    String[] parts = repoSlug == null ? new String[0] : repoSlug.split("/", -1);
    if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Repository must be in owner/repo format");
    }
    githubPullRequestService.validateRepository(parts[0], parts[1]);
    return new RepositorySlug(parts[0], parts[1]);
  }

  private String externalId(RepositorySlug repository, int pullNumber) {
    return "github:%s/%s#%d"
        .formatted(
            repository.owner().toLowerCase(Locale.ROOT),
            repository.repo().toLowerCase(Locale.ROOT),
            pullNumber);
  }

  private Optional<Instant> parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Instant.parse(value));
    } catch (DateTimeParseException exception) {
      return Optional.empty();
    }
  }

  private int clamp(int value, int minimum, int maximum, int fallback) {
    if (value < minimum) {
      return fallback;
    }
    return Math.min(value, maximum);
  }

  private record RepositorySlug(String owner, String repo) {}

  private static final class Counters {
    private int discovered;
    private int created;
    private int existing;
    private int failed;
    private boolean truncated;

    private String outcome() {
      return failed > 0 || truncated ? "PARTIAL" : "SUCCESS";
    }
  }
}
