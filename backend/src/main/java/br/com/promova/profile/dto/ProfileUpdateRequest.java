package br.com.promova.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateRequest(
    @NotBlank(message = "currentLevel is required") String currentLevel,
    @NotBlank(message = "targetLevel is required") String targetLevel) {}
