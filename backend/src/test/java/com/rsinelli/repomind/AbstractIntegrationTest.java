package com.rsinelli.repomind;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base para testes que precisam de banco e Redis reais. Postgres via Testcontainers
 * garante que constraints, tipo JSONB e TIMESTAMPTZ sejam exercitados de verdade — um H2
 * em memoria mascararia exatamente os erros que queremos pegar.
 *
 * <p>Os containers sao estaticos e iniciados uma unica vez por JVM: o custo de subir
 * Postgres e pago uma vez, nao por classe de teste.
 *
 * <p>{@code spring.config.import=} vazio desliga a leitura do {@code .env} de
 * desenvolvimento — teste nao pode depender da maquina de quem roda.
 */
@ActiveProfiles("mock")
@TestPropertySource(
    properties = {
      "spring.config.import=",
      "GITHUB_CLIENT_ID=test-client-id",
      "GITHUB_CLIENT_SECRET=test-client-secret",
      "repomind.frontend-origin=http://localhost:5173"
    })
public abstract class AbstractIntegrationTest {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("repomind")
          .withUsername("repomind")
          .withPassword("test");

  @SuppressWarnings("resource")
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void containerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }
}
