package com.rsinelli.repomind.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rsinelli.repomind.exception.ExternalServiceException;
import com.rsinelli.repomind.github.GitHubClient;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercita o loop manual de tool use contra um servidor que imita a API da Anthropic.
 *
 * <p>Isto valida a <b>forma do protocolo</b> — que a tool certa e executada, que o
 * tool_result volta com o id correspondente numa unica mensagem, que blocos de thinking
 * sao replayados intactos e que o teto de iteracoes existe. Nao valida que a API real
 * aceita estas requisicoes: sem chave da Anthropic, esse passo fica fora de escopo.
 */
class AnthropicAnalyzerTest {

  private MockWebServer anthropic;
  private GitHubClient gitHubClient;
  private AnthropicAnalyzer analyzer;
  private ObjectMapper objectMapper;

  private static final AnalysisRequest REQUEST =
      new AnalysisRequest("rapha/repomind", "Java", "analisador", "gho_token");

  @BeforeEach
  void setUp() throws Exception {
    anthropic = new MockWebServer();
    anthropic.start();

    gitHubClient = mock(GitHubClient.class);
    objectMapper =
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    AnthropicClient client =
        AnthropicOkHttpClient.builder()
            .apiKey("chave-de-teste")
            .baseUrl(anthropic.url("/").toString().replaceAll("/$", ""))
            .build();

    analyzer =
        new AnthropicAnalyzer(
            client, new GitHubToolExecutor(gitHubClient), objectMapper, "modelo-teste", 8);
  }

  @AfterEach
  void tearDown() throws Exception {
    anthropic.shutdown();
  }

