package br.com.promova.insight.service;

import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.evidence.Evidence;
import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.insight.dto.InsightsResponse;
import br.com.promova.insight.dto.InsightsResponse.CoverageStatus;
import br.com.promova.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InsightsService {
  private static final ZoneOffset UTC = ZoneOffset.UTC;
  private static final DateTimeFormatter TREND_DATE = DateTimeFormatter.ofPattern("dd/MM");
  private static final int MAX_TREND_BUCKETS = 6;

  private final SavedAnalysisRepository savedAnalysisRepository;
  private final FrameworkProvider frameworkProvider;
  private final ObjectMapper objectMapper;

  public InsightsService(
      SavedAnalysisRepository savedAnalysisRepository,
      FrameworkProvider frameworkProvider,
      ObjectMapper objectMapper) {
    this.savedAnalysisRepository = savedAnalysisRepository;
    this.frameworkProvider = frameworkProvider;
    this.objectMapper = objectMapper;
  }

  /**
   * Summarizes only saved analyses owned by {@code user}. The optional date window is inclusive,
   * matching GET /analyses: {@code from} and {@code to} are UTC instants and are passed directly to
   * the owner-scoped repository query.
   *
   * <p>When a bound is omitted, trend buckets use the earliest/latest saved timestamp in the
   * filtered result instead of the current clock. This keeps an unbounded response reproducible.
   * For up to six calendar days, buckets are daily; longer windows are split into at most six
   * consecutive calendar ranges.
   *
   * <p>A criterion is supported only when a saved analysis has the same normalized competency
   * label and the same saved estimated level as that framework criterion. This avoids treating a
   * lower- or higher-level description with the same label as proven by inference.
   */
  @Transactional(readOnly = true)
  public InsightsResponse summarizeForUser(User user, Instant from, Instant to) {
    validateDateWindow(from, to);

    List<SavedAnalysis> analyses =
        savedAnalysisRepository.findByUserAndDateRange(user, from, to).stream()
            .filter(Objects::nonNull)
            .filter(analysis -> belongsTo(analysis.getUser(), user))
            .sorted(analysisOrder())
            .toList();
    CareerFramework framework = frameworkProvider.load();
    List<FrameworkCriterion> criteria = frameworkCriteria(framework);

    List<InsightsResponse.CriterionCoverage> coverage = new ArrayList<>();
    List<InsightsResponse.Gap> gaps = new ArrayList<>();
    int criteriaWithEvidence = 0;

    for (FrameworkCriterion criterion : criteria) {
      List<InsightsResponse.EvidenceReference> supportingEvidence =
          supportingEvidence(analyses, user, criterion.level(), criterion.criterion());
      CoverageStatus status =
          supportingEvidence.isEmpty() ? CoverageStatus.NO_EVIDENCE : CoverageStatus.SUPPORTED;
      if (status == CoverageStatus.SUPPORTED) {
        criteriaWithEvidence++;
      }

      coverage.add(
          new InsightsResponse.CriterionCoverage(
              criterion.level(),
              criterion.levelTitle(),
              criterion.criterion(),
              criterion.description(),
              status,
              supportingEvidence.size(),
              supportingEvidence));

      if (status == CoverageStatus.NO_EVIDENCE) {
        gaps.add(
            new InsightsResponse.Gap(
                criterion.level(),
                criterion.levelTitle(),
                criterion.criterion(),
                criterion.description(),
                CoverageStatus.NO_EVIDENCE));
      }
    }

    return new InsightsResponse(
        analyses.size(),
        criteria.size(),
        criteriaWithEvidence,
        sourceDistribution(analyses),
        estimatedLevelDistribution(analyses, framework),
        coverage,
        recentTrend(analyses, from, to),
        gaps);
  }

  private void validateDateWindow(Instant from, Instant to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
    }
  }

  private List<FrameworkCriterion> frameworkCriteria(CareerFramework framework) {
    if (framework == null || framework.levels() == null) {
      return List.of();
    }

    List<FrameworkCriterion> criteria = new ArrayList<>();
    framework
        .levels()
        .forEach(
            (level, careerLevel) -> {
              if (level == null || level.isBlank() || careerLevel == null) {
                return;
              }
              Map<String, String> levelCriteria = careerLevel.criteria();
              if (levelCriteria == null) {
                return;
              }
              String levelTitle = displayLevelTitle(level, careerLevel);
              levelCriteria.forEach(
                  (criterion, description) -> {
                    if (criterion != null && !criterion.isBlank()) {
                      criteria.add(
                          new FrameworkCriterion(
                              level,
                              levelTitle,
                              criterion.trim(),
                              description == null ? "" : description));
                    }
                  });
            });
    return List.copyOf(criteria);
  }

  private String displayLevelTitle(String level, CareerLevel careerLevel) {
    return careerLevel.title() == null || careerLevel.title().isBlank()
        ? level
        : careerLevel.title();
  }

  private List<InsightsResponse.Distribution> sourceDistribution(List<SavedAnalysis> analyses) {
    Map<String, Integer> counts = new TreeMap<>();
    Map<String, String> labels = new HashMap<>();
    for (SavedAnalysis analysis : analyses) {
      String label = displayValue(analysis.getSource(), "Fonte desconhecida");
      String key = normalize(label);
      counts.merge(key, 1, Integer::sum);
      labels.merge(key, label, this::stableLabel);
    }
    return distributions(counts, labels, analyses.size());
  }

  private List<InsightsResponse.Distribution> estimatedLevelDistribution(
      List<SavedAnalysis> analyses, CareerFramework framework) {
    Map<String, Integer> observed = new HashMap<>();
    for (SavedAnalysis analysis : analyses) {
      String level = displayValue(analysis.getImpactLevel(), "Nível desconhecido");
      observed.merge(level, 1, Integer::sum);
    }

    Map<String, Integer> ordered = new LinkedHashMap<>();
    Map<String, String> labels = new HashMap<>();
    if (framework != null && framework.levels() != null) {
      framework
          .levelKeys()
          .forEach(
              level -> {
                if (observed.containsKey(level)) {
                  ordered.put(level, observed.remove(level));
                  labels.put(level, level);
                }
              });
    }

    observed.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
        .forEach(
            entry -> {
              ordered.put(entry.getKey(), entry.getValue());
              labels.put(entry.getKey(), entry.getKey());
            });
    return distributions(ordered, labels, analyses.size());
  }

  private List<InsightsResponse.Distribution> distributions(
      Map<String, Integer> counts, Map<String, String> labels, int total) {
    return counts.entrySet().stream()
        .map(
            entry ->
                new InsightsResponse.Distribution(
                    labels.getOrDefault(entry.getKey(), entry.getKey()),
                    entry.getValue(),
                    percentage(entry.getValue(), total)))
        .toList();
  }

  private List<InsightsResponse.EvidenceReference> supportingEvidence(
      List<SavedAnalysis> analyses, User owner, String level, String criterion) {
    String criterionKey = normalize(criterion);
    Map<String, InsightsResponse.EvidenceReference> uniqueReferences = new LinkedHashMap<>();
    for (SavedAnalysis analysis : analyses) {
      if (!normalize(analysis.getImpactLevel()).equals(normalize(level))
          || !readCompetencyKeys(analysis).contains(criterionKey)) {
        continue;
      }
      String id = referenceId(analysis);
      uniqueReferences.putIfAbsent(
          id,
          new InsightsResponse.EvidenceReference(
              id,
              linkedEvidenceId(analysis, owner),
              displayValue(analysis.getSourceMeta(), "Evidência salva"),
              displayValue(analysis.getSource(), "Fonte desconhecida"),
              analysis.getCreatedAt()));
    }
    return uniqueReferences.values().stream().toList();
  }

  private Long linkedEvidenceId(SavedAnalysis analysis, User owner) {
    Evidence evidence = analysis.getEvidenceEntity();
    if (evidence == null
        || evidence.getId() == null
        || !belongsTo(analysis.getUser(), owner)
        || !belongsTo(evidence.getUser(), owner)) {
      return null;
    }
    return evidence.getId();
  }

  private boolean belongsTo(User candidate, User owner) {
    return candidate != null
        && owner != null
        && candidate.getId() != null
        && owner.getId() != null
        && Objects.equals(candidate.getId(), owner.getId());
  }

  private Set<String> readCompetencyKeys(SavedAnalysis analysis) {
    if (analysis.getCompetenciesJson() == null || analysis.getCompetenciesJson().isBlank()) {
      return Set.of();
    }

    try {
      List<String> values =
          objectMapper.readValue(analysis.getCompetenciesJson(), new TypeReference<>() {});
      if (values == null) {
        return Set.of();
      }
      return values.stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(value -> !value.isBlank())
          .map(this::normalize)
          .collect(Collectors.toCollection(LinkedHashSet::new));
    } catch (JsonProcessingException exception) {
      return Set.of();
    }
  }

  private List<InsightsResponse.TrendBucket> recentTrend(
      List<SavedAnalysis> analyses, Instant from, Instant to) {
    if (analyses.isEmpty()) {
      return List.of();
    }

    Instant earliest =
        analyses.stream()
            .map(SavedAnalysis::getCreatedAt)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);
    Instant latest =
        analyses.stream()
            .map(SavedAnalysis::getCreatedAt)
            .filter(Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(null);
    if (earliest == null || latest == null) {
      return List.of();
    }

    Instant windowStart = from == null ? earliest : from;
    Instant windowEnd = to == null ? latest : to;
    LocalDate firstDate = windowStart.atZone(UTC).toLocalDate();
    LocalDate lastDate = windowEnd.atZone(UTC).toLocalDate();
    long days = ChronoUnit.DAYS.between(firstDate, lastDate) + 1;
    long bucketDays = days <= MAX_TREND_BUCKETS ? 1 : (days + MAX_TREND_BUCKETS - 1) / MAX_TREND_BUCKETS;

    List<TrendRange> ranges = new ArrayList<>();
    LocalDate rangeStart = firstDate;
    while (!rangeStart.isAfter(lastDate)) {
      LocalDate rangeEnd =
          rangeStart.plusDays(bucketDays - 1).isAfter(lastDate)
              ? lastDate
              : rangeStart.plusDays(bucketDays - 1);
      Instant bucketStart =
          max(windowStart, rangeStart.atStartOfDay(UTC).toInstant());
      Instant bucketEnd =
          min(
              windowEnd,
              rangeEnd.plusDays(1).atStartOfDay(UTC).toInstant().minusNanos(1));
      ranges.add(new TrendRange(rangeStart, rangeEnd, bucketStart, bucketEnd));
      rangeStart = rangeEnd.plusDays(1);
    }

    List<Integer> counts =
        ranges.stream()
            .map(
                range ->
                    (int)
                        analyses.stream()
                            .filter(analysis -> inRange(analysis.getCreatedAt(), range))
                            .count())
            .toList();
    int maxCount = counts.stream().mapToInt(Integer::intValue).max().orElse(0);

    List<InsightsResponse.TrendBucket> trend = new ArrayList<>();
    for (int index = 0; index < ranges.size(); index++) {
      TrendRange range = ranges.get(index);
      int count = counts.get(index);
      trend.add(
          new InsightsResponse.TrendBucket(
              trendLabel(range.startDate(), range.endDate()),
              range.bucketStart(),
              range.bucketEnd(),
              count,
              percentage(count, maxCount)));
    }
    return List.copyOf(trend);
  }

  private boolean inRange(Instant createdAt, TrendRange range) {
    return createdAt != null
        && !createdAt.isBefore(range.bucketStart())
        && !createdAt.isAfter(range.bucketEnd());
  }

  private String trendLabel(LocalDate start, LocalDate end) {
    String first = TREND_DATE.format(start);
    String last = TREND_DATE.format(end);
    return first.equals(last) ? first : first + " - " + last;
  }

  private Comparator<SavedAnalysis> analysisOrder() {
    return Comparator.comparing(
            SavedAnalysis::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(this::referenceId, String.CASE_INSENSITIVE_ORDER);
  }

  private String referenceId(SavedAnalysis analysis) {
    if (analysis.getExternalId() != null && !analysis.getExternalId().isBlank()) {
      return analysis.getExternalId();
    }
    return analysis.getId() == null ? "unknown-analysis" : String.valueOf(analysis.getId());
  }

  private String displayValue(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String stableLabel(String first, String second) {
    return first.compareTo(second) <= 0 ? first : second;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private int percentage(int value, int total) {
    if (value <= 0 || total <= 0) {
      return 0;
    }
    return (int) Math.round(value * 100.0 / total);
  }

  private Instant max(Instant first, Instant second) {
    return first.isAfter(second) ? first : second;
  }

  private Instant min(Instant first, Instant second) {
    return first.isBefore(second) ? first : second;
  }

  private record FrameworkCriterion(
      String level, String levelTitle, String criterion, String description) {}

  private record TrendRange(
      LocalDate startDate,
      LocalDate endDate,
      Instant bucketStart,
      Instant bucketEnd) {}
}
