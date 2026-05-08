package br.com.promova.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GithubPullRequestSearchResponse(
    @JsonProperty("total_count") int totalCount,
    @JsonProperty("incomplete_results") boolean incompleteResults,
    List<GithubPullSummary> items) {}
