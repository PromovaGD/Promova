package br.com.promova.github.connection;

import br.com.promova.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
    name = "github_connection_settings",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_github_settings_user", columnNames = "user_id"))
public class GithubConnectionSettings {
  public static final String OUTCOME_NOT_CONFIGURED = "NOT_CONFIGURED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "repo_slug", length = 255)
  private String repoSlug;

  @Column(name = "author_login", length = 39)
  private String authorLogin;

  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

  @Column(name = "last_sync_outcome", nullable = false, length = 100)
  private String lastSyncOutcome;

  protected GithubConnectionSettings() {}

  public GithubConnectionSettings(User user) {
    this.user = Objects.requireNonNull(user, "user is required");
    this.lastSyncOutcome = OUTCOME_NOT_CONFIGURED;
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getRepoSlug() {
    return repoSlug;
  }

  public String getAuthorLogin() {
    return authorLogin;
  }

  public Instant getLastSyncAt() {
    return lastSyncAt;
  }

  public String getLastSyncOutcome() {
    return lastSyncOutcome;
  }

  public void configure(String repoSlug, String authorLogin) {
    this.repoSlug = repoSlug;
    this.authorLogin = authorLogin;
    this.lastSyncAt = null;
    this.lastSyncOutcome = OUTCOME_NOT_CONFIGURED;
  }

  public void recordSync(Instant syncAt, String outcome) {
    this.lastSyncAt = Objects.requireNonNull(syncAt, "syncAt is required");
    this.lastSyncOutcome = Objects.requireNonNull(outcome, "outcome is required");
  }
}
