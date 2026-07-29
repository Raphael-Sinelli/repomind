package com.rsinelli.repomind.ai;

/**
 * Unico ponto de integracao com a IA.
 *
 * <p>Duas implementacoes: {@code MockAnalyzer} (perfil {@code mock}) e
 * {@code AnthropicAnalyzer} (perfil {@code anthropic}). Trocar entre elas e mudar
 * {@code SPRING_PROFILES_ACTIVE} — nenhuma outra camada sabe qual esta ativa.
 */
public interface RepoAnalyzer {

  AnalysisResult analyze(AnalysisRequest request);

  /** Identificador gravado em {@code analyses.model_used}, para rastreabilidade. */
  String modelIdentifier();
}
