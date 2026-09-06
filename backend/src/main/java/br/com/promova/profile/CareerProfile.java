package br.com.promova.profile;

import br.com.promova.organization.JobRole;
import br.com.promova.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "career_profiles",
    uniqueConstraints = @UniqueConstraint(name = "uk_career_profiles_user", columnNames = "user_id"))
public class CareerProfile {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "current_level", nullable = false)
  private String currentLevel;

  @Column(name = "target_level", nullable = false)
  private String targetLevel;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "job_role_id", nullable = false)
  private JobRole jobRole;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "career_profile_characteristics",
      joinColumns = @JoinColumn(name = "career_profile_id"))
  @OrderColumn(name = "sort_order")
  @Column(name = "characteristic", nullable = false, length = 120)
  private List<String> characteristics = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CareerProfile() {}

  public CareerProfile(User user, String currentLevel, String targetLevel) {
    this(user, null, currentLevel, targetLevel, List.of());
  }

  public CareerProfile(
      User user,
      JobRole jobRole,
      String currentLevel,
      String targetLevel,
      List<String> characteristics) {
    this.user = user;
    this.jobRole = jobRole;
    this.createdAt = Instant.now();
    updatePlan(jobRole, currentLevel, targetLevel, characteristics);
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getCurrentLevel() {
    return currentLevel;
  }

  public String getTargetLevel() {
    return targetLevel;
  }

  public JobRole getJobRole() {
    return jobRole;
  }

  public List<String> getCharacteristics() {
    return List.copyOf(characteristics);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void updateLevels(String currentLevel, String targetLevel) {
    this.currentLevel = currentLevel;
    this.targetLevel = targetLevel;
    this.updatedAt = Instant.now();
  }

  public void updatePlan(
      JobRole jobRole,
      String currentLevel,
      String targetLevel,
      List<String> characteristics) {
    this.jobRole = jobRole;
    this.currentLevel = currentLevel;
    this.targetLevel = targetLevel;
    this.characteristics = new ArrayList<>(characteristics == null ? List.of() : characteristics);
    this.updatedAt = Instant.now();
  }
}
