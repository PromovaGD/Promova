package br.com.promova.github.controller;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.github.connection.GithubConnectionSettingsService;
import br.com.promova.github.connection.GithubSyncService;
import br.com.promova.github.connection.dto.GithubConnectionTestResponse;
import br.com.promova.github.connection.dto.GithubSettingsRequest;
import br.com.promova.github.connection.dto.GithubSettingsResponse;
import br.com.promova.github.connection.dto.GithubSyncResponse;
import br.com.promova.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/github")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class GithubConnectionController {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final GithubConnectionSettingsService settingsService;
  private final GithubSyncService syncService;
  private final GithubConnectionTestService connectionTestService;

  public GithubConnectionController(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      GithubConnectionSettingsService settingsService,
      GithubSyncService syncService,
      GithubConnectionTestService connectionTestService) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.settingsService = settingsService;
    this.syncService = syncService;
    this.connectionTestService = connectionTestService;
  }

  @GetMapping("/settings")
  public GithubSettingsResponse getSettings(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    return settingsService.getForUser(requireUser(authorization));
  }

  @PutMapping("/settings")
  public GithubSettingsResponse updateSettings(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody GithubSettingsRequest request) {
    return settingsService.updateForUser(requireUser(authorization), request);
  }

  @PostMapping("/settings/test")
  public GithubConnectionTestResponse testSettings(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    return connectionTestService.test(requireUser(authorization));
  }

  @PostMapping("/sync")
  public GithubSyncResponse sync(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    return syncService.sync(requireUser(authorization));
  }

  private User requireUser(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    return authService.requireUser(token);
  }
}
