package br.com.promova.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.time.Instant;

public record CareerPlanUpdateRequest(
    @NotNull Long jobRoleId,
    @NotBlank String currentLevel,
    @NotBlank String targetLevel,
    @Size(max = 10) List<@NotBlank @Size(max = 120) String> characteristics,
    Instant expectedUpdatedAt) {
  public CareerPlanUpdateRequest(
      Long jobRoleId,
      String currentLevel,
      String targetLevel,
      List<String> characteristics) {
    this(jobRoleId, currentLevel, targetLevel, characteristics, null);
  }
}
