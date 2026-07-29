package com.rsinelli.repomind.web;

import com.rsinelli.repomind.analysis.AnalysisService;
import com.rsinelli.repomind.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories/{repositoryId}/analyses")
public class AnalysisController {

  private final AnalysisService analysisService;
  private final CurrentUser currentUser;

  public AnalysisController(AnalysisService analysisService, CurrentUser currentUser) {
    this.analysisService = analysisService;
    this.currentUser = currentUser;
  }

  /**
   * Dispara a analise do repositorio. Se ja existir analise para o commit atual do HEAD,
   * ela e reaproveitada e nenhuma chamada de IA acontece — do ponto de vista do cliente a
   * resposta e a mesma, so mais rapida.
   */
  @PostMapping
  public AnalysisResponse analyze(
      @AuthenticationPrincipal OAuth2User principal, @PathVariable UUID repositoryId) {
    User user = currentUser.require(principal);
    return AnalysisResponse.from(analysisService.analyze(repositoryId, user));
  }

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public List<AnalysisResponse> history(
      @AuthenticationPrincipal OAuth2User principal, @PathVariable UUID repositoryId) {
    User user = currentUser.require(principal);
    return analysisService.history(repositoryId, user).stream().map(AnalysisResponse::from).toList();
  }
}
