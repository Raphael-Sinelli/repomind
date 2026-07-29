package com.rsinelli.repomind.analysis;

import com.rsinelli.repomind.repository.Repository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analyses")
public class Analysis {

  @Id
  @GeneratedValue
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "repository_id", nullable = false, updatable = false)
  private Repository repository;

  @Column(name = "summary", nullable = false, columnDefinition = "text")
  private String summary;

  @Column(name = "quality_score", nullable = false)
  private int qualityScore;

  /** JSONB no Postgres: consultavel depois sem precisar de tabela filha. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "suggestions", nullable = false, columnDefinition = "jsonb")
  private List<String> suggestions;

  @Column(name = "model_used", nullable = false)
  private String modelUsed;

  /** SHA do HEAD no momento da analise — amarra o resultado a um estado do codigo. */
  @Column(name = "analyzed_commit_sha", nullable = false, updatable = false)
  private String analyzedCommitSha;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Analysis() {
    // exigido pelo JPA
  }

  public Analysis(
      Repository repository,
      String summary,
      int qualityScore,
      List<String> suggestions,
      String modelUsed,
      String analyzedCommitSha) {
    this.repository = repository;
    this.summary = summary;
    this.qualityScore = qualityScore;
    this.suggestions = suggestions;
    this.modelUsed = modelUsed;
    this.analyzedCommitSha = analyzedCommitSha;
    this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  public UUID getId() {
    return id;
  }

  public Repository getRepository() {
    return repository;
  }

  public String getSummary() {
    return summary;
  }

  public int getQualityScore() {
    return qualityScore;
  }

  public List<String> getSuggestions() {
    return suggestions;
  }

  public String getModelUsed() {
    return modelUsed;
  }

  public String getAnalyzedCommitSha() {
    return analyzedCommitSha;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
