package br.com.promova.organization.dto;

import br.com.promova.organization.JobRole;
import br.com.promova.organization.JobRoleStatus;
import java.util.List;

public record JobRoleResponse(
    Long id,
    String name,
    String description,
    JobRoleStatus status,
    List<String> allowedLevelIds) {
  public static JobRoleResponse from(JobRole role) {
    return new JobRoleResponse(
        role.getId(),
        role.getName(),
        role.getDescription(),
        role.getStatus(),
        role.getAllowedLevelIds());
  }
}
