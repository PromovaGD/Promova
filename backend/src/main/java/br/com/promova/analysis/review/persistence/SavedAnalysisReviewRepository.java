package br.com.promova.analysis.review.persistence;

import br.com.promova.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SavedAnalysisReviewRepository extends JpaRepository<SavedAnalysisReview, Long> {
  @Query(
      """
      SELECT r FROM SavedAnalysisReview r
      WHERE r.analysis.id = :analysisId
        AND r.analysis.user.id = :ownerId
      ORDER BY r.createdAt ASC, r.id ASC
      """)
  List<SavedAnalysisReview> findHistoryForAnalysis(
      @Param("analysisId") Long analysisId, @Param("ownerId") Long ownerId);

  Optional<SavedAnalysisReview> findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(Long analysisId);

  Optional<SavedAnalysisReview> findByAnalysisIdAndReviewerIdAndIdempotencyKey(
      Long analysisId, Long reviewerId, String idempotencyKey);

  @Query(
      value =
          """
          SELECT a.id AS analysisId,
                 a.user_id AS ownerId,
                 u.name AS employeeName,
                 u.email AS employeeEmail,
                 a.source AS source,
                 a.source_meta AS sourceMeta,
                 a.impact_level AS impactLevel,
                 a.confidence AS confidence,
                 a.created_at AS createdAt,
                 COALESCE((SELECT r.status FROM saved_analysis_reviews r
                           WHERE r.saved_analysis_id = a.id
                           ORDER BY r.created_at DESC, r.id DESC LIMIT 1), 'UNREVIEWED') AS currentStatus,
                 (SELECT r.id FROM saved_analysis_reviews r
                  WHERE r.saved_analysis_id = a.id
                  ORDER BY r.created_at DESC, r.id DESC LIMIT 1) AS latestReviewId
          FROM saved_analyses a
          JOIN users u ON u.id = a.user_id
          WHERE u.role = 'EMPLOYEE'
            AND (:employeeId IS NULL OR a.user_id = :employeeId)
            AND (:source IS NULL OR LOWER(a.source) = LOWER(:source))
            AND (:fromInstant IS NULL OR a.created_at >= :fromInstant)
            AND (:toInstant IS NULL OR a.created_at <= :toInstant)
            AND (:status IS NULL OR COALESCE((SELECT r.status FROM saved_analysis_reviews r
                                             WHERE r.saved_analysis_id = a.id
                                             ORDER BY r.created_at DESC, r.id DESC LIMIT 1), 'UNREVIEWED') = :status)
          ORDER BY a.created_at ASC, a.id ASC
          """,
      countQuery =
          """
          SELECT COUNT(*)
          FROM saved_analyses a
          JOIN users u ON u.id = a.user_id
          WHERE u.role = 'EMPLOYEE'
            AND (:employeeId IS NULL OR a.user_id = :employeeId)
            AND (:source IS NULL OR LOWER(a.source) = LOWER(:source))
            AND (:fromInstant IS NULL OR a.created_at >= :fromInstant)
            AND (:toInstant IS NULL OR a.created_at <= :toInstant)
            AND (:status IS NULL OR COALESCE((SELECT r.status FROM saved_analysis_reviews r
                                             WHERE r.saved_analysis_id = a.id
                                             ORDER BY r.created_at DESC, r.id DESC LIMIT 1), 'UNREVIEWED') = :status)
          """,
      nativeQuery = true)
  Page<ReviewQueueProjection> findReviewQueue(
      @Param("status") String status,
      @Param("employeeId") Long employeeId,
      @Param("source") String source,
      @Param("fromInstant") Instant from,
      @Param("toInstant") Instant to,
      Pageable pageable);

  @Query(
      value =
          """
          SELECT currentStatus, COUNT(*) AS total
          FROM (
            SELECT COALESCE((SELECT r.status FROM saved_analysis_reviews r
                             WHERE r.saved_analysis_id = a.id
                             ORDER BY r.created_at DESC, r.id DESC LIMIT 1), 'UNREVIEWED') AS currentStatus
            FROM saved_analyses a
            JOIN users u ON u.id = a.user_id
            WHERE u.role = 'EMPLOYEE'
              AND (:employeeId IS NULL OR a.user_id = :employeeId)
              AND (:source IS NULL OR LOWER(a.source) = LOWER(:source))
              AND (:fromInstant IS NULL OR a.created_at >= :fromInstant)
              AND (:toInstant IS NULL OR a.created_at <= :toInstant)
          ) statuses
          GROUP BY currentStatus
          """,
      nativeQuery = true)
  List<ReviewQueueCountProjection> countReviewQueueByStatus(
      @Param("employeeId") Long employeeId,
      @Param("source") String source,
      @Param("fromInstant") Instant from,
      @Param("toInstant") Instant to);

  /** Keeps the existing clear-analysis endpoint usable when review rows exist. */
  @Modifying
  @Query(
      """
      DELETE FROM SavedAnalysisReview r
      WHERE r.analysis.user = :user
        AND (:from IS NULL OR r.analysis.createdAt >= :from)
        AND (:to IS NULL OR r.analysis.createdAt <= :to)
      """)
  void deleteForAnalysisOwnerAndDateRange(
      @Param("user") User user, @Param("from") Instant from, @Param("to") Instant to);
}
