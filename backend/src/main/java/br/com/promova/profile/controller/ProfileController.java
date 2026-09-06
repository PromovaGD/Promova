package br.com.promova.profile.controller;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.profile.ProfileService;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/profile")
public class ProfileController {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final ProfileService profileService;

  public ProfileController(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      ProfileService profileService) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.profileService = profileService;
  }

  @GetMapping
  public ProfileResponse getProfile(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    User user = authService.requireUser(requireToken(authorization));
    if (user.getRole() != UserRole.EMPLOYEE) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil disponível apenas ao funcionário.");
    }
    return profileService.getProfile(user);
  }

  private String requireToken(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    return token;
  }
}
