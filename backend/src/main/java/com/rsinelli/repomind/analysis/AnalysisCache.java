package com.rsinelli.repomind.analysis;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache de analises no Redis.
 *
 * <p>Guarda apenas o <b>id</b> da analise, nao o conteudo: o Postgres continua sendo a
 * fonte da verdade, e o Redis so encurta o caminho ate ela. Isso evita ter a mesma
 * analise em dois lugares podendo divergir.
 *
 * <p>A chave inclui o SHA do HEAD. Um TTL sozinho seria a escolha errada: expiraria
 * analise ainda valida de um repositorio parado, e continuaria servindo analise obsoleta
 * de um repositorio que recebeu dez commits. O TTL aqui e so faxina de memoria.
 */
@Component
public class AnalysisCache {

  private static final Logger log = LoggerFactory.getLogger(AnalysisCache.class);
  private static final String KEY_PREFIX = "analysis:";

  private final StringRedisTemplate redis;
  private final Duration ttl;

  public AnalysisCache(
      StringRedisTemplate redis, @Value("${repomind.cache.analysis-ttl}") Duration ttl) {
    this.redis = redis;
    this.ttl = ttl;
  }

  public UUID get(UUID repositoryId, String commitSha) {
    try {
      String value = redis.opsForValue().get(key(repositoryId, commitSha));
      return value == null ? null : UUID.fromString(value);
    } catch (DataAccessException | IllegalArgumentException ex) {
      // Redis indisponivel ou valor corrompido nao pode derrubar a analise: o pior caso
      // e pagar uma chamada de IA que teria sido evitada.
      log.warn("Falha ao ler o cache de analises; seguindo sem cache", ex);
      return null;
    }
  }

  public void put(UUID repositoryId, String commitSha, UUID analysisId) {
    try {
      redis.opsForValue().set(key(repositoryId, commitSha), analysisId.toString(), ttl);
    } catch (DataAccessException ex) {
      log.warn("Falha ao gravar no cache de analises; resultado segue persistido no banco", ex);
    }
  }

  public void evict(UUID repositoryId, String commitSha) {
    try {
      redis.delete(key(repositoryId, commitSha));
    } catch (DataAccessException ex) {
      log.warn("Falha ao remover entrada do cache de analises", ex);
    }
  }

  private static String key(UUID repositoryId, String commitSha) {
    return KEY_PREFIX + repositoryId + ":" + commitSha;
  }
}
