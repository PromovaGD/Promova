package br.com.promova.manager.controller;

import br.com.promova.analysis.dto.SavedAnalysisResponse;
import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.analysis.review.service.AnalysisReviewService;
import br.com.promova.analysis.review.ReviewStatus;
import br.com.promova.common.PageRequestFactory;
import br.com.promova.common.PageResponse;
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
  private final AnalysisReviewService analysisReviewService;

  public ManagerController(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      UserRepository userRepository,
      SavedAnalysisService savedAnalysisService,
      CareerProfileRepository careerProfileRepository,
      CareerObjectiveRepository careerObjectiveRepository,
      EvidenceService evidenceService,
      AnalysisReviewService analysisReviewService) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.userRepository = userRepository;
    this.savedAnalysisService = savedAnalysisService;
    this.careerProfileRepository = careerProfileRepository;
    this.careerObjectiveRepository = careerObjectiveRepository;
    this.evidenceService = evidenceService;
    this.analysisReviewService = analysisReviewService;
  }

  @GetMapping("/employees")
  @Transactional(readOnly = true)
  public Object employees(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long jobRoleId,
      @RequestParam(required = false) String level,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    requireManager(authorization);
    String normalizedQuery = normalize(query);
    String normalizedLevel = normalize(level);
    if (page != null || pageSize != null) {
      var result =
          userRepository.searchByRole(
              UserRole.EMPLOYEE,
              normalizedQuery,
              jobRoleId,
              normalizedLevel,
              PageRequestFactory.create(page, pageSize));
      return PageResponse.from(result.map(this::employeeSummary));
    }
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

  @GetMapping("/employees/{userId}")
  @Transactional(readOnly = true)
  public ManagerEmployeeSummaryResponse employee(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long userId) {
    requireManager(authorization);
    return employeeSummary(requireEmployee(userId));
  }

  @GetMapping("/employees/{userId}/evidences")
  @Transactional(readOnly = true)
  public Object employeeEvidences(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long userId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    requireManager(authorization);
    User employee = requireEmployee(userId);
    if (page != null || pageSize != null || (source != null && !source.isBlank())) {
      return evidenceService.listForUserPaged(
          employee, status, source, from, to, PageRequestFactory.create(page, pageSize));
    }
    return evidenceService.listForUser(employee, status, from, to);
  }

  @GetMapping("/employees/{userId}/evidences/{evidenceId}")
  @Transactional(readOnly = true)
  public Object employeeEvidence(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long userId,
      @PathVariable Long evidenceId) {
    requireManager(authorization);
    return evidenceService.getCanonicalForUser(requireEmployee(userId), evidenceId);
  }

  @GetMapping("/employees/{userId}/analyses")
  @Transactional(readOnly = true)
  public Object employeeAnalyses(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String review,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    requireManager(authorization);
    User employee = requireEmployee(userId);
    if (page != null || pageSize != null || (source != null && !source.isBlank())) {
      ReviewStatus reviewStatus = parseReviewStatus(review);
      return savedAnalysisService.listForUserPaged(
          employee, source, from, to, reviewStatus, PageRequestFactory.create(page, pageSize));
    }
    return savedAnalysisService.listForUser(employee, from, to);
  }

  @GetMapping("/employees/{userId}/analyses/{analysisId}")
  @Transactional(readOnly = true)
  public Object employeeAnalysis(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long userId,
      @PathVariable Long analysisId) {
    requireManager(authorization);
    return savedAnalysisService.getForUser(requireEmployee(userId), analysisId);
  }

  @GetMapping("/reviews")
  @Transactional(readOnly = true)
  public Object reviewQueue(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long employee,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    requireManager(authorization);
    if (employee != null) {
      requireEmployee(employee);
    }
    return analysisReviewService.queue(
        status, employee, source, from, to, PageRequestFactory.create(page, pageSize));
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

  private ReviewStatus parseReviewStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return ReviewStatus.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de revisão inválido.");
    }
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
