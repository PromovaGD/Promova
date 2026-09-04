package br.com.promova.evidence.dto;

import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceStatus;
import java.time.Instant;

public record EvidenceResponse(
    Long id,
    String source,
    String externalId,
    String sourceMeta,
    String content,
    String sourceUrl,
    Instant occurredAt,
    Instant capturedAt,
    Instant updatedAt,
    EvidenceStatus status) {
  public EvidenceResponse(
      Long id,
      String source,
      String externalId,
      String sourceMeta,
      String content,
      String sourceUrl,
      Instant capturedAt,
      Instant updatedAt,
      EvidenceStatus status) {
    this(
        id,
        source,
        externalId,
        sourceMeta,
        content,
        sourceUrl,
        capturedAt,
        capturedAt,
        updatedAt,
        status);
  }

  public static EvidenceResponse from(Evidence evidence) {
    return new EvidenceResponse(
        evidence.getId(),
        evidence.getSource(),
        evidence.getExternalId(),
        evidence.getSourceMeta(),
        evidence.getContent(),
        evidence.getSourceUrl(),
        evidence.getOccurredAt(),
        evidence.getCapturedAt(),
        evidence.getUpdatedAt(),
        evidence.getStatus());
  }
}
