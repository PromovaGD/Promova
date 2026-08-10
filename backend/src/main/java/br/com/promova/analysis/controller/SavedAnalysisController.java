package br.com.promova.analysis.controller;

import br.com.promova.analysis.dto.SavedAnalysisResponse;
import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.user.User;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/analyses")
public class SavedAnalysisController {
  private final SavedAnalysisService savedAnalysisService;
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;

  public SavedAnalysisController(
      SavedAnalysisService savedAnalysisService,
      AuthService authService,
      AuthTokenResolver authTokenResolver) {
    this.savedAnalysisService = savedAnalysisService;
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
  }

  @GetMapping
  public List<SavedAnalysisResponse> list(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    User user = authService.requireUser(requireToken(authorization));
    return savedAnalysisService.listForUser(user, from, to);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clear(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    User user = authService.requireUser(requireToken(authorization));
    savedAnalysisService.clearForUser(user, from, to);
  }

  private String requireToken(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    return token;
  }
}
