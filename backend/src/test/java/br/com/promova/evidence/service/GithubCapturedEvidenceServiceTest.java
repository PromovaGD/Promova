package br.com.promova.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.evidence.EvidenceStatus;
import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.github.adapter.GithubSourceAdapter;
import br.com.promova.source.NormalizedEvidence;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GithubCapturedEvidenceServiceTest {
  @Mock private GithubSourceAdapter githubSourceAdapter;
  @Mock private EvidenceService evidenceService;

  @Test
  void importsTheSamePullRequestOncePerOwnerAndReusesThePersistedEvidence() {
    User owner = user(7L);
    EvidenceResponse existing = response(41L, EvidenceStatus.PENDING);
    GithubCapturedEvidenceService service =
        new GithubCapturedEvidenceService(githubSourceAdapter, evidenceService);
    when(githubSourceAdapter.externalIdFor(any(String.class), eq(7)))
        .thenReturn("github:acme/project#7");
    when(evidenceService.findByNaturalKey(owner, "GitHub", "github:acme/project#7"))
        .thenReturn(Optional.empty(), Optional.of(existing));
    when(githubSourceAdapter.fetchPullRequest("acme/project", 7, "octocat"))
        .thenReturn(normalizedEvidence());
    when(evidenceService.capture(
            any(User.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class)))
        .thenReturn(existing);

    service.fromPullRequest(owner, "acme/project", 7, "octocat");
    EvidenceResponse second = service.fromPullRequest(owner, "ACME/PROJECT", 7, "other-login");

    assertThat(second).isSameAs(existing);
    verify(githubSourceAdapter, times(1))
        .fetchPullRequest("acme/project", 7, "octocat");
    verify(evidenceService, times(1))
        .capture(
            eq(owner),
            eq("GitHub"),
            eq("github:acme/project#7"),
            eq("PR #7 - acme/project"),
            any(String.class),
            eq("https://github.com/acme/project/pull/7"));
  }

  private User user(Long id) {
    User user = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private EvidenceResponse response(Long id, EvidenceStatus status) {
    Instant timestamp = Instant.parse("2026-05-12T10:00:00Z");
    return new EvidenceResponse(
        id,
        "GitHub",
        "github:acme/project#7",
        "PR #7 - acme/project",
        "Added tests",
        "https://github.com/acme/project/pull/7",
        timestamp,
        timestamp,
        status);
  }

  private NormalizedEvidence normalizedEvidence() {
    return new NormalizedEvidence(
        "GitHub",
        "github:acme/project#7",
        "PR #7 - acme/project",
        "Added tests",
        "https://github.com/acme/project/pull/7",
        "octocat",
        Instant.parse("2026-05-10T10:00:00Z"),
        Map.of("repository", "acme/project", "number", "7"));
  }
}
