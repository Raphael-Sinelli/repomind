package com.rsinelli.repomind.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsinelli.repomind.exception.ExternalServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Analisa um repositorio com a API da Anthropic usando tool use real.
 *
 * <p>O loop e escrito a mao de proposito. O {@code BetaToolRunner} do SDK faria o mesmo
 * em menos linhas, mas esconderia justamente o que este projeto quer demonstrar: o
 * modelo pede uma tool, o backend executa, devolve o resultado, e o ciclo se repete ate
 * o modelo concluir.
 *
 * <p><b>Nao verificado contra a API real.</b> Sem chave da Anthropic disponivel, este
 * caminho e coberto apenas por testes com MockWebServer, que exercitam a forma do
 * protocolo. Ver a secao de escopo no README.
 */
@Component
@Profile("anthropic")
public class AnthropicAnalyzer implements RepoAnalyzer {

  private static final Logger log = LoggerFactory.getLogger(AnthropicAnalyzer.class);
  private static final String SERVICE = "anthropic";
  private static final long MAX_TOKENS = 16_000L;

  private static final String SYSTEM_PROMPT =
      """
      Voce analisa repositorios de codigo e produz uma avaliacao objetiva e util.

      Investigue antes de concluir: use as tools disponiveis para ler o README, os
      commits recentes e as issues. Nao suponha o que pode verificar.

      Ao pontuar de 0 a 100, considere clareza da documentacao, consistencia e
      frequencia dos commits, e sinais de manutencao ativa. Um repositorio sem README
      ou sem commits recentes deve receber nota baixa, com a razao explicitada.

      As sugestoes devem ser acionaveis e especificas para este repositorio — nada de
      conselhos genericos que caberiam em qualquer projeto. Ordene da maior para a
      menor consequencia.

      Escreva o resumo e as sugestoes em portugues do Brasil.
      """;

  private final AnthropicClient client;
  private final GitHubToolExecutor toolExecutor;
  private final ObjectMapper objectMapper;
  private final String model;
  private final int maxToolIterations;

  public AnthropicAnalyzer(
      AnthropicClient client,
      GitHubToolExecutor toolExecutor,
      ObjectMapper objectMapper,
      @Value("${repomind.anthropic.model}") String model,
      @Value("${repomind.anthropic.max-tool-iterations}") int maxToolIterations) {
    this.client = client;
    this.toolExecutor = toolExecutor;
    this.objectMapper = objectMapper;
    this.model = model;
    this.maxToolIterations = maxToolIterations;
  }

  @Override
  public String modelIdentifier() {
    return model;
  }

  @Override
  public AnalysisResult analyze(AnalysisRequest request) {
    List<MessageParam> conversation = new ArrayList<>();
    conversation.add(
        MessageParam.builder()
            .role(MessageParam.Role.USER)
            .content(initialPrompt(request))
            .build());

    for (int iteration = 1; iteration <= maxToolIterations; iteration++) {
      Message response = callModel(conversation);

      if (!isToolUse(response)) {
        return parseFinalAnswer(response, request);
      }

      // O turno inteiro do assistente volta para a conversa sem edicao. Os blocos de
      // thinking precisam ser replayados exatamente como vieram — remover ou alterar
      // qualquer um deles faz a API rejeitar a proxima requisicao com 400.
      conversation.add(
          MessageParam.builder()
              .role(MessageParam.Role.ASSISTANT)
              .contentOfBlockParams(response.content().stream().map(ContentBlock::toParam).toList())
              .build());

      // Todos os tool_result do turno vao numa UNICA mensagem do usuario. Dividir em
      // varias ensina o modelo a parar de pedir tools em paralelo.
      conversation.add(
          MessageParam.builder()
              .role(MessageParam.Role.USER)
              .contentOfBlockParams(executeRequestedTools(response, request))
              .build());

      log.debug("Iteracao {}/{} do loop de tool use", iteration, maxToolIterations);
    }

    throw new ExternalServiceException(
        SERVICE,
        ("O modelo nao concluiu a analise em %d rodadas de tools. Isso costuma indicar "
                + "prompt ou tools mal definidas, nao um problema temporario.")
            .formatted(maxToolIterations));
  }

