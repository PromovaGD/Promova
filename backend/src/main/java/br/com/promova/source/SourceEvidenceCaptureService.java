package br.com.promova.source;

import br.com.promova.evidence.service.EvidenceCaptureResult;
import br.com.promova.evidence.service.EvidenceService;
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

  public SourceEvidenceCaptureService(EvidenceService evidenceService) {
    this.evidenceService = evidenceService;
  }

  public EvidenceCaptureResult capture(User user, NormalizedEvidence normalizedEvidence) {
    if (normalizedEvidence == null) {
      throw new IllegalArgumentException("normalizedEvidence is required");
    }
    return evidenceService.captureResult(
        user,
        normalizedEvidence.source(),
        normalizedEvidence.externalId(),
        normalizedEvidence.sourceMeta(),
        normalizedEvidence.evidenceText(),
        normalizedEvidence.sourceUrl());
  }
}
