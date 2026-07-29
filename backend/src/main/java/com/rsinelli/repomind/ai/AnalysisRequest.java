package com.rsinelli.repomind.ai;

/**
 * Contexto de uma analise.
 *
 * <p>O {@code fullName} vive aqui e nao nos parametros das tools de proposito: se o
 * modelo pudesse escolher o repositorio, um prompt malicioso poderia faze-lo ler um
 * repositorio que o usuario nunca pediu. As tools operam sempre sobre este alvo fixo.
 */
public record AnalysisRequest(
    String fullName, String primaryLanguage, String description, String gitHubToken) {}
