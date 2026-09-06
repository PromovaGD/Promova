package br.com.promova.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "terminology_settings")
public class TerminologySettings {
  public static final long SINGLETON_ID = 1L;

  @Id private Long id;

  @Column(name = "manager_label", nullable = false, length = 80)
  private String managerLabel;

  @Column(name = "employee_label", nullable = false, length = 80)
  private String employeeLabel;

  @Column(name = "job_role_label", nullable = false, length = 80)
  private String jobRoleLabel;

  @Column(name = "level_label", nullable = false, length = 80)
  private String levelLabel;

  @Column(name = "characteristics_label", nullable = false, length = 80)
  private String characteristicsLabel;

  @Column(name = "objective_label", nullable = false, length = 80)
  private String objectiveLabel;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TerminologySettings() {}

  public TerminologySettings(
      String managerLabel,
      String employeeLabel,
      String jobRoleLabel,
      String levelLabel,
      String characteristicsLabel,
      String objectiveLabel) {
    this.id = SINGLETON_ID;
    update(
        managerLabel,
        employeeLabel,
        jobRoleLabel,
        levelLabel,
        characteristicsLabel,
        objectiveLabel);
  }

  public void update(
      String managerLabel,
      String employeeLabel,
      String jobRoleLabel,
      String levelLabel,
      String characteristicsLabel,
      String objectiveLabel) {
    this.managerLabel = required(managerLabel);
    this.employeeLabel = required(employeeLabel);
    this.jobRoleLabel = required(jobRoleLabel);
    this.levelLabel = required(levelLabel);
    this.characteristicsLabel = required(characteristicsLabel);
    this.objectiveLabel = required(objectiveLabel);
    this.updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getManagerLabel() { return managerLabel; }
  public String getEmployeeLabel() { return employeeLabel; }
  public String getJobRoleLabel() { return jobRoleLabel; }
  public String getLevelLabel() { return levelLabel; }
  public String getCharacteristicsLabel() { return characteristicsLabel; }
  public String getObjectiveLabel() { return objectiveLabel; }
  public Instant getUpdatedAt() { return updatedAt; }

  private String required(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Terminology labels are required.");
    }
    return value.trim();
  }
}
