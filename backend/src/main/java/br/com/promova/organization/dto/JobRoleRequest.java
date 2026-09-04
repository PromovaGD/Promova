package br.com.promova.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record JobRoleRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 1000) String description,
    @NotEmpty List<@NotBlank @Size(max = 40) String> allowedLevelIds) {}
