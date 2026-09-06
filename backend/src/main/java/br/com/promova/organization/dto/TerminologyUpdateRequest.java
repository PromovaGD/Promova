package br.com.promova.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TerminologyUpdateRequest(
    @NotBlank @Size(max = 80) String manager,
    @NotBlank @Size(max = 80) String employee,
    @NotBlank @Size(max = 80) String jobRole,
    @NotBlank @Size(max = 80) String level,
    @NotBlank @Size(max = 80) String characteristics,
    @NotBlank @Size(max = 80) String objective) {}
