package com.rsinelli.repomind.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rsinelli.repomind.exception.ExternalServiceException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP do GitHub. Recebe o token por parametro em vez de resolver o usuario
 * autenticado internamente: assim a classe e testavel sem SecurityContext, e quem decide
 * de quem e o token e a camada de servico.
 */
@Component
public class GitHubClient {

  private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
  private static final String SERVICE = "github";
  private static final int MAX_PAGES = 5;
  private static final int PAGE_SIZE = 100;

  private final RestClient restClient;

  public GitHubClient(@Value("${repomind.github.base-url}") String baseUrl) {
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            // RestClient.builder() nao herda o ObjectMapper do Spring: ele monta os
            // proprios converters com configuracao padrao. Sem este converter dedicado,
            // `full_name` nao mapearia para `fullName` e os campos chegariam nulos.
            // Configurar aqui tambem isola o contrato do GitHub de mudancas na
            // configuracao global de Jackson da aplicacao.
            .messageConverters(converters -> converters.addFirst(gitHubJsonConverter()))
            .build();
  }

  private static MappingJackson2HttpMessageConverter gitHubJsonConverter() {
    ObjectMapper mapper =
        JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();
    return new MappingJackson2HttpMessageConverter(mapper);
  }

  /**
   * Repositorios do usuario autenticado. Pagina ate {@link #MAX_PAGES} — o teto e
   * proposital: sem ele, uma conta com milhares de repositorios travaria a requisicao.
   */
  public List<GitHubRepo> listRepositories(String token) {
    List<GitHubRepo> all = new ArrayList<>();
    for (int page = 1; page <= MAX_PAGES; page++) {
      final int currentPage = page;
      List<GitHubRepo> batch =
          execute(
              "listar repositorios",
              () ->
                  restClient
                      .get()
                      .uri(
                          uri ->
                              uri.path("/user/repos")
                                  .queryParam("per_page", PAGE_SIZE)
                                  .queryParam("page", currentPage)
                                  .queryParam("sort", "updated")
                                  .queryParam("affiliation", "owner")
                                  .build())
                      .headers(h -> h.setBearerAuth(token))
                      .retrieve()
                      .body(new ParameterizedTypeReference<List<GitHubRepo>>() {}));

      if (batch == null || batch.isEmpty()) {
        break;
      }
      all.addAll(batch);
      if (batch.size() < PAGE_SIZE) {
        break;
      }
    }
    return all;
  }

  /** SHA do commit mais recente. E a chave de invalidacao do cache de analises. */
  public String getHeadCommitSha(String token, String fullName) {
    List<GitHubCommit> commits = listCommits(token, fullName, 1);
    if (commits.isEmpty()) {
      throw new ExternalServiceException(
          SERVICE,
          "O repositorio %s nao tem nenhum commit, entao nao ha o que analisar."
              .formatted(fullName));
    }
    return commits.getFirst().sha();
  }

