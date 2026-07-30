# RepoMind

Analisador de repositorios GitHub com **tool use real de IA**: o modelo decide
quais ferramentas chamar (commits, README, issues) e o backend as executa. Nao e
um prompt unico — e um loop de agente, com o modelo decidindo em cada rodada se
quer mais dados ou se ja pode responder.

Projeto de portfolio. Java 21 + Spring Boot no backend, React 19 no frontend.

## Demo

**[repomind-flame.vercel.app](https://repomind-flame.vercel.app)** — deploy real,
login OAuth real, fluxo completo funcionando.

> **Primeira requisicao pode levar ~30-50s.** O backend roda no tier gratuito do
> Render, que hiberna sem trafego — e normal, nao e bug. Depois do primeiro
> load, tudo fica rapido.

O login usa OAuth de verdade contra a API do GitHub — **nao existe usuario de
demonstracao**. Cada pessoa que autoriza o app ve os proprios repositorios dela,
nao os do autor do projeto. Isso e o comportamento esperado, nao um bug: e a
prova de que a integracao OAuth e o `GitHubClient` sao reais, nao mockados. Pra
testar de verdade e preciso autorizar com a propria conta GitHub (permissoes
`read:user`, `user:email`, `repo` — leitura, nunca escrita).

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security (OAuth2 Client), Spring Data JPA, PostgreSQL, Flyway, Spring Data Redis |
| IA | `com.anthropic:anthropic-java`, loop manual de tool use, structured outputs |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query, React Router, Axios |
| Infra | Docker (dev + Dockerfile de producao), GitHub Actions CI |
| Testes | JUnit 5, Testcontainers (Postgres + Redis), MockWebServer, Vitest, Testing Library |

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

A chave de cache e `repoId + SHA do HEAD`, nao um TTL simples: TTL sozinho
serviria uma analise obsoleta depois de um push. Amarrar ao commit invalida
exatamente quando o repositorio muda.

## Deploy

A producao publica roda numa stack gratuita, escolhida por custo — nao e a
arquitetura "de producao real" deste projeto (essa e a AWS, ver
[`infra/`](infra/README.md) abaixo):

| Componente | Onde | Nota |
|---|---|---|
| Frontend | Vercel | `vercel.json` reescreve `/api/*`, `/oauth2/*` e o callback do GitHub para o backend no Render (Vercel nao tem o proxy do Vite), mais um catch-all pra `/index.html` (sem ele, rotas do React Router acessadas direto pela URL davam 404) |
| Backend | Render (Docker, tier gratuito) | `backend/Dockerfile` multi-stage; le `PORT` injetada pelo Render em runtime, sem alterar `application.yml` |
| Postgres | Neon | TLS obrigatorio — `POSTGRES_SSL_MODE=require` |
| Redis | Upstash | TLS + senha obrigatorios — `REDIS_SSL=true`, `REDIS_PASSWORD` |

Todas as variaveis novas de TLS/senha (`POSTGRES_SSL_MODE`, `REDIS_PASSWORD`,
`REDIS_SSL`) tem default seguro para o dev local (`disable`, vazio, `false`) — o
`docker-compose.yml` local nunca fala TLS e continua funcionando sem mudanca.

O design de producao "real" deste projeto e AWS (VPC, RDS, ElastiCache, ECS
Fargate, ALB, Secrets Manager), versionado em Terraform em
[`infra/`](infra/README.md) mas **nunca aplicado** — custo continuo sem
orcamento alocado pra um projeto de portfolio.

## Escopo da integracao com IA

O `AnthropicAnalyzer` (perfil `anthropic`) implementa o loop de tool use por
completo e e coberto por testes com `MockWebServer` simulando o protocolo da API
(tool_use → tool_result, replay de blocos de thinking, teto de iteracoes, erro
de tool virando `tool_result` com `is_error`). **Ele nunca foi executado contra
`api.anthropic.com`** — este projeto nao tem uma chave da Anthropic.

O perfil padrao (`mock`, ativo tambem em producao) usa o `MockAnalyzer`: busca
dados reais do GitHub (README, commits, issues) e deriva uma nota por
heuristica local, sem chamar nenhum modelo. Tudo o mais no fluxo — OAuth,
Postgres, Redis, cache por commit SHA, frontend — e real.

Trocar para a integracao real e uma variavel de ambiente
(`SPRING_PROFILES_ACTIVE=anthropic` + `ANTHROPIC_API_KEY`), nao uma reescrita —
mas ate isso acontecer, a afirmacao honesta e "o loop esta correto conforme a
especificacao da API e testado no nivel de protocolo", nao "funciona em
producao".

## Bugs reais encontrados

Nao ficaram so no codigo — apareceram rodando o sistema de verdade (Testcontainers,
navegador, deploy real). Registro porque cada um ensina algo especifico.

| Bug | Causa raiz | Como foi descoberto | Fix |
|---|---|---|---|
| `POST` de analise sempre 403 | Backend usa `CookieCsrfTokenRepository` (Spring Security); Axios nao envia o cookie `XSRF-TOKEN` como header `X-XSRF-TOKEN` por padrao. A rejeicao acontece no filtro de seguranca, antes do controller — a resposta nao vem no formato `{error:{...}}`, entao o frontend so tinha um erro generico pra mostrar | Testando o fluxo de analise de ponta a ponta no navegador (nao pego por teste automatizado) | `withXSRFToken`, `xsrfCookieName`, `xsrfHeaderName` explicitos no client Axios |
| Teste de igualdade de `User` falhava so contra Postgres real | `Instant.now()` tem precisao de nanossegundos; `TIMESTAMPTZ` do Postgres trunca pra microssegundos. Reler a entidade do banco devolvia um timestamp diferente do que foi criado em memoria | Testcontainers (Postgres real, nao H2) — um banco em memoria com semantica diferente teria escondido isso | `Instant.now().truncatedTo(ChronoUnit.MICROS)` na entidade |
| Parametros de tool sempre viravam o valor default, sem erro em lugar nenhum | `JsonValue.toString()` no SDK da Anthropic produz formato Java-map (`{limit=42}`), nao JSON — parsear isso como JSON falhava silenciosamente e o codigo caia no fallback | Teste verificando o argumento chegando ao `GitHubToolExecutor` | Trocado para `JsonValue.convert(Map.class)` |
| CORS derrubava a config de seguranca no boot em producao | `${FRONTEND_ORIGIN:http://localhost:5173}` so aplica o default quando a variavel **nao existe**. No Render ela estava setada como string vazia (nao omitida), e chegava vazia pro Spring | Deploy real no Render | Garantir que a env var simplesmente nao seja definida quando se quer o default, em vez de definida como vazia |
| CI falhava em 6s com "Permission denied" no `./mvnw` | Commit original feito no Windows: `git-bash` mostra `rwxr-xr-x` no `ls` local, mas isso e so ACL do Windows sendo simulada — o modo real gravado no objeto git era `100644` (nao executavel). Runner Linux do Actions respeita o modo real | `gh run view --log-failed` no job que falhava | `git update-index --chmod=+x backend/mvnw` |
| CI nunca disparou em nenhum push, sem nenhum erro visivel | `ci.yml` filtrava `branches: [main]`, mas o repositorio usa `master` como branch padrao | Auditoria manual — `gh api .../actions/runs` retornava `total_count: 0` | Branch filter corrigido pra `master` |
| Rotas do React Router acessadas direto pela URL davam 404 no Vercel | Sem rewrite catch-all, o Vercel tentava servir `/repositories` como arquivo estatico em vez de deixar o React Router resolver client-side | Acessando a URL direto em producao | Rewrite `/(.*) → /index.html` adicionado por ultimo em `vercel.json`, depois dos rewrites de API/OAuth |

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
repositorios reais → clicar "Analisar" → resumo + nota + sugestoes → clicar de
novo → resposta instantanea (cache hit, confirmavel em `docker compose logs`).

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

## Infraestrutura AWS

Design de producao completo (VPC, RDS, ElastiCache, ECR, ECS Fargate, ALB,
Secrets Manager) versionado em Terraform, nunca aplicado. Diagrama e detalhes em
[`infra/README.md`](infra/README.md).
