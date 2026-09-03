package br.com.promova.analysis.review.service;

import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.analysis.review.ReviewStatus;
import br.com.promova.analysis.review.dto.AnalysisReviewResponse;
import br.com.promova.analysis.review.dto.SavedAnalysisReviewRequest;
import br.com.promova.analysis.review.dto.SavedAnalysisReviewResponse;
import br.com.promova.analysis.review.persistence.SavedAnalysisReview;
import br.com.promova.analysis.review.persistence.SavedAnalysisReviewRepository;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalysisReviewService {
  private final SavedAnalysisRepository savedAnalysisRepository;
  private final SavedAnalysisReviewRepository reviewRepository;

  public AnalysisReviewService(
      SavedAnalysisRepository savedAnalysisRepository,
      SavedAnalysisReviewRepository reviewRepository) {
    this.savedAnalysisRepository = savedAnalysisRepository;
    this.reviewRepository = reviewRepository;
  }

  @Transactional(readOnly = true)
  public AnalysisReviewResponse listForOwner(User owner, Long analysisId) {
    SavedAnalysis analysis = requireAnalysis(analysisId, owner.getId());
    return responseFor(analysis);
  }

  @Transactional(readOnly = true)
  public AnalysisReviewResponse listForManager(Long ownerId, Long analysisId) {
    SavedAnalysis analysis = requireAnalysis(analysisId, ownerId);
    return responseFor(analysis);
  }

  @Transactional
  public AnalysisReviewResponse appendForManager(
      User reviewer, Long ownerId, Long analysisId, SavedAnalysisReviewRequest request) {
    if (reviewer == null || reviewer.getRole() != UserRole.MANAGER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager review access required.");
    }

    SavedAnalysis analysis = requireAnalysis(analysisId, ownerId);
    ReviewStatus status = ReviewStatus.parseAction(request.status());
    String comment = normalizeComment(request.comment());
    reviewRepository.save(
        new SavedAnalysisReview(analysis, reviewer, status, comment, Instant.now()));
    return responseFor(analysis);
  }

  private SavedAnalysis requireAnalysis(Long analysisId, Long ownerId) {
    return savedAnalysisRepository
        .findByIdAndUserId(analysisId, ownerId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found."));
  }

  private AnalysisReviewResponse responseFor(SavedAnalysis analysis) {
    List<SavedAnalysisReview> reviews =
        reviewRepository.findHistoryForAnalysis(analysis.getId(), analysis.getUser().getId());
    List<SavedAnalysisReviewResponse> history = reviews.stream().map(this::toResponse).toList();
    ReviewStatus currentStatus =
        history.isEmpty() ? ReviewStatus.UNREVIEWED : history.get(history.size() - 1).status();
    return new AnalysisReviewResponse(analysis.getId(), currentStatus, history);
  }

  private SavedAnalysisReviewResponse toResponse(SavedAnalysisReview review) {
    User reviewer = review.getReviewer();
    return new SavedAnalysisReviewResponse(
        review.getId(),
        reviewer.getId(),
        reviewer.getName(),
        reviewer.getEmail(),
        review.getCreatedAt(),
        review.getStatus(),
        review.getComment());
  }

  private String normalizeComment(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();
    if (normalized.length() > SavedAnalysisReviewRequest.MAX_COMMENT_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Review comment must be at most "
              + SavedAnalysisReviewRequest.MAX_COMMENT_LENGTH
              + " characters.");
    }
    return normalized.isEmpty() ? null : normalized;
  }
}
