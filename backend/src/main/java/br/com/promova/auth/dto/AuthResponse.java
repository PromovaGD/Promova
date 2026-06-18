package br.com.promova.auth.dto;

import br.com.promova.user.User;

public record AuthResponse(String token, UserSummaryResponse user) {
  public static AuthResponse of(String token, User user) {
    return new AuthResponse(token, UserSummaryResponse.from(user));
  }
}
