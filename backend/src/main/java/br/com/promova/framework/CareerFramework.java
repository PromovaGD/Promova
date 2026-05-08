package br.com.promova.framework;

import java.util.Map;

public record CareerFramework(Map<String, CareerLevel> levels) {
  public String resolveLevelOrDefault(String requestedLevel, String fallbackLevel) {
    if (levels.containsKey(requestedLevel)) {
      return requestedLevel;
    }

    return fallbackLevel;
  }
}
