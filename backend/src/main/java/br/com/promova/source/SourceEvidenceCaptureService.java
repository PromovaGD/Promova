package br.com.promova.source;

import br.com.promova.evidence.service.EvidenceCaptureResult;
import br.com.promova.evidence.service.EvidenceService;
import br.com.promova.github.metrics.GithubPullMetricsService;
import br.com.promova.user.User;
import org.springframework.stereotype.Service;

/**
 * Provider-neutral bridge into the existing EvidenceService capture path.
 *
 * <p>Ownership, source/external-id uniqueness, pending status, and duplicate classification remain
 * owned by EvidenceService. Source adapters only discover and normalize.
 */
@Service
public class SourceEvidenceCaptureService {
  private final EvidenceService evidenceService;
  private final GithubPullMetricsService githubPullMetricsService;

  public SourceEvidenceCaptureService(
      EvidenceService evidenceService, GithubPullMetricsService githubPullMetricsService) {
    this.evidenceService = evidenceService;
    this.githubPullMetricsService = githubPullMetricsService;
  }

  public EvidenceCaptureResult capture(User user, NormalizedEvidence normalizedEvidence) {
    if (normalizedEvidence == null) {
      throw new IllegalArgumentException("normalizedEvidence is required");
    }
    EvidenceCaptureResult result = evidenceService.captureResult(
        user,
        normalizedEvidence.source(),
        normalizedEvidence.externalId(),
        normalizedEvidence.sourceMeta(),
        normalizedEvidence.content(),
        normalizedEvidence.sourceUrl(),
        normalizedEvidence.occurredAt());
    if (result.created()) {
      evidenceService.attachSourceFacts(user, result.evidence().id(),
          normalizedEvidence.author(), normalizedEvidence.providerMetadata().get("mergedAt"),
          normalizedEvidence.providerMetadata().get("labels"));
    }
    githubPullMetricsService.upsert(user, normalizedEvidence, result.evidence());
    return result;
  }
}
