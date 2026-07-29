package com.rsinelli.repomind.repository;

import com.rsinelli.repomind.exception.NotFoundException;
import com.rsinelli.repomind.github.GitHubClient;
import com.rsinelli.repomind.github.GitHubRepo;
import com.rsinelli.repomind.github.GitHubTokenProvider;
import com.rsinelli.repomind.user.User;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryService {

  private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

  private final RepositoryRepository repositories;
  private final GitHubClient gitHubClient;
  private final GitHubTokenProvider tokenProvider;

  public RepositoryService(
      RepositoryRepository repositories,
      GitHubClient gitHubClient,
      GitHubTokenProvider tokenProvider) {
    this.repositories = repositories;
    this.gitHubClient = gitHubClient;
    this.tokenProvider = tokenProvider;
  }

  /**
   * Busca os repositorios no GitHub e reconcilia com o banco. O GitHub e a fonte da
   * verdade: nome, estrelas e linguagem sao reescritos a cada sync.
   *
   * <p>Repositorios que sumiram do GitHub permanecem no banco de proposito — apagar em
   * cascata levaria junto o historico de analises de algo que o usuario apenas tornou
   * privado ou renomeou.
   */
  @Transactional
  public List<Repository> syncFromGitHub(User user) {
    String token = tokenProvider.currentToken();
    List<GitHubRepo> remote = gitHubClient.listRepositories(token);

    for (GitHubRepo remoteRepo : remote) {
      if (remoteRepo.id() == null || remoteRepo.fullName() == null) {
        log.warn("Repositorio do GitHub sem id ou full_name; ignorado.");
        continue;
      }
      Repository local =
          repositories
              .findByUserAndGithubRepoId(user, remoteRepo.id())
              .orElseGet(() -> new Repository(user, remoteRepo.id(), remoteRepo.fullName()));

      local.syncFrom(
          remoteRepo.fullName(),
          remoteRepo.description(),
          remoteRepo.stargazersCount() == null ? 0 : remoteRepo.stargazersCount(),
          remoteRepo.language());

      repositories.save(local);
    }

    log.debug("Sync concluido para {}: {} repositorios", user.getUsername(), remote.size());
    return repositories.findByUserOrderByStarsDescFullNameAsc(user);
  }

  @Transactional(readOnly = true)
  public List<Repository> listLocal(User user) {
    return repositories.findByUserOrderByStarsDescFullNameAsc(user);
  }

  /**
   * Sempre resolve o repositorio no escopo do dono. Buscar so por id permitiria que um
   * usuario lesse — e mandasse analisar — repositorio de outra conta.
   */
  @Transactional(readOnly = true)
  public Repository requireOwned(UUID repositoryId, User user) {
    return repositories
        .findByIdAndUser(repositoryId, user)
        .orElseThrow(() -> new NotFoundException("Repositorio nao encontrado."));
  }
}
