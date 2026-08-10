package br.com.promova.github.dto;

import java.util.List;

/** A single explicitly requested page from GitHub's pull-request listing. */
public record GithubPullRequestPage(
    List<GithubPullSummary> pullRequests, int malformedItems, boolean hasPotentialNextPage) {}
