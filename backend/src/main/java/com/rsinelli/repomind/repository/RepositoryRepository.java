package com.rsinelli.repomind.repository;

import com.rsinelli.repomind.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Nome redundante por conta da convencao do Spring Data ({@code <Entidade>Repository}) e
 * do fato de a entidade se chamar {@code Repository}. Preferi manter a convencao a
 * inventar um sufixo so deste projeto.
 */
public interface RepositoryRepository extends JpaRepository<Repository, UUID> {

  List<Repository> findByUserOrderByStarsDescFullNameAsc(User user);

  Optional<Repository> findByUserAndGithubRepoId(User user, Long githubRepoId);

  /** Escopo por usuario: impede que alguem leia repositorio de outra conta pelo id. */
  Optional<Repository> findByIdAndUser(UUID id, User user);
}
