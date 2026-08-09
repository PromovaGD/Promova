package br.com.promova.evidence.dto;

import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceStatus;
import java.time.Instant;

public record EvidenceResponse(
    Long id,
    String source,
    String externalId,
    String sourceMeta,
    String evidence,
    String sourceUrl,
    Instant capturedAt,
    Instant updatedAt,
    EvidenceStatus status) {
  public static EvidenceResponse from(Evidence evidence) {
    return new EvidenceResponse(
        evidence.getId(),
        evidence.getSource(),
        evidence.getExternalId(),
        evidence.getSourceMeta(),
        evidence.getEvidence(),
        evidence.getSourceUrl(),
        evidence.getCapturedAt(),
        evidence.getUpdatedAt(),
        evidence.getStatus());
  }
}
