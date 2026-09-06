package br.com.promova.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CareerPlanUpdateRequest(
    @NotNull Long jobRoleId,
    @NotBlank String currentLevel,
    @NotBlank String targetLevel,
    @Size(max = 10) List<@NotBlank @Size(max = 120) String> characteristics) {}
