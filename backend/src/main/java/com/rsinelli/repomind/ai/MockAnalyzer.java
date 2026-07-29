package com.rsinelli.repomind.ai;

import com.rsinelli.repomind.github.GitHubCommit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Analisador do perfil {@code mock}: nao chama a API da Anthropic.
 *
 * <p>Existe para que todo o resto do sistema — cache, persistencia, API, frontend — seja
 * exercitavel de ponta a ponta sem chave da Anthropic. Nao e um stub que devolve texto
 * fixo: ele le o repositorio de verdade pelas mesmas tools e deriva a nota de sinais
 * observaveis, entao o fluxo tem dados plausiveis e o cache tem o que cachear.
 */
@Component
@Profile("mock")
public class MockAnalyzer implements RepoAnalyzer {

  private static final Logger log = LoggerFactory.getLogger(MockAnalyzer.class);

  private final com.rsinelli.repomind.github.GitHubClient gitHubClient;

  public MockAnalyzer(com.rsinelli.repomind.github.GitHubClient gitHubClient) {
    this.gitHubClient = gitHubClient;
  }

  @Override
  public String modelIdentifier() {
    // Prefixo explicito para que nenhuma analise gravada no banco seja confundida com
    // saida de modelo real ao olhar a coluna model_used.
    return "mock/heuristica-local";
  }

  @Override
  public AnalysisResult analyze(AnalysisRequest request) {
    log.info("Analise em modo mock para {} (nenhuma chamada a Anthropic)", request.fullName());

    String readme = gitHubClient.getReadme(request.gitHubToken(), request.fullName());
    List<GitHubCommit> commits =
        gitHubClient.listCommits(request.gitHubToken(), request.fullName(), 20);
    int issuesAbertas =
        gitHubClient.listIssues(request.gitHubToken(), request.fullName(), "open", 50).size();

    int score = 50;
    List<String> suggestions = new ArrayList<>();

    if (readme.isBlank()) {
      score -= 20;
      suggestions.add(
          "Adicione um README explicando o que o projeto faz, como rodar e como contribuir.");
    } else if (readme.length() < 500) {
      score -= 5;
      suggestions.add(
          "O README e curto: inclua instrucoes de instalacao, exemplo de uso e requisitos.");
    } else {
      score += 15;
    }

    if (commits.isEmpty()) {
      score -= 15;
      suggestions.add("O repositorio nao tem commits — publique ao menos uma versao inicial.");
    } else {
      score += Math.min(20, commits.size());
      long mensagensVagas =
          commits.stream()
              .map(GitHubCommit::subject)
              .filter(s -> s.length() < 15 || s.toLowerCase().matches("(update|fix|wip|test).*"))
              .count();
      if (mensagensVagas > commits.size() / 2) {
        score -= 10;
        suggestions.add(
            "Boa parte das mensagens de commit e vaga. Adote Conventional Commits para "
                + "tornar o historico legivel.");
      }
    }

    if (issuesAbertas > 20) {
      score -= 5;
      suggestions.add(
          "Ha %d issues abertas. Triagem periodica evita que o backlog vire ruido."
              .formatted(issuesAbertas));
    }

    if (request.primaryLanguage() == null) {
      suggestions.add("O GitHub nao detectou linguagem principal — verifique o .gitattributes.");
    }
    if (suggestions.isEmpty()) {
      suggestions.add("Nada critico encontrado. Considere adicionar badges de CI ao README.");
    }

    int scoreFinal = Math.clamp(score, 0, 100);

    String summary =
        ("[ANALISE SIMULADA — sem chamada a modelo de IA] O repositorio %s%s tem %d commits "
                + "recentes analisados, %s e %d issues abertas. A nota %d foi derivada por "
                + "heuristica local sobre esses sinais.")
            .formatted(
                request.fullName(),
                request.primaryLanguage() == null ? "" : " (" + request.primaryLanguage() + ")",
                commits.size(),
                readme.isBlank() ? "nao possui README" : "possui README",
                issuesAbertas,
                scoreFinal);

    return new AnalysisResult(summary, scoreFinal, suggestions);
  }
}
