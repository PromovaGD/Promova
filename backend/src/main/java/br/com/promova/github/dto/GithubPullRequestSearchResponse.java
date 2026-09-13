package br.com.promova.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GithubPullRequestSearchResponse(
    @JsonProperty("total_count") int totalCount,
    @JsonProperty("incomplete_results") boolean incompleteResults,
    List<GithubPullSummary> items,
    int page,
    @JsonProperty("page_size") int pageSize) {
  public GithubPullRequestSearchResponse(
      int totalCount, boolean incompleteResults, List<GithubPullSummary> items) {
    this(totalCount, incompleteResults, items, 1, items == null ? 0 : items.size());
  }
}
