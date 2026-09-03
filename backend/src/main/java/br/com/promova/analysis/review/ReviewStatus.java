package br.com.promova.analysis.review;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The complete review state vocabulary. An analysis without review events is UNREVIEWED. */
public enum ReviewStatus {
  UNREVIEWED,
  ACCEPTED,
  NEEDS_CONTEXT;

  /** Parses a manager action status; UNREVIEWED is derived and cannot be appended as an event. */
  public static ReviewStatus parseAction(String value) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review status is required.");
    }

    try {
      ReviewStatus status = valueOf(value.trim().toUpperCase(Locale.ROOT));
      if (status == UNREVIEWED) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "UNREVIEWED is only the state before the first review.");
      }
      return status;
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid review status.");
    }
  }
}
