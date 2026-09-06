package br.com.promova.analysis.dto;

import jakarta.validation.constraints.Size;

public record EvidenceAnalysisCommand(
    @Size(max = 2000, message = "A observação deve ter no máximo 2.000 caracteres.")
        String userObservation) {}
