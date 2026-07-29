package com.rsinelli.repomind.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rsinelli.repomind.exception.ExternalServiceException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes de unidade do cliente HTTP: nenhuma chamada sai para api.github.com. O
 * MockWebServer devolve payloads com o mesmo formato dos que a API do GitHub retorna.
 */
class GitHubClientTest {

  private MockWebServer server;
  private GitHubClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = new GitHubClient(server.url("/").toString());
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private void enqueueJson(String body) {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(body));
  }

  @Test
  @DisplayName("envia o token do usuario no header Authorization")
  void sendsBearerToken() throws Exception {
    enqueueJson("[]");

    client.listRepositories("gho_token_do_usuario");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer gho_token_do_usuario");
    assertThat(request.getHeader("Accept")).contains("application/vnd.github+json");
  }

  @Test
  @DisplayName("mapeia snake_case do GitHub para camelCase e ignora campos desconhecidos")
  void mapsRepositories() {
    enqueueJson(
        """
        [
          {
            "id": 123,
            "name": "repomind",
            "full_name": "rapha/repomind",
            "description": "analisador",
            "stargazers_count": 7,
            "language": "Java",
            "fork": false,
            "archived": false,
            "default_branch": "main",
            "campo_que_nao_conhecemos": "deve ser ignorado"
          }
        ]
        """);

    List<GitHubRepo> repos = client.listRepositories("token");

    assertThat(repos).hasSize(1);
    GitHubRepo repo = repos.getFirst();
    assertThat(repo.id()).isEqualTo(123L);
    // Prova que o converter dedicado com SNAKE_CASE esta ativo: sem ele viria null.
    assertThat(repo.fullName()).isEqualTo("rapha/repomind");
    assertThat(repo.stargazersCount()).isEqualTo(7);
    assertThat(repo.defaultBranch()).isEqualTo("main");
  }

  @Test
  @DisplayName("getHeadCommitSha devolve o sha do commit mais recente")
  void readsHeadCommitSha() {
    enqueueJson(
        """
        [{"sha": "abc123def456",
          "commit": {"message": "feat: cache\\n\\ncorpo longo",
                     "author": {"name": "rapha", "date": "2026-07-29T10:00:00Z"}}}]
        """);

    assertThat(client.getHeadCommitSha("token", "rapha/repomind")).isEqualTo("abc123def456");
  }

  @Test
  @DisplayName("subject() corta o corpo da mensagem de commit")
  void commitSubjectStripsBody() {
    enqueueJson(
        """
        [{"sha": "a1",
          "commit": {"message": "fix: corrige cache\\n\\nexplicacao longa que nao interessa",
                     "author": {"name": "rapha", "date": "2026-07-29T10:00:00Z"}}}]
        """);

    List<GitHubCommit> commits = client.listCommits("token", "rapha/repomind", 1);

    assertThat(commits.getFirst().subject()).isEqualTo("fix: corrige cache");
  }

  @Test
  @DisplayName("repositorio vazio nao tem HEAD e isso e um erro claro, nao um null")
  void failsClearlyOnEmptyRepository() {
    enqueueJson("[]");

    assertThatThrownBy(() -> client.getHeadCommitSha("token", "rapha/vazio"))
        .isInstanceOf(ExternalServiceException.class)
        .hasMessageContaining("nenhum commit");
  }

  @Test
  @DisplayName("README chega em base64 com quebras de linha e precisa ser decodificado")
  void decodesBase64Readme() {
    String conteudo = "# RepoMind\n\nAnalisador de repositorios.";
    // O GitHub quebra o base64 em linhas. Um decoder estrito lancaria excecao aqui.
    String base64ComQuebras =
        Base64.getEncoder()
            .encodeToString(conteudo.getBytes(StandardCharsets.UTF_8))
            .replaceAll("(.{10})", "$1\n");

    enqueueJson(
        "{\"name\": \"README.md\", \"encoding\": \"base64\", \"content\": \"%s\"}"
            .formatted(base64ComQuebras.replace("\n", "\\n")));

    assertThat(client.getReadme("token", "rapha/repomind")).isEqualTo(conteudo);
  }

  @Test
  @DisplayName("repositorio sem README devolve texto vazio, nao explode")
  void returnsEmptyWhenReadmeMissing() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"message\":\"Not Found\"}"));

    assertThat(client.getReadme("token", "rapha/sem-readme")).isEmpty();
  }

  @Test
  @DisplayName("README com 401 propaga o erro em vez de mascarar como ausente")
  void readmeDoesNotSwallowUnauthorized() {
    server.enqueue(
        new MockResponse().setResponseCode(401).setBody("{\"message\":\"Bad credentials\"}"));

    assertThatThrownBy(() -> client.getReadme("token-ruim", "rapha/repomind"))
        .isInstanceOf(ExternalServiceException.class);
  }

  @Test
  @DisplayName("listIssues descarta pull requests, que o GitHub mistura no mesmo endpoint")
  void filtersOutPullRequests() {
    enqueueJson(
        """
        [
          {"number": 1, "title": "Bug no cache", "state": "open",
           "created_at": "2026-07-01T10:00:00Z"},
          {"number": 2, "title": "PR: refactor", "state": "open",
           "created_at": "2026-07-02T10:00:00Z",
           "pull_request": {"url": "https://api.github.com/repos/rapha/repomind/pulls/2"}}
        ]
        """);

    List<GitHubIssue> issues = client.listIssues("token", "rapha/repomind", "open", 30);

    assertThat(issues).hasSize(1);
    assertThat(issues.getFirst().number()).isEqualTo(1L);
  }

  @Test
  @DisplayName("owner/repo vira dois segmentos de path, nao %2F escapado")
  void doesNotEscapeSlashInFullName() throws Exception {
    enqueueJson("[]");

    client.listCommits("token", "rapha/repomind", 15);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/repos/rapha/repomind/commits");
    assertThat(request.getRequestUrl().queryParameter("per_page")).isEqualTo("15");
  }

  @Test
  @DisplayName("full_name mal formado falha antes de qualquer chamada de rede")
  void rejectsMalformedFullName() {
    assertThatThrownBy(() -> client.listCommits("token", "semBarra", 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("owner/repo");
  }

  @Test
  @DisplayName("401 do GitHub vira ExternalServiceException, nao 500 generico")
  void translatesUnauthorized() {
    server.enqueue(
        new MockResponse().setResponseCode(401).setBody("{\"message\":\"Bad credentials\"}"));

    assertThatThrownBy(() -> client.listRepositories("token-invalido"))
        .isInstanceOf(ExternalServiceException.class)
        .hasMessageContaining("expirou");
  }

  @Test
  @DisplayName("rate limit (403) e traduzido com mensagem util")
  void translatesRateLimit() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(403)
            .addHeader("X-RateLimit-Remaining", "0")
            .setBody("{\"message\":\"API rate limit exceeded\"}"));

    assertThatThrownBy(() -> client.listCommits("token", "rapha/repomind", 10))
        .isInstanceOf(ExternalServiceException.class)
        .hasMessageContaining("limite");
  }
}
