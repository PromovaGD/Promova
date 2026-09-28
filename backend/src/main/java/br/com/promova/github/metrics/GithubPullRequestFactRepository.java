package br.com.promova.github.metrics;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GithubPullRequestFactRepository extends JpaRepository<GithubPullRequestFact, Long> {
  Optional<GithubPullRequestFact> findByEmployeeIdAndRepositoryAndPullNumber(
      Long employeeId, String repository, int pullNumber);

  @Query("""
      SELECT f FROM GithubPullRequestFact f
      WHERE EXISTS (
        SELECT m.id FROM TeamMembership m
        WHERE m.employee = f.employee AND m.team.manager.id = :managerId
          AND m.endedAt IS NULL
      )
      ORDER BY f.lastSyncedAt DESC, f.id DESC
      """)
  List<GithubPullRequestFact> recentVisible(
      @Param("managerId") Long managerId, Pageable pageable);
}
