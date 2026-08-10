package br.com.promova.github.connection;

import br.com.promova.evidence.service.EvidenceCaptureResult;
import br.com.promova.evidence.service.EvidenceService;
import br.com.promova.github.connection.dto.GithubSyncResponse;
import br.com.promova.github.support.GithubApiException;
import br.com.promova.source.NormalizedEvidence;
import br.com.promova.source.SourceAdapter;
import br.com.promova.source.SourceAdapterRequest;
import br.com.promova.source.SourceEvidenceCaptureService;
import br.com.promova.source.SourcePageResult;
import br.com.promova.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** GitHub-facing sync orchestration backed internally by the provider-neutral source contract. */
@Service
public class GithubSyncService {
  private final GithubConnectionSettingsService settingsService;
  private final SourceAdapter sourceAdapter;
  private final SourceEvidenceCaptureService sourceEvidenceCaptureService;
  private final EvidenceService evidenceService;
  private final Clock clock;
  private final int lookbackDays;
  private final int pageSize;
  private final int maxPages;

  public GithubSyncService(
      GithubConnectionSettingsService settingsService,
      @Qualifier("githubSourceAdapter")
      SourceAdapter sourceAdapter,
      SourceEvidenceCaptureService sourceEvidenceCaptureService,
      EvidenceService evidenceService,
      Clock clock,
      @Value("${github.sync.lookback-days:90}") int lookbackDays,
      @Value("${github.sync.page-size:50}") int pageSize,
      @Value("${github.sync.max-pages:10}") int maxPages) {
    this.settingsService = settingsService;
    this.sourceAdapter = sourceAdapter;
    this.sourceEvidenceCaptureService = sourceEvidenceCaptureService;
    this.evidenceService = evidenceService;
    this.clock = clock;
    this.lookbackDays = clamp(lookbackDays, 1, 3650, 90);
    this.pageSize = clamp(pageSize, 1, 100, 50);
    this.maxPages = clamp(maxPages, 1, 100, 10);
  }

  public GithubSyncResponse sync(User user) {
    GithubConnectionSettings settings = settingsService.requireConfigured(user);
    String scope = settings.getRepoSlug();
    String author = settings.getAuthorLogin();
    Instant syncAt = clock.instant();
    Instant cutoff = syncAt.minus(Duration.ofDays(lookbackDays));
    Counters counters = new Counters();

    try {
      boolean hasMorePages = true;
      for (int page = 1; page <= maxPages && hasMorePages; page++) {
        SourcePageResult response =
            Objects.requireNonNull(
                sourceAdapter.discover(
                    new SourceAdapterRequest(scope, author, cutoff, pageSize, page)),
                "source adapter returned no page result");
        counters.failed += response.failedItems();

        for (NormalizedEvidence normalizedEvidence : response.items()) {
          counters.discovered++;
          captureOne(user, normalizedEvidence, counters);
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
              scope,
              author,
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
      User user, NormalizedEvidence normalizedEvidence, Counters counters) {
    String externalId = normalizedEvidence.externalId();
    try {
      EvidenceCaptureResult result = sourceEvidenceCaptureService.capture(user, normalizedEvidence);
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
    if (evidenceService.findByNaturalKey(user, sourceAdapter.source(), externalId).isPresent()) {
      counters.existing++;
    } else {
      counters.failed++;
    }
  }

  private boolean pageIsOlderThanCutoff(SourcePageResult response, Instant cutoff) {
    if (response.oldestObservedAt() == null) {
      return response.failedItems() == 0;
    }
    return response.oldestObservedAt().isBefore(cutoff);
  }

  private void recordFailure(User user, Instant syncAt, String outcome) {
    try {
      settingsService.recordSyncOutcome(user, syncAt, outcome);
    } catch (RuntimeException ignored) {
      // Preserve the safe upstream error even if the status bookkeeping cannot be written.
    }
  }

  private int clamp(int value, int minimum, int maximum, int fallback) {
    if (value < minimum) {
      return fallback;
    }
    return Math.min(value, maximum);
  }

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
