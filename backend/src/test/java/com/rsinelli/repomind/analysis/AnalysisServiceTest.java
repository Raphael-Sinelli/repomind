package com.rsinelli.repomind.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rsinelli.repomind.AbstractIntegrationTest;
import com.rsinelli.repomind.ai.AnalysisRequest;
import com.rsinelli.repomind.ai.AnalysisResult;
import com.rsinelli.repomind.ai.RepoAnalyzer;
import com.rsinelli.repomind.exception.NotFoundException;
import com.rsinelli.repomind.github.GitHubClient;
import com.rsinelli.repomind.github.GitHubRepo;
import com.rsinelli.repomind.github.GitHubTokenProvider;
import com.rsinelli.repomind.repository.Repository;
import com.rsinelli.repomind.repository.RepositoryRepository;
import com.rsinelli.repomind.repository.RepositoryService;
import com.rsinelli.repomind.user.User;
import com.rsinelli.repomind.user.UserRepository;
import com.rsinelli.repomind.user.UserService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AnalysisServiceTest extends AbstractIntegrationTest {

  private static final String SHA_INICIAL = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String SHA_APOS_COMMIT = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

  @Autowired AnalysisService analysisService;
  @Autowired AnalysisRepository analyses;
  @Autowired RepositoryService repositoryService;
  @Autowired RepositoryRepository repositories;
  @Autowired UserService userService;
  @Autowired UserRepository users;
  @Autowired StringRedisTemplate redis;

  @MockitoBean GitHubClient gitHubClient;
  @MockitoBean GitHubTokenProvider tokenProvider;
  @MockitoBean RepoAnalyzer analyzer;

  private User user;
  private Repository repository;

  @BeforeEach
  void setUp() {
    analyses.deleteAll();
    repositories.deleteAll();
    users.deleteAll();
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();

    user =
        userService.upsertFromGitHub(
            Map.of("id", 1L, "login", "rapha", "email", "r@x.com", "avatar_url", "http://a"));

    when(tokenProvider.currentToken()).thenReturn("token");
    when(gitHubClient.listRepositories(anyString()))
        .thenReturn(
            List.of(
                new GitHubRepo(
                    10L, "repomind", "rapha/repomind", "desc", 5, "Java", false, false, "main")));
    repository = repositoryService.syncFromGitHub(user).getFirst();

    when(gitHubClient.getHeadCommitSha("token", "rapha/repomind")).thenReturn(SHA_INICIAL);
    when(analyzer.modelIdentifier()).thenReturn("modelo-de-teste");
    when(analyzer.analyze(any(AnalysisRequest.class)))
        .thenReturn(new AnalysisResult("resumo", 77, List.of("sugestao")));
  }

  @Test
  @DisplayName("primeira analise chama o analisador e persiste o resultado")
  void firstAnalysisInvokesAnalyzer() {
    Analysis result = analysisService.analyze(repository.getId(), user);

    verify(analyzer, times(1)).analyze(any(AnalysisRequest.class));
    assertThat(result.getQualityScore()).isEqualTo(77);
    assertThat(result.getSuggestions()).containsExactly("sugestao");
    assertThat(result.getModelUsed()).isEqualTo("modelo-de-teste");
    assertThat(result.getAnalyzedCommitSha()).isEqualTo(SHA_INICIAL);
    assertThat(analyses.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("segunda chamada no mesmo commit NAO chama o analisador — este e o ponto do cache")
  void secondCallOnSameCommitHitsCache() {
    Analysis primeira = analysisService.analyze(repository.getId(), user);
    Analysis segunda = analysisService.analyze(repository.getId(), user);

    // A assercao que importa: uma unica chamada de IA para duas requisicoes.
    verify(analyzer, times(1)).analyze(any(AnalysisRequest.class));
    assertThat(segunda.getId()).isEqualTo(primeira.getId());
    assertThat(analyses.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("commit novo invalida o cache e dispara nova analise")
  void newCommitInvalidatesCache() {
    analysisService.analyze(repository.getId(), user);

    // Alguem fez push: o HEAD mudou.
    when(gitHubClient.getHeadCommitSha("token", "rapha/repomind")).thenReturn(SHA_APOS_COMMIT);
    Analysis depois = analysisService.analyze(repository.getId(), user);

    // Um TTL sozinho nao teria percebido a mudanca e serviria analise obsoleta.
    verify(analyzer, times(2)).analyze(any(AnalysisRequest.class));
    assertThat(depois.getAnalyzedCommitSha()).isEqualTo(SHA_APOS_COMMIT);
    assertThat(analyses.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("com Redis limpo o Postgres ainda evita a chamada de IA")
  void postgresIsSecondLineOfDefence() {
    analysisService.analyze(repository.getId(), user);

    // Simula perda do cache: restart do Redis, eviction, deploy.
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();

    Analysis segunda = analysisService.analyze(repository.getId(), user);

    verify(analyzer, times(1)).analyze(any(AnalysisRequest.class));
    assertThat(analyses.count()).isEqualTo(1);
    assertThat(segunda.getAnalyzedCommitSha()).isEqualTo(SHA_INICIAL);
  }

  @Test
  @DisplayName("a chave do cache carrega o SHA, nao so o id do repositorio")
  void cacheKeyIncludesCommitSha() {
    analysisService.analyze(repository.getId(), user);

    String chaveEsperada = "analysis:" + repository.getId() + ":" + SHA_INICIAL;
    assertThat(redis.opsForValue().get(chaveEsperada)).isNotNull();
  }

  @Test
  @DisplayName("nao analisa repositorio de outro usuario")
  void refusesRepositoryOfAnotherUser() {
    User intruso =
        userService.upsertFromGitHub(
            Map.of("id", 2L, "login", "intruso", "email", "i@x.com", "avatar_url", "http://b"));

    assertThatThrownBy(() -> analysisService.analyze(repository.getId(), intruso))
        .isInstanceOf(NotFoundException.class);

    verify(analyzer, never()).analyze(any(AnalysisRequest.class));
  }

  @Test
  @DisplayName("historico vem do mais recente para o mais antigo")
  void historyIsNewestFirst() {
    analysisService.analyze(repository.getId(), user);
    when(gitHubClient.getHeadCommitSha("token", "rapha/repomind")).thenReturn(SHA_APOS_COMMIT);
    analysisService.analyze(repository.getId(), user);

    List<Analysis> historico = analysisService.history(repository.getId(), user);

    assertThat(historico).hasSize(2);
    assertThat(historico.getFirst().getAnalyzedCommitSha()).isEqualTo(SHA_APOS_COMMIT);
  }
}
