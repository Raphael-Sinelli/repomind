package com.rsinelli.repomind.analysis;

import com.rsinelli.repomind.repository.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

  List<Analysis> findByRepositoryOrderByCreatedAtDesc(Repository repository);

  /**
   * Analise ja existente para exatamente este estado do codigo. E a segunda linha de
   * defesa do cache: se o Redis for limpo, o banco ainda evita uma chamada de IA.
   */
  Optional<Analysis> findFirstByRepositoryAndAnalyzedCommitShaOrderByCreatedAtDesc(
      Repository repository, String analyzedCommitSha);
}
