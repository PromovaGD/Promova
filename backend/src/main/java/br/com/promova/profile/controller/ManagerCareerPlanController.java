package br.com.promova.profile.controller;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.profile.CareerPlanService;
import br.com.promova.manager.team.ManagerTeamService;
import br.com.promova.profile.dto.CareerObjectiveRequest;
import br.com.promova.profile.dto.CareerObjectiveResponse;
import br.com.promova.profile.dto.CareerPlanUpdateRequest;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/manager/employees/{employeeId}/career-plan")
public class ManagerCareerPlanController {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final CareerPlanService careerPlanService;
  private final ManagerTeamService managerTeamService;

  public ManagerCareerPlanController(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      CareerPlanService careerPlanService,
      ManagerTeamService managerTeamService) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.careerPlanService = careerPlanService;
    this.managerTeamService = managerTeamService;
  }

  @GetMapping
  public ProfileResponse getPlan(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long employeeId) {
    managerTeamService.requireVisible(requireManager(authorization), employeeId);
    return careerPlanService.getPlan(employeeId);
  }

  @PutMapping
  public ProfileResponse updatePlan(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long employeeId,
      @Valid @RequestBody CareerPlanUpdateRequest request) {
    managerTeamService.requireVisible(requireManager(authorization), employeeId);
    return careerPlanService.updatePlan(employeeId, request);
  }

  @PostMapping("/objectives")
  public CareerObjectiveResponse createObjective(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long employeeId,
      @Valid @RequestBody CareerObjectiveRequest request) {
    User manager = requireManager(authorization);
    managerTeamService.requireVisible(manager, employeeId);
    return careerPlanService.createObjective(employeeId, request, manager);
  }

  @PutMapping("/objectives/{objectiveId}")
  public CareerObjectiveResponse updateObjective(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long employeeId,
      @PathVariable Long objectiveId,
      @Valid @RequestBody CareerObjectiveRequest request) {
    User manager = requireManager(authorization);
    managerTeamService.requireVisible(manager, employeeId);
    return careerPlanService.updateObjective(employeeId, objectiveId, request, manager);
  }

  private User requireManager(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    User user = authService.requireUser(token);
    if (user.getRole() != UserRole.MANAGER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso restrito a gestores.");
    }
    return user;
  }
}
