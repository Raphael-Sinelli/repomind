package com.rsinelli.repomind.web;

import com.rsinelli.repomind.analysis.Analysis;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisResponse(
    UUID id,
    UUID repositoryId,
    String summary,
    int qualityScore,
    List<String> suggestions,
    String modelUsed,
    String analyzedCommitSha,
    Instant createdAt) {

  public static AnalysisResponse from(Analysis analysis) {
    return new AnalysisResponse(
        analysis.getId(),
        analysis.getRepository().getId(),
        analysis.getSummary(),
        analysis.getQualityScore(),
        analysis.getSuggestions(),
        analysis.getModelUsed(),
        analysis.getAnalyzedCommitSha(),
        analysis.getCreatedAt());
  }
}
