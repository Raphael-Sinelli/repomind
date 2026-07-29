package com.rsinelli.repomind.web;

import com.rsinelli.repomind.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

  private final CurrentUser currentUser;

  public MeController(CurrentUser currentUser) {
    this.currentUser = currentUser;
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal OAuth2User principal) {
    User user = currentUser.require(principal);
    return UserResponse.from(user);
  }
}
