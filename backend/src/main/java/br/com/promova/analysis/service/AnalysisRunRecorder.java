package br.com.promova.analysis.service;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records execution metadata without persisting prompts, credentials, or provider responses. */
@Service
public class AnalysisRunRecorder {
  private final JdbcTemplate jdbc;
  private final String engine;
  private final String model;
  private final String version;

  public AnalysisRunRecorder(
      JdbcTemplate jdbc,
      @Value("${promova.analysis.engine:mock}") String engine,
      @Value("${openrouter.model:}") String model,
      @Value("${promova.analysis.prompt-version:1}") String version) {
    this.jdbc = jdbc;
    this.engine = engine;
    this.model = "openrouter".equals(engine) ? model : null;
    this.version = version;
  }

  @Transactional
  public void succeeded(
      Long evidenceId, Long analysisId, Instant startedAt, Instant completedAt,
      List<String> suggestions) {
    write(evidenceId, analysisId, "SUCCEEDED", null, startedAt, completedAt);
    for (int index = 0; index < suggestions.size(); index++) {
      jdbc.update("""
          INSERT INTO analysis_suggestions
          (analysis_id, ordinal, suggestion_text)
          VALUES (?, ?, ?)
          """, analysisId, index, suggestions.get(index));
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(Long evidenceId, String errorType, Instant startedAt, Instant completedAt) {
    write(evidenceId, null, "FAILED", errorType, startedAt, completedAt);
  }

  private void write(
      Long evidenceId, Long analysisId, String outcome, String errorType,
      Instant startedAt, Instant completedAt) {
    jdbc.update("""
        INSERT INTO analysis_runs
        (evidence_id, analysis_id, engine, engine_version, model, outcome, error_type, started_at, completed_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, evidenceId, analysisId, engine, version, model, outcome, errorType,
        startedAt, completedAt);
  }
}
