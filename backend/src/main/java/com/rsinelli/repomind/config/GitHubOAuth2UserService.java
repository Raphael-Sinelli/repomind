package com.rsinelli.repomind.config;

import com.rsinelli.repomind.user.UserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Roda dentro do fluxo de login: o Spring busca o perfil no GitHub e, antes de criar a
 * sessao, gravamos o usuario. Fazer isso aqui — e nao num success handler — garante que
 * nenhuma sessao autenticada exista sem a linha correspondente em {@code users}.
 */
@Service
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
  private final UserService userService;

  public GitHubOAuth2UserService(UserService userService) {
    this.userService = userService;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = delegate.loadUser(userRequest);
    userService.upsertFromGitHub(oAuth2User.getAttributes());
    return oAuth2User;
  }
}
