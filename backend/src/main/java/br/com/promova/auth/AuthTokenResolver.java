package br.com.promova.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthTokenResolver {
  public String resolve(String authorizationHeader) {
    if (!StringUtils.hasText(authorizationHeader)) {
      return null;
    }

    String[] parts = authorizationHeader.trim().split("\\s+");
    if (parts.length != 2 || !"Bearer".equalsIgnoreCase(parts[0]) || !StringUtils.hasText(parts[1])) {
      return null;
    }

    return parts[1].trim();
  }

  public String resolve(HttpHeaders headers) {
    return resolve(headers.getFirst(HttpHeaders.AUTHORIZATION));
  }
}
