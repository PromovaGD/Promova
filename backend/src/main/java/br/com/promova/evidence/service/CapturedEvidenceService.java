package br.com.promova.evidence.service;

import br.com.promova.evidence.dto.CapturedEvidenceResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CapturedEvidenceService {
  private static final List<CapturedEvidenceResponse> CAPTURED_EVIDENCES =
      List.of(
          new CapturedEvidenceResponse(
              "github-pr-1842",
              "GitHub",
              "PR #1842 · payments-service",
              "Refactored payment module and increased test coverage to 85%",
              "L3",
              "L4",
              1),
          new CapturedEvidenceResponse(
              "jira-prom-218",
              "Jira",
              "PROM-218 · Sprint 14",
              "Optimized backend service latency and reduced incident response time",
              "L3",
              "L4",
              2),
          new CapturedEvidenceResponse(
              "slack-platform-rollout",
              "Slack",
              "#eng-platform · thread resumida",
              "Mentored two developers during rollout and helped team fix production bugs",
              "L3",
              "L4",
              0));

  public CapturedEvidenceResponse next(int cursor) {
    int normalizedCursor = Math.floorMod(cursor, CAPTURED_EVIDENCES.size());
    return CAPTURED_EVIDENCES.get(normalizedCursor);
  }
}
