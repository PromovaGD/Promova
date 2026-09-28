package br.com.promova.evidence.service;

import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.evidence.EvidenceStatus;
import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.evidence.dto.EvidenceItemResponse;
import br.com.promova.evidence.dto.EvidenceInboxResponse;
import br.com.promova.evidence.dto.EvidenceInboxItemResponse;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.common.PageResponse;
import br.com.promova.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvidenceService {
  private final EvidenceRepository evidenceRepository;
  private final SavedAnalysisRepository savedAnalysisRepository;

  public EvidenceService(
      EvidenceRepository evidenceRepository, SavedAnalysisRepository savedAnalysisRepository) {
    this.evidenceRepository = evidenceRepository;
    this.savedAnalysisRepository = savedAnalysisRepository;
  }

  @Transactional(readOnly = true)
  public List<EvidenceResponse> listForUser(
      User user, String statusValue, Instant from, Instant to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
    }

    EvidenceStatus status = parseStatus(statusValue);
    return evidenceRepository.findForUser(user.getId(), status, from, to).stream()
        .map(EvidenceResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public EvidenceResponse getForUser(User user, Long evidenceId) {
    return evidenceRepository
        .findByIdAndUserId(evidenceId, user.getId())
        .map(EvidenceResponse::from)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidência não encontrada."));
  }

  @Transactional(readOnly = true)
  public EvidenceItemResponse getCanonicalForUser(User user, Long evidenceId) {
    Evidence evidence = findOwnedEntity(user, evidenceId);
    return toCanonical(evidence);
  }

  @Transactional(readOnly = true)
  public PageResponse<EvidenceItemResponse> listForUserPaged(
      User user,
      String statusValue,
      String source,
      Instant from,
      Instant to,
      Pageable pageable) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
    }
    EvidenceStatus status = parseStatus(statusValue);
    var page =
        evidenceRepository.findForUserPaged(
            user.getId(), status, normalizeOptional(source), from, to, pageable);
    return PageResponse.from(page.map(this::toCanonical));
  }

  @Transactional(readOnly = true)
  public EvidenceInboxResponse inbox(User user, String bucket, String q, String source,
      Instant from, Instant to, String sort, Pageable pageable) {
    String selected = bucket == null || bucket.isBlank() ? "pending" : bucket.toLowerCase(Locale.ROOT);
    if (!List.of("pending", "accepted", "dismissed", "all").contains(selected))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado da caixa inválido.");
    String order = sort == null || sort.isBlank() ? "newest" : sort.toLowerCase(Locale.ROOT);
    if (!List.of("newest", "oldest").contains(order))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ordenação inválida.");
    if (from != null && to != null && from.isAfter(to))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
    String search = normalizeOptional(q);
    if (search != null && search.length() > 120)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Busca muito longa.");
    search = search == null ? null : search.toLowerCase(Locale.ROOT);
    Sort.Direction direction = order.equals("oldest") ? Sort.Direction.ASC : Sort.Direction.DESC;
    Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
        Sort.by(direction, "occurredAt", "id"));
    var page = evidenceRepository.findInbox(user.getId(), selected, search,
        normalizeOptional(source), from, to, sorted);
    Map<Long, Long> analyses = new HashMap<>();
    if (!page.isEmpty()) {
      savedAnalysisRepository.findForEvidenceIds(user.getId(),
          page.getContent().stream().map(Evidence::getId).toList())
          .forEach(a -> analyses.put(a.getEvidenceEntity().getId(), a.getId()));
    }
    Map<String, Long> counts = new HashMap<>(Map.of("pending", 0L, "accepted", 0L,
        "dismissed", 0L, "all", 0L));
    for (Object[] row : evidenceRepository.countInboxStatuses(user.getId(), search,
        normalizeOptional(source), from, to)) {
      EvidenceStatus status = (EvidenceStatus) row[0];
      long count = ((Number) row[1]).longValue();
      counts.put("all", counts.get("all") + count);
      if (status == EvidenceStatus.PENDING) counts.put("pending", count);
      if (status == EvidenceStatus.DISMISSED) counts.put("dismissed", count);
    }
    counts.put("accepted", evidenceRepository.countAcceptedInbox(user.getId(), search,
        normalizeOptional(source), from, to));
    List<EvidenceInboxItemResponse> items = page.getContent().stream()
        .map(e -> EvidenceInboxItemResponse.from(e, analyses.get(e.getId()),
            selected.equals("accepted") ? "ACCEPTED" : null)).toList();
    return new EvidenceInboxResponse(items, page.getNumber() + 1, page.getSize(),
        page.getTotalElements(), counts);
  }

  @Transactional
  public void attachSourceFacts(User user, Long evidenceId, String author,
      String mergedAt, String labels) {
    Evidence evidence = findOwnedEntity(user, evidenceId);
    Instant merged = null;
    if (mergedAt != null && !mergedAt.isBlank()) {
      try { merged = Instant.parse(mergedAt); }
      catch (DateTimeParseException ignored) { /* Source may not provide a valid merge date. */ }
    }
    evidence.setSourceFacts(author, merged, labels);
  }

  @Transactional(readOnly = true)
  public Optional<EvidenceResponse> findByNaturalKey(
      User user, String source, String externalId) {
    return evidenceRepository
        .findByUserIdAndSourceAndExternalId(
            user.getId(), normalizeRequired(source, "source"), normalizeRequired(externalId, "externalId"))
        .map(EvidenceResponse::from);
  }

  @Transactional
  public EvidenceResponse capture(
      User user,
      String source,
      String externalId,
      String sourceMeta,
      String content,
      String sourceUrl) {
    return capture(user, source, externalId, sourceMeta, content, sourceUrl, Instant.now());
  }

  @Transactional
  public EvidenceResponse capture(
      User user,
      String source,
      String externalId,
      String sourceMeta,
      String content,
      String sourceUrl,
      Instant occurredAt) {
    return captureResult(user, source, externalId, sourceMeta, content, sourceUrl, occurredAt)
        .evidence();
  }

  /**
   * Captures through the same owner/source/external-id uniqueness boundary while also telling
   * integrations whether the row was newly created or already present.
   */
  @Transactional
  public EvidenceCaptureResult captureResult(
      User user,
      String source,
      String externalId,
      String sourceMeta,
      String content,
      String sourceUrl) {
    return captureResult(user, source, externalId, sourceMeta, content, sourceUrl, Instant.now());
  }

  @Transactional
  public EvidenceCaptureResult captureResult(
      User user,
      String source,
      String externalId,
      String sourceMeta,
      String content,
      String sourceUrl,
      Instant occurredAt) {
    String normalizedSource = normalizeRequired(source, "source");
    String normalizedExternalId = normalizeRequired(externalId, "externalId");

    Optional<Evidence> existing =
        evidenceRepository.findByUserIdAndSourceAndExternalId(
            user.getId(), normalizedSource, normalizedExternalId);
    if (existing.isPresent()) {
      return new EvidenceCaptureResult(EvidenceResponse.from(existing.get()), false);
    }

    Evidence created =
        evidenceRepository.save(
            new Evidence(
                user,
                normalizedSource,
                normalizedExternalId,
                sourceMeta,
                content,
                sourceUrl,
                occurredAt,
                Instant.now()));
    return new EvidenceCaptureResult(EvidenceResponse.from(created), true);
  }

  @Transactional
  public EvidenceResponse dismiss(User user, Long evidenceId) {
    Evidence evidence = findOwnedEntityForUpdate(user, evidenceId);
    try {
      evidence.dismiss();
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Evidência não pode ser dispensada neste estado.");
    }
    return EvidenceResponse.from(evidenceRepository.save(evidence));
  }

  /**
   * Controlled transition for the server-owned analysis workflow. There is intentionally no
   * browser-facing endpoint for this method in this task.
   */
  @Transactional
  public Evidence markAnalyzed(User user, Long evidenceId) {
    Evidence evidence = findOwnedEntity(user, evidenceId);
    try {
      evidence.markAnalyzed();
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Evidência não pode ser analisada neste estado.");
    }
    return evidenceRepository.save(evidence);
  }

  private Evidence findOwnedEntity(User user, Long evidenceId) {
    return evidenceRepository
        .findByIdAndUserId(evidenceId, user.getId())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidência não encontrada."));
  }

  private Evidence findOwnedEntityForUpdate(User user, Long evidenceId) {
    return evidenceRepository
        .findByIdAndUserIdForUpdate(evidenceId, user.getId())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidência não encontrada."));
  }

  private EvidenceStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return EvidenceStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de evidência inválido.");
    }
  }

  private String normalizeRequired(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
    }
    return value.trim();
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private EvidenceItemResponse toCanonical(Evidence evidence) {
    var analysis =
        savedAnalysisRepository
            .findByEvidenceIdAndUserId(evidence.getId(), evidence.getUser().getId())
            .orElse(null);
    return EvidenceItemResponse.from(
        evidence,
        analysis == null ? null : analysis.getId(),
        analysis == null ? null : analysis.getGeneratedTitle());
  }
}
