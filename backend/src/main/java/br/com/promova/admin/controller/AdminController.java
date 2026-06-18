package br.com.promova.admin.controller;

import br.com.promova.analysis.dto.SavedAnalysisResponse;
import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.auth.dto.UserSummaryResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin")
public class AdminController {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final UserRepository userRepository;
  private final SavedAnalysisService savedAnalysisService;

  public AdminController(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      UserRepository userRepository,
      SavedAnalysisService savedAnalysisService) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.userRepository = userRepository;
    this.savedAnalysisService = savedAnalysisService;
  }

  @GetMapping("/employees")
  @Transactional(readOnly = true)
  public List<UserSummaryResponse> employees(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    User admin = requireAdmin(authorization);
    return userRepository.findAllExcept(admin.getId()).stream()
        .map(UserSummaryResponse::from)
        .toList();
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
    User admin = requireAdmin(authorization);
    User employee =
        userRepository
            .findById(userId)
            .filter(user -> !user.getId().equals(admin.getId()))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

    return savedAnalysisService.listForUser(employee, from, to);
  }

  @PutMapping("/users/{userId}/role")
  @Transactional
  public UserSummaryResponse updateUserRole(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long userId,
      @RequestParam UserRole role) {
    requireAdmin(authorization);
    
    User user = userRepository
        .findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    
    user.setRole(role);
    User updated = userRepository.save(user);
    return UserSummaryResponse.from(updated);
  }

  private User requireAdmin(String authorization) {
    User user = authService.requireUser(requireToken(authorization));
    if (user.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso restrito a administradores.");
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
