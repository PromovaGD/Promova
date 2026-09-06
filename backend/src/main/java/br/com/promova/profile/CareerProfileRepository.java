package br.com.promova.profile;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareerProfileRepository extends JpaRepository<CareerProfile, Long> {
  Optional<CareerProfile> findByUserId(Long userId);

  @Query(value = "SELECT COUNT(*) FROM career_profiles WHERE job_role_id = :roleId", nativeQuery = true)
  long countByJobRoleId(@Param("roleId") Long roleId);

  @Modifying
  @Query(
      value = "UPDATE career_profiles SET job_role_id = :replacementId WHERE job_role_id = :roleId",
      nativeQuery = true)
  int replaceJobRole(
      @Param("roleId") Long roleId, @Param("replacementId") Long replacementId);
}
