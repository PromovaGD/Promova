package br.com.promova.analysis.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.analysis.review.ReviewStatus;
import br.com.promova.analysis.review.dto.AnalysisReviewResponse;
import br.com.promova.analysis.review.dto.SavedAnalysisReviewResponse;
import br.com.promova.analysis.review.service.AnalysisReviewService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(SavedAnalysisReviewController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class SavedAnalysisReviewControllerSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AnalysisReviewService analysisReviewService;
  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private UserRepository userRepository;

  @Test
  void rejectsAnonymousReviewReads() throws Exception {
    mockMvc
        .perform(get("/analyses/41/reviews"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void employeeCanReadOnlyTheirOwnReviewHistory() throws Exception {
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    authenticate(employee);
    when(analysisReviewService.listForOwner(employee, 41L))
        .thenReturn(new AnalysisReviewResponse(41L, ReviewStatus.UNREVIEWED, List.of()));

    mockMvc
        .perform(get("/analyses/41/reviews").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysisId").value(41))
        .andExpect(jsonPath("$.currentStatus").value("UNREVIEWED"))
        .andExpect(jsonPath("$.history").isEmpty());
  }

  @Test
  void employeeCannotCreateAReview() throws Exception {
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    authenticate(employee);

    mockMvc
        .perform(
            post("/analyses/41/reviews")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\",\"reviewerId\":999}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCanAppendAReviewForAVisibleEmployeeAnalysis() throws Exception {
    User manager = user("Manager", "manager@example.com", UserRole.MANAGER, 1L);
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    authenticate(manager);
    when(userRepository.findById(2L)).thenReturn(Optional.of(employee));
    SavedAnalysisReviewResponse event =
        new SavedAnalysisReviewResponse(
            9L,
            1L,
            "Manager",
            "manager@example.com",
            Instant.parse("2026-08-09T10:00:00Z"),
            ReviewStatus.ACCEPTED,
            "Evidence is clear.");
    when(analysisReviewService.appendForManager(eq(manager), eq(2L), eq(41L), any()))
        .thenReturn(new AnalysisReviewResponse(41L, ReviewStatus.ACCEPTED, List.of(event)));

    mockMvc
        .perform(
            post("/manager/employees/2/analyses/41/reviews")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\",\"comment\":\"Evidence is clear.\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currentStatus").value("ACCEPTED"))
        .andExpect(jsonPath("$.history[0].reviewerId").value(1))
        .andExpect(jsonPath("$.history[0].reviewerEmail").value("manager@example.com"));

    verify(analysisReviewService).appendForManager(eq(manager), eq(2L), eq(41L), any());
  }

  @Test
  void employeeCannotUseTheManagerReviewRoute() throws Exception {
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    authenticate(employee);

    mockMvc
        .perform(
            post("/manager/employees/3/analyses/41/reviews")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsOwnerScopedNotFoundFromTheReviewService() throws Exception {
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    authenticate(employee);
    when(analysisReviewService.listForOwner(employee, 41L))
        .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Analysis not found."));

    mockMvc
        .perform(get("/analyses/41/reviews").header(HttpHeaders.AUTHORIZATION, bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsOversizedCommentsBeforeCreatingAnEvent() throws Exception {
    User manager = user("Manager", "manager@example.com", UserRole.MANAGER, 1L);
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    authenticate(manager);
    when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

    mockMvc
        .perform(
            post("/manager/employees/2/analyses/41/reviews")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\",\"comment\":\"" + "x".repeat(2001) + "\"}"))
        .andExpect(status().isBadRequest());

    verify(analysisReviewService, never()).appendForManager(any(), any(), any(), any());
  }

  private void authenticate(User user) {
    when(authTokenResolver.resolve(bearer())).thenReturn("token");
    when(authService.requireUser("token")).thenReturn(user);
  }

  private String bearer() {
    return "Bearer review-token";
  }

  private User user(String name, String email, UserRole role, Long id) {
    User user = new User(name, email, "hash", role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
