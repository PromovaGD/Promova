package br.com.promova.evidence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  @Query(
      """
      SELECT e FROM Evidence e
      WHERE e.user.id = :userId
        AND (:status IS NULL OR e.status = :status)
        AND (:source IS NULL OR LOWER(e.source) = LOWER(:source))
        AND (:from IS NULL OR e.occurredAt >= :from)
        AND (:to IS NULL OR e.occurredAt <= :to)
      ORDER BY e.occurredAt DESC, e.id DESC
      """)
  Page<Evidence> findForUserPaged(
      @Param("userId") Long userId,
      @Param("status") EvidenceStatus status,
      @Param("source") String source,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  @Query("""
      SELECT e FROM Evidence e
      WHERE e.user.id = :userId
        AND (:source IS NULL OR LOWER(e.source) = LOWER(:source))
        AND (:from IS NULL OR e.occurredAt >= :from)
        AND (:to IS NULL OR e.occurredAt <= :to)
        AND (:q IS NULL OR LOCATE(:q, LOWER(e.sourceMeta)) > 0 OR LOCATE(:q, LOWER(e.content)) > 0
             OR LOCATE(:q, LOWER(e.source)) > 0 OR LOCATE(:q, LOWER(e.externalId)) > 0)
        AND ((:bucket = 'pending' AND e.status = br.com.promova.evidence.EvidenceStatus.PENDING)
          OR (:bucket = 'dismissed' AND e.status = br.com.promova.evidence.EvidenceStatus.DISMISSED)
          OR (:bucket = 'accepted' AND EXISTS (
            SELECT a.id FROM SavedAnalysis a WHERE a.evidenceEntity = e
              AND EXISTS (SELECT r.id FROM SavedAnalysisReview r WHERE r.analysis = a
                AND r.id = (SELECT MAX(r2.id) FROM SavedAnalysisReview r2 WHERE r2.analysis = a)
                AND r.status = br.com.promova.analysis.review.ReviewStatus.ACCEPTED)))
          OR :bucket = 'all')
      """)
  Page<Evidence> findInbox(
      @Param("userId") Long userId, @Param("bucket") String bucket,
      @Param("q") String q, @Param("source") String source,
      @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

  @Query("""
      SELECT e.status, COUNT(e) FROM Evidence e
      WHERE e.user.id = :userId
        AND (:source IS NULL OR LOWER(e.source) = LOWER(:source))
        AND (:from IS NULL OR e.occurredAt >= :from)
        AND (:to IS NULL OR e.occurredAt <= :to)
        AND (:q IS NULL OR LOCATE(:q, LOWER(e.sourceMeta)) > 0 OR LOCATE(:q, LOWER(e.content)) > 0
             OR LOCATE(:q, LOWER(e.source)) > 0 OR LOCATE(:q, LOWER(e.externalId)) > 0)
      GROUP BY e.status
      """)
  List<Object[]> countInboxStatuses(
      @Param("userId") Long userId, @Param("q") String q,
      @Param("source") String source, @Param("from") Instant from, @Param("to") Instant to);

  @Query("""
      SELECT COUNT(e) FROM Evidence e
      WHERE e.user.id = :userId
        AND (:source IS NULL OR LOWER(e.source) = LOWER(:source))
        AND (:from IS NULL OR e.occurredAt >= :from)
        AND (:to IS NULL OR e.occurredAt <= :to)
        AND (:q IS NULL OR LOCATE(:q, LOWER(e.sourceMeta)) > 0 OR LOCATE(:q, LOWER(e.content)) > 0
             OR LOCATE(:q, LOWER(e.source)) > 0 OR LOCATE(:q, LOWER(e.externalId)) > 0)
        AND EXISTS (SELECT a.id FROM SavedAnalysis a WHERE a.evidenceEntity = e
          AND EXISTS (SELECT r.id FROM SavedAnalysisReview r WHERE r.analysis = a
            AND r.id = (SELECT MAX(r2.id) FROM SavedAnalysisReview r2 WHERE r2.analysis = a)
            AND r.status = br.com.promova.analysis.review.ReviewStatus.ACCEPTED))
      """)
  long countAcceptedInbox(
      @Param("userId") Long userId, @Param("q") String q,
      @Param("source") String source, @Param("from") Instant from, @Param("to") Instant to);

  @Query("SELECT e FROM Evidence e WHERE e.id = :id AND e.user.id = :userId")
  Optional<Evidence> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM Evidence e WHERE e.id = :id AND e.user.id = :userId")
  Optional<Evidence> findByIdAndUserIdForUpdate(
      @Param("id") Long id, @Param("userId") Long userId);
}
