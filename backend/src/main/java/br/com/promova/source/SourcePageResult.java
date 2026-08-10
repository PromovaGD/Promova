package br.com.promova.source;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Result of one bounded adapter page.
 *
 * <p>{@code failedItems} counts malformed or otherwise unnormalizable provider items that were
 * isolated from the rest of the page. {@code oldestObservedAt} is the oldest timestamp observed
 * in the provider page, including items filtered out of {@code items}; it lets a sync stop at the
 * lookback boundary without making the contract provider-specific.
 */
public record SourcePageResult(
    List<NormalizedEvidence> items,
    int failedItems,
    boolean hasPotentialNextPage,
    Instant oldestObservedAt) {
  public SourcePageResult {
    List<NormalizedEvidence> safeItems =
        items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
    int nullItems = items == null ? 0 : (int) items.stream().filter(Objects::isNull).count();
    if (failedItems < 0) {
      throw new IllegalArgumentException("failedItems cannot be negative");
    }
    items = safeItems;
    failedItems += nullItems;
  }

  /** Convenience constructor for adapters that do not need a separate page-age marker. */
  public SourcePageResult(
      List<NormalizedEvidence> items, int failedItems, boolean hasPotentialNextPage) {
    this(items, failedItems, hasPotentialNextPage, oldestItem(items));
  }

  /** Compatibility alias for callers that describe malformed provider payloads explicitly. */
  public int malformedItems() {
    return failedItems;
  }

  private static Instant oldestItem(List<NormalizedEvidence> items) {
    if (items == null) {
      return null;
    }
    return items.stream()
        .filter(Objects::nonNull)
        .map(NormalizedEvidence::occurredAt)
        .filter(Objects::nonNull)
        .min(Instant::compareTo)
        .orElse(null);
  }
}
