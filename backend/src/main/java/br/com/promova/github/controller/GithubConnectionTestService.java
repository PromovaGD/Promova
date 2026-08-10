package br.com.promova.github.controller;

import br.com.promova.github.connection.GithubConnectionSettings;
import br.com.promova.github.connection.GithubConnectionSettingsService;
import br.com.promova.github.connection.dto.GithubConnectionTestResponse;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.user.User;
import org.springframework.stereotype.Service;

@Service
public class GithubConnectionTestService {
  private final GithubConnectionSettingsService settingsService;
  private final GithubPullRequestService githubPullRequestService;

  public GithubConnectionTestService(
      GithubConnectionSettingsService settingsService,
      GithubPullRequestService githubPullRequestService) {
    this.settingsService = settingsService;
    this.githubPullRequestService = githubPullRequestService;
  }

  public GithubConnectionTestResponse test(User user) {
    GithubConnectionSettings settings = settingsService.requireConfigured(user);
    String[] parts = settings.getRepoSlug().split("/", -1);
    githubPullRequestService.verifyRepositoryAccess(parts[0], parts[1]);
    return new GithubConnectionTestResponse(
        true,
        settings.getRepoSlug(),
        settings.getAuthorLogin(),
        "GitHub repository access verified with the configured server token");
  }
}
