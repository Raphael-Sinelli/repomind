package com.rsinelli.repomind.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rsinelli.repomind.AbstractIntegrationTest;
import com.rsinelli.repomind.user.User;
import com.rsinelli.repomind.user.UserRepository;
import com.rsinelli.repomind.user.UserService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MeControllerTest extends AbstractIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserService userService;
  @Autowired UserRepository userRepository;

  @BeforeEach
  void clean() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("sem sessao a API responde 401, nao redirect para pagina de login")
  void returns401WhenAnonymous() throws Exception {
    mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("com sessao devolve o perfil do usuario logado")
  void returnsProfileWhenAuthenticated() throws Exception {
    User user =
        userService.upsertFromGitHub(
            Map.of(
                "id", 4242L,
                "login", "rapha",
                "email", "rapha@example.com",
                "avatar_url", "https://avatars.githubusercontent.com/u/4242"));

    mockMvc
        .perform(get("/api/v1/me").with(oauth2Login().attributes(a -> a.put("id", 4242L))))
        .andExpect(status().isOk())
        // Contrato JSON e snake_case, mesmo com o codigo Java em camelCase.
        .andExpect(jsonPath("$.github_id").value(4242))
        .andExpect(jsonPath("$.username").value("rapha"))
        .andExpect(jsonPath("$.avatar_url").value(user.getAvatarUrl()));
  }

  @Test
  @DisplayName("health continua publico")
  void healthIsPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
