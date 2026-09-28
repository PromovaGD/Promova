package br.com.promova.github.metrics;

import br.com.promova.source.NormalizedEvidence;
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
import java.time.format.DateTimeParseException;
import java.util.Map;

@Entity
@Table(name = "github_pull_requests")
public class GithubPullRequestFact {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_user_id", nullable = false)
  private User employee;

  @Column(name = "evidence_id")
  private Long evidenceId;

  @Column(nullable = false, length = 255)
  private String repository;

  @Column(name = "pull_number", nullable = false)
  private int pullNumber;

  @Column(name = "author_login", length = 100)
  private String authorLogin;

  @Column(length = 1000)
  private String title;

  @Column(nullable = false, length = 30)
  private String state;

  @Column(nullable = false)
  private boolean draft;

  @Column(name = "source_url", length = 2048)
  private String sourceUrl;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "merged_at")
  private Instant mergedAt;

  @Column(name = "commit_count")
  private Integer commitCount;

  @Column(name = "changed_files_count")
  private Integer changedFilesCount;

  @Column(name = "first_review_requested_at")
  private Instant firstReviewRequestedAt;

  @Column(name = "request_events_synced_at")
  private Instant requestEventsSyncedAt;

  @Column(name = "last_synced_at", nullable = false)
  private Instant lastSyncedAt;

  protected GithubPullRequestFact() {}

  public GithubPullRequestFact(User employee, String repository, int pullNumber) {
    this.employee = employee;
    this.repository = repository;
    this.pullNumber = pullNumber;
    this.state = "unknown";
    this.lastSyncedAt = Instant.now();
  }

  public void update(NormalizedEvidence normalized, Long evidenceId) {
    Map<String, String> meta = normalized.providerMetadata();
    this.evidenceId = evidenceId;
    this.authorLogin = limit(meta.get("author"), 100);
    this.title = limit(meta.get("title"), 1000);
    this.state = limit(meta.getOrDefault("state", "unknown").toLowerCase(), 30);
    this.draft = Boolean.parseBoolean(meta.get("draft"));
    this.sourceUrl = normalized.sourceUrl();
    this.createdAt = instant(meta.get("createdAt"));
    this.updatedAt = instant(meta.get("updatedAt"));
    this.closedAt = instant(meta.get("closedAt"));
    this.mergedAt = instant(meta.get("mergedAt"));
    this.commitCount = nonnegative(meta.get("commitCount"), this.commitCount);
    this.changedFilesCount = nonnegative(meta.get("changedFilesCount"), this.changedFilesCount);
    this.lastSyncedAt = Instant.now();
  }

  public void recordReviewRequest(Instant requestedAt) {
    if (requestedAt != null &&
        (firstReviewRequestedAt == null || requestedAt.isBefore(firstReviewRequestedAt))) {
      firstReviewRequestedAt = requestedAt;
    }
    requestEventsSyncedAt = Instant.now();
  }

  private static Integer nonnegative(String text, Integer current) {
    if (text == null) return current;
    try {
      int parsed = Integer.parseInt(text);
      return parsed < 0 ? current : parsed;
    } catch (NumberFormatException ignored) {
      return current;
    }
  }

  private static Instant instant(String value) {
    if (value == null || value.isBlank()) return null;
    try { return Instant.parse(value); }
    catch (DateTimeParseException error) { return null; }
  }

  private static String limit(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }

  public Long getId() { return id; }
  public User getEmployee() { return employee; }
  public String getRepository() { return repository; }
  public int getPullNumber() { return pullNumber; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getMergedAt() { return mergedAt; }
}
