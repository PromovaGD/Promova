package br.com.promova.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.organization.dto.JobRoleArchiveRequest;
import br.com.promova.organization.dto.JobRoleRequest;
import br.com.promova.profile.CareerProfileRepository;
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
class OrganizationConfigurationServiceTest {
  @Mock private TerminologySettingsRepository terminologyRepository;
  @Mock private JobRoleRepository jobRoleRepository;
  @Mock private CareerProfileRepository profileRepository;
  @Mock private FrameworkProvider frameworkProvider;

  private OrganizationConfigurationService service;

  @BeforeEach
  void setUp() {
    service =
        new OrganizationConfigurationService(
            terminologyRepository, jobRoleRepository, profileRepository, frameworkProvider);
  }

  @Test
  void rejectsRoleLevelsThatAreNotInTheServerFramework() {
    when(frameworkProvider.load()).thenReturn(framework());
    JobRoleRequest request =
        new JobRoleRequest("Platform", "Platform engineering", List.of("L3", "L99"));

    assertThatThrownBy(() -> service.createRole(request))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(jobRoleRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void reportsAffectedCountBeforeArchivingAnAssignedRole() {
    JobRole role = role(11L, "Platform");
    when(jobRoleRepository.findById(11L)).thenReturn(Optional.of(role));
    when(profileRepository.countByJobRoleId(11L)).thenReturn(3L);

    assertThatThrownBy(() -> service.archiveRole(11L, new JobRoleArchiveRequest(null)))
        .isInstanceOfSatisfying(
            JobRoleInUseException.class,
            exception -> assertThat(exception.affectedCount()).isEqualTo(3));
    verify(jobRoleRepository, never()).save(role);
  }

  @Test
  void explicitlyReassignsUsersBeforeArchiving() {
    JobRole role = role(11L, "Platform");
    JobRole replacement = role(12L, "Product Engineering");
    when(jobRoleRepository.findById(11L)).thenReturn(Optional.of(role));
    when(jobRoleRepository.findById(12L)).thenReturn(Optional.of(replacement));
    when(profileRepository.countByJobRoleId(11L)).thenReturn(2L);
    when(jobRoleRepository.save(role)).thenReturn(role);

    var response = service.archiveRole(11L, new JobRoleArchiveRequest(12L));

    verify(profileRepository).replaceJobRole(11L, 12L);
    assertThat(response.status()).isEqualTo(JobRoleStatus.ARCHIVED);
  }

  private JobRole role(Long id, String name) {
    JobRole role = new JobRole(name, "Description", List.of("L3", "L4"));
    ReflectionTestUtils.setField(role, "id", id);
    return role;
  }

  private CareerFramework framework() {
    LinkedHashMap<String, CareerLevel> levels = new LinkedHashMap<>();
    levels.put("L3", new CareerLevel("Engineer I", "Engineer I", Map.of()));
    levels.put("L4", new CareerLevel("Engineer II", "Engineer II", Map.of()));
    levels.put("L5", new CareerLevel("Senior Engineer", "Senior Engineer", Map.of()));
    return new CareerFramework(levels);
  }
}
