-- gen_random_uuid() vem do pgcrypto (nativo no Postgres 13+ via pgcrypto,
-- e built-in a partir do 13 no core — a extensao garante compatibilidade).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  github_id  BIGINT      NOT NULL UNIQUE,
  username   TEXT        NOT NULL,
  email      TEXT,
  avatar_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE repositories (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  github_repo_id   BIGINT  NOT NULL,
  full_name        TEXT    NOT NULL,
  description      TEXT,
  stars            INTEGER NOT NULL DEFAULT 0,
  primary_language TEXT,
  last_synced_at   TIMESTAMPTZ,
  CONSTRAINT uq_repositories_user_github_repo UNIQUE (user_id, github_repo_id)
);

CREATE INDEX idx_repositories_user_id ON repositories (user_id);

CREATE TABLE analyses (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  repository_id       UUID        NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
  summary             TEXT        NOT NULL,
  quality_score       INTEGER     NOT NULL CHECK (quality_score BETWEEN 0 AND 100),
  suggestions         JSONB       NOT NULL,
  model_used          TEXT        NOT NULL,
  -- SHA do HEAD no momento da analise: e a chave de invalidacao do cache. Um TTL
  -- sozinho descartaria analise ainda valida e serviria analise obsoleta.
  analyzed_commit_sha TEXT        NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analyses_repository_id ON analyses (repository_id, created_at DESC);
