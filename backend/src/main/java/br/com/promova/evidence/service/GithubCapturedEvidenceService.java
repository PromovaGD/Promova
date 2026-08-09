package br.com.promova.evidence.service;

import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.github.dto.GithubPullRequestBundle;
import br.com.promova.github.dto.GithubPullSummary;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.user.User;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GithubCapturedEvidenceService {
  private final GithubPullRequestService githubPullRequestService;
  private final EvidenceService evidenceService;

  public GithubCapturedEvidenceService(
      GithubPullRequestService githubPullRequestService, EvidenceService evidenceService) {
    this.githubPullRequestService = githubPullRequestService;
    this.evidenceService = evidenceService;
  }

  public EvidenceResponse fromPullRequest(
      User user, String repoSlug, int pullNumber, String usernameHint) {
    RepositorySlug repositorySlug = parseRepositorySlug(repoSlug);
    if (pullNumber < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request number must be positive");
    }

    String externalId = externalId(repositorySlug, pullNumber);
    var existing = evidenceService.findByNaturalKey(user, "GitHub", externalId);
    if (existing.isPresent()) {
      return existing.get();
    }

    GithubPullRequestBundle bundle =
        githubPullRequestService.pullRequestDetails(
            repositorySlug.owner(), repositorySlug.repo(), pullNumber);
    GithubPullSummary pullRequest = bundle.pullRequest();
    String profileLine =
        usernameHint == null || usernameHint.isBlank()
            ? "Contexto/perfil relacionado a leitura: " + pullRequest.authorLogin()
            : "Contexto/perfil relacionado a leitura: " + usernameHint.trim();
    String bodyPreview =
        pullRequest.bodyPreview() == null || pullRequest.bodyPreview().isBlank()
            ? "Sem descricao no PR."
            : pullRequest.bodyPreview();

    String evidence =
        String.join(
            "\n\n",
            "GitHub - repositorio %s - PR #%d".formatted(bundle.repository(), pullRequest.number()),
            profileLine,
            "Titulo: " + pullRequest.title(),
            "Volume coletado via API (+%d -%d linhas, %d arquivo(s))."
                .formatted(bundle.additions(), bundle.deletions(), bundle.changedFilesCount()),
            "Descricao/resumo:\n" + bodyPreview,
            "Link publico do PR: " + pullRequest.htmlUrl(),
            "Leitura preparada automaticamente para revisao no Promova.");

    return evidenceService.capture(
        user,
        "GitHub",
        externalId,
        "PR #%d - %s".formatted(pullRequest.number(), bundle.repository()),
        evidence,
        pullRequest.htmlUrl());
  }

  private RepositorySlug parseRepositorySlug(String repoSlug) {
    if (repoSlug == null || repoSlug.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository must be in owner/repo format");
    }

    String[] parts = repoSlug.trim().split("/", -1);
    if (parts.length != 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository must be in owner/repo format");
    }
    githubPullRequestService.validateRepository(parts[0], parts[1]);
    return new RepositorySlug(parts[0], parts[1]);
  }

  private String externalId(RepositorySlug repositorySlug, int pullNumber) {
    return "github:%s/%s#%d"
        .formatted(
            repositorySlug.owner().toLowerCase(Locale.ROOT),
            repositorySlug.repo().toLowerCase(Locale.ROOT),
            pullNumber);
  }

  private record RepositorySlug(String owner, String repo) {}
}
