package br.com.promova.analysis.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import br.com.promova.analysis.engine.MockAnalysisEngine;
import br.com.promova.analysis.service.EvidenceAnalysisService;
import br.com.promova.framework.MockFrameworkProvider;

@WebMvcTest(EvidenceAnalysisController.class)
@Import({EvidenceAnalysisService.class, MockAnalysisEngine.class, MockFrameworkProvider.class})
class EvidenceAnalysisControllerTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void analyzesEvidence() throws Exception {
    mockMvc
        .perform(
            post("/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "evidence": "Refactored payment module and increased test coverage",
                      "currentLevel": "L3",
                      "targetLevel": "L4"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimatedLevel").value("L4"))
        .andExpect(jsonPath("$.confidence").value("medium"))
        .andExpect(jsonPath("$.competencies", containsInAnyOrder("Code Quality", "Ownership")));
  }

  @Test
  void rejectsBlankEvidence() throws Exception {
    mockMvc
        .perform(
            post("/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "evidence": "",
                      "currentLevel": "L3",
                      "targetLevel": "L4"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }
}
