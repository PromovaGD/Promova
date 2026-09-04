package br.com.promova.config;

import br.com.promova.github.support.GithubApiException;
import br.com.promova.github.support.GithubPayloadException;
import br.com.promova.organization.JobRoleInUseException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException exception) {
    String message =
        switch (exception.getStatusCode().value()) {
          case 401 -> "Autenticação necessária.";
          case 403 -> "Você não tem permissão para realizar esta ação.";
          default ->
              exception.getReason() == null ? "Erro na requisição." : exception.getReason();
        };
    return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(
      MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("Invalid request.");
    return ResponseEntity.badRequest().body(Map.of("message", message));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, String>> handleUnreadableRequest(
      HttpMessageNotReadableException exception) {
    return ResponseEntity.badRequest().body(Map.of("message", "Invalid request body."));
  }

  @ExceptionHandler(GithubApiException.class)
  public ResponseEntity<Map<String, String>> handleGithubApi(GithubApiException exception) {
    String message =
        switch (exception.statusCode()) {
          case 401 -> "GitHub rejected the configured server token.";
          case 403 -> "GitHub denied access or the server token is rate-limited.";
          case 404 -> "GitHub repository was not found or is not accessible.";
          case 429 -> "GitHub API rate limit exceeded. Try again later.";
          default -> "GitHub API request failed. Try again later.";
        };
    return ResponseEntity.status(exception.statusCode()).body(Map.of("message", message));
  }

  @ExceptionHandler(GithubPayloadException.class)
  public ResponseEntity<Map<String, String>> handleGithubPayload(GithubPayloadException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(Map.of("message", "GitHub returned an invalid response."));
  }

  @ExceptionHandler(JobRoleInUseException.class)
  public ResponseEntity<Map<String, Object>> handleJobRoleInUse(JobRoleInUseException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            Map.of(
                "message", exception.getMessage(),
                "affectedCount", exception.affectedCount()));
  }
}
