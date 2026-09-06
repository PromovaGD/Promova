package br.com.promova.evidence;

import br.com.promova.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
    name = "evidences",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_evidences_user_source_external",
            columnNames = {"user_id", "source", "external_id"}))
public class Evidence {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 100)
  private String source;

  @Column(name = "external_id", nullable = false, length = 500)
  private String externalId;

  @Column(nullable = false, length = 1000)
  private String sourceMeta;

  @Column(nullable = false, length = 10000)
  private String content;

  @Column(name = "source_url", length = 2048)
  private String sourceUrl;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EvidenceStatus status;

  protected Evidence() {}

  public Evidence(
      User user,
      String source,
      String externalId,
      String sourceMeta,
      String content,
      String sourceUrl,
      Instant capturedAt) {
    this(user, source, externalId, sourceMeta, content, sourceUrl, capturedAt, capturedAt);
  }

  public Evidence(
      User user,
      String source,
      String externalId,
      String sourceMeta,
      String content,
      String sourceUrl,
      Instant occurredAt,
      Instant capturedAt) {
    this.user = Objects.requireNonNull(user, "user is required");
    this.source = requiredText(source, "source");
    this.externalId = requiredText(externalId, "externalId");
    this.sourceMeta = requiredText(sourceMeta, "sourceMeta");
    this.content = requiredText(content, "content");
    this.sourceUrl = normalizeOptional(sourceUrl);
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt is required");
    this.updatedAt = capturedAt;
    this.status = EvidenceStatus.PENDING;
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getSource() {
    return source;
  }

  public String getExternalId() {
    return externalId;
  }

  public String getSourceMeta() {
    return sourceMeta;
  }

  public String getContent() {
    return content;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public EvidenceStatus getStatus() {
    return status;
  }

  public void markAnalyzed() {
    requirePending();
    status = EvidenceStatus.ANALYZED;
    updatedAt = Instant.now();
  }

  public void dismiss() {
    requirePending();
    status = EvidenceStatus.DISMISSED;
    updatedAt = Instant.now();
  }

  private void requirePending() {
    if (status != EvidenceStatus.PENDING) {
      throw new IllegalStateException("Evidence status cannot transition from " + status);
    }
  }

  private static String requiredText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
