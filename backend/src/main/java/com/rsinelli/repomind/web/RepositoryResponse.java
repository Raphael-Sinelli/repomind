package com.rsinelli.repomind.web;

import com.rsinelli.repomind.repository.Repository;
import java.time.Instant;
import java.util.UUID;

public record RepositoryResponse(
    UUID id,
    Long githubRepoId,
    String fullName,
    String description,
    int stars,
    String primaryLanguage,
    Instant lastSyncedAt) {

  public static RepositoryResponse from(Repository repository) {
    return new RepositoryResponse(
        repository.getId(),
        repository.getGithubRepoId(),
        repository.getFullName(),
        repository.getDescription(),
        repository.getStars(),
        repository.getPrimaryLanguage(),
        repository.getLastSyncedAt());
  }
}
