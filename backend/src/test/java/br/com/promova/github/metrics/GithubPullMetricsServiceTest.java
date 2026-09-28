package br.com.promova.github.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.github.client.GithubApiClient;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class GithubPullMetricsServiceTest {
  @Mock private GithubPullRequestFactRepository facts;
  @Mock private GithubPullReviewRepository reviews;
  @Mock private GithubApiClient github;
  @Mock private GithubPullRequestService pullRequests;

  @Test
  void refreshesVisibleReviewEventsWithProviderIdDeduplication() throws Exception {
    User manager = new User("Gestora", "manager@example.com", "hash", UserRole.MANAGER);
    ReflectionTestUtils.setField(manager, "id", 11L);
    User employee = new User("Pessoa", "person@example.com", "hash", UserRole.EMPLOYEE);
    GithubPullRequestFact fact = new GithubPullRequestFact(employee, "acme/project", 42);
    ReflectionTestUtils.setField(fact, "id", 99L);
    when(facts.recentVisible(eq(11L), eq(PageRequest.of(0, 10)))).thenReturn(List.of(fact));
    when(github.get("/repos/acme/project/pulls/42/reviews?per_page=100&page=1"))
        .thenReturn(new ObjectMapper().readTree("""
            [{"id":101,"user":{"login":"reviewer"},"state":"APPROVED",
              "submitted_at":"2026-09-20T10:00:00Z"},
             {"id":102,"user":{"login":"reviewer"},"state":"COMMENTED"}]
            """));
    when(github.get("/repos/acme/project/issues/42/events?per_page=100&page=1"))
        .thenReturn(new ObjectMapper().readTree("""
            [{"event":"review_requested","created_at":"2026-09-19T10:00:00Z"},
             {"event":"review_requested","created_at":"2026-09-18T10:00:00Z"}]
            """));
    when(reviews.existsByPullRequestIdAndProviderReviewId(99L, 101L)).thenReturn(false);
    when(reviews.existsByPullRequestIdAndProviderReviewId(99L, 102L)).thenReturn(true);

    var result = new GithubPullMetricsService(facts, reviews, github, pullRequests)
        .refreshReviews(manager, 10);

    assertThat(result.pullRequests()).isEqualTo(1);
    assertThat(result.reviewsAdded()).isEqualTo(1);
    assertThat(result.failedPullRequests()).isZero();
    ArgumentCaptor<GithubPullReview> captured = ArgumentCaptor.forClass(GithubPullReview.class);
    verify(reviews).save(captured.capture());
    assertThat(ReflectionTestUtils.getField(captured.getValue(), "providerReviewId")).isEqualTo(101L);
    assertThat(ReflectionTestUtils.getField(fact, "firstReviewRequestedAt"))
        .isEqualTo(java.time.Instant.parse("2026-09-18T10:00:00Z"));
    verify(facts).save(fact);
  }
}
