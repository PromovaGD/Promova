package br.com.promova.organization.dto;

import br.com.promova.profile.dto.FrameworkLevelResponse;
import java.util.List;

public record CareerConfigurationResponse(
    TerminologyResponse labels,
    List<JobRoleResponse> activeRoles,
    List<FrameworkLevelResponse> frameworkLevels) {}
