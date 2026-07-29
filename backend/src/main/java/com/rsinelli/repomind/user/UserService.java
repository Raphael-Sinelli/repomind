package com.rsinelli.repomind.user;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Cria ou atualiza o usuario a partir dos atributos devolvidos pelo user-info do
   * GitHub. Chamado a cada login, entao mudanca de username ou avatar se reflete sozinha.
   */
  @Transactional
  public User upsertFromGitHub(Map<String, Object> attributes) {
    Long githubId = requireGithubId(attributes);
    String username = asText(attributes.get("login"));
    String email = asText(attributes.get("email"));
    String avatarUrl = asText(attributes.get("avatar_url"));

    return userRepository
        .findByGithubId(githubId)
        .map(
            existing -> {
              existing.refreshProfile(username, email, avatarUrl);
              return existing;
            })
        .orElseGet(() -> userRepository.save(new User(githubId, username, email, avatarUrl)));
  }

  /**
   * O user-info do GitHub entrega {@code id} como numero JSON, que Jackson pode
   * materializar como Integer ou Long dependendo da magnitude. Normalizar via Number
   * evita um ClassCastException que so apareceria com ids grandes.
   */
  private static Long requireGithubId(Map<String, Object> attributes) {
    Object raw = attributes.get("id");
    if (raw instanceof Number number) {
      return number.longValue();
    }
    if (raw instanceof String text && !text.isBlank()) {
      return Long.parseLong(text.trim());
    }
    throw new IllegalArgumentException(
        "Resposta do GitHub sem o atributo 'id'; nao da para identificar o usuario.");
  }

  /** Normaliza string vazia para null — email privado no GitHub chega como "". */
  private static String asText(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString().trim();
    return text.isEmpty() ? null : text;
  }
}
