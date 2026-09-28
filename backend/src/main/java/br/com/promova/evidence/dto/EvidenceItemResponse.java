package br.com.promova.evidence.dto;

import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Arrays;

/** Canonical evidence representation used by paged and detail contracts. */
public record EvidenceItemResponse(
    Long id,
    Long ownerId,
    String source,
    String externalId,
    String sourceMeta,
    String content,
    String sourceUrl,
    Instant occurredAt,
    Instant capturedAt,
    Instant updatedAt,
    EvidenceStatus status,
    Long analysisId,
    String analysisTitle,
    String author,
    Instant mergedAt,
    List<String> tags) {

  public static EvidenceItemResponse from(Evidence evidence, Long analysisId, String analysisTitle) {
    return new EvidenceItemResponse(
        evidence.getId(),
        evidence.getUser().getId(),
        evidence.getSource(),
        evidence.getExternalId(),
        evidence.getSourceMeta(),
        evidence.getContent(),
        evidence.getSourceUrl(),
        evidence.getOccurredAt(),
        evidence.getCapturedAt(),
        evidence.getUpdatedAt(),
        evidence.getStatus(),
        analysisId,
        analysisTitle,
        evidence.getSourceAuthor(),
        evidence.getSourceMergedAt(),
        evidence.getSourceLabels() == null ? List.of() : Arrays.stream(evidence.getSourceLabels().split(","))
            .map(String::trim).filter(label -> !label.isEmpty()).toList());
  }
}
