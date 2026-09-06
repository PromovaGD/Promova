package br.com.promova.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.organization.JobRole;
import br.com.promova.organization.JobRoleRepository;
import br.com.promova.organization.JobRoleStatus;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
  @Mock private CareerProfileRepository profileRepository;
  @Mock private FrameworkProvider frameworkProvider;
  @Mock private JobRoleRepository jobRoleRepository;
  @Mock private CareerObjectiveRepository objectiveRepository;

  private CareerFramework framework;
  private ProfileService profileService;

  @BeforeEach
  void setUp() {
    LinkedHashMap<String, CareerLevel> levels = new LinkedHashMap<>();
    levels.put("L2", new CareerLevel("Engineer I"));
    levels.put("L10", new CareerLevel("Engineer II"));
    levels.put("L11", new CareerLevel("Senior Engineer"));
    framework = new CareerFramework(levels);
    profileService =
        new ProfileService(
            profileRepository, frameworkProvider, jobRoleRepository, objectiveRepository);
    when(frameworkProvider.load()).thenReturn(framework);
  }

  @Test
  void createsAndPersistsFrameworkCompatibleDefaultProfile() {
    User employee = user(7L, UserRole.EMPLOYEE);
    JobRole role = role(3L);
    when(profileRepository.findByUserId(7L)).thenReturn(Optional.empty());
    when(jobRoleRepository.findFirstByStatusOrderByNameAsc(JobRoleStatus.ACTIVE))
        .thenReturn(Optional.of(role));
    when(profileRepository.save(any(CareerProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ProfileResponse response = profileService.getProfile(employee);

    assertThat(response.currentLevel()).isEqualTo("L2");
    assertThat(response.targetLevel()).isEqualTo("L10");
    assertThat(response.jobRole().name()).isEqualTo("Engineering");
    assertThat(response.levels()).extracting("key").containsExactly("L2", "L10", "L11");
    verify(profileRepository).save(any(CareerProfile.class));
  }

  @Test
  void repairsAPlanThatNoLongerUsesAllowedFrameworkLevels() {
    User employee = user(7L, UserRole.EMPLOYEE);
    JobRole role = role(3L);
    CareerProfile profile =
        new CareerProfile(employee, role, "L2", "L11", List.of("Mentoria"));
    when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
    when(profileRepository.save(profile)).thenReturn(profile);

    ProfileResponse response = profileService.getProfile(employee);

    assertThat(response.currentLevel()).isEqualTo("L2");
    assertThat(response.targetLevel()).isEqualTo("L10");
    assertThat(response.characteristics()).containsExactly("Mentoria");
    verify(profileRepository).save(profile);
  }

  private JobRole role(Long id) {
    JobRole role =
        new JobRole("Engineering", "Build products", List.of("L2", "L10"));
    ReflectionTestUtils.setField(role, "id", id);
    return role;
  }

  private User user(Long id, UserRole role) {
    User user = new User("Employee", "employee-" + id + "@example.com", "hash", role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
