package br.com.promova.evidence.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GithubPullRequestCaptureRequest(
    @NotBlank(message = "repo is required") String repo,
    @Min(value = 1, message = "pullNumber must be positive") int pullNumber,
    String usernameHint) {}
