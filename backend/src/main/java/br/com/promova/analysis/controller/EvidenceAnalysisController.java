package br.com.promova.analysis.controller;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.analysis.service.EvidenceAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class EvidenceAnalysisController {
  private final EvidenceAnalysisService evidenceAnalysisService;

  public EvidenceAnalysisController(EvidenceAnalysisService evidenceAnalysisService) {
    this.evidenceAnalysisService = evidenceAnalysisService;
  }

  @PostMapping("/analyze")
  @ResponseStatus(HttpStatus.OK)
  public EvidenceAnalysisResponse analyze(@Valid @RequestBody EvidenceAnalysisRequest request) {
    return evidenceAnalysisService.analyze(request);
  }
}
