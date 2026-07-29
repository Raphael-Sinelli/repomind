package com.rsinelli.repomind.repository;

import com.rsinelli.repomind.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Um repositorio do GitHub que o usuario tem visivel no RepoMind. */
@Entity
@Table(
    name = "repositories",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_repositories_user_github_repo",
            columnNames = {"user_id", "github_repo_id"}))
public class Repository {

  @Id
  @GeneratedValue
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Column(name = "github_repo_id", nullable = false, updatable = false)
  private Long githubRepoId;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "description")
  private String description;

  @Column(name = "stars", nullable = false)
  private int stars;

  @Column(name = "primary_language")
  private String primaryLanguage;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  protected Repository() {
    // exigido pelo JPA
  }

  public Repository(User user, Long githubRepoId, String fullName) {
    this.user = user;
    this.githubRepoId = githubRepoId;
    this.fullName = fullName;
    this.stars = 0;
  }

  /** Reaplica o estado vindo do GitHub e marca o momento do sync. */
  public void syncFrom(String fullName, String description, int stars, String primaryLanguage) {
    this.fullName = fullName;
    this.description = description;
    this.stars = stars;
    this.primaryLanguage = primaryLanguage;
    this.lastSyncedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public Long getGithubRepoId() {
    return githubRepoId;
  }

  public String getFullName() {
    return fullName;
  }

  public String getDescription() {
    return description;
  }

  public int getStars() {
    return stars;
  }

  public String getPrimaryLanguage() {
    return primaryLanguage;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }
}
