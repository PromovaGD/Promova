package br.com.promova.analysis.persistence;

import br.com.promova.user.User;
import br.com.promova.analysis.review.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedAnalysisRepository extends JpaRepository<SavedAnalysis, Long> {
  List<SavedAnalysis> findByUserOrderByCreatedAtDesc(User user);

  @Query(
      """
      SELECT a FROM SavedAnalysis a
      WHERE a.id = :analysisId
        AND a.user.id = :userId
      """)
  Optional<SavedAnalysis> findByIdAndUserId(
      @Param("analysisId") Long analysisId, @Param("userId") Long userId);

  @Query(
      """
      SELECT a FROM SavedAnalysis a
      WHERE a.evidenceEntity.id = :evidenceId
        AND a.user.id = :userId
      """)
  Optional<SavedAnalysis> findByEvidenceIdAndUserId(
      @Param("evidenceId") Long evidenceId, @Param("userId") Long userId);

  @Query(
      """
      SELECT a FROM SavedAnalysis a
      WHERE a.user = :user
        AND (:from IS NULL OR a.createdAt >= :from)
        AND (:to IS NULL OR a.createdAt <= :to)
      ORDER BY a.createdAt DESC
      """)
  List<SavedAnalysis> findByUserAndDateRange(
      @Param("user") User user, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      """
      SELECT a FROM SavedAnalysis a
      WHERE a.user = :user
        AND (:source IS NULL OR LOWER(a.source) = LOWER(:source))
        AND (:from IS NULL OR a.createdAt >= :from)
        AND (:to IS NULL OR a.createdAt <= :to)
        AND (
          :reviewStatus IS NULL
          OR (:reviewStatus = br.com.promova.analysis.review.ReviewStatus.UNREVIEWED
            AND NOT EXISTS (SELECT r0.id FROM SavedAnalysisReview r0 WHERE r0.analysis = a))
          OR EXISTS (
            SELECT r.id FROM SavedAnalysisReview r
            WHERE r.analysis = a
              AND r.id = (SELECT MAX(r2.id) FROM SavedAnalysisReview r2 WHERE r2.analysis = a)
              AND r.status = :reviewStatus
          )
        )
      ORDER BY a.createdAt DESC, a.id DESC
      """)
  Page<SavedAnalysis> findByUserAndDateRangePaged(
      @Param("user") User user,
      @Param("source") String source,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("reviewStatus") ReviewStatus reviewStatus,
      Pageable pageable);

  @Modifying
  @Query("DELETE FROM SavedAnalysis a WHERE a.user = :user")
  void deleteAllByUser(@Param("user") User user);

  @Modifying
  @Query(
      """
      DELETE FROM SavedAnalysis a
      WHERE a.user = :user
        AND (:from IS NULL OR a.createdAt >= :from)
        AND (:to IS NULL OR a.createdAt <= :to)
      """)
  void deleteByUserAndDateRange(
      @Param("user") User user, @Param("from") Instant from, @Param("to") Instant to);
}
