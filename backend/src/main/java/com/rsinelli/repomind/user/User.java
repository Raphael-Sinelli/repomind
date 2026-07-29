package com.rsinelli.repomind.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /** Identificador numerico e imutavel do GitHub. O login pode mudar; este nao. */
  @Column(name = "github_id", nullable = false, unique = true, updatable = false)
  private Long githubId;

  @Column(name = "username", nullable = false)
  private String username;

  /** Null quando o usuario mantem o email privado no GitHub. */
  @Column(name = "email")
  private String email;

  @Column(name = "avatar_url")
  private String avatarUrl;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected User() {
    // exigido pelo JPA
  }

  public User(Long githubId, String username, String email, String avatarUrl) {
    this.githubId = githubId;
    this.username = username;
    this.email = email;
    this.avatarUrl = avatarUrl;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      // Instant.now() tem precisao de nanossegundos; TIMESTAMPTZ guarda microssegundos.
      // Sem truncar, o objeto em memoria e o mesmo registro relido do banco comparam
      // como diferentes — um equals() que falha sem motivo aparente.
      createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
  }

  /** Atualiza o que pode mudar entre logins. {@code githubId} e {@code createdAt} nao. */
  public void refreshProfile(String username, String email, String avatarUrl) {
    this.username = username;
    this.email = email;
    this.avatarUrl = avatarUrl;
  }

  public UUID getId() {
    return id;
  }

  public Long getGithubId() {
    return githubId;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
