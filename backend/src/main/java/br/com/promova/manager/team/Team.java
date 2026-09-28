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
@Table(name = "teams")
public class Team {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "manager_user_id", nullable = false)
  private User manager;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false)
  private Instant createdAt;

  protected Team() {}

  public Team(User manager, String name) {
    this.manager = manager;
    this.name = name;
    this.createdAt = Instant.now();
  }

  public Long getId() { return id; }
  public User getManager() { return manager; }
  public String getName() { return name; }
  public Instant getCreatedAt() { return createdAt; }
}
