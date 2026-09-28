package br.com.promova.user;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmailIgnoreCase(String email);

  List<User> findByRoleOrderByNameAsc(UserRole role);

  @Query(
      """
      SELECT u FROM User u
      LEFT JOIN CareerProfile p ON p.user = u
      WHERE u.role = :role
        AND (:query IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:jobRoleId IS NULL OR p.jobRole.id = :jobRoleId)
        AND (:level IS NULL OR LOWER(p.currentLevel) = LOWER(:level)
             OR LOWER(p.targetLevel) = LOWER(:level))
      ORDER BY LOWER(u.name) ASC, u.id ASC
      """)
  Page<User> searchByRole(
      @Param("role") UserRole role,
      @Param("query") String query,
      @Param("jobRoleId") Long jobRoleId,
      @Param("level") String level,
      Pageable pageable);

  @Query("""
      SELECT u FROM User u LEFT JOIN CareerProfile p ON p.user = u
      WHERE u.role = br.com.promova.user.UserRole.EMPLOYEE
        AND EXISTS (
          SELECT m.id FROM TeamMembership m
          WHERE m.employee = u AND m.team.manager.id = :managerId
            AND m.startedAt <= :now AND (m.endedAt IS NULL OR m.endedAt > :now)
        )
        AND (:query IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:jobRoleId IS NULL OR p.jobRole.id = :jobRoleId)
        AND (:level IS NULL OR LOWER(p.currentLevel) = LOWER(:level)
             OR LOWER(p.targetLevel) = LOWER(:level))
      ORDER BY LOWER(u.name) ASC, u.id ASC
      """)
  Page<User> searchVisibleEmployees(
      @Param("managerId") Long managerId,
      @Param("now") Instant now,
      @Param("query") String query,
      @Param("jobRoleId") Long jobRoleId,
      @Param("level") String level,
      Pageable pageable);

  @Query("""
      SELECT u FROM User u LEFT JOIN CareerProfile p ON p.user = u
      WHERE u.role = br.com.promova.user.UserRole.EMPLOYEE
        AND EXISTS (
          SELECT m.id FROM TeamMembership m
          WHERE m.employee = u AND m.team.manager.id = :managerId
            AND m.startedAt <= :now AND (m.endedAt IS NULL OR m.endedAt > :now)
        )
        AND (:query IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:jobRoleId IS NULL OR p.jobRole.id = :jobRoleId)
        AND (:level IS NULL OR LOWER(p.currentLevel) = LOWER(:level)
             OR LOWER(p.targetLevel) = LOWER(:level))
      ORDER BY LOWER(u.name) ASC, u.id ASC
      """)
  List<User> listVisibleEmployees(
      @Param("managerId") Long managerId,
      @Param("now") Instant now,
      @Param("query") String query,
      @Param("jobRoleId") Long jobRoleId,
      @Param("level") String level);

  @Query("""
      SELECT u FROM User u
      WHERE u.role = br.com.promova.user.UserRole.EMPLOYEE
        AND (
          NOT EXISTS (SELECT m.id FROM TeamMembership m
                      WHERE m.employee = u AND m.endedAt IS NULL)
          OR EXISTS (SELECT m2.id FROM TeamMembership m2
                     WHERE m2.employee = u AND m2.endedAt IS NULL
                       AND m2.team.manager.id = :managerId)
        )
      ORDER BY LOWER(u.name), u.id
      """)
  List<User> assignmentCandidates(@Param("managerId") Long managerId);

  @Query("SELECT u FROM User u WHERE u.id != :excludeId ORDER BY u.name ASC")
  List<User> findAllExcept(@Param("excludeId") Long excludeId);
}
