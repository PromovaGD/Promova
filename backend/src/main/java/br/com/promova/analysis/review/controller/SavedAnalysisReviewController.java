package br.com.promova.analysis.review.controller;

import br.com.promova.analysis.review.dto.AnalysisReviewResponse;
import br.com.promova.analysis.review.dto.SavedAnalysisReviewRequest;
import br.com.promova.analysis.review.service.AnalysisReviewService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SavedAnalysisReviewController {
  private final AnalysisReviewService analysisReviewService;
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final UserRepository userRepository;

  public SavedAnalysisReviewController(
      AnalysisReviewService analysisReviewService,
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      UserRepository userRepository) {
    this.analysisReviewService = analysisReviewService;
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.userRepository = userRepository;
  }

  @GetMapping("/analyses/{analysisId}/reviews")
  public AnalysisReviewResponse ownerHistory(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long analysisId) {
    return analysisReviewService.listForOwner(requireUser(authorization), analysisId);
  }

  /** Employees have read-only review access; manager writes use the explicit manager route below. */
  @PostMapping("/analyses/{analysisId}/reviews")
  public void rejectOwnerWrite(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long analysisId) {
    requireUser(authorization);
    throw new ResponseStatusException(
        HttpStatus.FORBIDDEN, "Employees cannot create or change reviews.");
  }

  @GetMapping("/manager/employees/{employeeId}/analyses/{analysisId}/reviews")
  public AnalysisReviewResponse managerHistory(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long employeeId,
      @PathVariable Long analysisId) {
    User manager = requireManager(authorization);
    requireManagerVisibleEmployee(manager, employeeId);
    return analysisReviewService.listForManager(employeeId, analysisId);
  }

  @PostMapping("/manager/employees/{employeeId}/analyses/{analysisId}/reviews")
  @ResponseStatus(HttpStatus.CREATED)
  public AnalysisReviewResponse appendManagerReview(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long employeeId,
      @PathVariable Long analysisId,
      @Valid @RequestBody SavedAnalysisReviewRequest request) {
    User manager = requireManager(authorization);
    requireManagerVisibleEmployee(manager, employeeId);
    return analysisReviewService.appendForManager(manager, employeeId, analysisId, request);
  }

  private User requireManagerVisibleEmployee(User manager, Long employeeId) {
    return userRepository
        .findById(employeeId)
        .filter(user -> !user.getId().equals(manager.getId()))
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));
  }

  private User requireManager(String authorization) {
    User user = requireUser(authorization);
    if (user.getRole() != UserRole.MANAGER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager access required.");
    }
    return user;
  }

  private User requireUser(String authorization) {
    String token = authTokenResolver.resolve(authorization);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token missing.");
    }
    return authService.requireUser(token);
  }
}
