package br.com.promova.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable JSON envelope for opt-in paginated API reads. Page numbers are one-based. */
public record PageResponse<T>(List<T> items, int page, int pageSize, long total) {
  public PageResponse {
    items = items == null ? List.of() : List.copyOf(items);
    if (page < 1) {
      throw new IllegalArgumentException("page must be at least 1");
    }
    if (pageSize < 1 || pageSize > 50) {
      throw new IllegalArgumentException("pageSize must be between 1 and 50");
    }
  }

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements());
  }
}
