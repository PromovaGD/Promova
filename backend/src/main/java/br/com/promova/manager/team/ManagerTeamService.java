package br.com.promova.manager.team;

import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.util.List;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManagerTeamService {
  private final TeamRepository teams;
  private final TeamMembershipRepository memberships;
  private final UserRepository users;

  public ManagerTeamService(
      TeamRepository teams, TeamMembershipRepository memberships, UserRepository users) {
    this.teams = teams;
    this.memberships = memberships;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public List<TeamSummary> list(User manager) {
    return teams.findByManagerIdOrderByNameAsc(manager.getId()).stream()
        .map(team -> new TeamSummary(team.getId(), team.getName(), memberships.activeCount(team.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MemberSummary> members(User manager, Long teamId) {
    requireOwnedTeam(manager, teamId);
    return memberships.activeTeamMembers(manager.getId(), teamId).stream()
        .map(membership -> new MemberSummary(
            membership.getEmployee().getId(),
            membership.getEmployee().getName(),
            membership.getEmployee().getEmail()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MemberSummary> visibleMembers(User manager) {
    return users.listVisibleEmployees(manager.getId(), Instant.now(), null, null, null).stream()
        .map(user -> new MemberSummary(user.getId(), user.getName(), user.getEmail())).toList();
  }

  @Transactional(readOnly = true)
  public List<MemberSummary> assignmentCandidates(User manager) {
    return users.assignmentCandidates(manager.getId()).stream()
        .map(user -> new MemberSummary(user.getId(), user.getName(), user.getEmail())).toList();
  }

  @Transactional
  public TeamSummary create(User manager, String name) {
    String normalized = name == null ? "" : name.trim();
    if (normalized.isEmpty() || normalized.length() > 120) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome da equipe inválido.");
    }
    Team team = teams.save(new Team(manager, normalized));
    return new TeamSummary(team.getId(), team.getName(), 0);
  }

  @Transactional
  public void bootstrapDemoTeam(User manager, List<User> employees) {
    if (!teams.findByManagerIdOrderByNameAsc(manager.getId()).isEmpty()) return;
    Team team = teams.save(new Team(manager, "Equipe principal"));
    employees.forEach(employee -> memberships.save(new TeamMembership(team, employee)));
  }

  @Transactional
  public void onboardNewEmployee(User employee) {
    List<User> managers = users.findByRoleOrderByNameAsc(UserRole.MANAGER);
    if (managers.size() != 1) return;
    List<Team> managerTeams = teams.findByManagerIdOrderByNameAsc(managers.get(0).getId());
    if (managerTeams.size() == 1) {
      memberships.save(new TeamMembership(managerTeams.get(0), employee));
    }
  }

  @Transactional
  public TeamSummary assign(User manager, Long teamId, Long employeeId) {
    Team team = requireOwnedTeamEntity(manager, teamId);
    User employee = users.findById(employeeId)
        .filter(user -> user.getRole() == UserRole.EMPLOYEE)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pessoa não encontrada."));
    List<TeamMembership> previous = memberships.activeForManagerEmployee(manager.getId(), employeeId);
    if (previous.isEmpty() && memberships.activeForOtherManager(manager.getId(), employeeId) > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Pessoa já está vinculada a outro gestor.");
    }
    if (previous.stream().noneMatch(membership -> membership.getTeam().getId().equals(teamId))) {
      previous.forEach(TeamMembership::end);
      memberships.save(new TeamMembership(team, employee));
    }
    return new TeamSummary(team.getId(), team.getName(), memberships.activeCount(team.getId()));
  }

  @Transactional
  public void remove(User manager, Long teamId, Long employeeId) {
    requireOwnedTeam(manager, teamId);
    memberships.activeForManagerEmployee(manager.getId(), employeeId).stream()
        .filter(membership -> membership.getTeam().getId().equals(teamId))
        .forEach(TeamMembership::end);
  }

  @Transactional(readOnly = true)
  public void requireVisible(User manager, Long employeeId) {
    if (manager.getRole() != UserRole.MANAGER
        || memberships.visibleCount(manager.getId(), employeeId) == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pessoa não encontrada.");
    }
  }

  @Transactional(readOnly = true)
  public void requireOwnedTeam(User manager, Long teamId) {
    teams.findByIdAndManagerId(teamId, manager.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe não encontrada."));
  }

  private Team requireOwnedTeamEntity(User manager, Long teamId) {
    return teams.findByIdAndManagerId(teamId, manager.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe não encontrada."));
  }

  public record TeamSummary(Long id, String name, long members) {}
  public record MemberSummary(Long id, String name, String email) {}
}
