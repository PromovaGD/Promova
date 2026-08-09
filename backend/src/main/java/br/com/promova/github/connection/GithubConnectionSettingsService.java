package br.com.promova.github.connection;

import br.com.promova.github.connection.dto.GithubSettingsRequest;
import br.com.promova.github.connection.dto.GithubSettingsResponse;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.user.User;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GithubConnectionSettingsService {
  private static final String LOGIN_PATTERN = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})";

  private final GithubConnectionSettingsRepository settingsRepository;
  private final GithubPullRequestService githubPullRequestService;

  public GithubConnectionSettingsService(
      GithubConnectionSettingsRepository settingsRepository,
      GithubPullRequestService githubPullRequestService) {
    this.settingsRepository = settingsRepository;
    this.githubPullRequestService = githubPullRequestService;
  }

  @Transactional(readOnly = true)
  public GithubSettingsResponse getForUser(User user) {
    return settingsRepository
        .findByUserId(user.getId())
        .map(GithubSettingsResponse::from)
        .orElseGet(GithubSettingsResponse::unconfigured);
  }

  @Transactional
  public GithubSettingsResponse updateForUser(User user, GithubSettingsRequest request) {
    String repoSlug = normalizeRepository(request.repoSlug());
    String authorLogin = normalizeAuthorLogin(request.authorLogin());

    GithubConnectionSettings settings =
        settingsRepository
            .findByUserId(user.getId())
            .orElseGet(() -> new GithubConnectionSettings(user));
    settings.configure(repoSlug, authorLogin);
    return GithubSettingsResponse.from(settingsRepository.save(settings));
  }

  @Transactional(readOnly = true)
  public GithubConnectionSettings requireConfigured(User user) {
    GithubConnectionSettings settings =
        settingsRepository
            .findByUserId(user.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Save a GitHub repository and author login first"));
    if (settings.getRepoSlug() == null || settings.getAuthorLogin() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Save a GitHub repository and author login first");
    }
    return settings;
  }

  @Transactional
  public void recordSyncOutcome(User user, Instant syncAt, String outcome) {
    GithubConnectionSettings settings =
        settingsRepository
            .findByUserId(user.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Save a GitHub repository and author login first"));
    settings.recordSync(syncAt, outcome);
    settingsRepository.save(settings);
  }

  public String normalizeRepository(String repoSlug) {
    if (repoSlug == null || repoSlug.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Repository must be in owner/repo format");
    }

    String normalized = repoSlug.trim();
    String[] parts = normalized.split("/", -1);
    if (parts.length != 2) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Repository must be in owner/repo format");
    }
    githubPullRequestService.validateRepository(parts[0], parts[1]);
    return parts[0] + "/" + parts[1];
  }

  private String normalizeAuthorLogin(String authorLogin) {
    String normalized = authorLogin == null ? "" : authorLogin.trim();
    if (!normalized.matches(LOGIN_PATTERN)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "authorLogin must be a valid GitHub login");
    }
    return normalized;
  }
}
