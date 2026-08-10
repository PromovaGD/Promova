package br.com.promova.github.connection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GithubSettingsRequest(
    @NotBlank(message = "repoSlug is required") @Size(max = 255, message = "repoSlug is too long")
        String repoSlug,
    @NotBlank(message = "authorLogin is required")
        @Size(max = 39, message = "authorLogin is too long")
        String authorLogin) {}
