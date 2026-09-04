package br.com.promova.organization.dto;

import br.com.promova.organization.TerminologySettings;

public record TerminologyResponse(
    String manager,
    String employee,
    String jobRole,
    String level,
    String characteristics,
    String objective) {
  public static TerminologyResponse from(TerminologySettings settings) {
    return new TerminologyResponse(
        settings.getManagerLabel(),
        settings.getEmployeeLabel(),
        settings.getJobRoleLabel(),
        settings.getLevelLabel(),
        settings.getCharacteristicsLabel(),
        settings.getObjectiveLabel());
  }
}
