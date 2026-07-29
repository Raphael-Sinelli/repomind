package com.rsinelli.repomind.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Recorte do repositorio como o GitHub o devolve. Apenas os campos que usamos —
 * {@code @JsonIgnoreProperties} evita que um campo novo na API quebre a desserializacao.
 *
 * <p>Os nomes vem em snake_case do GitHub e a estrategia global do Jackson ja e
 * SNAKE_CASE, entao o mapeamento sai sem anotacao por campo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepo(
    Long id,
    String name,
    String fullName,
    String description,
    Integer stargazersCount,
    String language,
    Boolean fork,
    Boolean archived,
    String defaultBranch) {}
