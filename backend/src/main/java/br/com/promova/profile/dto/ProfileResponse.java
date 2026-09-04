package br.com.promova.profile.dto;

import br.com.promova.framework.CareerFramework;
import br.com.promova.profile.CareerProfile;
import br.com.promova.organization.dto.JobRoleResponse;
import java.util.List;

public record ProfileResponse(
    Long userId,
    JobRoleResponse jobRole,
    String currentLevel,
    String targetLevel,
    List<String> characteristics,
    List<CareerObjectiveResponse> objectives,
    List<FrameworkLevelResponse> levels) {
  public ProfileResponse(
      String currentLevel, String targetLevel, List<FrameworkLevelResponse> levels) {
    this(null, null, currentLevel, targetLevel, List.of(), List.of(), levels);
  }

  public static ProfileResponse from(
      CareerProfile profile,
      CareerFramework framework,
      List<CareerObjectiveResponse> objectives) {
    List<FrameworkLevelResponse> levels =
        framework.levelKeys().stream()
            .map(
                key -> {
                  String title = framework.levels().get(key).title();
                  return new FrameworkLevelResponse(key, title == null ? key : title);
                })
            .toList();

    return new ProfileResponse(
        profile.getUser().getId(),
        JobRoleResponse.from(profile.getJobRole()),
        profile.getCurrentLevel(),
        profile.getTargetLevel(),
        profile.getCharacteristics(),
        objectives,
        levels);
  }
}