  private Message callModel(List<MessageParam> conversation) {
    try {
      return client.messages().create(baseParams().messages(conversation).build());
    } catch (RuntimeException ex) {
      throw new ExternalServiceException(
          SERVICE, "Falha ao chamar a API da Anthropic: " + ex.getMessage(), ex);
    }
  }

  private MessageCreateParams.Builder baseParams() {
    return MessageCreateParams.builder()
        .model(model)
        .maxTokens(MAX_TOKENS)
        .system(SYSTEM_PROMPT)
        // Thinking adaptativo: o modelo decide quanto raciocinar por tarefa.
        .thinking(ThinkingConfigAdaptive.builder().build())
        .outputConfig(
            OutputConfig.builder()
                .effort(OutputConfig.Effort.HIGH)
                // Structured output garante que a resposta final seja JSON valido no
                // formato esperado — sem isso, o parse dependeria do modelo lembrar de
                // formatar direito.
                .format(AnalysisSchema.outputFormat())
                .build())
        .addTool(AnalysisSchema.getCommitsTool())
        .addTool(AnalysisSchema.getReadmeTool())
        .addTool(AnalysisSchema.getIssuesTool());
  }

  private static boolean isToolUse(Message response) {
    return response.stopReason().map(reason -> reason.equals(StopReason.TOOL_USE)).orElse(false);
  }

  /**
   * Executa cada tool pedida e devolve um {@code tool_result} por {@code tool_use}. A
   * correspondencia precisa ser um-para-um: a API rejeita a requisicao se algum
   * {@code tool_use} ficar sem resposta — por isso o erro tambem vira um bloco, com
   * {@code is_error}, em vez de ser omitido.
   */
  private List<ContentBlockParam> executeRequestedTools(Message response, AnalysisRequest request) {
    List<ContentBlockParam> results = new ArrayList<>();

    for (ContentBlock block : response.content()) {
      ToolUseBlock toolUse = block.toolUse().orElse(null);
      if (toolUse == null) {
        continue;
      }

      ToolResultBlockParam.Builder result =
          ToolResultBlockParam.builder().toolUseId(toolUse.id());
      try {
        Map<String, Object> input = toolInput(toolUse);
        result.content(toolExecutor.execute(toolUse.name(), input, request));
      } catch (RuntimeException ex) {
        log.warn("Tool {} falhou; devolvendo is_error ao modelo", toolUse.name(), ex);
        // Devolver o erro deixa o modelo se adaptar (tentar outra tool, ou concluir com
        // o que tem). Omitir o bloco quebraria a requisicao inteira.
        result.content("Falha ao executar a tool: " + ex.getMessage()).isError(true);
      }
      results.add(ContentBlockParam.ofToolResult(result.build()));
    }
    return results;
  }

  /**
   * O input da tool precisa ser lido com {@code convert()}. {@code JsonValue.toString()}
   * devolve a representacao Java do mapa ({@code {limit=42}}), que nao e JSON valido —
   * parsear essa string faz todo argumento cair silenciosamente no valor padrao.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> toolInput(ToolUseBlock toolUse) {
    JsonValue raw = toolUse._input();
    if (raw == null) {
      return Map.of();
    }
    try {
      Map<String, Object> converted = raw.convert(Map.class);
      return converted == null ? Map.of() : converted;
    } catch (RuntimeException ex) {
      log.warn("Input da tool {} nao pode ser lido como objeto; usando padroes", toolUse.name(), ex);
      return Map.of();
    }
  }

  /** A resposta final vem como JSON no primeiro bloco de texto, por conta do schema. */
  private AnalysisResult parseFinalAnswer(Message response, AnalysisRequest request) {
    String json =
        response.content().stream()
            .map(block -> block.text().map(t -> t.text()).orElse(null))
            .filter(text -> text != null && !text.isBlank())
            .findFirst()
            .orElseThrow(
                () ->
                    new ExternalServiceException(
                        SERVICE,
                        "O modelo terminou sem texto na resposta para " + request.fullName()));

    try {
      return objectMapper.readValue(json, AnalysisResult.class);
    } catch (Exception ex) {
      throw new ExternalServiceException(
          SERVICE, "Resposta do modelo nao segue o formato esperado: " + ex.getMessage(), ex);
    }
  }

