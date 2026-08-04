package br.com.promova.auth.controller;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.auth.dto.AuthResponse;
import br.com.promova.auth.dto.LoginRequest;
import br.com.promova.auth.dto.RegisterRequest;
import br.com.promova.auth.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;

  public AuthController(AuthService authService, AuthTokenResolver authTokenResolver) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
  }

  @PostMapping("/register")
  public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token != null) {
      authService.logout(token);
    }
  }

  @GetMapping("/me")
  public UserSummaryResponse me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    return authService.currentUser(requireToken(authorization));
  }

  private String requireToken(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    return token;
  }
}
