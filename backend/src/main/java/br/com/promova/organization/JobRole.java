package br.com.promova.organization;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "job_roles",
    uniqueConstraints = @UniqueConstraint(name = "uk_job_roles_name", columnNames = "name"))
public class JobRole {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private JobRoleStatus status;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "job_role_levels", joinColumns = @JoinColumn(name = "job_role_id"))
  @OrderColumn(name = "sort_order")
  @Column(name = "level_id", nullable = false, length = 40)
  private List<String> allowedLevelIds = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected JobRole() {}

  public JobRole(String name, String description, List<String> allowedLevelIds) {
    this.status = JobRoleStatus.ACTIVE;
    this.createdAt = Instant.now();
    update(name, description, allowedLevelIds);
  }

  public void update(String name, String description, List<String> allowedLevelIds) {
    this.name = required(name, "name");
    this.description = required(description, "description");
    this.allowedLevelIds = new ArrayList<>(allowedLevelIds);
    this.updatedAt = Instant.now();
  }

  public void archive() {
    this.status = JobRoleStatus.ARCHIVED;
    this.updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public JobRoleStatus getStatus() { return status; }
  public List<String> getAllowedLevelIds() { return List.copyOf(allowedLevelIds); }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  private String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }
}
