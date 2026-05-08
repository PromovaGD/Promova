package br.com.promova.analysis.service;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.analysis.engine.AnalysisEngine;
import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.FrameworkProvider;
import org.springframework.stereotype.Service;

@Service
public class EvidenceAnalysisService {
  private final FrameworkProvider frameworkProvider;
  private final AnalysisEngine analysisEngine;

  public EvidenceAnalysisService(
      FrameworkProvider frameworkProvider, AnalysisEngine analysisEngine) {
    this.frameworkProvider = frameworkProvider;
    this.analysisEngine = analysisEngine;
  }

  public EvidenceAnalysisResponse analyze(EvidenceAnalysisRequest request) {
    CareerFramework careerFramework = frameworkProvider.load();
    return analysisEngine.analyze(request, careerFramework);
  }
}