  private void enqueue(String body) {
    anthropic.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(body));
  }

  /** Turno em que o modelo pede uma tool, precedido de um bloco de thinking. */
  private static String toolUseTurn(String toolUseId, String toolName, String inputJson) {
    return """
        {
          "id": "msg_1", "type": "message", "role": "assistant", "model": "modelo-teste",
          "content": [
            {"type": "thinking", "thinking": "Preciso ler o README antes de pontuar.",
             "signature": "assinatura-opaca-do-servidor"},
            {"type": "tool_use", "id": "%s", "name": "%s", "input": %s}
          ],
          "stop_reason": "tool_use",
          "usage": {"input_tokens": 100, "output_tokens": 20}
        }
        """
        .formatted(toolUseId, toolName, inputJson);
  }

  private static String finalTurn(String summary, int score, String suggestion) {
    return """
        {
          "id": "msg_2", "type": "message", "role": "assistant", "model": "modelo-teste",
          "content": [{"type": "text", "text": "{\\"summary\\": \\"%s\\", \\"quality_score\\": %d, \\"suggestions\\": [\\"%s\\"]}"}],
          "stop_reason": "end_turn",
          "usage": {"input_tokens": 150, "output_tokens": 40}
        }
        """
        .formatted(summary, score, suggestion);
  }

  @Test
  @DisplayName("modelo pede tool, backend executa e o resultado volta para o modelo")
  void executesRequestedToolAndFeedsResultBack() throws Exception {
    when(gitHubClient.getReadme("gho_token", "rapha/repomind"))
        .thenReturn("# RepoMind\nDocumentacao completa.");

    enqueue(toolUseTurn("toolu_01", GitHubToolExecutor.GET_README, "{}"));
    enqueue(finalTurn("Projeto bem documentado.", 82, "Adicione badges de CI."));

    AnalysisResult result = analyzer.analyze(REQUEST);

    // A tool foi de fato executada contra o GitHub.
    verify(gitHubClient).getReadme("gho_token", "rapha/repomind");

    assertThat(result.qualityScore()).isEqualTo(82);
    assertThat(result.summary()).isEqualTo("Projeto bem documentado.");
    assertThat(result.suggestions()).containsExactly("Adicione badges de CI.");

    assertThat(anthropic.getRequestCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("tool_result volta com o tool_use_id correspondente, numa unica mensagem user")
  void sendsToolResultsInASingleUserMessage() throws Exception {
    when(gitHubClient.getReadme(anyString(), anyString())).thenReturn("conteudo");

    enqueue(toolUseTurn("toolu_abc", GitHubToolExecutor.GET_README, "{}"));
    enqueue(finalTurn("ok", 50, "sugestao"));

    analyzer.analyze(REQUEST);

    anthropic.takeRequest(); // primeira chamada
    RecordedRequest second = anthropic.takeRequest();
    JsonNode body = objectMapper.readTree(second.getBody().readUtf8());
    JsonNode messages = body.get("messages");

    // user inicial, assistant (turno com tool_use), user (tool_result)
    assertThat(messages).hasSize(3);

    JsonNode toolResultMessage = messages.get(2);
    assertThat(toolResultMessage.get("role").asText()).isEqualTo("user");

    JsonNode content = toolResultMessage.get("content");
    // Uma unica mensagem carregando todos os resultados — dividir em varias mensagens
    // faria o modelo parar de pedir tools em paralelo.
    assertThat(content).hasSize(1);
    assertThat(content.get(0).get("type").asText()).isEqualTo("tool_result");
    assertThat(content.get(0).get("tool_use_id").asText()).isEqualTo("toolu_abc");
  }

  @Test
  @DisplayName("blocos de thinking sao replayados sem alteracao — editar gera 400 na API")
  void replaysThinkingBlocksUntouched() throws Exception {
    when(gitHubClient.getReadme(anyString(), anyString())).thenReturn("conteudo");

    enqueue(toolUseTurn("toolu_01", GitHubToolExecutor.GET_README, "{}"));
    enqueue(finalTurn("ok", 50, "sugestao"));

    analyzer.analyze(REQUEST);

    anthropic.takeRequest();
    RecordedRequest second = anthropic.takeRequest();
    JsonNode assistantContent =
        objectMapper.readTree(second.getBody().readUtf8()).get("messages").get(1).get("content");

    JsonNode thinking = assistantContent.get(0);
    assertThat(thinking.get("type").asText()).isEqualTo("thinking");
    assertThat(thinking.get("thinking").asText())
        .isEqualTo("Preciso ler o README antes de pontuar.");
    // A assinatura e o que a API usa para validar que o bloco nao foi adulterado.
    assertThat(thinking.get("signature").asText()).isEqualTo("assinatura-opaca-do-servidor");
  }

  @Test
  @DisplayName("input da tool chega parseado ao executor")
  void passesToolInputThrough() throws Exception {
    when(gitHubClient.listCommits(anyString(), anyString(), anyInt())).thenReturn(List.of());

    enqueue(toolUseTurn("toolu_01", GitHubToolExecutor.GET_COMMITS, "{\"limit\": 42}"));
    enqueue(finalTurn("ok", 50, "sugestao"));

    analyzer.analyze(REQUEST);

    verify(gitHubClient).listCommits("gho_token", "rapha/repomind", 42);
  }

  @Test
  @DisplayName("tool que falha vira tool_result com is_error, nunca um bloco ausente")
  void failedToolBecomesErrorResultInsteadOfMissingBlock() throws Exception {
    when(gitHubClient.getReadme(anyString(), anyString()))
        .thenThrow(new ExternalServiceException("github", "repositorio sumiu"));

    enqueue(toolUseTurn("toolu_01", GitHubToolExecutor.GET_README, "{}"));
    enqueue(finalTurn("Analise parcial.", 30, "Verifique o acesso ao repositorio."));

    AnalysisResult result = analyzer.analyze(REQUEST);

    RecordedRequest ignored = anthropic.takeRequest();
    RecordedRequest second = anthropic.takeRequest();
    JsonNode toolResult =
        objectMapper
            .readTree(second.getBody().readUtf8())
            .get("messages")
            .get(2)
            .get("content")
            .get(0);

    // O bloco existe (a API rejeitaria a requisicao sem ele) e sinaliza o erro.
    assertThat(toolResult.get("tool_use_id").asText()).isEqualTo("toolu_01");
    assertThat(toolResult.get("is_error").asBoolean()).isTrue();
    assertThat(result.qualityScore()).isEqualTo(30);
  }

  @Test
  @DisplayName("modelo que so pede tools para no teto de iteracoes com erro explicito")
  void stopsAtIterationCeiling() {
    when(gitHubClient.getReadme(anyString(), anyString())).thenReturn("conteudo");
    // Sempre tool_use: um modelo em loop nunca chegaria ao fim.
    for (int i = 0; i < 12; i++) {
      enqueue(toolUseTurn("toolu_" + i, GitHubToolExecutor.GET_README, "{}"));
    }

    assertThatThrownBy(() -> analyzer.analyze(REQUEST))
        .isInstanceOf(ExternalServiceException.class)
        .hasMessageContaining("nao concluiu a analise");

    // Parou no teto configurado (8), nao consumiu as 12 respostas disponiveis.
    assertThat(anthropic.getRequestCount()).isEqualTo(8);
  }

  @Test
  @DisplayName("resposta final fora do formato esperado vira erro claro")
  void rejectsMalformedFinalAnswer() {
    enqueue(
        """
        {"id": "msg_1", "type": "message", "role": "assistant", "model": "modelo-teste",
         "content": [{"type": "text", "text": "isto nao e json"}],
         "stop_reason": "end_turn",
         "usage": {"input_tokens": 10, "output_tokens": 5}}
        """);

    assertThatThrownBy(() -> analyzer.analyze(REQUEST))
        .isInstanceOf(ExternalServiceException.class)
        .hasMessageContaining("formato esperado");
  }

  @Test
  @DisplayName("modelo que responde direto, sem tools, tambem funciona")
  void worksWithoutAnyToolCall() {
    enqueue(finalTurn("Analise direta.", 70, "Documente melhor."));

    AnalysisResult result = analyzer.analyze(REQUEST);

    assertThat(result.qualityScore()).isEqualTo(70);
    assertThat(anthropic.getRequestCount()).isEqualTo(1);
  }
}
