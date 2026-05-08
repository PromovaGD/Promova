package br.com.promova.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GithubPullRequestBundle(
    String repository,
    @JsonProperty("pull_request") GithubPullSummary pullRequest,
    @JsonProperty("changed_files_count") int changedFilesCount,
    int additions,
    int deletions,
    int changes,
    List<GithubFilePatch> files) {}
