package br.com.promova.profile.dto;

import br.com.promova.profile.CareerObjective;
import br.com.promova.profile.ObjectiveStatus;
import java.time.Instant;
import java.time.LocalDate;

public record CareerObjectiveResponse(
    Long id,
    String text,
    ObjectiveStatus status,
    LocalDate targetDate,
    Long updatedBy,
    String updatedByName,
    Instant createdAt,
    Instant updatedAt) {
  public static CareerObjectiveResponse from(CareerObjective objective) {
    return new CareerObjectiveResponse(
        objective.getId(),
        objective.getText(),
        objective.getStatus(),
        objective.getTargetDate(),
        objective.getUpdatedBy().getId(),
        objective.getUpdatedBy().getName(),
        objective.getCreatedAt(),
        objective.getUpdatedAt());
  }
}
