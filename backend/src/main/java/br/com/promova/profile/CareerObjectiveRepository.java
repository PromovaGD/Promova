package br.com.promova.profile;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerObjectiveRepository extends JpaRepository<CareerObjective, Long> {
  List<CareerObjective> findByCareerProfileIdOrderByCreatedAtAsc(Long careerProfileId);
  Optional<CareerObjective> findByIdAndCareerProfileId(Long id, Long careerProfileId);
  long countByCareerProfileIdAndStatus(Long careerProfileId, ObjectiveStatus status);
}
