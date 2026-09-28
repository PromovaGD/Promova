package br.com.promova.manager.service;

import br.com.promova.common.PageResponse;
import br.com.promova.manager.dto.DashboardPersonResponse;
import br.com.promova.manager.dto.ManagerDashboardResponse;
import br.com.promova.manager.dto.ManagerDashboardResponse.AttentionItem;
import br.com.promova.manager.dto.ManagerDashboardResponse.HighlightItem;
import br.com.promova.manager.dto.ManagerDashboardResponse.SourceBucket;
import br.com.promova.manager.dto.ManagerDashboardResponse.StatusBucket;
import br.com.promova.manager.dto.ManagerDashboardResponse.TrendPoint;
import br.com.promova.manager.dto.ManagerDashboardResponse.PullRequestMetrics;
import br.com.promova.manager.team.ManagerTeamService;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManagerDashboardService {
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  private final NamedParameterJdbcTemplate jdbc;
  private final ManagerTeamService teams;
  private final boolean demoData;
  private final int attentionDays;
  private final FrameworkProvider frameworkProvider;
  private final ObjectMapper mapper;

  public ManagerDashboardService(
      NamedParameterJdbcTemplate jdbc, ManagerTeamService teams,
      FrameworkProvider frameworkProvider, ObjectMapper mapper,
      @Value("${promova.demo-data.enabled:false}") boolean demoData,
      @Value("${promova.dashboard.attention-days:7}") int attentionDays) {
    this.jdbc = jdbc;
    this.teams = teams;
    this.demoData = demoData;
    this.frameworkProvider = frameworkProvider;
    this.mapper = mapper;
    this.attentionDays = Math.max(1, Math.min(90, attentionDays));
  }

  @Transactional(readOnly = true)
  public ManagerDashboardResponse summary(User manager, DashboardFilter filter) {
    Scope scope = scope(manager, filter);
    LocalDate from = dateFrom(filter);
    LocalDate to = dateTo(filter);
    MapSqlParameterSource params = periodParams(scope.params(), from, to);
    String cte = "WITH scoped AS (" + scope.sql() + ") ";
    String analysisStatus = """
        COALESCE((SELECT r.status FROM saved_analysis_reviews r
                  WHERE r.saved_analysis_id = a.id
                  ORDER BY r.created_at DESC, r.id DESC LIMIT 1), 'UNREVIEWED')
        """;

    long people = count(cte + "SELECT COUNT(*) FROM scoped", params);
    long evidence = count(cte + """
        SELECT COUNT(*) FROM scoped s JOIN evidences e ON e.user_id = s.id
        WHERE e.occurred_at >= :fromInstant AND e.occurred_at < :toExclusive
        """, params);
    long analyses = count(cte + """
        SELECT COUNT(*) FROM scoped s JOIN saved_analyses a ON a.user_id = s.id
        WHERE a.created_at >= :fromInstant AND a.created_at < :toExclusive
        """, params);
    Map<String, Long> current = statusCounts(cte, params, false, analysisStatus);
    Map<String, Long> period = statusCounts(cte, params, true, analysisStatus);
    long overdue = count(cte + """
        SELECT COUNT(*) FROM scoped s JOIN career_objectives o ON o.career_profile_id = s.profile_id
        WHERE o.status = 'ACTIVE' AND o.target_date < :today
        """, params);
    long dueSoon = count(cte + """
        SELECT COUNT(*) FROM scoped s JOIN career_objectives o ON o.career_profile_id = s.profile_id
        WHERE o.status = 'ACTIVE' AND o.target_date >= :today AND o.target_date <= :dueSoon
        """, params);
    long withoutObjective = count(cte + """
        SELECT COUNT(*) FROM scoped s
        WHERE NOT EXISTS (SELECT 1 FROM career_objectives o
                          WHERE o.career_profile_id = s.profile_id AND o.status = 'ACTIVE')
        """, params);
    long agedQueue = count(cte + """
        SELECT COUNT(*) FROM scoped s JOIN saved_analyses a ON a.user_id = s.id
        WHERE a.created_at < :sevenDaysAgo
          AND NOT EXISTS (SELECT 1 FROM saved_analysis_reviews r WHERE r.saved_analysis_id = a.id)
        """, params);
    long withoutRecentCapture = count(cte + """
        SELECT COUNT(*) FROM scoped s WHERE NOT EXISTS (
          SELECT 1 FROM evidences e WHERE e.user_id = s.id AND e.captured_at >= :sevenDaysAgo)
        """, params);
    List<Instant> lastCaptures = jdbc.query(cte + """
        SELECT MAX(e.captured_at) AS last_capture
        FROM scoped s JOIN evidences e ON e.user_id = s.id
        """, params, (rs, row) -> timestampOrNull(rs, "last_capture"));
    Instant lastCapture = lastCaptures.isEmpty() ? null : lastCaptures.get(0);
    long failedRuns = count(cte + """
        SELECT COUNT(*) FROM scoped s JOIN evidences e ON e.user_id = s.id
        JOIN analysis_runs ar ON ar.evidence_id = e.id
        WHERE ar.started_at >= :fromInstant AND ar.started_at < :toExclusive
          AND ar.outcome = 'FAILED'
        """, params);
    long[] coverage = criterionCoverage(cte, params);

    List<TrendPoint> trend = trend(cte, params, from, to);
    List<SourceBucket> sources = jdbc.query(cte + """
        SELECT e.source, COUNT(*) AS total
        FROM scoped s JOIN evidences e ON e.user_id = s.id
        WHERE e.occurred_at >= :fromInstant AND e.occurred_at < :toExclusive
        GROUP BY e.source ORDER BY total DESC, e.source
        """, params, (rs, row) -> new SourceBucket(rs.getString("source"), rs.getLong("total")));
    List<Double> reviewHours = jdbc.query(cte + """
        SELECT a.created_at AS analysis_at, MIN(r.created_at) AS first_review_at
        FROM scoped s JOIN saved_analyses a ON a.user_id = s.id
        JOIN saved_analysis_reviews r ON r.saved_analysis_id = a.id
        WHERE a.created_at >= :fromInstant AND a.created_at < :toExclusive
        GROUP BY a.id, a.created_at
        """, params, (rs, row) -> Duration.between(
            timestamp(rs, "analysis_at"), timestamp(rs, "first_review_at")).toMillis() / 3600000.0);
    reviewHours.sort(Comparator.naturalOrder());
    Double median = reviewHours.isEmpty() ? null
        : (reviewHours.get((reviewHours.size() - 1) / 2) + reviewHours.get(reviewHours.size() / 2)) / 2;

    return new ManagerDashboardResponse(
        from, to, hasDemoRecords(), people, evidence, analyses,
        current.get("UNREVIEWED"), current.get("NEEDS_CONTEXT"), current.get("ACCEPTED"),
        overdue, dueSoon, withoutObjective, agedQueue,
        attentionDays, withoutRecentCapture, lastCapture, coverage[0], coverage[1], failedRuns,
        median, reviewHours.size(),
        trend,
        List.of(new StatusBucket("UNREVIEWED", period.get("UNREVIEWED")),
            new StatusBucket("NEEDS_CONTEXT", period.get("NEEDS_CONTEXT")),
            new StatusBucket("ACCEPTED", period.get("ACCEPTED"))),
        sources,
        attention(cte, params, analysisStatus),
        highlights(cte, params, analysisStatus),
        pullRequestMetrics(cte, params));
  }

  private boolean hasDemoRecords() {
    return demoData && count("""
        SELECT COUNT(*) FROM evidences
        WHERE source = 'GitHub' AND external_id LIKE 'github:promova/demo#%'
        """, new MapSqlParameterSource()) > 0;
  }

  private long[] criterionCoverage(String cte, MapSqlParameterSource params) {
    Set<String> declared = new HashSet<>();
    frameworkProvider.load().levels().forEach((level, details) -> {
      if (details != null && details.criteria() != null) {
        details.criteria().keySet().forEach(name -> declared.add(key(level, name)));
      }
    });
    Set<String> supported = new HashSet<>();
    jdbc.query(cte + """
        SELECT a.impact_level, a.competencies_json FROM scoped s
        JOIN saved_analyses a ON a.user_id = s.id
        WHERE a.created_at >= :fromInstant AND a.created_at < :toExclusive
        """, params, rs -> {
      try {
        String level = rs.getString("impact_level");
        String[] competencies = mapper.readValue(rs.getString("competencies_json"), String[].class);
        if (competencies != null) {
          for (String name : competencies) {
            String criterion = key(level, name);
            if (declared.contains(criterion)) supported.add(criterion);
          }
        }
      } catch (Exception ignored) {
        // Historical free-text analyses can contain malformed JSON; they give no coverage.
      }
    });
    return new long[] {supported.size(), declared.size()};
  }

  private static String key(String level, String name) {
    return (level == null ? "" : level.trim().toLowerCase(Locale.ROOT)) + "|"
        + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
  }

  private PullRequestMetrics pullRequestMetrics(String cte, MapSqlParameterSource params) {
    String base = cte + """
        SELECT COUNT(*) AS registered,
          COALESCE(SUM(CASE WHEN f.merged_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS merged,
          COALESCE(SUM(CASE WHEN f.state = 'open' AND f.merged_at IS NULL THEN 1 ELSE 0 END), 0) AS open_count,
          COALESCE(SUM(CASE WHEN f.state = 'closed' AND f.merged_at IS NULL THEN 1 ELSE 0 END), 0) AS closed_without_merge,
          COALESCE(SUM(CASE WHEN EXISTS (SELECT 1 FROM github_pull_reviews r
                                         WHERE r.pull_request_id = f.id)
                            THEN 1 ELSE 0 END), 0) AS with_human_review,
          COALESCE(SUM(f.commit_count), 0) AS commits_on_known_pulls,
          COUNT(f.commit_count) AS commit_count_sample
        FROM scoped s JOIN github_pull_requests f ON f.employee_user_id = s.id
        WHERE f.created_at >= :fromInstant AND f.created_at < :toExclusive
        """;
    List<long[]> counts = jdbc.query(base, params, (rs, row) -> new long[] {
        rs.getLong("registered"), rs.getLong("merged"), rs.getLong("open_count"),
        rs.getLong("closed_without_merge"), rs.getLong("with_human_review"),
        rs.getLong("commits_on_known_pulls"), rs.getLong("commit_count_sample")});
    long[] values = counts.get(0);
    List<Double> requestLatency = jdbc.query(cte + """
        SELECT f.created_at, f.first_review_requested_at
        FROM scoped s JOIN github_pull_requests f ON f.employee_user_id = s.id
        WHERE f.created_at >= :fromInstant AND f.created_at < :toExclusive
          AND f.first_review_requested_at IS NOT NULL
        """, params, (rs, row) -> Duration.between(
            timestamp(rs, "created_at"), timestamp(rs, "first_review_requested_at"))
            .toMillis() / 3600000.0);
    requestLatency.sort(Comparator.naturalOrder());
    Double requestMedian = requestLatency.isEmpty() ? null
        : (requestLatency.get((requestLatency.size() - 1) / 2)
            + requestLatency.get(requestLatency.size() / 2)) / 2;
    List<Double> latency = jdbc.query(cte + """
        SELECT f.created_at, MIN(r.submitted_at) AS first_review_at
        FROM scoped s JOIN github_pull_requests f ON f.employee_user_id = s.id
        JOIN github_pull_reviews r ON r.pull_request_id = f.id
        WHERE f.created_at >= :fromInstant AND f.created_at < :toExclusive
          AND r.submitted_at IS NOT NULL
        GROUP BY f.id, f.created_at
        """, params, (rs, row) -> Duration.between(
            timestamp(rs, "created_at"), timestamp(rs, "first_review_at")).toMillis() / 3600000.0);
    latency.sort(Comparator.naturalOrder());
    Double median = latency.isEmpty() ? null
        : (latency.get((latency.size() - 1) / 2) + latency.get(latency.size() / 2)) / 2;
    return new PullRequestMetrics(values[0], values[1], values[2], values[3], values[4],
        values[5], values[6], requestMedian, requestLatency.size(), median, latency.size());
  }

  @Transactional(readOnly = true)
  public PageResponse<DashboardPersonResponse> people(
      User manager, DashboardFilter filter, int page, int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > 50) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida.");
    }
    Scope scope = scope(manager, filter);
    MapSqlParameterSource params = scope.params()
        .addValue("limit", pageSize).addValue("offset", (page - 1) * pageSize);
    String cte = "WITH scoped AS (" + scope.sql() + ") ";
    long total = count(cte + "SELECT COUNT(*) FROM scoped", params);
    List<DashboardPersonResponse> items = jdbc.query(cte + """
        SELECT s.id, s.name, s.role_name, s.current_level, s.target_level,
          (SELECT COUNT(*) FROM career_objectives o
           WHERE o.career_profile_id = s.profile_id AND o.status = 'ACTIVE') AS active_objectives,
          (SELECT COUNT(*) FROM evidences e
           WHERE e.user_id = s.id AND e.status = 'PENDING') AS pending_evidence,
          (SELECT COUNT(*) FROM saved_analyses a
           WHERE a.user_id = s.id
             AND NOT EXISTS (SELECT 1 FROM saved_analysis_reviews r
                             WHERE r.saved_analysis_id = a.id)) AS unreviewed_analyses,
          (SELECT MAX(e.occurred_at) FROM evidences e WHERE e.user_id = s.id) AS evidence_at,
          (SELECT MAX(a.created_at) FROM saved_analyses a WHERE a.user_id = s.id) AS analysis_at
        FROM scoped s ORDER BY LOWER(s.name), s.id LIMIT :limit OFFSET :offset
        """, params, (rs, row) -> new DashboardPersonResponse(
            rs.getLong("id"), rs.getString("name"), rs.getString("role_name"),
            rs.getString("current_level"), rs.getString("target_level"),
            rs.getLong("active_objectives"), rs.getLong("pending_evidence"),
            rs.getLong("unreviewed_analyses"),
            later(timestampOrNull(rs, "evidence_at"), timestampOrNull(rs, "analysis_at"))));
    return new PageResponse<>(items, page, pageSize, total);
  }

  private Map<String, Long> statusCounts(
      String cte, MapSqlParameterSource params, boolean period, String statusSql) {
    String date = period
        ? " AND a.created_at >= :fromInstant AND a.created_at < :toExclusive"
        : "";
    Map<String, Long> result = new LinkedHashMap<>();
    result.put("UNREVIEWED", 0L);
    result.put("NEEDS_CONTEXT", 0L);
    result.put("ACCEPTED", 0L);
    jdbc.query(cte + """
        SELECT current_status, COUNT(*) AS total FROM (
          SELECT
        """ + statusSql + " AS current_status FROM scoped s JOIN saved_analyses a ON a.user_id = s.id"
        + " WHERE 1=1" + date + """
        ) states GROUP BY current_status
        """, params, rs -> {
          result.put(rs.getString("current_status"), rs.getLong("total"));
        });
    return result;
  }

  private List<TrendPoint> trend(
      String cte, MapSqlParameterSource params, LocalDate from, LocalDate to) {
    int bucketCount = (int) (ChronoUnit.DAYS.between(from, to) / 7) + 1;
    int[][] values = new int[bucketCount][3];
    addTrendDates(cte + """
        SELECT e.occurred_at AS event_at FROM scoped s JOIN evidences e ON e.user_id = s.id
        WHERE e.occurred_at >= :fromInstant AND e.occurred_at < :toExclusive
        """, params, from, values, 0);
    addTrendDates(cte + """
        SELECT a.created_at AS event_at FROM scoped s JOIN saved_analyses a ON a.user_id = s.id
        WHERE a.created_at >= :fromInstant AND a.created_at < :toExclusive
        """, params, from, values, 1);
    addTrendDates(cte + """
        SELECT r.created_at AS event_at FROM scoped s JOIN saved_analyses a ON a.user_id = s.id
        JOIN saved_analysis_reviews r ON r.saved_analysis_id = a.id
        WHERE r.created_at >= :fromInstant AND r.created_at < :toExclusive
        """, params, from, values, 2);
    List<TrendPoint> points = new ArrayList<>();
    for (int index = 0; index < bucketCount; index++) {
      LocalDate start = from.plusDays(index * 7L);
      points.add(new TrendPoint(start, start.plusDays(6).isAfter(to) ? to : start.plusDays(6),
          values[index][0], values[index][1], values[index][2]));
    }
    return points;
  }

  private void addTrendDates(
      String sql, MapSqlParameterSource params, LocalDate from, int[][] values, int column) {
    jdbc.query(sql, params, rs -> {
      LocalDate date = timestamp(rs, "event_at").atZone(ZONE).toLocalDate();
      int index = (int) (ChronoUnit.DAYS.between(from, date) / 7);
      if (index >= 0 && index < values.length) values[index][column]++;
    });
  }

  private List<AttentionItem> attention(
      String cte, MapSqlParameterSource params, String statusSql) {
    List<AttentionItem> items = new ArrayList<>();
    jdbc.query(cte + """
        SELECT a.id, s.id AS employee_id, s.name, a.source_meta, a.created_at,
        """ + statusSql + """
        AS current_status
        FROM scoped s JOIN saved_analyses a ON a.user_id = s.id
        ORDER BY a.created_at ASC, a.id ASC
        """, params, rs -> {
      String status = rs.getString("current_status");
      if (("UNREVIEWED".equals(status) || "NEEDS_CONTEXT".equals(status)) && items.size() < 6) {
        items.add(new AttentionItem(status, rs.getLong("employee_id"), rs.getString("name"),
            rs.getLong("id"), rs.getString("source_meta"), timestamp(rs, "created_at")));
      }
    });
    jdbc.query(cte + """
        SELECT o.id, s.id AS employee_id, s.name, o.text, o.target_date
        FROM scoped s JOIN career_objectives o ON o.career_profile_id = s.profile_id
        WHERE o.status = 'ACTIVE' AND o.target_date < :today
        ORDER BY o.target_date ASC, o.id ASC LIMIT 3
        """, params, rs -> {
          items.add(new AttentionItem(
              "OBJECTIVE_OVERDUE", rs.getLong("employee_id"), rs.getString("name"),
              rs.getLong("id"), rs.getString("text"),
              rs.getDate("target_date").toLocalDate().atStartOfDay(ZONE).toInstant()));
        });
    return items;
  }

  private List<HighlightItem> highlights(
      String cte, MapSqlParameterSource params, String statusSql) {
    return jdbc.query(cte + """
        SELECT a.id, s.id AS employee_id, s.name,
               COALESCE(NULLIF(a.generated_title, ''),
                   (SELECT g.title FROM github_pull_requests g
                    WHERE g.evidence_id = a.evidence_id ORDER BY g.id DESC LIMIT 1)) AS display_title,
               a.source,
               a.impact_level, a.created_at,
        """ + statusSql + """
        AS current_status
        FROM scoped s JOIN saved_analyses a ON a.user_id = s.id
        WHERE a.created_at >= :fromInstant AND a.created_at < :toExclusive
        ORDER BY a.created_at DESC, a.id DESC LIMIT 6
        """, params, (rs, row) -> new HighlightItem(
            rs.getLong("id"), rs.getLong("employee_id"), rs.getString("name"),
            rs.getString("display_title"), rs.getString("source"),
            rs.getString("impact_level"), rs.getString("current_status"),
            timestamp(rs, "created_at")));
  }

  private Scope scope(User manager, DashboardFilter filter) {
    if (filter.teamId() != null) teams.requireOwnedTeam(manager, filter.teamId());
    if (filter.employeeId() != null) teams.requireVisible(manager, filter.employeeId());
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("managerId", manager.getId()).addValue("asOf", Timestamp.from(Instant.now()));
    StringBuilder sql = new StringBuilder("""
        SELECT u.id, u.name, cp.id AS profile_id, cp.current_level, cp.target_level,
               cp.job_role_id, jr.name AS role_name
        FROM users u
        LEFT JOIN career_profiles cp ON cp.user_id = u.id
        LEFT JOIN job_roles jr ON jr.id = cp.job_role_id
        WHERE u.role = 'EMPLOYEE'
          AND EXISTS (
            SELECT 1 FROM team_memberships tm JOIN teams t ON t.id = tm.team_id
            WHERE tm.employee_user_id = u.id AND t.manager_user_id = :managerId
              AND tm.started_at <= :asOf AND (tm.ended_at IS NULL OR tm.ended_at > :asOf)
        """);
    if (filter.teamId() != null) {
      sql.append(" AND tm.team_id = :teamId");
      params.addValue("teamId", filter.teamId());
    }
    sql.append(")");
    if (filter.jobRoleId() != null) {
      sql.append(" AND cp.job_role_id = :jobRoleId");
      params.addValue("jobRoleId", filter.jobRoleId());
    }
    if (filter.employeeId() != null) {
      sql.append(" AND u.id = :employeeId");
      params.addValue("employeeId", filter.employeeId());
    }
    return new Scope(sql.toString(), params);
  }

  private MapSqlParameterSource periodParams(
      MapSqlParameterSource params, LocalDate from, LocalDate to) {
    return params
        .addValue("fromInstant", Timestamp.from(from.atStartOfDay(ZONE).toInstant()))
        .addValue("toExclusive", Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant()))
        .addValue("today", LocalDate.now(ZONE))
        .addValue("dueSoon", LocalDate.now(ZONE).plusDays(attentionDays))
        .addValue("sevenDaysAgo", Timestamp.from(Instant.now().minus(attentionDays, ChronoUnit.DAYS)));
  }

  private LocalDate dateFrom(DashboardFilter filter) {
    if (!List.of("7", "30", "90", "custom").contains(filter.period())
        || ("custom".equals(filter.period())
            && (filter.from() == null || filter.to() == null))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido.");
    }
    LocalDate to = dateTo(filter);
    int days = "90".equals(filter.period()) ? 90 : "7".equals(filter.period()) ? 7 : 30;
    LocalDate from = filter.from() == null ? to.minusDays(days - 1L) : filter.from();
    if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 365) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido.");
    }
    return from;
  }

  private LocalDate dateTo(DashboardFilter filter) {
    return filter.to() == null ? LocalDate.now(ZONE) : filter.to();
  }

  private long count(String sql, MapSqlParameterSource params) {
    Long result = jdbc.queryForObject(sql, params, Long.class);
    return result == null ? 0 : result;
  }

  private static Instant timestamp(java.sql.ResultSet rs, String field) throws java.sql.SQLException {
    return rs.getTimestamp(field).toInstant();
  }

  private static Instant timestampOrNull(java.sql.ResultSet rs, String field)
      throws java.sql.SQLException {
    Timestamp value = rs.getTimestamp(field);
    return value == null ? null : value.toInstant();
  }

  private static Instant later(Instant first, Instant second) {
    if (first == null) return second;
    if (second == null) return first;
    return first.isAfter(second) ? first : second;
  }

  private record Scope(String sql, MapSqlParameterSource params) {}

  public record DashboardFilter(
      LocalDate from, LocalDate to, Long teamId, Long jobRoleId, Long employeeId, String period) {}
}
