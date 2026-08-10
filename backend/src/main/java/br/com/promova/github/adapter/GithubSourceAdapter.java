package br.com.promova.github.adapter;

import br.com.promova.github.dto.GithubPullRequestBundle;
import br.com.promova.github.dto.GithubPullRequestPage;
import br.com.promova.github.dto.GithubPullSummary;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.github.support.GithubPayloadException;
import br.com.promova.source.NormalizedEvidence;
import br.com.promova.source.SourceAdapter;
import br.com.promova.source.SourceAdapterRequest;
import br.com.promova.source.SourcePageResult;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * GitHub implementation of the provider-neutral source adapter contract.
 *
 * <p>GitHub JSON/HTTP DTOs stop at this class. The sync and evidence domains receive only
 * {@link NormalizedEvidence}.
 */
@Service
public class GithubSourceAdapter implements SourceAdapter {
  public static final String SOURCE = "GitHub";

  private final GithubPullRequestService githubPullRequestService;

  public GithubSourceAdapter(GithubPullRequestService githubPullRequestService) {
    this.githubPullRequestService = githubPullRequestService;
  }

  @Override
  public String source() {
    return SOURCE;
  }

  @Override
  public SourcePageResult discover(SourceAdapterRequest request) {
    RepositorySlug repository = parseRepository(request.scope());
    GithubPullRequestPage page =
        githubPullRequestService.listClosedPullRequestsForSync(
            repository.owner(), repository.repo(), request.pageSize(), request.page());

    Instant oldestObservedAt =
        page.pullRequests().stream()
            .map(GithubPullSummary::updatedAt)
            .map(this::parseInstant)
            .flatMap(Optional::stream)
            .min(Instant::compareTo)
            .orElse(null);

    List<NormalizedEvidence> items = new ArrayList<>();
    int failedItems = page.malformedItems();
    for (GithubPullSummary pullRequest : page.pullRequests()) {
      if (!matchesSyncFilter(pullRequest, request)) {
        continue;
      }
      try {
        items.add(normalizeSummary(repository.value(), pullRequest));
      } catch (RuntimeException exception) {
        // A single malformed item must not discard the rest of the provider page.
        failedItems++;
      }
    }

    return new SourcePageResult(items, failedItems, page.hasPotentialNextPage(), oldestObservedAt);
  }

  /** Returns the stable natural key used by the existing per-user evidence uniqueness boundary. */
  public String externalIdFor(String repoSlug, int pullNumber) {
    RepositorySlug repository = parseRepository(repoSlug);
    if (pullNumber < 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Pull request number must be positive");
    }
    return externalId(repository, pullNumber);
  }

  /** Fetches and normalizes one GitHub PR for the legacy single-PR capture route. */
  public NormalizedEvidence fetchPullRequest(
      String repoSlug, int pullNumber, String usernameHint) {
    RepositorySlug repository = parseRepository(repoSlug);
    if (pullNumber < 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Pull request number must be positive");
    }

    GithubPullRequestBundle bundle =
        githubPullRequestService.pullRequestDetails(
            repository.owner(), repository.repo(), pullNumber);
    if (bundle == null || bundle.pullRequest() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "GitHub returned an invalid pull request");
    }

    GithubPullSummary pullRequest = bundle.pullRequest();
    String profileLine =
        usernameHint == null || usernameHint.isBlank()
            ? "Contexto/perfil relacionado a leitura: " + pullRequest.authorLogin()
            : "Contexto/perfil relacionado a leitura: " + usernameHint.trim();
    String bodyPreview =
        pullRequest.bodyPreview() == null || pullRequest.bodyPreview().isBlank()
            ? "Sem descricao no PR."
            : pullRequest.bodyPreview();
    String htmlUrl = trimTo(pullRequest.htmlUrl(), 2_048);
    String evidence =
        String.join(
            "\n\n",
            "GitHub - repositorio %s - PR #%d"
                .formatted(bundle.repository(), pullRequest.number()),
            profileLine,
            "Titulo: " + pullRequest.title(),
            "Volume coletado via API (+%d -%d linhas, %d arquivo(s))."
                .formatted(bundle.additions(), bundle.deletions(), bundle.changedFilesCount()),
            "Descricao/resumo:\n" + bodyPreview,
            "Link publico do PR: " + htmlUrl,
            "Leitura preparada automaticamente para revisao no Promova.");

