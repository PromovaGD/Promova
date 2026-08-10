package br.com.promova.insight.controller;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.insight.dto.InsightsResponse;
import br.com.promova.insight.service.InsightsService;
import br.com.promova.user.User;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/insights")
public class InsightsController {
  private final InsightsService insightsService;
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;

  public InsightsController(
      InsightsService insightsService,
      AuthService authService,
      AuthTokenResolver authTokenResolver) {
    this.insightsService = insightsService;
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
  }

  /**
   * Returns the authenticated user's saved-evidence view. The endpoint intentionally has no
   * userId/employeeId parameter; from/to use the same inclusive ISO instant contract as /analyses.
   */
  @GetMapping
  public InsightsResponse get(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    User user = authService.requireUser(requireToken(authorization));
    return insightsService.summarizeForUser(user, from, to);
  }

  private String requireToken(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    return token;
  }
}
