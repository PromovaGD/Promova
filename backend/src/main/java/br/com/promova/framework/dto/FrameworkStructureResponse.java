package br.com.promova.framework.dto;

import java.util.List;

public record FrameworkStructureResponse(String version, List<Level> levels) {
  public FrameworkStructureResponse {
    levels = levels == null ? List.of() : List.copyOf(levels);
  }

  public record Level(
      String key, String title, String description, List<Criterion> criteria) {
    public Level {
      criteria = criteria == null ? List.of() : List.copyOf(criteria);
    }
  }

  public record Criterion(String id, String key, String description) {}
}
