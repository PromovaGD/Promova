package br.com.promova.profile.dto;

import br.com.promova.framework.CareerFramework;
import br.com.promova.profile.CareerProfile;
import java.util.List;

public record ProfileResponse(
    String currentLevel, String targetLevel, List<FrameworkLevelResponse> levels) {
  public static ProfileResponse from(CareerProfile profile, CareerFramework framework) {
    List<FrameworkLevelResponse> levels =
        framework.levelKeys().stream()
            .map(
                key -> {
                  String title = framework.levels().get(key).title();
                  return new FrameworkLevelResponse(key, title == null ? key : title);
                })
            .toList();

    return new ProfileResponse(profile.getCurrentLevel(), profile.getTargetLevel(), levels);
  }
}
