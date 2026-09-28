package br.com.promova.manager.controller;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.common.PageResponse;
import br.com.promova.manager.dto.DashboardPersonResponse;
import br.com.promova.manager.dto.ManagerDashboardResponse;
import br.com.promova.manager.service.ManagerDashboardService;
import br.com.promova.manager.service.ManagerDashboardService.DashboardFilter;
import br.com.promova.github.metrics.GithubPullMetricsService;
import br.com.promova.github.metrics.GithubPullMetricsService.RefreshResult;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/manager/dashboard")
public class ManagerDashboardController {
  private final AuthService auth;
  private final AuthTokenResolver tokens;
  private final ManagerDashboardService dashboard;
  private final GithubPullMetricsService githubPullMetrics;

  public ManagerDashboardController(
      AuthService auth, AuthTokenResolver tokens, ManagerDashboardService dashboard,
      GithubPullMetricsService githubPullMetrics) {
    this.auth = auth;
    this.tokens = tokens;
    this.dashboard = dashboard;
    this.githubPullMetrics = githubPullMetrics;
  }

  @GetMapping
  public ManagerDashboardResponse summary(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) Long teamId,
      @RequestParam(required = false) Long jobRoleId,
      @RequestParam(required = false) Long employeeId,
      @RequestParam(defaultValue = "30") String period) {
    return dashboard.summary(manager(authorization), new DashboardFilter(from, to, teamId, jobRoleId, employeeId, period));
  }

  @GetMapping("/people")
  public PageResponse<DashboardPersonResponse> people(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(required = false) Long teamId,
      @RequestParam(required = false) Long jobRoleId,
      @RequestParam(required = false) Long employeeId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "25") int pageSize) {
    return dashboard.people(
        manager(authorization), new DashboardFilter(null, null, teamId, jobRoleId, employeeId, "30"),
        page, pageSize);
  }

  @PostMapping("/pr-reviews/refresh")
  public RefreshResult refreshPullRequestReviews(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam(defaultValue = "10") int limit) {
    return githubPullMetrics.refreshReviews(manager(authorization), limit);
  }

  private User manager(String authorization) {
    String token = tokens.resolve(authorization);
    if (token == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    User user = auth.requireUser(token);
    if (user.getRole() != UserRole.MANAGER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso restrito a gestores.");
    }
    return user;
  }
}
