package br.com.promova.source;

import java.time.Instant;

/**
 * Provider-neutral inputs for a bounded source discovery page.
 *
 * <p>{@code scope} is intentionally opaque to the contract. A GitHub adapter may interpret it as
 * {@code owner/repository}; a future Jira or Slack adapter can interpret it as its own configured
 * project/workspace scope without changing this interface or selecting an auth model here.
 */
public record SourceAdapterRequest(
    String scope, String author, Instant occurredAfter, int pageSize, int page) {
  public SourceAdapterRequest {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("scope is required");
    }
    scope = scope.trim();
    author = author == null || author.isBlank() ? null : author.trim();
    pageSize = pageSize < 1 ? 1 : Math.min(pageSize, 100);
    page = Math.max(page, 1);
  }
}
