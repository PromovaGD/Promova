package br.com.promova.organization;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRoleRepository extends JpaRepository<JobRole, Long> {
  List<JobRole> findAllByOrderByNameAsc();
  List<JobRole> findByStatusOrderByNameAsc(JobRoleStatus status);
  Optional<JobRole> findFirstByStatusOrderByNameAsc(JobRoleStatus status);
  boolean existsByNameIgnoreCase(String name);
  boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