  private static String initialPrompt(AnalysisRequest request) {
    return """
        Analise o repositorio %s.

        Linguagem principal: %s
        Descricao declarada: %s

        Use as tools para investigar antes de responder.
        """
        .formatted(
            request.fullName(),
            request.primaryLanguage() == null ? "nao informada" : request.primaryLanguage(),
            request.description() == null || request.description().isBlank()
                ? "nenhuma"
                : request.description());
  }

  /** Definicoes de tools e do schema de saida, agrupadas para nao poluir o loop. */
  static final class AnalysisSchema {

    private AnalysisSchema() {}

    static Tool getCommitsTool() {
      return Tool.builder()
          .name(GitHubToolExecutor.GET_COMMITS)
          .description(
              "Lista os commits mais recentes do repositorio em analise, do mais novo para "
                  + "o mais antigo. Use para avaliar frequencia, consistencia e qualidade "
                  + "das mensagens de commit.")
          .inputSchema(
              Tool.InputSchema.builder()
                  .properties(
                      Tool.InputSchema.Properties.builder()
                          .putAdditionalProperty(
                              "limit",
                              JsonValue.from(
                                  Map.of(
                                      "type", "integer",
                                      "description", "Quantos commits trazer (1 a 100).")))
                          .build())
                  .required(List.of())
                  .build())
          .build();
    }

    static Tool getReadmeTool() {
      return Tool.builder()
          .name(GitHubToolExecutor.GET_README)
          .description(
              "Devolve o conteudo do README do repositorio em analise, ja decodificado. "
                  + "Informa explicitamente quando o repositorio nao tem README.")
          .inputSchema(
              Tool.InputSchema.builder()
                  .properties(Tool.InputSchema.Properties.builder().build())
                  .required(List.of())
                  .build())
          .build();
    }

    static Tool getIssuesTool() {
      return Tool.builder()
          .name(GitHubToolExecutor.GET_ISSUES)
          .description(
              "Lista issues do repositorio em analise. Pull requests sao excluidos. Use "
                  + "para avaliar manutencao e volume de problemas em aberto.")
          .inputSchema(
              Tool.InputSchema.builder()
                  .properties(
                      Tool.InputSchema.Properties.builder()
                          .putAdditionalProperty(
                              "state",
                              JsonValue.from(
                                  Map.of(
                                      "type", "string",
                                      "enum", List.of("open", "closed", "all"),
                                      "description", "Estado das issues. Padrao: open.")))
                          .putAdditionalProperty(
                              "limit",
                              JsonValue.from(
                                  Map.of(
                                      "type", "integer",
                                      "description", "Quantas issues trazer (1 a 100).")))
                          .build())
                  .required(List.of())
                  .build())
          .build();
    }

    static com.anthropic.models.messages.JsonOutputFormat outputFormat() {
      return com.anthropic.models.messages.JsonOutputFormat.builder()
          .schema(
              com.anthropic.models.messages.JsonOutputFormat.Schema.builder()
                  .putAdditionalProperty("type", JsonValue.from("object"))
                  .putAdditionalProperty(
                      "properties",
                      JsonValue.from(
                          Map.of(
                              "summary",
                                  Map.of(
                                      "type", "string",
                                      "description",
                                          "Resumo em prosa do que o repositorio e e em que "
                                              + "estado esta."),
                              "quality_score",
                                  Map.of(
                                      "type", "integer",
                                      "description", "Nota de 0 a 100."),
                              "suggestions",
                                  Map.of(
                                      "type", "array",
                                      "items", Map.of("type", "string"),
                                      "description",
                                          "Melhorias acionaveis, da maior para a menor "
                                              + "consequencia."))))
                  .putAdditionalProperty(
                      "required", JsonValue.from(List.of("summary", "quality_score", "suggestions")))
                  .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                  .build())
          .build();
    }
  }
}
