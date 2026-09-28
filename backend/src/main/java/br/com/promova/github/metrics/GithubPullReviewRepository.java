package br.com.promova.github.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubPullReviewRepository extends JpaRepository<GithubPullReview, Long> {
  boolean existsByPullRequestIdAndProviderReviewId(Long pullRequestId, Long providerReviewId);
}
