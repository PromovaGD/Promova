package br.com.promova.analysis.persistence;

import br.com.promova.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedAnalysisRepository extends JpaRepository<SavedAnalysis, Long> {
  List<SavedAnalysis> findByUserOrderByCreatedAtDesc(User user);

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
