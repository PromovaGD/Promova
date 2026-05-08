package br.com.promova.github.service;

import br.com.promova.github.client.GithubApiClient;
import br.com.promova.github.dto.GithubFilePatch;
import br.com.promova.github.dto.GithubPullRequestBundle;
import br.com.promova.github.dto.GithubPullRequestSearchResponse;
import br.com.promova.github.dto.GithubPullSummary;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GithubPullRequestService {
  private static final Pattern REPO_PART = Pattern.compile("[A-Za-z0-9_.-]+");
  private static final int BODY_PREVIEW_LIMIT = 1_200;
  private static final int PATCH_PREVIEW_LIMIT = 1_600;

  private final GithubApiClient githubApiClient;

  public GithubPullRequestService(GithubApiClient githubApiClient) {
    this.githubApiClient = githubApiClient;
  }

  public List<GithubPullSummary> listPullRequests(
      String owner, String repo, String state, int perPage, int page) {
    validateRepository(owner, repo);
    String normalizedState = normalizeState(state);
    int normalizedPerPage = normalizePerPage(perPage);
    int normalizedPage = Math.max(page, 1);

    JsonNode response =
        githubApiClient.get(
            "/repos/%s/%s/pulls?state=%s&sort=updated&direction=desc&per_page=%d&page=%d"
                .formatted(
                    path(owner), path(repo), query(normalizedState), normalizedPerPage, normalizedPage));

    return streamArray(response).stream().map(this::toPullSummary).toList();
  }

  public GithubPullRequestBundle pullRequestDetails(String owner, String repo, int number) {
    validateRepository(owner, repo);
    if (number < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request number must be positive");
    }

    JsonNode pullRequest = githubApiClient.get("/repos/%s/%s/pulls/%d".formatted(path(owner), path(repo), number));
    JsonNode fileResponse =
        githubApiClient.get(
            "/repos/%s/%s/pulls/%d/files?per_page=100".formatted(path(owner), path(repo), number));

    List<GithubFilePatch> files = streamArray(fileResponse).stream().map(this::toFilePatch).toList();
    int additions = pullRequest.path("additions").asInt(files.stream().mapToInt(GithubFilePatch::additions).sum());
    int deletions = pullRequest.path("deletions").asInt(files.stream().mapToInt(GithubFilePatch::deletions).sum());
    int changes = pullRequest.path("changed_files").asInt(files.size());

    return new GithubPullRequestBundle(
        owner + "/" + repo,
        toPullSummary(pullRequest),
        changes,
        additions,
        deletions,
        additions + deletions,
        files);
  }

  public GithubPullRequestSearchResponse searchPullRequests(
      String owner, String repo, String query, int perPage, int page) {
    validateRepository(owner, repo);
    String normalizedQuery = query == null || query.isBlank() ? "is:open" : query.trim();
    int normalizedPerPage = normalizePerPage(perPage);
    int normalizedPage = Math.max(page, 1);
    String searchQuery = "is:pr repo:%s/%s %s".formatted(owner, repo, normalizedQuery.replace("is:pr", "").trim());

    JsonNode response =
        githubApiClient.get(
            "/search/issues?q=%s&per_page=%d&page=%d"
                .formatted(query(searchQuery), normalizedPerPage, normalizedPage));

    List<GithubPullSummary> items = streamArray(response.path("items")).stream().map(this::toPullSummary).toList();
    return new GithubPullRequestSearchResponse(
        response.path("total_count").asInt(items.size()),
        response.path("incomplete_results").asBoolean(false),
        items);
  }

  public void validateRepository(String owner, String repo) {
    if (!isSafeRepositoryPart(owner) || !isSafeRepositoryPart(repo)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository must be in owner/repo format");
    }
  }

  private GithubPullSummary toPullSummary(JsonNode node) {
    return new GithubPullSummary(
        node.path("number").asInt(),
        node.path("title").asText("Untitled pull request"),
        node.path("state").asText("unknown"),
        node.path("draft").asBoolean(false),
        node.path("locked").asBoolean(false),
        textOrNull(node.path("merged_at")),
        textOrNull(node.path("closed_at")),
        node.path("html_url").asText(""),
        node.path("user").path("login").asText("unknown"),
        textOrNull(node.path("head").path("ref")),
        textOrNull(node.path("base").path("ref")),
        textOrNull(node.path("created_at")),
        textOrNull(node.path("updated_at")),
        labels(node.path("labels")),
        preview(node.path("body").asText(""), BODY_PREVIEW_LIMIT));
  }

  private GithubFilePatch toFilePatch(JsonNode node) {
    return new GithubFilePatch(
        node.path("filename").asText("unknown"),
        node.path("status").asText("modified"),
        node.path("additions").asInt(),
        node.path("deletions").asInt(),
        node.path("changes").asInt(),
        node.path("blob_url").asText(""),
        node.path("raw_url").asText(""),
        preview(node.path("patch").asText(""), PATCH_PREVIEW_LIMIT));
  }

  private List<String> labels(JsonNode labels) {
    return streamArray(labels).stream().map((label) -> label.path("name").asText()).filter((name) -> !name.isBlank()).toList();
  }

  private List<JsonNode> streamArray(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }

    List<JsonNode> values = new ArrayList<>();
    node.forEach(values::add);
    return values;
  }

  private String normalizeState(String state) {
    String normalized = state == null || state.isBlank() ? "open" : state.trim().toLowerCase(Locale.ROOT);
    if (!List.of("open", "closed", "all").contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State must be open, closed, or all");
    }
    return normalized;
  }

  private int normalizePerPage(int perPage) {
    if (perPage < 1) {
      return 10;
    }
    return Math.min(perPage, 100);
  }

  private boolean isSafeRepositoryPart(String value) {
    return value != null && REPO_PART.matcher(value).matches();
  }

  private String textOrNull(JsonNode node) {
    return node == null || node.isNull() || node.isMissingNode() ? null : node.asText();
  }

  private String preview(String value, int limit) {
    if (value == null || value.isBlank()) {
      return "";
    }

    String collapsed = value.replaceAll("\\s+", " ").trim();
    return collapsed.length() <= limit ? collapsed : collapsed.substring(0, limit - 1) + "...";
  }

  private String path(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String query(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
