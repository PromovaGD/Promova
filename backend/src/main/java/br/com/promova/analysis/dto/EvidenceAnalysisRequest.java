package br.com.promova.analysis.dto;

import jakarta.validation.constraints.NotBlank;

public record EvidenceAnalysisRequest(
    @NotBlank(message = "evidence is required") String evidence,
    @NotBlank(message = "currentLevel is required") String currentLevel,
    @NotBlank(message = "targetLevel is required") String targetLevel) {}
