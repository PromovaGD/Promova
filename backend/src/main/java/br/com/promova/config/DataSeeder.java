package br.com.promova.config;

import br.com.promova.analysis.dto.SavedAnalysisRequest;
import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.profile.ProfileService;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {
  @Bean
  CommandLineRunner seedUsers(
      UserRepository userRepository,
      SavedAnalysisService savedAnalysisService,
      PasswordEncoder passwordEncoder,
      ProfileService profileService) {
    return args -> {
      if (userRepository.count() > 0) {
        userRepository.findAll().forEach(profileService::ensureProfile);
        return;
      }

      userRepository.save(
          new User(
              "Administrador",
              "admin@promova.com",
              passwordEncoder.encode("admin123"),
              UserRole.ADMIN));

      User joao =
          userRepository.save(
              new User(
                  "João Silva",
                  "joao.silva@empresa.com",
                  passwordEncoder.encode("senha123"),
                  UserRole.EMPLOYEE));

      User maria =
          userRepository.save(
              new User(
                  "Maria Santos",
                  "maria.santos@empresa.com",
                  passwordEncoder.encode("senha123"),
                  UserRole.EMPLOYEE));

      User pedro =
          userRepository.save(
              new User(
                  "Pedro Costa",
                  "pedro.costa@empresa.com",
                  passwordEncoder.encode("senha123"),
                  UserRole.EMPLOYEE));

      userRepository.findAll().forEach(profileService::ensureProfile);

      seedAnalysis(
          savedAnalysisService,
          joao,
          "github-pr-1842",
          "GitHub",
          "PR #1842 · payment-service",
          "Refatorou o módulo de pagamentos e aumentou a cobertura de testes de 62% para 89%.",
          "L3",
          "L4",
          "L4",
          "high",
          "A evidência mostra ownership técnico, melhoria mensurável de qualidade e impacto direto em um módulo crítico.",
          List.of("Code Quality", "Ownership", "Testing"),
          List.of(
              "Documente o impacto em incidentes ou retrabalho evitado.",
              "Conecte a entrega a um resultado de negócio mensurável."),
          Instant.parse("2026-05-12T14:30:00Z"));

      seedAnalysis(
          savedAnalysisService,
          joao,
          "jira-prom-218",
          "Jira",
          "PROM-218 · Plataforma",
          "Liderou a entrega da funcionalidade de rollout gradual com feature flags.",
          "L3",
          "L4",
          "L4",
          "medium",
          "Demonstra coordenação de entrega, gestão de risco e visão de plataforma alinhada ao nível alvo.",
          List.of("Delivery", "Platform Thinking", "Risk Management"),
          List.of("Registre métricas de rollout e rollback.", "Compartilhe aprendizados com o time."),
          Instant.parse("2026-05-08T09:15:00Z"));

      seedAnalysis(
          savedAnalysisService,
          maria,
          "slack-platform-rollout",
          "Slack",
          "#platform · Comunicação",
          "Conduziu alinhamento cross-team sobre incidente de produção e plano de mitigação.",
          "L4",
          "L5",
          "L4",
          "medium",
          "Evidência forte de comunicação e liderança situacional, ainda abaixo do alvo L5 por falta de escopo estratégico amplo.",
          List.of("Communication", "Leadership", "Incident Response"),
          List.of("Amplie o impacto para iniciativas preventivas.", "Formalize postmortem com métricas."),
          Instant.parse("2026-05-10T18:45:00Z"));

      seedAnalysis(
          savedAnalysisService,
          maria,
          "github-pr-902",
          "GitHub",
          "PR #902 · auth-service",
          "Implementou autenticação OAuth e revisou PRs de colegas júnior.",
          "L4",
          "L5",
          "L4",
          "high",
          "Combina entrega técnica relevante com mentoria, indicando maturidade consistente com L4.",
          List.of("Security", "Mentorship", "System Design"),
          List.of("Escale a mentoria para um programa estruturado.", "Documente decisões arquiteturais."),
          Instant.parse("2026-05-03T11:20:00Z"));

      seedAnalysis(
          savedAnalysisService,
          pedro,
          "jira-prom-301",
          "Jira",
          "PROM-301 · Observabilidade",
          "Configurou dashboards de observabilidade e alertas para o serviço de checkout.",
          "L2",
          "L3",
          "L3",
          "medium",
          "Entrega sólida de operação e confiabilidade, alinhada ao nível atual com caminho claro para L4.",
          List.of("Observability", "Reliability"),
          List.of("Conecte alertas a SLOs.", "Proponha melhorias proativas além do escopo pedido."),
          Instant.parse("2026-05-01T16:00:00Z"));
    };
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private void seedAnalysis(
      SavedAnalysisService savedAnalysisService,
      User user,
      String externalId,
      String source,
      String sourceMeta,
      String evidence,
      String currentLevel,
      String targetLevel,
      String impactLevel,
      String confidence,
      String justification,
      List<String> competencies,
      List<String> suggestions,
      Instant createdAt) {
    savedAnalysisService.save(
        user,
        new SavedAnalysisRequest(
            externalId,
            source,
            sourceMeta,
            evidence,
            currentLevel,
            targetLevel,
            impactLevel,
            confidence,
            justification,
            impactLevel.equals(targetLevel) || impactLevel.compareTo(targetLevel) >= 0
                ? "Esta evidência está alinhada com o alvo atual de " + targetLevel + "."
                : "Esta evidência ainda está abaixo do alvo de "
                    + targetLevel
                    + ", então pode ser fortalecida com resultados mensuráveis.",
            createdAt,
            competencies,
            suggestions));
  }
}
