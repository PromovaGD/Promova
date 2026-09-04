package br.com.promova.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.evidence.EvidenceStatus;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {
  @Mock private EvidenceRepository evidenceRepository;

  private EvidenceService evidenceService;
  private User owner;
  private User other;

  @BeforeEach
  void setUp() {
    evidenceService = new EvidenceService(evidenceRepository);
    owner = user(7L, "owner@example.com");
    other = user(8L, "other@example.com");
  }

  @Test
  void reusesAnExistingNaturalKeyWithoutCreatingAnotherRow() {
    Evidence existing = evidence(owner, 41L, EvidenceStatus.PENDING);
    when(evidenceRepository.findByUserIdAndSourceAndExternalId(
            7L, "GitHub", "github:acme/project#7"))
        .thenReturn(Optional.of(existing));

    var response =
        evidenceService.capture(
            owner,
            "GitHub",
            "github:acme/project#7",
            "new metadata",
            "new evidence",
            "https://example.test/pr/7");

    assertThat(response.id()).isEqualTo(41L);
    assertThat(response.status()).isEqualTo(EvidenceStatus.PENDING);
    verify(evidenceRepository, never()).save(any(Evidence.class));
  }

  @Test
  void createsPendingEvidenceWithNoHardCodedCareerLevels() {
    when(evidenceRepository.findByUserIdAndSourceAndExternalId(
            7L, "GitHub", "github:acme/project#7"))
        .thenReturn(Optional.empty());
    when(evidenceRepository.save(any(Evidence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        evidenceService.capture(
            owner,
            "GitHub",
            "github:acme/project#7",
            "PR #7 - acme/project",
            "Improved coverage",
            null);

    assertThat(response.status()).isEqualTo(EvidenceStatus.PENDING);
    assertThat(response.content()).isEqualTo("Improved coverage");
    assertThat(response.sourceUrl()).isNull();
    verify(evidenceRepository).save(any(Evidence.class));
  }

  @Test
  void dismissesOnlyPendingOwnedEvidenceAndRejectsInvalidTransitions() {
    Evidence pending = evidence(owner, 41L, EvidenceStatus.PENDING);
    when(evidenceRepository.findByIdAndUserIdForUpdate(41L, 7L)).thenReturn(Optional.of(pending));
    when(evidenceRepository.save(pending)).thenReturn(pending);

    assertThat(evidenceService.dismiss(owner, 41L).status()).isEqualTo(EvidenceStatus.DISMISSED);

    assertThatThrownBy(() -> evidenceService.dismiss(owner, 41L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void hidesAnotherUsersEvidenceForFetchAndDismiss() {
    when(evidenceRepository.findByIdAndUserId(41L, 8L)).thenReturn(Optional.empty());
    when(evidenceRepository.findByIdAndUserIdForUpdate(41L, 8L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> evidenceService.getForUser(other, 41L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThatThrownBy(() -> evidenceService.dismiss(other, 41L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void passesStatusAndDateFiltersToTheOwnerScopedRepositoryQuery() {
    Instant from = Instant.parse("2026-05-01T00:00:00Z");
    Instant to = Instant.parse("2026-05-31T23:59:59Z");
    when(evidenceRepository.findForUser(7L, EvidenceStatus.PENDING, from, to))
        .thenReturn(List.of());

    assertThat(evidenceService.listForUser(owner, "pending", from, to)).isEmpty();
    verify(evidenceRepository).findForUser(7L, EvidenceStatus.PENDING, from, to);
  }

  @Test
  void rejectsInvalidStatusAndReversedDateRange() {
    assertThatThrownBy(() -> evidenceService.listForUser(owner, "UNKNOWN", null, null))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThatThrownBy(
            () ->
                evidenceService.listForUser(
                    owner,
                    null,
                    Instant.parse("2026-06-01T00:00:00Z"),
                    Instant.parse("2026-05-01T00:00:00Z")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private User user(Long id, String email) {
    User user = new User("Employee", email, "hash", UserRole.EMPLOYEE);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private Evidence evidence(User user, Long id, EvidenceStatus status) {
    Evidence evidence =
        new Evidence(
            user,
            "GitHub",
            "github:acme/project#7",
            "PR #7 - acme/project",
            "Improved coverage",
            null,
            Instant.parse("2026-05-12T10:00:00Z"));
    ReflectionTestUtils.setField(evidence, "id", id);
    if (status == EvidenceStatus.DISMISSED) {
      evidence.dismiss();
    } else if (status == EvidenceStatus.ANALYZED) {
      evidence.markAnalyzed();
    }
    return evidence;
  }
}
