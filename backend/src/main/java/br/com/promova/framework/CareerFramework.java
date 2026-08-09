package br.com.promova.framework;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record CareerFramework(Map<String, CareerLevel> levels) {
  public CareerFramework {
    Map<String, CareerLevel> orderedLevels = new LinkedHashMap<>();
    if (levels != null) {
      orderedLevels.putAll(levels);
    }
    levels = Collections.unmodifiableMap(orderedLevels);
  }

  public List<String> levelKeys() {
    return List.copyOf(levels.keySet());
  }

  public boolean containsLevel(String level) {
    return level != null && levels.containsKey(level);
  }

  public boolean isAbove(String currentLevel, String targetLevel) {
    return indexOf(currentLevel) >= 0
        && indexOf(targetLevel) >= 0
        && indexOf(targetLevel) > indexOf(currentLevel);
  }

  public void validateProgression(String currentLevel, String targetLevel) {
    requireDeclaredLevel(currentLevel, "currentLevel");
    requireDeclaredLevel(targetLevel, "targetLevel");

    if (!isAbove(currentLevel, targetLevel)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "targetLevel must be above currentLevel according to the career framework.");
    }
  }

  public String defaultCurrentLevel() {
    if (containsLevel("L3") && indexOf("L3") < levelKeys().size() - 1) {
      return "L3";
    }
    return firstLevel();
  }

  public String defaultTargetLevel() {
    String currentLevel = defaultCurrentLevel();
    if (containsLevel("L4") && isAbove(currentLevel, "L4")) {
      return "L4";
    }

    int currentIndex = indexOf(currentLevel);
    if (currentIndex >= 0 && currentIndex + 1 < levelKeys().size()) {
      return levelKeys().get(currentIndex + 1);
    }

    throw new IllegalStateException("Career framework must declare at least two ordered levels.");
  }

  public String resolveLevelOrDefault(String requestedLevel, String fallbackLevel) {
    if (levels.containsKey(requestedLevel)) {
      return requestedLevel;
    }

    if (levels.containsKey(fallbackLevel)) {
      return fallbackLevel;
    }

    return firstLevel();
  }

  private void requireDeclaredLevel(String level, String fieldName) {
    if (!containsLevel(level)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, fieldName + " must be a declared career framework level.");
    }
  }

  private int indexOf(String level) {
    return levelKeys().indexOf(level);
  }

  private String firstLevel() {
    if (levels.isEmpty()) {
      throw new IllegalStateException("Career framework has no levels.");
    }
    return levelKeys().get(0);
  }
}
