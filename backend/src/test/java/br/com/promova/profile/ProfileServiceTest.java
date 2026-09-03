package br.com.promova.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.profile.dto.ProfileUpdateRequest;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
  @Mock private CareerProfileRepository profileRepository;
  @Mock private FrameworkProvider frameworkProvider;

  private CareerFramework framework;
  private ProfileService profileService;

  @BeforeEach
  void setUp() {
    LinkedHashMap<String, CareerLevel> levels = new LinkedHashMap<>();
    levels.put("L2", new CareerLevel("Engineer I"));
    levels.put("L10", new CareerLevel("Engineer II"));
    levels.put("L11", new CareerLevel("Senior Engineer"));
    framework = new CareerFramework(levels);
    profileService = new ProfileService(profileRepository, frameworkProvider);
    when(frameworkProvider.load()).thenReturn(framework);
  }

  @Test
  void createsAndPersistsFrameworkCompatibleDefaultProfile() {
    User employee = user(7L, UserRole.EMPLOYEE);
    when(profileRepository.findByUserId(7L)).thenReturn(Optional.empty());
    when(profileRepository.save(any(CareerProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ProfileResponse response = profileService.getProfile(employee);

    assertThat(response.currentLevel()).isEqualTo("L2");
    assertThat(response.targetLevel()).isEqualTo("L10");
    assertThat(response.levels()).extracting("key").containsExactly("L2", "L10", "L11");
    verify(profileRepository).save(any(CareerProfile.class));
  }

  @Test
  void updatesOnlyTheAuthenticatedUsersProfileAndLeavesRoleUntouched() {
    User employee = user(7L, UserRole.MANAGER);
    CareerProfile profile = new CareerProfile(employee, "L2", "L10");
    when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));

    ProfileResponse response =
        profileService.updateProfile(employee, new ProfileUpdateRequest("L10", "L11"));

    assertThat(response.currentLevel()).isEqualTo("L10");
    assertThat(response.targetLevel()).isEqualTo("L11");
    assertThat(profile.getUser()).isSameAs(employee);
    assertThat(employee.getRole()).isEqualTo(UserRole.MANAGER);
    verify(profileRepository).findByUserId(eq(7L));
    verify(profileRepository, never()).findByUserId(eq(8L));
  }

  @Test
  void rejectsUnknownEqualAndReversedLevelsBeforeProfileMutation() {
    User employee = user(7L, UserRole.EMPLOYEE);

    assertThatThrownBy(
            () -> profileService.updateProfile(employee, new ProfileUpdateRequest("L2", "L99")))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () -> profileService.updateProfile(employee, new ProfileUpdateRequest("L10", "L10")))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () -> profileService.updateProfile(employee, new ProfileUpdateRequest("L11", "L2")))
        .isInstanceOf(ResponseStatusException.class);

    verify(profileRepository, never()).findByUserId(any());
    verify(profileRepository, never()).save(any(CareerProfile.class));
  }

  private User user(Long id, UserRole role) {
    User user = new User("Employee", "employee-" + id + "@example.com", "hash", role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
