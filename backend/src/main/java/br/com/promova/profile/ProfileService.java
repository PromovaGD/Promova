package br.com.promova.profile;

import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.profile.dto.ProfileUpdateRequest;
import br.com.promova.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
  private final CareerProfileRepository profileRepository;
  private final FrameworkProvider frameworkProvider;

  public ProfileService(
      CareerProfileRepository profileRepository, FrameworkProvider frameworkProvider) {
    this.profileRepository = profileRepository;
    this.frameworkProvider = frameworkProvider;
  }

  @Transactional
  public ProfileResponse getProfile(User user) {
    CareerFramework framework = frameworkProvider.load();
    return ProfileResponse.from(ensureProfile(user, framework), framework);
  }

  @Transactional
  public ProfileResponse updateProfile(User user, ProfileUpdateRequest request) {
    CareerFramework framework = frameworkProvider.load();
    framework.validateProgression(request.currentLevel(), request.targetLevel());

    CareerProfile profile = ensureProfile(user, framework);
    profile.updateLevels(request.currentLevel(), request.targetLevel());
    return ProfileResponse.from(profile, framework);
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
                    new CareerProfile(
                        user,
                        framework.defaultCurrentLevel(),
                        framework.defaultTargetLevel())));
  }

  private CareerProfile repairIfFrameworkChanged(
      CareerProfile profile, CareerFramework framework) {
    if (!framework.isAbove(profile.getCurrentLevel(), profile.getTargetLevel())) {
      profile.updateLevels(framework.defaultCurrentLevel(), framework.defaultTargetLevel());
      return profileRepository.save(profile);
    }
    return profile;
  }
}
