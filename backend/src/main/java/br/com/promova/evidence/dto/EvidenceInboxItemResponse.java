package br.com.promova.evidence.dto;

import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Lightweight, source-derived inbox item. Full captured text remains on GET /evidences/{id}. */
public record EvidenceInboxItemResponse(
    Long id, String source, String title, String summary, String origin,
    Instant occurredAt, Instant capturedAt, EvidenceStatus status,
    String sourceUrl, Long analysisId, String currentReviewStatus,
    String author, Instant mergedAt, List<String> tags) {

  public static EvidenceInboxItemResponse from(Evidence evidence, Long analysisId, String reviewStatus) {
    String content = evidence.getContent();
    String title = null;
    String summary = null;
    boolean readingDescription = false;
    for (String line : content.split("\\R")) {
      String value = line.trim();
      if (title == null && value.matches("(?i)^t[ií]tulo\\s*:.*")) title = value.replaceFirst("^[^:]*:\\s*", "");
      if (summary == null && value.matches("(?i)^descri[cç][aã]o/resumo\\s*:.*")) {
        summary = value.replaceFirst("^[^:]*:\\s*", "");
        readingDescription = summary.isBlank();
      } else if (readingDescription && !value.isBlank()) {
        summary = value;
        readingDescription = false;
      }
    }
    if (title == null || title.isBlank()) title = evidence.getSourceMeta();
    if (summary == null || summary.isBlank()) summary = "Sem resumo na fonte.";
    List<String> tags = evidence.getSourceLabels() == null ? List.of()
        : Arrays.stream(evidence.getSourceLabels().split(",")).map(String::trim)
            .filter(tag -> !tag.isEmpty()).toList();
    return new EvidenceInboxItemResponse(evidence.getId(), evidence.getSource(), clip(title, 120),
        clip(summary, 240), evidence.getSourceMeta(), evidence.getOccurredAt(),
        evidence.getCapturedAt(), evidence.getStatus(), evidence.getSourceUrl(), analysisId,
        reviewStatus, evidence.getSourceAuthor(), evidence.getSourceMergedAt(), tags);
  }

  private static String clip(String value, int length) {
    String compact = value.replaceAll("\\s+", " ").trim();
    return compact.length() <= length ? compact : compact.substring(0, length - 1) + "…";
  }
}
