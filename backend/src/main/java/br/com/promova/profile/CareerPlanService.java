package br.com.promova.profile;

import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.organization.JobRole;
import br.com.promova.organization.JobRoleRepository;
import br.com.promova.organization.JobRoleStatus;
import br.com.promova.profile.dto.CareerObjectiveRequest;
import br.com.promova.profile.dto.CareerObjectiveResponse;
import br.com.promova.profile.dto.CareerPlanUpdateRequest;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CareerPlanService {
  private final UserRepository userRepository;
  private final CareerProfileRepository profileRepository;
  private final CareerObjectiveRepository objectiveRepository;
  private final JobRoleRepository jobRoleRepository;
  private final FrameworkProvider frameworkProvider;
  private final ProfileService profileService;

  public CareerPlanService(
      UserRepository userRepository,
      CareerProfileRepository profileRepository,
      CareerObjectiveRepository objectiveRepository,
      JobRoleRepository jobRoleRepository,
      FrameworkProvider frameworkProvider,
      ProfileService profileService) {
    this.userRepository = userRepository;
    this.profileRepository = profileRepository;
    this.objectiveRepository = objectiveRepository;
    this.jobRoleRepository = jobRoleRepository;
    this.frameworkProvider = frameworkProvider;
    this.profileService = profileService;
  }

  @Transactional
  public ProfileResponse getPlan(Long employeeId) {
    return profileService.getProfile(requireEmployee(employeeId));
  }

  @Transactional
  public ProfileResponse updatePlan(Long employeeId, CareerPlanUpdateRequest request) {
    User employee = requireEmployee(employeeId);
    CareerFramework framework = frameworkProvider.load();
    framework.validateProgression(request.currentLevel(), request.targetLevel());
    JobRole role =
        jobRoleRepository
            .findById(request.jobRoleId())
            .filter(candidate -> candidate.getStatus() == JobRoleStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "O cargo selecionado não está ativo."));
    if (!role.getAllowedLevelIds().contains(request.currentLevel())
        || !role.getAllowedLevelIds().contains(request.targetLevel())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Os níveis selecionados não são permitidos para este cargo.");
    }

    CareerProfile profile = profileService.ensureProfile(employee);
    profile.updatePlan(
        role,
        request.currentLevel(),
        request.targetLevel(),
        normalizeCharacteristics(request.characteristics()));
    profileRepository.save(profile);
    return profileService.getProfile(employee);
  }

  @Transactional
  public CareerObjectiveResponse createObjective(
      Long employeeId, CareerObjectiveRequest request, User manager) {
    CareerProfile profile = profileService.ensureProfile(requireEmployee(employeeId));
    CareerObjective objective =
        new CareerObjective(profile, request.text(), request.targetDate(), manager);
    if (request.status() != ObjectiveStatus.ACTIVE) {
      objective.update(request.text(), request.status(), request.targetDate(), manager);
    }
    return CareerObjectiveResponse.from(objectiveRepository.save(objective));
  }

  @Transactional
  public CareerObjectiveResponse updateObjective(
      Long employeeId, Long objectiveId, CareerObjectiveRequest request, User manager) {
    CareerProfile profile = profileService.ensureProfile(requireEmployee(employeeId));
    CareerObjective objective =
        objectiveRepository
            .findByIdAndCareerProfileId(objectiveId, profile.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Objetivo não encontrado."));
    objective.update(request.text(), request.status(), request.targetDate(), manager);
    return CareerObjectiveResponse.from(objectiveRepository.save(objective));
  }

  private User requireEmployee(Long employeeId) {
    return userRepository
        .findById(employeeId)
        .filter(user -> user.getRole() == UserRole.EMPLOYEE)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
  }

  private List<String> normalizeCharacteristics(List<String> values) {
    if (values == null) {
      return List.of();
    }
    LinkedHashMap<String, String> unique = new LinkedHashMap<>();
    for (String value : values) {
      String normalized = value.trim();
      unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
    }
    return List.copyOf(unique.values());
  }
}
