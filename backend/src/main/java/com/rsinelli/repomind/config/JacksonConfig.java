package com.rsinelli.repomind.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O codigo Java usa camelCase; o contrato JSON da API usa snake_case. Jackson faz a
 * traducao, entao nenhum DTO precisa de @JsonProperty.
 *
 * <p>Datas saem como ISO-8601 UTC ("2026-07-29T14:30:00Z"), nunca como epoch numerico:
 * o servidor entrega UTC e a formatacao para o fuso do usuario e responsabilidade do
 * frontend.
 */
@Configuration
public class JacksonConfig {

  @Bean
  Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder ->
        builder
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }
}
