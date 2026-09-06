package br.com.promova.manager.controller;

import br.com.promova.analysis.dto.SavedAnalysisResponse;
import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.evidence.service.EvidenceService;
import br.com.promova.manager.dto.ManagerEmployeeSummaryResponse;
import br.com.promova.profile.CareerObjectiveRepository;
import br.com.promova.profile.CareerProfile;
import br.com.promova.profile.CareerProfileRepository;
import br.com.promova.profile.ObjectiveStatus;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/manager")
public class ManagerController {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final UserRepository userRepository;
  private final SavedAnalysisService savedAnalysisService;
  private final CareerProfileRepository careerProfileRepository;
  private final CareerObjectiveRepository careerObjectiveRepository;
  private final EvidenceService evidenceService;

  public ManagerController(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      UserRepository userRepository,
      SavedAnalysisService savedAnalysisService,
      CareerProfileRepository careerProfileRepository,
      CareerObjectiveRepository careerObjectiveRepository,
      EvidenceService evidenceService) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.userRepository = userRepository;
    this.savedAnalysisService = savedAnalysisService;
    this.careerProfileRepository = careerProfileRepository;
    this.careerObjectiveRepository = careerObjectiveRepository;
    this.evidenceService = evidenceService;
  }

  @GetMapping("/employees")
  @Transactional(readOnly = true)
  public List<ManagerEmployeeSummaryResponse> employees(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long jobRoleId,
      @RequestParam(required = false) String level) {
    requireManager(authorization);
    String normalizedQuery = normalize(query);
    String normalizedLevel = normalize(level);
    return userRepository.findByRoleOrderByNameAsc(UserRole.EMPLOYEE).stream()
        .map(this::employeeSummary)
        .filter(
            employee ->
                normalizedQuery == null
                    || employee.name().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || employee.email().toLowerCase(Locale.ROOT).contains(normalizedQuery))
        .filter(employee -> jobRoleId == null || jobRoleId.equals(employee.jobRoleId()))
        .filter(
            employee ->
                normalizedLevel == null
                    || normalizedLevel.equals(normalize(employee.currentLevel()))
                    || normalizedLevel.equals(normalize(employee.targetLevel())))
        .toList();
  }

  @GetMapping("/employees/{userId}/evidences")
  @Transactional(readOnly = true)
  public List<EvidenceResponse> employeeEvidences(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long userId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    requireManager(authorization);
    return evidenceService.listForUser(requireEmployee(userId), status, from, to);
  }

  @GetMapping("/employees/{userId}/analyses")
  @Transactional(readOnly = true)
  public List<SavedAnalysisResponse> employeeAnalyses(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    requireManager(authorization);
    return savedAnalysisService.listForUser(requireEmployee(userId), from, to);
  }

  private ManagerEmployeeSummaryResponse employeeSummary(User employee) {
    CareerProfile profile = careerProfileRepository.findByUserId(employee.getId()).orElse(null);
    long activeObjectiveCount =
        profile == null
            ? 0
            : careerObjectiveRepository.countByCareerProfileIdAndStatus(
                profile.getId(), ObjectiveStatus.ACTIVE);
    return ManagerEmployeeSummaryResponse.from(employee, profile, activeObjectiveCount);
  }

  private User requireEmployee(Long userId) {
    return userRepository
        .findById(userId)
        .filter(user -> user.getRole() == UserRole.EMPLOYEE)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private User requireManager(String authorization) {
    User user = authService.requireUser(requireToken(authorization));
    if (user.getRole() != UserRole.MANAGER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso restrito a gestores.");
    }
    return user;
  }

  private String requireToken(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    }
    return token;
  }
}
