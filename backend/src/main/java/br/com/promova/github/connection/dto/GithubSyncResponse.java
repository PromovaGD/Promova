package br.com.promova.github.connection.dto;

import java.time.Instant;

public record GithubSyncResponse(
    String repoSlug,
    String authorLogin,
    int discovered,
    int created,
    int existing,
    int failed,
    Instant lastSyncAt,
    String lastSyncOutcome) {}
