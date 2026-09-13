package br.com.promova.analysis.review.service;

import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.analysis.review.ReviewStatus;
import br.com.promova.analysis.review.dto.AnalysisReviewResponse;
import br.com.promova.analysis.review.dto.SavedAnalysisReviewRequest;
import br.com.promova.analysis.review.dto.SavedAnalysisReviewResponse;
import br.com.promova.analysis.review.dto.ReviewQueueItemResponse;
import br.com.promova.analysis.review.dto.ReviewQueueResponse;
import br.com.promova.analysis.review.persistence.SavedAnalysisReview;
import br.com.promova.analysis.review.persistence.SavedAnalysisReviewRepository;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Pageable;
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

  @Transactional(readOnly = true)
  public ReviewQueueResponse queue(
      String statusValue,
      Long employeeId,
      String source,
      Instant from,
      Instant to,
      Pageable pageable) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
    }
    ReviewStatus status = parseQueueStatus(statusValue);
    String normalizedSource = source == null || source.isBlank() ? null : source.trim();
    var page =
        reviewRepository.findReviewQueue(
            status == null ? null : status.name(),
            employeeId,
            normalizedSource,
            from,
            to,
            pageable);
    List<ReviewQueueItemResponse> items =
        page.getContent().stream()
            .map(
                item ->
                    new ReviewQueueItemResponse(
                        item.getAnalysisId(),
                        item.getOwnerId(),
                        item.getEmployeeName(),
                        item.getEmployeeEmail(),
                        item.getSource(),
                        item.getSourceMeta(),
                        item.getImpactLevel(),
                        item.getConfidence(),
                        item.getCreatedAt().toInstant(),
                        ReviewStatus.valueOf(item.getCurrentStatus()),
                        item.getLatestReviewId()))
            .toList();
    Map<ReviewStatus, Long> counts = new EnumMap<>(ReviewStatus.class);
    for (ReviewStatus value : ReviewStatus.values()) {
      counts.put(value, 0L);
    }
    reviewRepository
        .countReviewQueueByStatus(employeeId, normalizedSource, from, to)
        .forEach(
            count -> counts.put(ReviewStatus.valueOf(count.getCurrentStatus()), count.getTotal()));
    return new ReviewQueueResponse(
        items, page.getNumber() + 1, page.getSize(), page.getTotalElements(), counts);
  }

  @Transactional
  public AnalysisReviewResponse appendForManager(
      User reviewer, Long ownerId, Long analysisId, SavedAnalysisReviewRequest request) {
    if (reviewer == null || reviewer.getRole() != UserRole.MANAGER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager review access required.");
    }

    SavedAnalysis analysis = requireAnalysis(analysisId, ownerId);
    String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
    if (idempotencyKey != null
        && reviewRepository
            .findByAnalysisIdAndReviewerIdAndIdempotencyKey(
                analysisId, reviewer.getId(), idempotencyKey)
            .isPresent()) {
      return responseFor(analysis);
    }

    Long latestReviewId =
        reviewRepository
            .findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId)
            .map(SavedAnalysisReview::getId)
            .orElse(null);
    if (request.expectedLatestReviewId() != null
        && !request.expectedLatestReviewId().equals(latestReviewId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A revisão mudou. Atualize o histórico antes de tentar novamente.");
    }

    ReviewStatus status = ReviewStatus.parseAction(request.status());
    String comment = normalizeComment(request.comment());
    reviewRepository.save(
        new SavedAnalysisReview(
            analysis, reviewer, status, comment, Instant.now(), idempotencyKey));
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

  private String normalizeIdempotencyKey(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > 80) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Idempotency key must be at most 80 characters.");
    }
    return normalized;
  }

  private ReviewStatus parseQueueStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return ReviewStatus.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de revisão inválido.");
    }
  }
}
