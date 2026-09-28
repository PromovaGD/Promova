package br.com.promova.manager.team;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, Long> {
  @Query("""
      SELECT m FROM TeamMembership m
      WHERE m.team.manager.id = :managerId
        AND m.employee.id = :employeeId
        AND m.endedAt IS NULL
      """)
  List<TeamMembership> activeForManagerEmployee(
      @Param("managerId") Long managerId, @Param("employeeId") Long employeeId);

  @Query("""
      SELECT COUNT(m) FROM TeamMembership m
      WHERE m.team.id = :teamId AND m.endedAt IS NULL
      """)
  long activeCount(@Param("teamId") Long teamId);

  @Query("""
      SELECT COUNT(m) FROM TeamMembership m
      WHERE m.team.manager.id = :managerId
        AND m.employee.id = :employeeId
        AND m.endedAt IS NULL
      """)
  long visibleCount(@Param("managerId") Long managerId, @Param("employeeId") Long employeeId);

  @Query("""
      SELECT COUNT(m) FROM TeamMembership m
      WHERE m.employee.id = :employeeId AND m.endedAt IS NULL
        AND m.team.manager.id <> :managerId
      """)
  long activeForOtherManager(
      @Param("managerId") Long managerId, @Param("employeeId") Long employeeId);

  @Query("""
      SELECT m FROM TeamMembership m
      WHERE m.team.id = :teamId AND m.team.manager.id = :managerId AND m.endedAt IS NULL
      ORDER BY LOWER(m.employee.name), m.employee.id
      """)
  List<TeamMembership> activeTeamMembers(
      @Param("managerId") Long managerId, @Param("teamId") Long teamId);
}
