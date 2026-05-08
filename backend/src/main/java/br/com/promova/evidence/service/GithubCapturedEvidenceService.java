package br.com.promova.evidence.service;

import br.com.promova.evidence.dto.CapturedEvidenceResponse;
import br.com.promova.github.dto.GithubPullRequestBundle;
import br.com.promova.github.dto.GithubPullSummary;
import br.com.promova.github.service.GithubPullRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GithubCapturedEvidenceService {
  private final GithubPullRequestService githubPullRequestService;

  public GithubCapturedEvidenceService(GithubPullRequestService githubPullRequestService) {
    this.githubPullRequestService = githubPullRequestService;
  }

  public CapturedEvidenceResponse fromPullRequest(
      String repoSlug, int pullNumber, String usernameHint) {
    RepositorySlug repositorySlug = parseRepositorySlug(repoSlug);
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

    return new CapturedEvidenceResponse(
        "github-%s-%s-pr-%d"
            .formatted(repositorySlug.owner(), repositorySlug.repo(), pullRequest.number()),
        "GitHub",
        "PR #%d - %s".formatted(pullRequest.number(), bundle.repository()),
        evidence,
        "L3",
        "L4",
        0);
  }

  private RepositorySlug parseRepositorySlug(String repoSlug) {
    if (repoSlug == null || !repoSlug.contains("/")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository must be in owner/repo format");
    }

    String[] parts = repoSlug.trim().split("/", 2);
    githubPullRequestService.validateRepository(parts[0], parts[1]);
    return new RepositorySlug(parts[0], parts[1]);
  }

  private record RepositorySlug(String owner, String repo) {}
}
