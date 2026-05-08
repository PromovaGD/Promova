package br.com.promova.evidence.dto;

public record CapturedEvidenceResponse(
    String id,
    String source,
    String sourceMeta,
    String evidence,
    String currentLevel,
    String targetLevel,
    int nextCursor) {}
