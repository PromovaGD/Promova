package br.com.promova.analysis.review.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.analysis.review.ReviewStatus;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SavedAnalysisReviewRepositoryTest {
  private static final Instant SAME_INSTANT = Instant.parse("2026-08-09T12:00:00Z");

  @Autowired private UserRepository userRepository;
  @Autowired private SavedAnalysisRepository savedAnalysisRepository;
  @Autowired private SavedAnalysisReviewRepository reviewRepository;

  @Test
  void ordersSameTimestampEventsByServerGeneratedId() {
    User owner =
        userRepository.save(new User("Owner", "owner-order@example.com", "hash", UserRole.EMPLOYEE));
    User manager =
        userRepository.save(
            new User("Manager", "manager-order@example.com", "hash", UserRole.MANAGER));
    SavedAnalysis analysis =
        savedAnalysisRepository.save(
            new SavedAnalysis(
                "ordering-analysis",
                owner,
                "GitHub",
                "PR #1",
                "Evidence",
                "L3",
                "L4",
                "L4",
                "high",
                "Reasoning",
                "[]",
                "[]",
                "Ready",
                SAME_INSTANT));

    SavedAnalysisReview first =
        reviewRepository.save(
            new SavedAnalysisReview(
                analysis, manager, ReviewStatus.ACCEPTED, "First", SAME_INSTANT));
    SavedAnalysisReview second =
        reviewRepository.save(
            new SavedAnalysisReview(
                analysis, manager, ReviewStatus.NEEDS_CONTEXT, "Second", SAME_INSTANT));

    List<SavedAnalysisReview> history =
        reviewRepository.findHistoryForAnalysis(analysis.getId(), owner.getId());

    assertThat(history).extracting(SavedAnalysisReview::getId).containsExactly(first.getId(), second.getId());
    assertThat(history).extracting(SavedAnalysisReview::getStatus)
        .containsExactly(ReviewStatus.ACCEPTED, ReviewStatus.NEEDS_CONTEXT);
  }
}
