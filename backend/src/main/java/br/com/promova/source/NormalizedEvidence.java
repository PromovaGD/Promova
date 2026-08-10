package br.com.promova.source;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral evidence produced by a {@link SourceAdapter}.
 *
 * <p>The model deliberately contains no provider DTO. {@code sourceMeta} is the stable display
 * metadata used by the existing evidence/dashboard flow; {@code providerMetadata} is bounded,
 * safe traceability metadata for integration code and diagnostics. Credentials, authorization
 * material, and raw provider payloads are never part of this contract.
 */
public record NormalizedEvidence(
    String source,
    String externalId,
    String sourceMeta,
    String evidence,
    String sourceUrl,
    String author,
    Instant occurredAt,
    Map<String, String> providerMetadata) {
  public NormalizedEvidence {
    source = SourceDataSanitizer.required(source, "source", 100);
    externalId = SourceDataSanitizer.required(externalId, "externalId", 500);
    sourceMeta = SourceDataSanitizer.required(sourceMeta, "sourceMeta", 1_000);
    evidence = SourceDataSanitizer.required(evidence, "evidence", 10_000);
    sourceUrl = SourceDataSanitizer.url(sourceUrl, 2_048);
    author = SourceDataSanitizer.nullable(author, 200);
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    providerMetadata = SourceDataSanitizer.metadata(providerMetadata);
  }

  /** Alias that makes the text role explicit at adapter call sites. */
  public String evidenceText() {
    return evidence;
  }

  /** Alias for providers whose native item is called a body/message. */
  public String body() {
    return evidence;
  }

  /** Alias for generic source integrations. */
  public Map<String, String> metadata() {
    return providerMetadata;
  }

  private static final class SourceDataSanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final java.util.regex.Pattern CREDENTIAL_ASSIGNMENT =
        java.util.regex.Pattern.compile(
            "(?i)(\\b(?:authorization|token|access[_-]?token|refresh[_-]?token|api[_-]?key|secret|password|credential)\\b\\s*(?:[:=]\\s*|\\s+))([^\\s,;]+)");
    private static final java.util.regex.Pattern BEARER_CREDENTIAL =
        java.util.regex.Pattern.compile("(?i)\\b(?:bearer|basic)\\s+[^\\s,;]+");
    private static final java.util.regex.Pattern TOKEN_SHAPE =
        java.util.regex.Pattern.compile(
            "(?i)\\b(?:gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|sk-[A-Za-z0-9_-]{12,})\\b");
    private static final java.util.regex.Pattern SENSITIVE_KEY =
        java.util.regex.Pattern.compile(
            "(?i)(?:token|secret|authorization|password|credential|api[_-]?key|private[_-]?key|cookie)");

    private SourceDataSanitizer() {}

    private static String required(String value, String field, int limit) {
      String normalized = optional(value, limit);
      if (normalized.isBlank()) {
        throw new IllegalArgumentException(field + " is required");
      }
      return normalized;
    }

    private static String optional(String value, int limit) {
      if (value == null || value.isBlank()) {
        return "";
      }
      String sanitized = redact(value.trim());
      return sanitized.length() <= limit ? sanitized : sanitized.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static String nullable(String value, int limit) {
      return value == null || value.isBlank() ? null : optional(value, limit);
    }

    private static String url(String value, int limit) {
      if (value == null || value.isBlank()) {
        return null;
      }
      String normalized = value.trim();
      try {
        java.net.URI uri = java.net.URI.create(normalized);
        String query = uri.getRawQuery();
        if (query != null
            && java.util.Arrays.stream(query.split("&"))
                .anyMatch(part -> SENSITIVE_KEY.matcher(part.split("=", 2)[0]).find())) {
          normalized =
              new java.net.URI(uri.getScheme(), uri.getRawAuthority(), uri.getRawPath(), null, null)
                  .toString();
        } else if (uri.getRawFragment() != null
            && SENSITIVE_KEY.matcher(uri.getRawFragment()).find()) {
          normalized =
              new java.net.URI(uri.getScheme(), uri.getRawAuthority(), uri.getRawPath(), query, null)
                  .toString();
        }
      } catch (java.net.URISyntaxException | IllegalArgumentException ignored) {
        normalized = redact(normalized);
      }
      normalized = redact(normalized);
      return normalized.length() <= limit
          ? normalized
          : normalized.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static Map<String, String> metadata(Map<String, String> values) {
      if (values == null || values.isEmpty()) {
        return Map.of();
      }
      Map<String, String> safe = new LinkedHashMap<>();
      values.forEach(
          (key, value) -> {
            if (key == null || key.isBlank() || SENSITIVE_KEY.matcher(key).find()) {
              return;
            }
            String safeKey = key.trim();
            String safeValue = optional(value, 500);
            if (!safeValue.isBlank()) {
              safe.put(safeKey, safeValue);
            }
          });
      return Map.copyOf(safe);
    }

    private static String redact(String value) {
      String sanitized = TOKEN_SHAPE.matcher(value).replaceAll(REDACTED);
      sanitized = BEARER_CREDENTIAL.matcher(sanitized).replaceAll(REDACTED);
      return CREDENTIAL_ASSIGNMENT.matcher(sanitized).replaceAll("$1" + REDACTED);
    }
  }
}