  public List<GitHubCommit> listCommits(String token, String fullName, int limit) {
    Repo repo = Repo.parse(fullName);
    List<GitHubCommit> commits =
        execute(
            "listar commits de " + fullName,
            () ->
                restClient
                    .get()
                    .uri(
                        uri ->
                            uri.path("/repos/{owner}/{repo}/commits")
                                .queryParam("per_page", clamp(limit, 1, 100))
                                .build(repo.owner(), repo.name()))
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GitHubCommit>>() {}));
    return commits == null ? List.of() : commits;
  }

  /**
   * Conteudo do README ja decodificado. Repositorio sem README devolve string vazia — e
   * ausencia esperada, nao falha: muitos repositorios legitimamente nao tem um. Qualquer
   * outro erro (401, 403, 5xx) continua propagando.
   */
  public String getReadme(String token, String fullName) {
    Repo repo = Repo.parse(fullName);
    ReadmeResponse response;
    try {
      response =
          execute(
              "ler README de " + fullName,
              () ->
                  restClient
                      .get()
                      .uri("/repos/{owner}/{repo}/readme", repo.owner(), repo.name())
                      .headers(h -> h.setBearerAuth(token))
                      .retrieve()
                      .body(ReadmeResponse.class));
    } catch (ExternalServiceException ex) {
      if (ex.getCause() instanceof HttpClientErrorException.NotFound) {
        log.debug("Repositorio {} nao tem README", fullName);
        return "";
      }
      throw ex;
    }

    if (response == null || response.content() == null) {
      return "";
    }
    return decodeBase64(response.content());
  }

  /** Issues do repositorio, ja sem pull requests. */
  public List<GitHubIssue> listIssues(String token, String fullName, String state, int limit) {
    Repo repo = Repo.parse(fullName);
    String normalizedState = normalizeState(state);
    List<GitHubIssue> issues =
        execute(
            "listar issues de " + fullName,
            () ->
                restClient
                    .get()
                    .uri(
                        uri ->
                            uri.path("/repos/{owner}/{repo}/issues")
                                .queryParam("state", normalizedState)
                                .queryParam("per_page", clamp(limit, 1, 100))
                                .build(repo.owner(), repo.name()))
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GitHubIssue>>() {}));

    if (issues == null) {
      return List.of();
    }
    // O endpoint /issues devolve PRs junto. Sem esse filtro, "5 issues abertas" poderia
    // significar 5 pull requests — e a analise sairia errada.
    return issues.stream().filter(issue -> !issue.isPullRequest()).toList();
  }

  /**
   * O GitHub quebra o base64 do README em linhas. {@link Base64#getMimeDecoder()} tolera
   * as quebras; o decoder basico lancaria IllegalArgumentException.
   */
  private static String decodeBase64(String content) {
    try {
      return new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      log.warn("README com base64 invalido em resposta do GitHub; tratando como vazio", ex);
      return "";
    }
  }

  private <T> T execute(String action, Supplier<T> call) {
    try {
      return call.get();
    } catch (HttpClientErrorException.Unauthorized ex) {
      throw new ExternalServiceException(
          SERVICE, "Sua autorizacao com o GitHub expirou. Entre novamente.", ex);
    } catch (HttpClientErrorException.Forbidden ex) {
      throw new ExternalServiceException(
          SERVICE,
          ("GitHub recusou a requisicao ao %s: pode ser limite de uso da API ou permissao "
                  + "ausente no escopo concedido.")
              .formatted(action),
          ex);
    } catch (HttpClientErrorException.NotFound ex) {
      throw new ExternalServiceException(
          SERVICE, "Recurso nao encontrado no GitHub ao %s.".formatted(action), ex);
    } catch (RestClientException ex) {
      throw new ExternalServiceException(SERVICE, "Falha ao %s no GitHub.".formatted(action), ex);
    }
  }

  /**
   * "owner/repo" precisa virar dois segmentos de path. Passar a string inteira num unico
   * placeholder faria o RestClient escapar a barra como %2F e o GitHub devolveria 404.
   */
  private record Repo(String owner, String name) {
    static Repo parse(String fullName) {
      if (fullName == null) {
        throw new IllegalArgumentException("full_name nao pode ser nulo.");
      }
      int slash = fullName.indexOf('/');
      if (slash <= 0 || slash == fullName.length() - 1 || fullName.indexOf('/', slash + 1) >= 0) {
        throw new IllegalArgumentException(
            "full_name deve estar no formato owner/repo, recebido: " + fullName);
      }
      return new Repo(fullName.substring(0, slash), fullName.substring(slash + 1));
    }
  }

  private static String normalizeState(String state) {
    if (state == null) {
      return "open";
    }
    return switch (state.toLowerCase()) {
      case "open", "closed", "all" -> state.toLowerCase();
      default -> "open";
    };
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ReadmeResponse(String name, String encoding, String content) {}
}
