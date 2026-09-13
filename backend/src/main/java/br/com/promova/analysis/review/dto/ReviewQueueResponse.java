package br.com.promova.analysis.review.dto;

import br.com.promova.analysis.review.ReviewStatus;
import java.util.List;
import java.util.Map;

public record ReviewQueueResponse(
    List<ReviewQueueItemResponse> items,
    int page,
    int pageSize,
    long total,
    Map<ReviewStatus, Long> counts) {
  public ReviewQueueResponse {
    items = items == null ? List.of() : List.copyOf(items);
    counts = counts == null ? Map.of() : Map.copyOf(counts);
  }
}
