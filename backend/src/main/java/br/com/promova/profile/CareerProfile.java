package br.com.promova.profile;

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

  protected CareerProfile() {}

  public CareerProfile(User user, String currentLevel, String targetLevel) {
    this.user = user;
    this.currentLevel = currentLevel;
    this.targetLevel = targetLevel;
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

  public void updateLevels(String currentLevel, String targetLevel) {
    this.currentLevel = currentLevel;
    this.targetLevel = targetLevel;
  }
}
