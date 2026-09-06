package br.com.promova.organization.controller;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.organization.OrganizationConfigurationService;
import br.com.promova.organization.dto.CareerConfigurationResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/career-configuration")
public class CareerConfigurationController {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final OrganizationConfigurationService configurationService;

  public CareerConfigurationController(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      OrganizationConfigurationService configurationService) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.configurationService = configurationService;
  }

  @GetMapping
  public CareerConfigurationResponse get(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    authService.requireUser(token);
    return configurationService.readConfiguration();
  }
}
