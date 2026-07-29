package com.rsinelli.repomind.github;

import com.rsinelli.repomind.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Recupera o access token do GitHub do usuario autenticado.
 *
 * <p>O token vive no {@link OAuth2AuthorizedClientService} — em memoria, gerenciado pelo
 * Spring — e nunca e gravado no Postgres. Persistir credencial de terceiro e um risco que
 * este projeto nao precisa correr: se o processo reinicia, o usuario refaz o login.
 */
@Component
public class GitHubTokenProvider {

  private final OAuth2AuthorizedClientService authorizedClientService;

  public GitHubTokenProvider(OAuth2AuthorizedClientService authorizedClientService) {
    this.authorizedClientService = authorizedClientService;
  }

  public String currentToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
      throw new UnauthorizedException("Nenhuma sessao OAuth2 ativa.");
    }

    OAuth2AuthorizedClient client =
        authorizedClientService.loadAuthorizedClient(
            oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());

    if (client == null || client.getAccessToken() == null) {
      // Acontece quando o app reinicia: a sessao (cookie) sobrevive no navegador mas o
      // token, que estava em memoria, nao. Mensagem precisa dizer o que fazer.
      throw new UnauthorizedException(
          "Sua autorizacao com o GitHub nao esta mais disponivel. Entre novamente.");
    }
    return client.getAccessToken().getTokenValue();
  }
}
