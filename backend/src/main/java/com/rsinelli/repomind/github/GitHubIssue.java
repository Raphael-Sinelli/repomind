package com.rsinelli.repomind.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssue(
    Long number,
    String title,
    String state,
    Instant createdAt,
    /**
     * O endpoint /issues do GitHub devolve pull requests junto com issues. Este campo so
     * vem preenchido quando o item e um PR, e e como os separamos.
     */
    PullRequestMarker pullRequest) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PullRequestMarker(String url) {}

  public boolean isPullRequest() {
    return pullRequest != null;
  }
}
