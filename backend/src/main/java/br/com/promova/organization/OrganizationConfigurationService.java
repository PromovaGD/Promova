package br.com.promova.organization;

import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.organization.dto.CareerConfigurationResponse;
import br.com.promova.organization.dto.JobRoleArchiveRequest;
import br.com.promova.organization.dto.JobRoleRequest;
import br.com.promova.organization.dto.JobRoleResponse;
import br.com.promova.organization.dto.TerminologyResponse;
import br.com.promova.organization.dto.TerminologyUpdateRequest;
import br.com.promova.profile.CareerProfileRepository;
import br.com.promova.profile.dto.FrameworkLevelResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationConfigurationService {
  private final TerminologySettingsRepository terminologyRepository;
  private final JobRoleRepository jobRoleRepository;
  private final CareerProfileRepository profileRepository;
  private final FrameworkProvider frameworkProvider;

  public OrganizationConfigurationService(
      TerminologySettingsRepository terminologyRepository,
      JobRoleRepository jobRoleRepository,
      CareerProfileRepository profileRepository,
      FrameworkProvider frameworkProvider) {
    this.terminologyRepository = terminologyRepository;
    this.jobRoleRepository = jobRoleRepository;
    this.profileRepository = profileRepository;
    this.frameworkProvider = frameworkProvider;
  }

  @Transactional(readOnly = true)
  public CareerConfigurationResponse readConfiguration() {
    CareerFramework framework = frameworkProvider.load();
    return new CareerConfigurationResponse(
        TerminologyResponse.from(requireTerminology()),
        jobRoleRepository.findByStatusOrderByNameAsc(JobRoleStatus.ACTIVE).stream()
            .map(JobRoleResponse::from)
            .toList(),
        framework.levels().entrySet().stream()
            .map(
                entry ->
                    new FrameworkLevelResponse(
                        entry.getKey(),
                        entry.getValue().title() == null || entry.getValue().title().isBlank()
                            ? entry.getKey()
                            : entry.getValue().title()))
            .toList());
  }

  @Transactional(readOnly = true)
  public List<JobRoleResponse> listRoles(boolean includeArchived) {
    List<JobRole> roles =
        includeArchived
            ? jobRoleRepository.findAllByOrderByNameAsc()
            : jobRoleRepository.findByStatusOrderByNameAsc(JobRoleStatus.ACTIVE);
    return roles.stream().map(JobRoleResponse::from).toList();
  }

  @Transactional
  public TerminologyResponse updateTerminology(TerminologyUpdateRequest request) {
    TerminologySettings settings = requireTerminology();
    settings.update(
        request.manager(),
        request.employee(),
        request.jobRole(),
        request.level(),
        request.characteristics(),
        request.objective());
    return TerminologyResponse.from(terminologyRepository.save(settings));
  }

  @Transactional
  public JobRoleResponse createRole(JobRoleRequest request) {
    String name = request.name().trim();
    if (jobRoleRepository.existsByNameIgnoreCase(name)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cargo com este nome.");
    }
    List<String> levels = validateAndOrderLevels(request.allowedLevelIds());
    return JobRoleResponse.from(
        jobRoleRepository.save(new JobRole(name, request.description(), levels)));
  }

  @Transactional
  public JobRoleResponse updateRole(Long roleId, JobRoleRequest request) {
    JobRole role = requireRole(roleId);
    String name = request.name().trim();
    if (jobRoleRepository.existsByNameIgnoreCaseAndIdNot(name, roleId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cargo com este nome.");
    }
    role.update(name, request.description(), validateAndOrderLevels(request.allowedLevelIds()));
    return JobRoleResponse.from(jobRoleRepository.save(role));
  }

  @Transactional
  public JobRoleResponse archiveRole(Long roleId, JobRoleArchiveRequest request) {
    JobRole role = requireRole(roleId);
    if (role.getStatus() == JobRoleStatus.ARCHIVED) {
      return JobRoleResponse.from(role);
    }

    long affectedCount = profileRepository.countByJobRoleId(roleId);
    Long replacementId = request == null ? null : request.replacementRoleId();
    if (affectedCount > 0 && replacementId == null) {
      throw new JobRoleInUseException(affectedCount);
    }
    if (affectedCount > 0) {
      JobRole replacement = requireRole(replacementId);
      if (replacement.getStatus() != JobRoleStatus.ACTIVE || replacement.getId().equals(roleId)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "O cargo alternativo deve estar ativo e ser diferente.");
      }
      profileRepository.replaceJobRole(roleId, replacementId);
    }
    role.archive();
    return JobRoleResponse.from(jobRoleRepository.save(role));
  }

  private TerminologySettings requireTerminology() {
    return terminologyRepository
        .findById(TerminologySettings.SINGLETON_ID)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Configuração de terminologia ausente."));
  }

  private JobRole requireRole(Long roleId) {
    if (roleId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cargo alternativo inválido.");
    }
    return jobRoleRepository
        .findById(roleId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cargo não encontrado."));
  }

  private List<String> validateAndOrderLevels(List<String> requestedLevels) {
    CareerFramework framework = frameworkProvider.load();
    Set<String> unique = new LinkedHashSet<>();
    requestedLevels.stream().map(String::trim).forEach(unique::add);
    if (unique.size() < 2
        || unique.size() != requestedLevels.size()
        || unique.stream().anyMatch(level -> !framework.containsLevel(level))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "O cargo deve permitir ao menos dois níveis únicos existentes no framework.");
    }
    return framework.levelKeys().stream().filter(unique::contains).toList();
  }
}
