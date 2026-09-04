package br.com.promova.profile;

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
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "career_objectives")
public class CareerObjective {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_profile_id", nullable = false)
  private CareerProfile careerProfile;

  @Column(nullable = false, length = 1000)
  private String text;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ObjectiveStatus status;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "updated_by", nullable = false)
  private User updatedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CareerObjective() {}

  public CareerObjective(
      CareerProfile careerProfile, String text, LocalDate targetDate, User createdBy) {
    this.careerProfile = careerProfile;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    update(text, ObjectiveStatus.ACTIVE, targetDate, createdBy);
  }

  public void update(String text, ObjectiveStatus status, LocalDate targetDate, User updatedBy) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("objective text is required");
    }
    this.text = text.trim();
    this.status = status;
    this.targetDate = targetDate;
    this.updatedBy = updatedBy;
    this.updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public CareerProfile getCareerProfile() { return careerProfile; }
  public String getText() { return text; }
  public ObjectiveStatus getStatus() { return status; }
  public LocalDate getTargetDate() { return targetDate; }
  public User getCreatedBy() { return createdBy; }
  public User getUpdatedBy() { return updatedBy; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
