package br.com.promova.evidence.controller;

import br.com.promova.analysis.dto.SavedAnalysisResponse;
import br.com.promova.analysis.service.EvidenceAnalysisService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.evidence.dto.GithubPullRequestCaptureRequest;
import br.com.promova.evidence.service.EvidenceService;
import br.com.promova.evidence.service.GithubCapturedEvidenceService;
import br.com.promova.user.User;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/evidences")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class CapturedEvidenceController {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final EvidenceService evidenceService;
  private final GithubCapturedEvidenceService githubCapturedEvidenceService;
  private final EvidenceAnalysisService evidenceAnalysisService;

  public CapturedEvidenceController(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      EvidenceService evidenceService,
      GithubCapturedEvidenceService githubCapturedEvidenceService,
      EvidenceAnalysisService evidenceAnalysisService) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.evidenceService = evidenceService;
    this.githubCapturedEvidenceService = githubCapturedEvidenceService;
    this.evidenceAnalysisService = evidenceAnalysisService;
  }

  @GetMapping
  public List<EvidenceResponse> list(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    return evidenceService.listForUser(requireUser(authorization), status, from, to);
  }

  @GetMapping("/{id}")
  public EvidenceResponse get(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long id) {
    return evidenceService.getForUser(requireUser(authorization), id);
  }

  @PostMapping("/{id}/dismiss")
  public EvidenceResponse dismiss(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long id) {
    return evidenceService.dismiss(requireUser(authorization), id);
  }

  /**
   * Requests the server-owned analysis and persistence transition. The endpoint intentionally has
   * no request-body parameter: evidence, profile levels, timestamps, and classification output
   * are loaded or produced on the server.
   */
  @PostMapping("/{id}/analysis")
  public SavedAnalysisResponse analyze(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long id) {
    return evidenceAnalysisService.analyzeOwnedEvidence(requireUser(authorization), id);
  }

  @PostMapping("/github/pull-request")
  public EvidenceResponse captureGithubPullRequest(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody GithubPullRequestCaptureRequest request) {
    return fromGithubPullRequest(
        requireUser(authorization), request.repo(), request.pullNumber(), request.usernameHint());
  }

  /** Compatibility wrapper for clients that still use the original GET capture route. */
  @GetMapping("/github/pull-request")
  public EvidenceResponse captureGithubPullRequestCompatibility(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam String repo,
      @RequestParam int pullNumber,
      @RequestParam(required = false) String usernameHint) {
    return fromGithubPullRequest(requireUser(authorization), repo, pullNumber, usernameHint);
  }

  private EvidenceResponse fromGithubPullRequest(
      User user, String repo, int pullNumber, String usernameHint) {
    return githubCapturedEvidenceService.fromPullRequest(user, repo, pullNumber, usernameHint);
  }

  private User requireUser(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    return authService.requireUser(token);
  }
}
