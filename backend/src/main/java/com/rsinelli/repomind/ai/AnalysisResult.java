package com.rsinelli.repomind.ai;

import java.util.List;

/**
 * Saida da analise, independente de quem a produziu (modelo real ou mock).
 *
 * @param summary resumo em prosa do que o repositorio e e em que estado esta
 * @param qualityScore 0 a 100
 * @param suggestions melhorias acionaveis, em ordem de impacto
 */
public record AnalysisResult(String summary, int qualityScore, List<String> suggestions) {

  public AnalysisResult {
    if (summary == null || summary.isBlank()) {
      throw new IllegalArgumentException("A analise precisa de um resumo.");
    }
    if (qualityScore < 0 || qualityScore > 100) {
      // A constraint equivalente existe no banco; falhar aqui da uma mensagem melhor
      // do que uma violacao de CHECK vinda do Postgres.
      throw new IllegalArgumentException(
          "quality_score deve estar entre 0 e 100, recebido: " + qualityScore);
    }
    suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
  }
}
