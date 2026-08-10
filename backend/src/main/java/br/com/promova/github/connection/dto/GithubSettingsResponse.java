package br.com.promova.github.connection.dto;

import br.com.promova.github.connection.GithubConnectionSettings;
import java.time.Instant;

public record GithubSettingsResponse(
    boolean configured,
    String repoSlug,
    String authorLogin,
    Instant lastSyncAt,
    String lastSyncOutcome) {
  public static GithubSettingsResponse from(GithubConnectionSettings settings) {
    return new GithubSettingsResponse(
        settings.getRepoSlug() != null && settings.getAuthorLogin() != null,
        settings.getRepoSlug(),
        settings.getAuthorLogin(),
        settings.getLastSyncAt(),
        settings.getLastSyncOutcome());
  }

  public static GithubSettingsResponse unconfigured() {
    return new GithubSettingsResponse(
        false,
        "",
        "",
        null,
        GithubConnectionSettings.OUTCOME_NOT_CONFIGURED);
  }
}
