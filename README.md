# RepoMind

Analisador de repositorios GitHub com **tool use real de IA**: o modelo decide quais
ferramentas chamar (commits, README, issues) e o backend as executa — nao e um
prompt unico, e um loop de agente com decisao do modelo em cada rodada.

Projeto de portfolio. Java 21 + Spring Boot no backend, React 19 no frontend.

**Em producao**: [repomind-flame.vercel.app](https://repomind-flame.vercel.app)
(frontend) + [repomind-backend-39ws.onrender.com](https://repomind-backend-39ws.onrender.com)
(backend). Login OAuth real, fluxo completo funcionando.

> O backend roda no tier gratuito do Render, que hiberna sem trafego. A
> **primeira requisicao depois de um periodo ocioso leva ~30-50s** (cold start)
> enquanto o container sobe — normal, nao e bug. Requisicoes seguintes sao rapidas.

## Arquitetura

```mermaid
flowchart LR
  subgraph Browser
    FE[React 19 + TS<br/>Vite em dev, Vercel em producao]
  end

  subgraph Backend["Spring Boot<br/>local em dev, Render em producao"]
    Sec[Spring Security<br/>OAuth2 Client]
    API[REST Controllers]
    Svc[AnalysisService]
    Analyzer["RepoAnalyzer<br/>(mock | anthropic)"]
  end

  GH[(GitHub REST API)]
  PG[(PostgreSQL 16<br/>docker-compose em dev, Neon em producao)]
  Redis[(Redis 7<br/>docker-compose em dev, Upstash em producao)]
  Anthropic[(api.anthropic.com)]

  FE -->|cookie de sessao| Sec
  FE --> API
  API --> Svc
  Sec -->|OAuth2 login| GH
  Svc -->|token do usuario| GH
  Svc --> PG
  Svc -->|cache: repoId+headSha| Redis
  Svc --> Analyzer
  Analyzer -.tool use.-> GH
  Analyzer -.perfil anthropic, nunca exercitado ao vivo.-> Anthropic
```

### Fluxo de uma analise

```
POST /api/v1/repositories/{id}/analyses
  → GitHubClient.getHeadCommitSha()
  → Redis GET analysis:{repoId}:{sha}   — HIT? retorna, zero custo de IA
  → MISS: RepoAnalyzer.analyze()
        loop manual: enquanto stop_reason == "tool_use"
          executa a tool pedida (get_commits | get_readme | get_issues)
          devolve o resultado ao modelo numa unica mensagem
        até resposta final (JSON validado via structured output)
  → persiste em Postgres, grava no Redis (TTL 24h), retorna ao cliente
```

A chave de cache e `repoId + SHA do HEAD`, nao um TTL simples: TTL sozinho serviria
uma analise obsoleta depois de um push. Amarrar ao commit invalida exatamente
quando o repositorio muda.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security (OAuth2 Client), Spring Data JPA, Spring Data Redis, Flyway |
| IA | `com.anthropic:anthropic-java`, loop manual de tool use, structured outputs |
| Persistencia | PostgreSQL 16 (TIMESTAMPTZ/UTC), Redis 7 |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query, React Router, Axios |
| Testes | JUnit 5, Testcontainers (Postgres + Redis), MockWebServer, Vitest, Testing Library |
| Infra (nao aplicada) | Terraform: VPC, RDS, ElastiCache, ECR, ECS Fargate, ALB, Secrets Manager |

## Rodando localmente

```bash
git clone <repo>
cd repomind
cp .env.example .env   # preencha GITHUB_CLIENT_ID/SECRET do seu OAuth App
docker compose up -d   # Postgres + Redis

cd backend
JAVA_HOME=<jdk21> ./mvnw spring-boot:run   # :8080, perfil "mock" por padrao

cd ../frontend
npm ci
npm run dev             # :5173, proxy /api e /oauth2 para :8080
```

GitHub OAuth App: callback URL deve ser `http://localhost:8080/login/oauth2/code/github`.

Fluxo manual: abrir `localhost:5173` → "Entrar com GitHub" → autorizar → lista de
repositorios reais → clicar "Analisar" → resumo + nota + sugestoes → clicar de novo
→ resposta instantanea (cache hit, confirmavel em `docker compose logs`).

## Testes

```bash
cd backend && ./mvnw verify     # 41 testes: unit + integracao com Testcontainers
cd frontend && npm test         # Vitest + Testing Library
```

## API

Erros sempre no formato `{"error":{"code","message","status"}}`.

| Metodo | Rota | Descricao |
|---|---|---|
| `GET` | `/oauth2/authorization/github` | Inicia login |
| `GET` | `/api/v1/me` | Usuario logado (401 se anonimo) |
| `POST` | `/api/v1/logout` | Encerra sessao |
| `GET` | `/api/v1/repositories` | Lista + sincroniza repos do usuario |
| `POST` | `/api/v1/repositories/{id}/analyses` | Dispara analise (cache-aware) |
| `GET` | `/api/v1/repositories/{id}/analyses` | Historico, mais recente primeiro |

## Escopo da integracao com IA — leia isto antes de assumir que "funciona"

O `AnthropicAnalyzer` (perfil `anthropic`) implementa o loop de tool use por
completo e e coberto por testes com `MockWebServer` simulando o protocolo da API
(tool_use → tool_result, replay de blocos de thinking, teto de iteracoes, erro de
tool virando `tool_result` com `is_error`). **Ele nunca foi executado contra
`api.anthropic.com`** — este projeto nao tem uma chave da Anthropic.

O perfil padrao (`mock`, usado em todo o fluxo demonstrado acima) usa o
`MockAnalyzer`: busca dados reais do GitHub (README, commits, issues) e deriva uma
nota por heuristica local, sem chamar nenhum modelo. Tudo o mais no fluxo — OAuth,
Postgres, Redis, cache por commit SHA, frontend — e real e foi verificado
manualmente no navegador.

Trocar para a integracao real e uma variavel de ambiente
(`SPRING_PROFILES_ACTIVE=anthropic` + `ANTHROPIC_API_KEY`), nao uma reescrita — mas
ate isso acontecer, a afirmacao honesta e "o loop esta correto conforme a
especificacao da API e testado no nivel de protocolo", nao "funciona em producao".

## Deploy

A producao atual roda numa stack gratuita, escolhida por custo — nao e a
arquitetura "de producao real" deste projeto (essa e a AWS, ver secao abaixo):

| Componente | Onde | Nota |
|---|---|---|
| Frontend | Vercel | `vercel.json` reescreve `/api/*`, `/oauth2/*` e o callback do GitHub para o backend no Render — o Vercel nao tem o proxy do Vite, entao sem isso login e chamadas de API cairiam 404 no proprio Vercel |
| Backend | Render (Docker, tier gratuito) | `backend/Dockerfile` multi-stage; le `PORT` injetada pelo Render em runtime, sem alterar `application.yml` |
| Postgres | Neon | TLS obrigatorio — `POSTGRES_SSL_MODE=require` |
| Redis | Upstash | TLS + senha obrigatorios — `REDIS_SSL=true`, `REDIS_PASSWORD` |

Todas as variaveis novas de TLS/senha (`POSTGRES_SSL_MODE`, `REDIS_PASSWORD`,
`REDIS_SSL`) tem default seguro para o dev local (`disable`, vazio, `false`) —
o `docker-compose.yml` local nunca fala TLS e continua funcionando sem mudanca.

## Infraestrutura AWS (design, nao aplicado)

`infra/` contem Terraform completo (VPC, RDS, ElastiCache, ECR, ECS Fargate, ALB,
Secrets Manager) — versionado como design de producao real, **nunca aplicado**
contra uma conta AWS (a demo publica acima usa Vercel/Render/Neon/Upstash por
serem gratuitos). Detalhes e diagrama em [`infra/README.md`](infra/README.md).

## Aprendizados

- **Testcontainers pegou um bug que H2 esconderia**: `Instant.now()` (nanossegundos)
  vs `TIMESTAMPTZ` do Postgres (microssegundos) quebrava um teste de igualdade ao
  reler uma entidade do banco. So apareceu porque o teste rodava contra Postgres
  real, nao um banco em memoria com semantica diferente.
- **`JsonValue.toString()` nao e JSON**: no loop de tool use, ler o input de uma
  tool via `.toString()` produzia formato Java-map (`{limit=42}`), nao JSON — todo
  parametro que o modelo especificasse seria silenciosamente ignorado, sem erro em
  lugar nenhum. Pego por um teste que verificava o argumento chegando ao executor;
  corrigido trocando para `JsonValue.convert(Map.class)`.
- **`RestClient.builder()` nao herda o `ObjectMapper` global do Spring**: sem
  configuracao explicita, o cliente do GitHub deserializava o JSON snake_case da
  API com Jackson no padrao camelCase-only, populando campos como `null` sem
  nenhum erro visivel.
