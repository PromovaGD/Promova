package br.com.promova.github.metrics;

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
@Table(name = "github_pull_reviews")
public class GithubPullReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pull_request_id", nullable = false)
  private GithubPullRequestFact pullRequest;

  @Column(name = "provider_review_id", nullable = false)
  private Long providerReviewId;

  @Column(name = "reviewer_login", nullable = false, length = 100)
  private String reviewerLogin;

  @Column(nullable = false, length = 40)
  private String state;

  private Instant submittedAt;

  protected GithubPullReview() {}

  public GithubPullReview(
      GithubPullRequestFact pullRequest, Long providerReviewId,
      String reviewerLogin, String state, Instant submittedAt) {
    this.pullRequest = pullRequest;
    this.providerReviewId = providerReviewId;
    this.reviewerLogin = reviewerLogin;
    this.state = state;
    this.submittedAt = submittedAt;
  }
}
