package br.com.promova.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthTokenResolver {
  public String resolve(String authorizationHeader) {
    if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
      return null;
    }

    return authorizationHeader.substring("Bearer ".length()).trim();
  }

  public String resolve(HttpHeaders headers) {
    return resolve(headers.getFirst(HttpHeaders.AUTHORIZATION));
  }
}
