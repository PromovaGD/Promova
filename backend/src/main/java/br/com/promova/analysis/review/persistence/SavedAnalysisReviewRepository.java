package br.com.promova.analysis.review.persistence;

import br.com.promova.user.User;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
