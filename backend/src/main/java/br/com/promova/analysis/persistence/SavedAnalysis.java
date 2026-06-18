package br.com.promova.analysis.persistence;

import br.com.promova.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "saved_analyses")
public class SavedAnalysis {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String externalId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String source;

  @Column(nullable = false)
  private String sourceMeta;

  @Column(nullable = false, length = 4000)
  private String evidence;

  @Column(nullable = false)
  private String currentLevel;

  @Column(nullable = false)
  private String targetLevel;

  @Column(nullable = false)
  private String impactLevel;

  @Column(nullable = false)
  private String confidence;

  @Column(nullable = false, length = 4000)
  private String justification;

  @Column(nullable = false, length = 4000)
  private String competenciesJson;

  @Column(nullable = false, length = 4000)
  private String suggestionsJson;

  @Column(nullable = false, length = 1000)
  private String readiness;

  @Column(nullable = false)
  private Instant createdAt;

  protected SavedAnalysis() {}

  public SavedAnalysis(
      String externalId,
      User user,
      String source,
      String sourceMeta,
      String evidence,
      String currentLevel,
      String targetLevel,
      String impactLevel,
      String confidence,
      String justification,
      String competenciesJson,
      String suggestionsJson,
      String readiness,
      Instant createdAt) {
    this.externalId = externalId;
    this.user = user;
    this.source = source;
    this.sourceMeta = sourceMeta;
    this.evidence = evidence;
    this.currentLevel = currentLevel;
    this.targetLevel = targetLevel;
    this.impactLevel = impactLevel;
    this.confidence = confidence;
    this.justification = justification;
    this.competenciesJson = competenciesJson;
    this.suggestionsJson = suggestionsJson;
    this.readiness = readiness;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getExternalId() {
    return externalId;
  }

  public User getUser() {
    return user;
  }

  public String getSource() {
    return source;
  }

  public String getSourceMeta() {
    return sourceMeta;
  }

  public String getEvidence() {
    return evidence;
  }

  public String getCurrentLevel() {
    return currentLevel;
  }

  public String getTargetLevel() {
    return targetLevel;
  }

  public String getImpactLevel() {
    return impactLevel;
  }

  public String getConfidence() {
    return confidence;
  }

  public String getJustification() {
    return justification;
  }

  public String getCompetenciesJson() {
    return competenciesJson;
  }

  public String getSuggestionsJson() {
    return suggestionsJson;
  }

  public String getReadiness() {
    return readiness;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
