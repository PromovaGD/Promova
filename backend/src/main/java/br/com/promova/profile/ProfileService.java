package br.com.promova.profile;

import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.organization.JobRole;
import br.com.promova.organization.JobRoleRepository;
import br.com.promova.organization.JobRoleStatus;
import br.com.promova.profile.dto.CareerObjectiveResponse;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.user.User;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
  private final CareerProfileRepository profileRepository;
  private final FrameworkProvider frameworkProvider;
  private final JobRoleRepository jobRoleRepository;
  private final CareerObjectiveRepository objectiveRepository;

  public ProfileService(
      CareerProfileRepository profileRepository,
      FrameworkProvider frameworkProvider,
      JobRoleRepository jobRoleRepository,
      CareerObjectiveRepository objectiveRepository) {
    this.profileRepository = profileRepository;
    this.frameworkProvider = frameworkProvider;
    this.jobRoleRepository = jobRoleRepository;
    this.objectiveRepository = objectiveRepository;
  }

  @Transactional
  public ProfileResponse getProfile(User user) {
    CareerFramework framework = frameworkProvider.load();
    CareerProfile profile = ensureProfile(user, framework);
    return ProfileResponse.from(
        profile,
        framework,
        objectiveRepository.findByCareerProfileIdOrderByCreatedAtAsc(profile.getId()).stream()
            .map(CareerObjectiveResponse::from)
            .toList());
  }

  @Transactional
  public CareerProfile ensureProfile(User user) {
    return ensureProfile(user, frameworkProvider.load());
  }

  private CareerProfile ensureProfile(User user, CareerFramework framework) {
    return profileRepository
        .findByUserId(user.getId())
        .map(profile -> repairIfFrameworkChanged(profile, framework))
        .orElseGet(
            () ->
                profileRepository.save(
                    defaultProfile(user, framework)));
  }

  private CareerProfile repairIfFrameworkChanged(
      CareerProfile profile, CareerFramework framework) {
    JobRole role = profile.getJobRole();
    if (role == null || role.getStatus() != JobRoleStatus.ACTIVE) {
      role = requireDefaultRole();
    }
    if (!framework.isAbove(profile.getCurrentLevel(), profile.getTargetLevel())
        || !role.getAllowedLevelIds().contains(profile.getCurrentLevel())
        || !role.getAllowedLevelIds().contains(profile.getTargetLevel())
        || profile.getJobRole() == null
        || !Objects.equals(profile.getJobRole().getId(), role.getId())) {
      List<String> defaults = defaultLevels(role, framework);
      profile.updatePlan(role, defaults.get(0), defaults.get(1), profile.getCharacteristics());
      return profileRepository.save(profile);
    }
    return profile;
  }

  private CareerProfile defaultProfile(User user, CareerFramework framework) {
    JobRole role = requireDefaultRole();
    List<String> levels = defaultLevels(role, framework);
    return new CareerProfile(user, role, levels.get(0), levels.get(1), List.of());
  }

  private JobRole requireDefaultRole() {
    return jobRoleRepository
        .findFirstByStatusOrderByNameAsc(JobRoleStatus.ACTIVE)
        .orElseThrow(() -> new IllegalStateException("At least one active job role is required."));
  }

  private List<String> defaultLevels(JobRole role, CareerFramework framework) {
    List<String> levels =
        framework.levelKeys().stream().filter(role.getAllowedLevelIds()::contains).toList();
    if (levels.size() < 2) {
      throw new IllegalStateException("The default job role must allow at least two framework levels.");
    }
    return levels.subList(0, 2);
  }
}
