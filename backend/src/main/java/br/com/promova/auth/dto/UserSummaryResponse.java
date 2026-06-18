package br.com.promova.auth.dto;

import br.com.promova.user.User;
import br.com.promova.user.UserRole;

public record UserSummaryResponse(Long id, String name, String email, UserRole role) {
  public static UserSummaryResponse from(User user) {
    return new UserSummaryResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
  }
}
