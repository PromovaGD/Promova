package br.com.promova.profile.dto;

import br.com.promova.profile.ObjectiveStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CareerObjectiveRequest(
    @NotBlank @Size(max = 1000) String text,
    @NotNull ObjectiveStatus status,
    LocalDate targetDate) {}
