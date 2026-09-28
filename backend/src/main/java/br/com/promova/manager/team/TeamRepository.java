package br.com.promova.manager.team;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
  List<Team> findByManagerIdOrderByNameAsc(Long managerId);
  Optional<Team> findByIdAndManagerId(Long id, Long managerId);
}
