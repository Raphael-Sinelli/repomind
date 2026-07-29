package com.rsinelli.repomind.analysis;

import com.rsinelli.repomind.ai.AnalysisRequest;
import com.rsinelli.repomind.ai.AnalysisResult;
import com.rsinelli.repomind.ai.RepoAnalyzer;
import com.rsinelli.repomind.github.GitHubClient;
import com.rsinelli.repomind.github.GitHubTokenProvider;
import com.rsinelli.repomind.repository.Repository;
import com.rsinelli.repomind.repository.RepositoryService;
import com.rsinelli.repomind.user.User;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisService {

  private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

  private final AnalysisRepository analyses;
  private final RepositoryService repositoryService;
  private final RepoAnalyzer analyzer;
  private final GitHubClient gitHubClient;
  private final GitHubTokenProvider tokenProvider;
  private final AnalysisCache cache;

  public AnalysisService(
      AnalysisRepository analyses,
      RepositoryService repositoryService,
      RepoAnalyzer analyzer,
      GitHubClient gitHubClient,
      GitHubTokenProvider tokenProvider,
      AnalysisCache cache) {
    this.analyses = analyses;
    this.repositoryService = repositoryService;
    this.analyzer = analyzer;
    this.gitHubClient = gitHubClient;
    this.tokenProvider = tokenProvider;
    this.cache = cache;
  }

  /**
   * Analisa um repositorio, reaproveitando resultado quando o codigo nao mudou.
   *
   * <p>Tres niveis, do mais barato ao mais caro:
   *
   * <ol>
   *   <li>Redis, por {@code repoId + sha} — resposta em milissegundos
   *   <li>Postgres, mesma chave — sobrevive a um flush do Redis
   *   <li>Chamada ao analisador — o unico caminho que custa dinheiro e tempo
   * </ol>
   */
  @Transactional
  public Analysis analyze(UUID repositoryId, User user) {
    Repository repository = repositoryService.requireOwned(repositoryId, user);
    String token = tokenProvider.currentToken();
    String headSha = gitHubClient.getHeadCommitSha(token, repository.getFullName());

    UUID cachedId = cache.get(repository.getId(), headSha);
    if (cachedId != null) {
      var cached = analyses.findById(cachedId);
      if (cached.isPresent()) {
        log.debug("Cache HIT (redis) para {} @ {}", repository.getFullName(), shortSha(headSha));
        return cached.get();
      }
      // Entrada apontando para registro que nao existe mais: limpa e segue.
      cache.evict(repository.getId(), headSha);
    }

    var persisted =
        analyses.findFirstByRepositoryAndAnalyzedCommitShaOrderByCreatedAtDesc(repository, headSha);
    if (persisted.isPresent()) {
      log.debug("Cache HIT (postgres) para {} @ {}", repository.getFullName(), shortSha(headSha));
      cache.put(repository.getId(), headSha, persisted.get().getId());
      return persisted.get();
    }

    log.info(
        "Cache MISS para {} @ {} — acionando {}",
        repository.getFullName(),
        shortSha(headSha),
        analyzer.modelIdentifier());

    AnalysisResult result =
        analyzer.analyze(
            new AnalysisRequest(
                repository.getFullName(),
                repository.getPrimaryLanguage(),
                repository.getDescription(),
                token));

    Analysis saved =
        analyses.save(
            new Analysis(
                repository,
                result.summary(),
                result.qualityScore(),
                result.suggestions(),
                analyzer.modelIdentifier(),
                headSha));

    cache.put(repository.getId(), headSha, saved.getId());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<Analysis> history(UUID repositoryId, User user) {
    Repository repository = repositoryService.requireOwned(repositoryId, user);
    return analyses.findByRepositoryOrderByCreatedAtDesc(repository);
  }

  private static String shortSha(String sha) {
    return sha.length() <= 7 ? sha : sha.substring(0, 7);
  }
}
