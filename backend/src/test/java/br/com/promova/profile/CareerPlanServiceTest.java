package br.com.promova.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.organization.JobRole;
import br.com.promova.organization.JobRoleRepository;
import br.com.promova.profile.dto.CareerObjectiveRequest;
import br.com.promova.profile.dto.CareerPlanUpdateRequest;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CareerPlanServiceTest {
  @Mock private UserRepository userRepository;
  @Mock private CareerProfileRepository profileRepository;
  @Mock private CareerObjectiveRepository objectiveRepository;
  @Mock private JobRoleRepository jobRoleRepository;
  @Mock private FrameworkProvider frameworkProvider;
  @Mock private ProfileService profileService;

  private CareerPlanService service;
  private User employee;
  private User manager;
  private JobRole role;

  @BeforeEach
  void setUp() {
    service =
        new CareerPlanService(
            userRepository,
            profileRepository,
            objectiveRepository,
            jobRoleRepository,
            frameworkProvider,
            profileService);
    employee = user(7L, "Employee", UserRole.EMPLOYEE);
    manager = user(1L, "Manager", UserRole.MANAGER);
    role = role(3L, "Engineering", List.of("L3", "L4"));
  }

  @Test
  void updatesAValidatedPlanAndNormalizesCharacteristics() {
    CareerProfile profile = new CareerProfile(employee, role, "L3", "L4", List.of());
    when(userRepository.findById(7L)).thenReturn(Optional.of(employee));
    when(frameworkProvider.load()).thenReturn(framework());
    when(jobRoleRepository.findById(3L)).thenReturn(Optional.of(role));
    when(profileService.ensureProfile(employee)).thenReturn(profile);
    when(profileService.getProfile(employee))
        .thenReturn(new ProfileResponse("L3", "L4", List.of()));

    service.updatePlan(
        7L,
        new CareerPlanUpdateRequest(
            3L, "L3", "L4", List.of("Mentoria", " mentoria ", "Ownership")));

    assertThat(profile.getCharacteristics()).containsExactly("Mentoria", "Ownership");
    verify(profileRepository).save(profile);
  }

  @Test
  void rejectsEqualReversedUnknownAndRoleDisallowedLevels() {
    when(userRepository.findById(7L)).thenReturn(Optional.of(employee));
    when(frameworkProvider.load()).thenReturn(framework());

    assertThatThrownBy(
            () ->
                service.updatePlan(
                    7L, new CareerPlanUpdateRequest(3L, "L4", "L4", List.of())))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                service.updatePlan(
                    7L, new CareerPlanUpdateRequest(3L, "L5", "L4", List.of())))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                service.updatePlan(
                    7L, new CareerPlanUpdateRequest(3L, "L3", "L99", List.of())))
        .isInstanceOf(ResponseStatusException.class);

    when(jobRoleRepository.findById(3L)).thenReturn(Optional.of(role));
    assertThatThrownBy(
            () ->
                service.updatePlan(
                    7L, new CareerPlanUpdateRequest(3L, "L3", "L5", List.of())))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(profileService, never()).ensureProfile(any());
  }

  @Test
  void objectiveUpdatesKeepActorAndAuditFields() {
    CareerProfile profile = new CareerProfile(employee, role, "L3", "L4", List.of());
    ReflectionTestUtils.setField(profile, "id", 19L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(employee));
    when(profileService.ensureProfile(employee)).thenReturn(profile);
    when(objectiveRepository.save(any(CareerObjective.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var created =
        service.createObjective(
            7L,
            new CareerObjectiveRequest(
                "Lead a release", ObjectiveStatus.ACTIVE, LocalDate.parse("2026-12-01")),
            manager);

    assertThat(created.status()).isEqualTo(ObjectiveStatus.ACTIVE);
    assertThat(created.updatedBy()).isEqualTo(1L);
    assertThat(created.createdAt()).isNotNull();
    assertThat(created.updatedAt()).isNotNull();
  }

  @Test
  void rejectsUnknownAndArchivedRoles() {
    when(userRepository.findById(7L)).thenReturn(Optional.of(employee));
    when(frameworkProvider.load()).thenReturn(framework());
    when(jobRoleRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.updatePlan(
                    7L, new CareerPlanUpdateRequest(99L, "L3", "L4", List.of())))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    role.archive();
    when(jobRoleRepository.findById(3L)).thenReturn(Optional.of(role));
    assertThatThrownBy(
            () ->
                service.updatePlan(
                    7L, new CareerPlanUpdateRequest(3L, "L3", "L4", List.of())))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private CareerFramework framework() {
    LinkedHashMap<String, CareerLevel> levels = new LinkedHashMap<>();
    levels.put("L3", new CareerLevel("Engineer I", "", Map.of()));
    levels.put("L4", new CareerLevel("Engineer II", "", Map.of()));
    levels.put("L5", new CareerLevel("Senior", "", Map.of()));
    return new CareerFramework(levels);
  }

  private User user(Long id, String name, UserRole userRole) {
    User user = new User(name, name.toLowerCase() + "@example.com", "hash", userRole);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private JobRole role(Long id, String name, List<String> levels) {
    JobRole jobRole = new JobRole(name, "Description", levels);
    ReflectionTestUtils.setField(jobRole, "id", id);
    return jobRole;
  }
}
