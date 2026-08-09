package br.com.promova.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class EvidenceRepositoryTest {
  @Autowired private EvidenceRepository evidenceRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void persistsLifecycleFieldsAndReloadsAcrossEntityManagerBoundaries() {
    User owner = user("owner@example.com");
    Evidence evidence =
        evidenceRepository.save(
            new Evidence(
                owner,
                "GitHub",
                "github:acme/project#7",
                "PR #7 - acme/project",
                "Improved coverage",
                "https://github.com/acme/project/pull/7",
                Instant.parse("2026-05-12T10:00:00Z")));

    assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.PENDING);
    evidenceRepository.flush();

    Evidence reloaded = evidenceRepository.findById(evidence.getId()).orElseThrow();

    assertThat(reloaded.getUser().getId()).isEqualTo(owner.getId());
    assertThat(reloaded.getExternalId()).isEqualTo("github:acme/project#7");
    assertThat(reloaded.getSourceUrl()).isEqualTo("https://github.com/acme/project/pull/7");
    assertThat(reloaded.getCapturedAt()).isEqualTo(Instant.parse("2026-05-12T10:00:00Z"));
    assertThat(reloaded.getUpdatedAt()).isEqualTo(reloaded.getCapturedAt());
  }

  @Test
  void enforcesOwnerSourceExternalIdUniqueness() {
    User owner = user("owner@example.com");
    evidenceRepository.save(
        evidence(owner, "GitHub", "github:acme/project#7", "2026-05-12T10:00:00Z"));
    evidenceRepository.flush();

    assertThatThrownBy(
            () -> {
              evidenceRepository.save(
                  evidence(owner, "GitHub", "github:acme/project#7", "2026-05-13T10:00:00Z"));
              evidenceRepository.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void scopesStatusAndDateQueriesToTheOwner() {
    User owner = user("owner@example.com");
    User other = user("other@example.com");
    Evidence pendingInRange =
        evidence(owner, "GitHub", "github:acme/project#7", "2026-05-12T10:00:00Z");
    Evidence dismissedInRange =
        evidence(owner, "Slack", "slack:thread-2", "2026-05-13T10:00:00Z");
    dismissedInRange.dismiss();
    evidenceRepository.save(pendingInRange);
    evidenceRepository.save(dismissedInRange);
    evidenceRepository.save(
        evidence(owner, "GitHub", "github:acme/project#8", "2026-06-01T10:00:00Z"));
    evidenceRepository.save(
        evidence(other, "GitHub", "github:acme/project#9", "2026-05-12T10:00:00Z"));
    evidenceRepository.flush();

    List<Evidence> results =
        evidenceRepository.findForUser(
            owner.getId(),
            EvidenceStatus.PENDING,
            Instant.parse("2026-05-01T00:00:00Z"),
            Instant.parse("2026-05-31T23:59:59Z"));

    assertThat(results).extracting(Evidence::getExternalId).containsExactly("github:acme/project#7");
  }

  private User user(String email) {
    return userRepository.save(new User("Employee", email, "hash", UserRole.EMPLOYEE));
  }

  private Evidence evidence(User owner, String source, String externalId, String capturedAt) {
    return new Evidence(
        owner,
        source,
        externalId,
        "source metadata",
        "some evidence",
        null,
        Instant.parse(capturedAt));
  }
}
