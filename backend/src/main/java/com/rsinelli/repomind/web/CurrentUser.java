package com.rsinelli.repomind.web;

import com.rsinelli.repomind.exception.UnauthorizedException;
import com.rsinelli.repomind.user.User;
import com.rsinelli.repomind.user.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Traduz o principal do OAuth2 para a entidade {@link User} do nosso banco.
 *
 * <p>Existe para que os controllers nunca leiam atributos crus do GitHub: eles recebem um
 * {@code User}, com id proprio, e a extracao do {@code github_id} fica num lugar so.
 */
@Component
public class CurrentUser {

  private final UserRepository userRepository;

  public CurrentUser(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User require(OAuth2User principal) {
    if (principal == null) {
      throw new UnauthorizedException("Requisicao sem usuario autenticado.");
    }
    Object raw = principal.getAttribute("id");
    if (!(raw instanceof Number number)) {
      throw new UnauthorizedException("Sessao sem o identificador do GitHub.");
    }
    return userRepository
        .findByGithubId(number.longValue())
        .orElseThrow(
            () ->
                new UnauthorizedException(
                    "Sessao valida mas o usuario nao existe mais. Faca login novamente."));
  }
}
