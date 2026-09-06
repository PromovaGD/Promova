package br.com.promova.organization.controller;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.organization.OrganizationConfigurationService;
import br.com.promova.organization.dto.CareerConfigurationResponse;
import br.com.promova.organization.dto.JobRoleArchiveRequest;
import br.com.promova.organization.dto.JobRoleRequest;
import br.com.promova.organization.dto.JobRoleResponse;
import br.com.promova.organization.dto.TerminologyResponse;
import br.com.promova.organization.dto.TerminologyUpdateRequest;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/manager/settings")
public class ManagerSettingsController {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final OrganizationConfigurationService configurationService;

  public ManagerSettingsController(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      OrganizationConfigurationService configurationService) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.configurationService = configurationService;
  }

  @GetMapping
  public CareerConfigurationResponse getSettings(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    requireManager(authorization);
    return configurationService.readConfiguration();
  }

  @GetMapping("/job-roles")
  public List<JobRoleResponse> listRoles(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(defaultValue = "false") boolean includeArchived) {
    requireManager(authorization);
    return configurationService.listRoles(includeArchived);
  }

  @PutMapping("/terminology")
  public TerminologyResponse updateTerminology(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody TerminologyUpdateRequest request) {
    requireManager(authorization);
    return configurationService.updateTerminology(request);
  }

  @PostMapping("/job-roles")
  public JobRoleResponse createRole(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody JobRoleRequest request) {
    requireManager(authorization);
    return configurationService.createRole(request);
  }

  @PutMapping("/job-roles/{roleId}")
  public JobRoleResponse updateRole(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long roleId,
      @Valid @RequestBody JobRoleRequest request) {
    requireManager(authorization);
    return configurationService.updateRole(roleId, request);
  }

  @PostMapping("/job-roles/{roleId}/archive")
  public JobRoleResponse archiveRole(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long roleId,
      @RequestBody(required = false) JobRoleArchiveRequest request) {
    requireManager(authorization);
    return configurationService.archiveRole(roleId, request);
  }

  private void requireManager(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    User user = authService.requireUser(token);
    if (user.getRole() != UserRole.MANAGER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso restrito a gestores.");
    }
  }
}
