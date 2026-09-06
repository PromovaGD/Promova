package br.com.promova.evidence.service;

import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.evidence.EvidenceStatus;
import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvidenceService {
  private final EvidenceRepository evidenceRepository;

  public EvidenceService(EvidenceRepository evidenceRepository) {
    this.evidenceRepository = evidenceRepository;
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
}
