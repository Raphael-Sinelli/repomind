package com.rsinelli.repomind.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommit(String sha, Commit commit) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Commit(String message, Author author) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Author(String name, Instant date) {}

  /** Primeira linha da mensagem — o resto e corpo e polui o contexto do modelo. */
  public String subject() {
    if (commit == null || commit.message() == null) {
      return "";
    }
    int newline = commit.message().indexOf('\n');
    return newline < 0 ? commit.message() : commit.message().substring(0, newline);
  }
}
