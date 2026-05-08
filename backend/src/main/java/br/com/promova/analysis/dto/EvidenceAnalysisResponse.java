package br.com.promova.analysis.dto;

import br.com.promova.analysis.engine.Confidence;
import java.util.List;

public record EvidenceAnalysisResponse(
    String estimatedLevel,
    Confidence confidence,
    String reasoning,
    List<String> competencies,
    List<String> suggestions) {}
