package com.rsinelli.repomind.web;

import com.rsinelli.repomind.repository.RepositoryService;
import com.rsinelli.repomind.user.User;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories")
public class RepositoryController {

  private final RepositoryService repositoryService;
  private final CurrentUser currentUser;

  public RepositoryController(RepositoryService repositoryService, CurrentUser currentUser) {
    this.repositoryService = repositoryService;
    this.currentUser = currentUser;
  }

  /**
   * @param refresh {@code false} le apenas o cache local — util para navegacao rapida sem
   *     gastar cota da API do GitHub. O padrao e sincronizar.
   */
  @GetMapping
  public List<RepositoryResponse> list(
      @AuthenticationPrincipal OAuth2User principal,
      @RequestParam(name = "refresh", defaultValue = "true") boolean refresh) {

    User user = currentUser.require(principal);
    var repositories =
        refresh ? repositoryService.syncFromGitHub(user) : repositoryService.listLocal(user);

    return repositories.stream().map(RepositoryResponse::from).toList();
  }
}