    return new NormalizedEvidence(
        SOURCE,
        externalId(repository, pullNumber),
        "PR #%d - %s".formatted(pullRequest.number(), bundle.repository()),
        evidence,
        htmlUrl,
        pullRequest.authorLogin(),
        occurredAt(pullRequest),
        metadata(repository.value(), pullRequest)
    );
  }

  /** Converts a provider DTO into the contract model used by sync and evidence capture. */
  public NormalizedEvidence normalizeSummary(String repoSlug, GithubPullSummary pullRequest) {
    RepositorySlug repository = parseRepository(repoSlug);
    if (pullRequest == null || pullRequest.number() < 1) {
      throw new GithubPayloadException("GitHub returned an invalid pull request");
    }

    String bodyPreview =
        pullRequest.bodyPreview() == null || pullRequest.bodyPreview().isBlank()
            ? "Sem descricao no PR."
            : pullRequest.bodyPreview();
    String htmlUrl = trimTo(pullRequest.htmlUrl(), 2_048);
    String evidence =
        String.join(
            "\n\n",
            "GitHub - repositorio %s - PR #%d".formatted(repository.value(), pullRequest.number()),
            "Autor GitHub: " + trimTo(pullRequest.authorLogin(), 100),
            "Titulo: " + trimTo(pullRequest.title(), 1_000),
            "Estado: "
                + trimTo(pullRequest.state(), 30)
                + "\nMerge: "
                + trimTo(pullRequest.mergedAt(), 80)
                + "\nFechamento: "
                + trimTo(pullRequest.closedAt(), 80),
            "Descricao/resumo:\n" + trimTo(bodyPreview, 1_200),
            "Link do PR: " + htmlUrl,
            "Leitura preparada automaticamente para revisao no Promova.");

    return new NormalizedEvidence(
        SOURCE,
        externalId(repository, pullRequest.number()),
        trimTo(
            "PR #%d - %s - %s".formatted(pullRequest.number(), repository.value(), pullRequest.title()),
            1_000),
        trimTo(evidence, 9_900),
        htmlUrl,
        trimTo(pullRequest.authorLogin(), 100),
        occurredAt(pullRequest),
        metadata(repository.value(), pullRequest));
  }

  private boolean matchesSyncFilter(GithubPullSummary pullRequest, SourceAdapterRequest request) {
    if (pullRequest == null
        || pullRequest.number() < 1
        || !"closed".equalsIgnoreCase(pullRequest.state())
        || pullRequest.mergedAt() == null
        || pullRequest.mergedAt().isBlank()
        || pullRequest.authorLogin() == null
        || pullRequest.authorLogin().isBlank()) {
      return false;
    }

    if (request.author() != null && !pullRequest.authorLogin().equalsIgnoreCase(request.author())) {
      return false;
    }

    return parseInstant(pullRequest.updatedAt())
        .map(updatedAt -> request.occurredAfter() == null || !updatedAt.isBefore(request.occurredAfter()))
        .orElse(false);
  }

  private RepositorySlug parseRepository(String repoSlug) {
    String normalized = repoSlug == null ? "" : repoSlug.trim();
    String[] parts = normalized.split("/", -1);
    if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Repository must be in owner/repo format");
    }
    githubPullRequestService.validateRepository(parts[0], parts[1]);
    return new RepositorySlug(parts[0], parts[1]);
  }

  private String externalId(RepositorySlug repository, int pullNumber) {
    return "github:%s/%s#%d"
        .formatted(
            repository.owner().toLowerCase(Locale.ROOT),
            repository.repo().toLowerCase(Locale.ROOT),
            pullNumber);
  }

  private Instant occurredAt(GithubPullSummary pullRequest) {
    return firstInstant(
            pullRequest.mergedAt(),
            pullRequest.closedAt(),
            pullRequest.updatedAt(),
            pullRequest.createdAt())
        .orElseThrow(
            () -> new GithubPayloadException("GitHub returned an invalid pull request timestamp"));
  }

  private Map<String, String> metadata(String repository, GithubPullSummary pullRequest) {
    Map<String, String> metadata = new LinkedHashMap<>();
    put(metadata, "repository", repository);
    put(metadata, "number", Integer.toString(pullRequest.number()));
    put(metadata, "title", pullRequest.title());
    put(metadata, "state", pullRequest.state());
    put(metadata, "author", pullRequest.authorLogin());
    put(metadata, "mergedAt", pullRequest.mergedAt());
    put(metadata, "closedAt", pullRequest.closedAt());
    put(metadata, "createdAt", pullRequest.createdAt());
    put(metadata, "updatedAt", pullRequest.updatedAt());
    put(metadata, "draft", Boolean.toString(pullRequest.draft()));
    put(metadata, "locked", Boolean.toString(pullRequest.locked()));
    if (pullRequest.labels() != null && !pullRequest.labels().isEmpty()) {
      put(metadata, "labels", String.join(",", pullRequest.labels()));
    }
    return metadata;
  }

  private void put(Map<String, String> metadata, String key, String value) {
    if (value != null && !value.isBlank()) {
      metadata.put(key, value);
    }
  }

  private Optional<Instant> firstInstant(String... values) {
    for (String value : values) {
      Optional<Instant> parsed = parseInstant(value);
      if (parsed.isPresent()) {
        return parsed;
      }
    }
    return Optional.empty();
  }

  private Optional<Instant> parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Instant.parse(value));
    } catch (DateTimeParseException exception) {
      return Optional.empty();
    }
  }

  private String trimTo(String value, int limit) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String normalized = value.trim();
    return normalized.length() <= limit ? normalized : normalized.substring(0, limit - 3) + "...";
  }

  private record RepositorySlug(String owner, String repo) {
    private String value() {
      return owner + "/" + repo;
    }
  }
}
