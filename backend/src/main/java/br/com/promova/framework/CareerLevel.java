package br.com.promova.framework;

import java.util.Map;

public record CareerLevel(String title, String description, Map<String, String> criteria) {
  public CareerLevel(String description) {
    this(null, description, Map.of());
  }

  public CareerLevel {
    if (criteria == null) {
      criteria = Map.of();
    }
  }
}
