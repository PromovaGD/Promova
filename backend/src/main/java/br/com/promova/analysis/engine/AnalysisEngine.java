package br.com.promova.analysis.engine;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.framework.CareerFramework;

public interface AnalysisEngine {
  EvidenceAnalysisResponse analyze(EvidenceAnalysisRequest request, CareerFramework careerFramework);
}
