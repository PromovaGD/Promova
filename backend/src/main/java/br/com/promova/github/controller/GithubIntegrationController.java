package br.com.promova.github.controller;

import br.com.promova.github.dto.GithubPullRequestBundle;
import br.com.promova.github.dto.GithubPullRequestSearchResponse;
import br.com.promova.github.dto.GithubPullSummary;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.github.support.GithubApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class GithubIntegrationController {
  private final GithubPullRequestService githubPullRequestService;

  public GithubIntegrationController(GithubPullRequestService githubPullRequestService) {
    this.githubPullRequestService = githubPullRequestService;
  }

  @GetMapping("/api/github/repos/{owner}/{repo}/pulls")
  public List<GithubPullSummary> listPullRequests(
      @PathVariable String owner,
      @PathVariable String repo,
      @RequestParam(defaultValue = "open") String state,
      @RequestParam(name = "per_page", defaultValue = "10") int perPage,
      @RequestParam(defaultValue = "1") int page) {
    return githubPullRequestService.listPullRequests(owner, repo, state, perPage, page);
  }

  @GetMapping("/api/github/repos/{owner}/{repo}/pulls/{number}")
  public GithubPullRequestBundle pullRequestDetails(
      @PathVariable String owner, @PathVariable String repo, @PathVariable int number) {
    return githubPullRequestService.pullRequestDetails(owner, repo, number);
  }

  @GetMapping("/api/github/repos/{owner}/{repo}/pulls/search")
  public GithubPullRequestSearchResponse searchPullRequests(
      @PathVariable String owner,
      @PathVariable String repo,
      @RequestParam String q,
      @RequestParam(name = "per_page", defaultValue = "10") int perPage,
      @RequestParam(defaultValue = "1") int page) {
    return githubPullRequestService.searchPullRequests(owner, repo, q, perPage, page);
  }

  @ExceptionHandler(GithubApiException.class)
  ResponseEntity<Map<String, Object>> handleGithubError(GithubApiException exception) {
    JsonNode body = exception.responseBody();
    return ResponseEntity.status(exception.statusCode())
        .body(Map.of("error", exception.getMessage(), "github_response", body));
  }
}
