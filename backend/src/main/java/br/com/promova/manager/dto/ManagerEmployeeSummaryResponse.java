package br.com.promova.manager.dto;

import br.com.promova.profile.CareerProfile;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.util.List;

public record ManagerEmployeeSummaryResponse(
    Long id,
    String name,
    String email,
    UserRole role,
    Long jobRoleId,
    String jobRoleName,
    String currentLevel,
    String targetLevel,
    List<String> characteristics,
    long activeObjectiveCount) {

  public static ManagerEmployeeSummaryResponse from(
      User user, CareerProfile profile, long activeObjectiveCount) {
    return new ManagerEmployeeSummaryResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getRole(),
        profile == null || profile.getJobRole() == null ? null : profile.getJobRole().getId(),
        profile == null || profile.getJobRole() == null ? null : profile.getJobRole().getName(),
        profile == null ? null : profile.getCurrentLevel(),
        profile == null ? null : profile.getTargetLevel(),
        profile == null ? List.of() : profile.getCharacteristics(),
        activeObjectiveCount);
  }
}
