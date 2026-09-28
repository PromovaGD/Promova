package br.com.promova.manager.dto;

import java.time.Instant;

public record DashboardPersonResponse(
    Long id,
    String name,
    String jobRoleName,
    String currentLevel,
    String targetLevel,
    long activeObjectives,
    long pendingEvidence,
    long unreviewedAnalyses,
    Instant lastActivity) {}
