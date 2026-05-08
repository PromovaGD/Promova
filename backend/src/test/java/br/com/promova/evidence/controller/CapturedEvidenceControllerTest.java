package br.com.promova.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.evidence.service.CapturedEvidenceService;
import br.com.promova.evidence.service.GithubCapturedEvidenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CapturedEvidenceController.class)
@Import(CapturedEvidenceService.class)
class CapturedEvidenceControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private GithubCapturedEvidenceService githubCapturedEvidenceService;

  @Test
  void returnsNextCapturedEvidence() throws Exception {
    mockMvc
        .perform(get("/evidences/next").param("cursor", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("jira-prom-218"))
        .andExpect(jsonPath("$.source").value("Jira"))
        .andExpect(jsonPath("$.nextCursor").value(2));
  }
}
