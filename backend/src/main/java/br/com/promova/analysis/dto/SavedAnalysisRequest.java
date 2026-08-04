package br.com.promova.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record SavedAnalysisRequest(
    @NotBlank String externalId,
    @NotBlank String source,
    @NotBlank String sourceMeta,
    @NotBlank String evidence,
    @NotBlank String currentLevel,
    @NotBlank String targetLevel,
    @NotBlank String impactLevel,
    @NotBlank String confidence,
    @NotBlank String justification,
    @NotBlank String readiness,
    @NotNull Instant createdAt,
    List<String> competencies,
    List<String> suggestions) {}
