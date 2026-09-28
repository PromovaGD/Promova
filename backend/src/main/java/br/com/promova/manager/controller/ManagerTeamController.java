package br.com.promova.manager.controller;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.manager.team.ManagerTeamService;
import br.com.promova.manager.team.ManagerTeamService.TeamSummary;
import br.com.promova.manager.team.ManagerTeamService.MemberSummary;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/manager/teams")
public class ManagerTeamController {
  private final AuthService auth;
  private final AuthTokenResolver tokens;
  private final ManagerTeamService teams;

  public ManagerTeamController(AuthService auth, AuthTokenResolver tokens, ManagerTeamService teams) {
    this.auth = auth;
    this.tokens = tokens;
    this.teams = teams;
  }

  @GetMapping
  public List<TeamSummary> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    return teams.list(manager(authorization));
  }

  @GetMapping("/members")
  public List<MemberSummary> visibleMembers(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    return teams.visibleMembers(manager(authorization));
  }

  @GetMapping("/candidates")
  public List<MemberSummary> candidates(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    return teams.assignmentCandidates(manager(authorization));
  }

  @GetMapping("/{teamId}/members")
  public List<MemberSummary> members(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long teamId) {
    return teams.members(manager(authorization), teamId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TeamSummary create(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody NewTeam request) {
    return teams.create(manager(authorization), request.name());
  }

  @PostMapping("/{teamId}/members")
  public TeamSummary assign(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long teamId,
      @Valid @RequestBody TeamMemberRequest request) {
    return teams.assign(manager(authorization), teamId, request.employeeId());
  }

  @DeleteMapping("/{teamId}/members/{employeeId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable Long teamId,
      @PathVariable Long employeeId) {
    teams.remove(manager(authorization), teamId, employeeId);
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

  public record NewTeam(@NotBlank @Size(max = 120) String name) {}
  public record TeamMemberRequest(@NotNull Long employeeId) {}
}
