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
@Table(name = "career_objective_events")
public class CareerObjectiveEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "objective_id", nullable = false)
  private CareerObjective objective;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "actor_user_id", nullable = false)
  private User actor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ObjectiveStatus status;

  private LocalDate targetDate;

  @Column(nullable = false)
  private Instant occurredAt;

  protected CareerObjectiveEvent() {}

  public CareerObjectiveEvent(CareerObjective objective, User actor) {
    this.objective = objective;
    this.actor = actor;
    this.status = objective.getStatus();
    this.targetDate = objective.getTargetDate();
    this.occurredAt = Instant.now();
  }

  public Long getId() { return id; }
  public CareerObjective getObjective() { return objective; }
  public ObjectiveStatus getStatus() { return status; }
  public LocalDate getTargetDate() { return targetDate; }
  public Instant getOccurredAt() { return occurredAt; }
}
