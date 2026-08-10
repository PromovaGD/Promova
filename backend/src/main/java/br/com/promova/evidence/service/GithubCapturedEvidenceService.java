package br.com.promova.evidence.service;

import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.github.adapter.GithubSourceAdapter;
import br.com.promova.source.NormalizedEvidence;
import br.com.promova.source.SourceEvidenceCaptureService;
import br.com.promova.user.User;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade for the existing single-GitHub-PR capture routes.
 *
 * <p>GitHub DTOs stay inside {@link GithubSourceAdapter}; this evidence-facing service accepts
 * only primitives or the provider-neutral normalized model and delegates persistence to the
 * existing EvidenceService boundary.
 */
@Service
public class GithubCapturedEvidenceService {
  private final GithubSourceAdapter githubSourceAdapter;
  private final EvidenceService evidenceService;
  private final SourceEvidenceCaptureService sourceEvidenceCaptureService;

  @Autowired
  public GithubCapturedEvidenceService(
      GithubSourceAdapter githubSourceAdapter,
      SourceEvidenceCaptureService sourceEvidenceCaptureService,
      EvidenceService evidenceService) {
    this.githubSourceAdapter = githubSourceAdapter;
    this.sourceEvidenceCaptureService = sourceEvidenceCaptureService;
    this.evidenceService = evidenceService;
  }

  /** Convenience constructor for focused unit tests that do not need the generic bridge bean. */
  public GithubCapturedEvidenceService(
      GithubSourceAdapter githubSourceAdapter, EvidenceService evidenceService) {
    this.githubSourceAdapter = githubSourceAdapter;
    this.sourceEvidenceCaptureService = null;
    this.evidenceService = evidenceService;
  }

  public EvidenceResponse fromPullRequest(
      User user, String repoSlug, int pullNumber, String usernameHint) {
    String externalId = githubSourceAdapter.externalIdFor(repoSlug, pullNumber);
    Optional<EvidenceResponse> existing =
        evidenceService.findByNaturalKey(user, GithubSourceAdapter.SOURCE, externalId);
    if (existing.isPresent()) {
      return existing.get();
    }

    NormalizedEvidence normalized =
        githubSourceAdapter.fetchPullRequest(repoSlug, pullNumber, usernameHint);
    return capture(user, normalized).evidence();
  }

  /** Captures already-normalized source data through the existing evidence path. */
  public EvidenceCaptureResult capture(User user, NormalizedEvidence normalizedEvidence) {
    if (sourceEvidenceCaptureService != null) {
      return sourceEvidenceCaptureService.capture(user, normalizedEvidence);
    }
    return
        new EvidenceCaptureResult(
            evidenceService.capture(
                user,
                normalizedEvidence.source(),
                normalizedEvidence.externalId(),
                normalizedEvidence.sourceMeta(),
                normalizedEvidence.evidenceText(),
                normalizedEvidence.sourceUrl()),
            true);
  }
}
