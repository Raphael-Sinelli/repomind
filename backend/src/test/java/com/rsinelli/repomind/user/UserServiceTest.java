package com.rsinelli.repomind.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.rsinelli.repomind.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserServiceTest extends AbstractIntegrationTest {

  @Autowired UserService userService;
  @Autowired UserRepository userRepository;

  @BeforeEach
  void clean() {
    userRepository.deleteAll();
  }

  private static Map<String, Object> githubAttributes(long id, String login, String email) {
    return Map.of(
        "id", id,
        "login", login,
        "email", email == null ? "" : email,
        "avatar_url", "https://avatars.githubusercontent.com/u/" + id);
  }

  @Test
  @DisplayName("primeiro login cria o usuario")
  void createsUserOnFirstLogin() {
    User saved = userService.upsertFromGitHub(githubAttributes(4242L, "rapha", "rapha@example.com"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getGithubId()).isEqualTo(4242L);
    assertThat(saved.getUsername()).isEqualTo("rapha");
    assertThat(saved.getEmail()).isEqualTo("rapha@example.com");
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(userRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("segundo login do mesmo github_id atualiza em vez de duplicar")
  void updatesInsteadOfDuplicating() {
    User first = userService.upsertFromGitHub(githubAttributes(4242L, "rapha", "old@example.com"));
    User second =
        userService.upsertFromGitHub(githubAttributes(4242L, "rapha-novo", "new@example.com"));

    assertThat(userRepository.count()).isEqualTo(1);
    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(second.getUsername()).isEqualTo("rapha-novo");
    assertThat(second.getEmail()).isEqualTo("new@example.com");
    // created_at nao pode ser reescrito num update.
    assertThat(second.getCreatedAt()).isEqualTo(first.getCreatedAt());
  }

  @Test
  @DisplayName("email privado no GitHub vira null, nao string vazia")
  void handlesMissingEmail() {
    User saved = userService.upsertFromGitHub(githubAttributes(99L, "sem-email", null));

    assertThat(saved.getEmail()).isNull();
  }

  @Test
  @DisplayName("usuarios diferentes coexistem")
  void keepsDistinctUsers() {
    userService.upsertFromGitHub(githubAttributes(1L, "a", "a@example.com"));
    userService.upsertFromGitHub(githubAttributes(2L, "b", "b@example.com"));

    assertThat(userRepository.count()).isEqualTo(2);
  }
}
