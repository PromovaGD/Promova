package br.com.promova.evidence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
  Optional<Evidence> findByUserIdAndSourceAndExternalId(
      Long userId, String source, String externalId);

  @Query(
      """
      SELECT e FROM Evidence e
      WHERE e.user.id = :userId
        AND (:status IS NULL OR e.status = :status)
        AND (:from IS NULL OR e.occurredAt >= :from)
        AND (:to IS NULL OR e.occurredAt <= :to)
      ORDER BY e.occurredAt DESC, e.id DESC
      """)
  List<Evidence> findForUser(
      @Param("userId") Long userId,
      @Param("status") EvidenceStatus status,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query("SELECT e FROM Evidence e WHERE e.id = :id AND e.user.id = :userId")
  Optional<Evidence> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM Evidence e WHERE e.id = :id AND e.user.id = :userId")
  Optional<Evidence> findByIdAndUserIdForUpdate(
      @Param("id") Long id, @Param("userId") Long userId);
}
