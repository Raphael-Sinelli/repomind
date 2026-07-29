package com.rsinelli.repomind.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Cliente da Anthropic — so existe no perfil {@code anthropic}. No perfil {@code mock}
 * nenhum bean e criado, entao a ausencia de ANTHROPIC_API_KEY nao impede a aplicacao de
 * subir.
 */
@Configuration
@Profile("anthropic")
public class AnthropicConfig {

  @Bean
  AnthropicClient anthropicClient(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      // Falhar aqui, na subida, com uma mensagem clara — e nao na primeira analise,
      // com um 401 cru vindo da API.
      throw new IllegalStateException(
          "Perfil 'anthropic' ativo mas ANTHROPIC_API_KEY esta vazia. "
              + "Defina a chave ou rode com SPRING_PROFILES_ACTIVE=mock.");
    }
    return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
  }
}
