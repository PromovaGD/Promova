package br.com.promova.user;

import java.util.List;
import java.util.Optional;
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

  @Query("SELECT u FROM User u WHERE u.id != :excludeId ORDER BY u.name ASC")
  List<User> findAllExcept(@Param("excludeId") Long excludeId);
}
