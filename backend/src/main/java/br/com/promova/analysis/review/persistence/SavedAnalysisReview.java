package br.com.promova.analysis.review.persistence;

import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.review.ReviewStatus;
import br.com.promova.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Immutable, append-only audit event for a saved analysis review. */
@Entity
@Table(
    name = "saved_analysis_reviews",
    indexes =
        @Index(
            name = "ix_saved_analysis_reviews_history",
            columnList = "saved_analysis_id, created_at, id"))
public class SavedAnalysisReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "saved_analysis_id", nullable = false)
  private SavedAnalysis analysis;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reviewer_user_id", nullable = false)
  private User reviewer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReviewStatus status;

  /** Optional reviewer feedback, bounded to 2,000 characters by the request/service boundary. */
  @Column(length = 2000)
  private String comment;

  @Column(nullable = false)
  private Instant createdAt;

  protected SavedAnalysisReview() {}

  public SavedAnalysisReview(
      SavedAnalysis analysis,
      User reviewer,
      ReviewStatus status,
      String comment,
      Instant createdAt) {
    this.analysis = analysis;
    this.reviewer = reviewer;
    this.status = status;
    this.comment = comment;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public SavedAnalysis getAnalysis() {
    return analysis;
  }

  public User getReviewer() {
    return reviewer;
  }

  public ReviewStatus getStatus() {
    return status;
  }

  public String getComment() {
    return comment;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
