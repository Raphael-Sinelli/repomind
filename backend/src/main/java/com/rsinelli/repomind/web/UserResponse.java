package com.rsinelli.repomind.web;

import com.rsinelli.repomind.user.User;
import java.time.Instant;
import java.util.UUID;

/**
 * Campos em camelCase; Jackson serializa em snake_case ({@code github_id},
 * {@code avatar_url}, {@code created_at}) por conta da estrategia global.
 */
public record UserResponse(
    UUID id,
    Long githubId,
    String username,
    String email,
    String avatarUrl,
    Instant createdAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getGithubId(),
        user.getUsername(),
        user.getEmail(),
        user.getAvatarUrl(),
        user.getCreatedAt());
  }
}
