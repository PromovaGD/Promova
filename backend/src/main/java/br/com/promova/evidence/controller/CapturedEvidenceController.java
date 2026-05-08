package br.com.promova.evidence.controller;

import br.com.promova.evidence.dto.CapturedEvidenceResponse;
import br.com.promova.evidence.service.CapturedEvidenceService;
import br.com.promova.evidence.service.GithubCapturedEvidenceService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class CapturedEvidenceController {
  private final CapturedEvidenceService capturedEvidenceService;
  private final GithubCapturedEvidenceService githubCapturedEvidenceService;

  public CapturedEvidenceController(
      CapturedEvidenceService capturedEvidenceService,
      GithubCapturedEvidenceService githubCapturedEvidenceService) {
    this.capturedEvidenceService = capturedEvidenceService;
    this.githubCapturedEvidenceService = githubCapturedEvidenceService;
  }

  @GetMapping("/evidences/next")
  public CapturedEvidenceResponse next(@RequestParam(defaultValue = "0") int cursor) {
    return capturedEvidenceService.next(cursor);
  }

  @GetMapping("/evidences/github/pull-request")
  public CapturedEvidenceResponse fromGithubPullRequest(
      @RequestParam String repo,
      @RequestParam int pullNumber,
      @RequestParam(required = false) String usernameHint) {
    return githubCapturedEvidenceService.fromPullRequest(repo, pullNumber, usernameHint);
  }
}
