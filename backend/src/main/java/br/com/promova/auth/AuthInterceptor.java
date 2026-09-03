package br.com.promova.auth;

import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;

  public AuthInterceptor(AuthService authService, AuthTokenResolver authTokenResolver) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    String path = requestPath(request);
    if (isPublicAuthPath(path)) {
      return true;
    }

    String token = authTokenResolver.resolve(request.getHeader(HttpHeaders.AUTHORIZATION));
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token ausente.");
    }

    User user = authService.requireUser(token);
    if (requiresManager(path) && user.getRole() != UserRole.MANAGER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso restrito a gestores.");
    }

    return true;
  }

  private String requestPath(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (requestUri == null) {
      return "";
    }
    if (contextPath == null || contextPath.isEmpty()) {
      return requestUri;
    }
    return requestUri.substring(contextPath.length());
  }

  private boolean isPublicAuthPath(String path) {
    return "/auth/register".equals(path) || "/auth/login".equals(path);
  }

  private boolean requiresManager(String path) {
    return "/manager".equals(path) || path.startsWith("/manager/");
  }
}
