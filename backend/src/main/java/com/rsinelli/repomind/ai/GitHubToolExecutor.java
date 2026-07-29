package com.rsinelli.repomind.ai;

import com.rsinelli.repomind.github.GitHubClient;
import com.rsinelli.repomind.github.GitHubCommit;
import com.rsinelli.repomind.github.GitHubIssue;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executa as tools que o modelo pede. E o lado "backend executa" do tool use: o modelo
 * decide o que chamar, esta classe efetivamente chama o GitHub e devolve texto.
 *
 * <p>Nenhuma tool recebe o repositorio como parametro — o alvo vem do
 * {@link AnalysisRequest}. Isso limita o raio de acao do modelo ao repositorio que o
 * usuario escolheu.
 */
@Component
public class GitHubToolExecutor {

  private static final Logger log = LoggerFactory.getLogger(GitHubToolExecutor.class);

  public static final String GET_COMMITS = "get_commits";
  public static final String GET_README = "get_readme";
  public static final String GET_ISSUES = "get_issues";

  private static final int DEFAULT_COMMIT_LIMIT = 20;
  private static final int DEFAULT_ISSUE_LIMIT = 20;
  /** README grande consome contexto sem ganho proporcional. */
  private static final int README_CHAR_LIMIT = 6000;

  private final GitHubClient gitHubClient;

  public GitHubToolExecutor(GitHubClient gitHubClient) {
    this.gitHubClient = gitHubClient;
  }

  /**
   * @return texto pronto para virar um bloco {@code tool_result}
   * @throws IllegalArgumentException se o modelo inventar um nome de tool
   */
  public String execute(String toolName, Map<String, Object> input, AnalysisRequest request) {
    log.debug("Tool solicitada pelo modelo: {} com input {}", toolName, input);

    return switch (toolName) {
      case GET_COMMITS -> formatCommits(request, intArg(input, "limit", DEFAULT_COMMIT_LIMIT));
      case GET_README -> formatReadme(request);
      case GET_ISSUES ->
          formatIssues(
              request,
              stringArg(input, "state", "open"),
              intArg(input, "limit", DEFAULT_ISSUE_LIMIT));
      default -> throw new IllegalArgumentException("Tool desconhecida: " + toolName);
    };
  }

  private String formatCommits(AnalysisRequest request, int limit) {
    List<GitHubCommit> commits =
        gitHubClient.listCommits(request.gitHubToken(), request.fullName(), limit);
    if (commits.isEmpty()) {
      return "Nenhum commit encontrado.";
    }
    StringBuilder sb = new StringBuilder("Commits mais recentes (do mais novo ao mais antigo):\n");
    for (GitHubCommit commit : commits) {
      String autor =
          commit.commit() != null && commit.commit().author() != null
              ? commit.commit().author().name()
              : "desconhecido";
      String data =
          commit.commit() != null && commit.commit().author() != null
              ? String.valueOf(commit.commit().author().date())
              : "sem data";
      sb.append("- [").append(data).append("] ").append(autor).append(": ")
          .append(commit.subject())
          .append('\n');
    }
    return sb.toString();
  }

  private String formatReadme(AnalysisRequest request) {
    String readme = gitHubClient.getReadme(request.gitHubToken(), request.fullName());
    if (readme.isBlank()) {
      // Ausencia e informacao: um repositorio sem README merece nota menor.
      return "Este repositorio nao tem README.";
    }
    if (readme.length() > README_CHAR_LIMIT) {
      return readme.substring(0, README_CHAR_LIMIT) + "\n\n[...README truncado...]";
    }
    return readme;
  }

  private String formatIssues(AnalysisRequest request, String state, int limit) {
    List<GitHubIssue> issues =
        gitHubClient.listIssues(request.gitHubToken(), request.fullName(), state, limit);
    if (issues.isEmpty()) {
      return "Nenhuma issue com estado '%s'. (Pull requests nao entram nesta lista.)"
          .formatted(state);
    }
    StringBuilder sb =
        new StringBuilder("Issues com estado '%s' (%d):\n".formatted(state, issues.size()));
    for (GitHubIssue issue : issues) {
      sb.append("- #").append(issue.number()).append(" [").append(issue.state()).append("] ")
          .append(issue.title())
          .append(" (aberta em ").append(issue.createdAt()).append(")\n");
    }
    return sb.toString();
  }

  private static int intArg(Map<String, Object> input, String key, int fallback) {
    Object value = input.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text) {
      try {
        return Integer.parseInt(text.trim());
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private static String stringArg(Map<String, Object> input, String key, String fallback) {
    Object value = input.get(key);
    return value instanceof String text && !text.isBlank() ? text : fallback;
  }
}
