package br.com.promova.manager.team;

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

@Entity
@Table(name = "team_memberships")
public class TeamMembership {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "team_id", nullable = false)
  private Team team;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_user_id", nullable = false)
  private User employee;

  @Column(nullable = false)
  private Instant startedAt;

  private Instant endedAt;

  protected TeamMembership() {}

  public TeamMembership(Team team, User employee) {
    this.team = team;
    this.employee = employee;
    this.startedAt = Instant.now();
  }

  public void end() {
    if (endedAt == null) endedAt = Instant.now();
  }

  public Long getId() { return id; }
  public Team getTeam() { return team; }
  public User getEmployee() { return employee; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getEndedAt() { return endedAt; }
}
