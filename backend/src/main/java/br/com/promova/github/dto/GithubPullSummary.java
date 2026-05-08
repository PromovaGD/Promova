package br.com.promova.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GithubPullSummary(
    int number,
    String title,
    String state,
    boolean draft,
    boolean locked,
    @JsonProperty("merged_at") String mergedAt,
    @JsonProperty("closed_at") String closedAt,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("author_login") String authorLogin,
    @JsonProperty("head_ref") String headRef,
    @JsonProperty("base_ref") String baseRef,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("updated_at") String updatedAt,
    List<String> labels,
    @JsonProperty("body_preview") String bodyPreview) {}
