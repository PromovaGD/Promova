package br.com.promova.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NormalizedEvidenceContractTest {
  @Test
  void keepsTraceabilityMetadataWhileRedactingCredentialMaterial() {
    String secret = "server-secret-that-must-not-leak";
    NormalizedEvidence normalizedEvidence =
        new NormalizedEvidence(
            "GitHub",
            "github:acme/project#7",
            "PR #7 - acme/project",
            "Description with Bearer " + secret,
            "https://github.com/acme/project/pull/7?access_token=" + secret,
            "octocat",
            Instant.parse("2026-08-08T10:00:00Z"),
            Map.of(
                "repository", "acme/project",
                "number", "7",
                "accessToken", secret,
                "notes", "token=" + secret));

    String serialized = normalizedEvidence.toString();

    assertThat(normalizedEvidence.providerMetadata())
        .containsEntry("repository", "acme/project")
        .containsEntry("number", "7")
        .doesNotContainKey("accessToken");
    assertThat(normalizedEvidence.sourceUrl()).isEqualTo("https://github.com/acme/project/pull/7");
    assertThat(serialized).doesNotContain(secret);
    assertThat(normalizedEvidence.evidenceText()).contains("[REDACTED]");
  }
}
