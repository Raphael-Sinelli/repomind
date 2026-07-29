package com.rsinelli.repomind.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.rsinelli.repomind.AbstractIntegrationTest;
import com.rsinelli.repomind.exception.NotFoundException;
import com.rsinelli.repomind.github.GitHubClient;
import com.rsinelli.repomind.github.GitHubRepo;
import com.rsinelli.repomind.github.GitHubTokenProvider;
import com.rsinelli.repomind.user.User;
import com.rsinelli.repomind.user.UserRepository;
import com.rsinelli.repomind.user.UserService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class RepositoryServiceTest extends AbstractIntegrationTest {

  @Autowired RepositoryService repositoryService;
  @Autowired RepositoryRepository repositories;
  @Autowired UserService userService;
  @Autowired UserRepository users;

  // A rede fica de fora: o que se testa aqui e a reconciliacao, nao o HTTP.
  @MockitoBean GitHubClient gitHubClient;
  @MockitoBean GitHubTokenProvider tokenProvider;

  private User user;

  @BeforeEach
  void setUp() {
    repositories.deleteAll();
    users.deleteAll();
    user =
        userService.upsertFromGitHub(
            Map.of("id", 1L, "login", "rapha", "email", "r@example.com", "avatar_url", "http://a"));
    when(tokenProvider.currentToken()).thenReturn("token-de-teste");
  }

  private static GitHubRepo remoteRepo(long id, String fullName, int stars, String language) {
    return new GitHubRepo(
        id, fullName.split("/")[1], fullName, "desc", stars, language, false, false, "main");
  }

  @Test
  @DisplayName("primeiro sync grava os repositorios")
  void firstSyncPersists() {
    when(gitHubClient.listRepositories(anyString()))
        .thenReturn(List.of(remoteRepo(10L, "rapha/repomind", 7, "Java")));

    List<Repository> result = repositoryService.syncFromGitHub(user);

    assertThat(result).hasSize(1);
    Repository saved = result.getFirst();
    assertThat(saved.getFullName()).isEqualTo("rapha/repomind");
    assertThat(saved.getStars()).isEqualTo(7);
    assertThat(saved.getPrimaryLanguage()).isEqualTo("Java");
    assertThat(saved.getLastSyncedAt()).isNotNull();
  }

  @Test
  @DisplayName("segundo sync atualiza em vez de duplicar, e o GitHub vence")
  void secondSyncUpdatesInPlace() {
    when(gitHubClient.listRepositories(anyString()))
        .thenReturn(List.of(remoteRepo(10L, "rapha/repomind", 7, "Java")));
    UUID idOriginal = repositoryService.syncFromGitHub(user).getFirst().getId();

    // Repositorio renomeado e com mais estrelas do lado do GitHub.
    when(gitHubClient.listRepositories(anyString()))
        .thenReturn(List.of(remoteRepo(10L, "rapha/repomind-v2", 42, "Kotlin")));
    List<Repository> depois = repositoryService.syncFromGitHub(user);

    assertThat(depois).hasSize(1);
    assertThat(repositories.count()).isEqualTo(1);
    assertThat(depois.getFirst().getId()).isEqualTo(idOriginal);
    assertThat(depois.getFirst().getFullName()).isEqualTo("rapha/repomind-v2");
    assertThat(depois.getFirst().getStars()).isEqualTo(42);
  }

  @Test
  @DisplayName("repositorio que sumiu do GitHub nao e apagado do banco")
  void keepsRepositoriesThatDisappearedRemotely() {
    when(gitHubClient.listRepositories(anyString()))
        .thenReturn(
            List.of(remoteRepo(10L, "rapha/a", 1, "Java"), remoteRepo(11L, "rapha/b", 2, "Go")));
    repositoryService.syncFromGitHub(user);

    // O repositorio 11 virou privado ou foi removido.
    when(gitHubClient.listRepositories(anyString()))
        .thenReturn(List.of(remoteRepo(10L, "rapha/a", 1, "Java")));
    repositoryService.syncFromGitHub(user);

    // Apagar levaria junto o historico de analises. Preservar e a escolha deliberada.
    assertThat(repositories.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("stargazers_count nulo vira zero em vez de estourar")
  void toleratesNullStars() {
    when(gitHubClient.listRepositories(anyString()))
        .thenReturn(
            List.of(new GitHubRepo(10L, "r", "rapha/r", null, null, null, false, false, "main")));

    assertThat(repositoryService.syncFromGitHub(user).getFirst().getStars()).isZero();
  }

  @Test
  @DisplayName("requireOwned nao entrega repositorio de outro usuario")
  void requireOwnedIsScopedToOwner() {
    when(gitHubClient.listRepositories(anyString()))
        .thenReturn(List.of(remoteRepo(10L, "rapha/repomind", 7, "Java")));
    UUID repoDoRapha = repositoryService.syncFromGitHub(user).getFirst().getId();

    User intruso =
        userService.upsertFromGitHub(
            Map.of("id", 2L, "login", "intruso", "email", "i@x.com", "avatar_url", "http://b"));

    assertThatThrownBy(() -> repositoryService.requireOwned(repoDoRapha, intruso))
        .isInstanceOf(NotFoundException.class);

    // E continua acessivel para o dono.
    assertThat(repositoryService.requireOwned(repoDoRapha, user).getId()).isEqualTo(repoDoRapha);
  }

  @Test
  @DisplayName("usuarios diferentes podem ter o mesmo repositorio do GitHub")
  void sameGitHubRepoCanBelongToTwoUsers() {
    when(gitHubClient.listRepositories(anyString()))
        .thenReturn(List.of(remoteRepo(10L, "org/compartilhado", 3, "Java")));
    repositoryService.syncFromGitHub(user);

    User outro =
        userService.upsertFromGitHub(
            Map.of("id", 2L, "login", "outro", "email", "o@x.com", "avatar_url", "http://c"));
    repositoryService.syncFromGitHub(outro);

    // A unique constraint e (user_id, github_repo_id), nao github_repo_id sozinho.
    assertThat(repositories.count()).isEqualTo(2);
  }
}
