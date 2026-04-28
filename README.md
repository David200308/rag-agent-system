# RAG Agent System

![frontend](./image/frontend.png)

## Tech Stack

### Backend

| Layer            | Technology                                 |
| ---------------- | ------------------------------------------ |
| Runtime          | Java 21 (virtual threads)                  |
| Framework        | Spring Boot 3.4.5                          |
| AI orchestration | Spring AI 1.1                              |
| Agent graph      | LangGraph4j 1.7                            |
| LLM providers    | OpenAI / OpenRouter / Anthropic / Local    |
| Vector store     | Weaviate                                   |
| Embeddings       | Spring AI embedding abstraction            |
| Document parsing | Apache Tika (PDF, text, HTML)              |
| HTML scraping    | Jsoup                                      |
| Circuit breaker  | Resilience4j 2.2                           |
| Auth             | OTP email (Resend) + JJWT stateless tokens |
| Persistence      | MySQL 8 + Spring Data JPA                  |
| MCP server       | Spring AI MCP WebMVC SSE transport         |
| API docs         | SpringDoc OpenAPI (Swagger UI)             |

### Frontend

| Layer     | Technology              |
| --------- | ----------------------- |
| Framework | Next.js 16 (App Router) |
| Language  | TypeScript 6            |
| UI        | React 19                |
| State     | Zustand 5               |
| Styling   | Tailwind CSS 4          |

### Infrastructure

| Component        | Technology                                  |
| ---------------- | ------------------------------------------- |
| Vector DB        | Weaviate (Docker)                           |
| Relational DB    | MySQL (Docker)                              |
| Scheduler        | Go microservice (cron jobs)                 |
| API Gateway      | Go microservice — `agent-openapi` (:8888)   |
| Containerization | Docker Compose                              |

### CLI

| Component | Technology          |
| --------- | ------------------- |
| Language  | Go 1.22             |
| Auth      | OTP + JWT (session) |
| Signing   | Ed25519 keypair     |
| CI/CD     | GitHub Actions + GoReleaser (multi-platform releases) |

---

## System Architecture

```
  ┌──────────────────────────┐   ┌──────────────┐   ┌──────────────────────────┐
  │     Frontend (Next.js)   │   │  CLI (Go)    │   │  agent-openapi (:8888)   │
  │  /  /upload  /workflow…  │   │  JWT session │   │  public API gateway      │
  └────────────┬─────────────┘   └──────┬───────┘   │  Ed25519 sig auth        │
               │ HTTP / SSE             │ JWT        └──────────┬───────────────┘
               │                        │                       │ X-Gateway-Key
               └────────────────────────┴───────────────────────┘
                                        │
┌───────────────────────────────────────▼─────────────────────┐
│                   Spring Boot Backend (:8081)                │
│                       (internal network)                     │
│                                                             │
│  AuthFilter (JWT / Gateway-Key)  ──►  AgentController      │
│                               │                             │
│                    ┌──────────▼──────────┐                  │
│                    │   RagAgentGraph      │                  │
│                    │  (LangGraph4j)       │                  │
│                    │                     │                  │
│                    │  START              │                  │
│                    │    └─► analyzeQuery │                  │
│                    │          ├─[RETRIEVE]─► retrieve       │
│                    │          │               ├─[found]──►  │
│                    │          │               └─[empty]──►  │
│                    │          ├─[DIRECT]──► generate ──►END │
│                    │          └─[FALLBACK]─► fallback ──►END│
│                    └─────────────────────┘                  │
│                                                             │
│  ┌──────────────────┐   ┌──────────────┐  ┌─────────────┐  │
│  │ DocumentIngestion│   │  Retrieval   │  │  Fallback   │  │
│  │ Service (Tika +  │   │  Service     │  │  Service    │  │
│  │  Jsoup)          │   │  (Weaviate)  │  │ (Resilience4j)│ │
│  └────────┬─────────┘   └──────┬───────┘  └─────────────┘  │
│           │                    │                             │
│  ┌────────▼────────────────────▼──────┐                     │
│  │        Spring AI Abstraction        │                     │
│  │  EmbeddingModel  │  ChatModel       │                     │
│  └──────┬──────────────────┬──────────┘                     │
│         │                  │                                 │
│   ┌─────▼────┐      ┌──────▼──────┐                         │
│   │ Weaviate │      │ OpenAI /    │                         │
│   │ Vector   │      │ Anthropic / │                         │
│   │ Store    │      │ OpenRouter  │                         │
│   └──────────┘      └─────────────┘                         │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │
│  │ Auth Module  │  │  MCP Server  │  │  Workflow Engine   │ │
│  │ OTP + JWT    │  │  (SSE)       │  │  + Sandbox Service │ │
│  └──────────────┘  └──────────────┘  └────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
          │                    │                   │
  ┌───────▼──────┐   ┌────────▼────────┐  ┌───────▼───────┐
  │  Weaviate    │   │     MySQL       │  │  Go Scheduler │
  │  (vectors)   │   │  (auth, convos, │  │   (:8082)     │
  │              │   │   workflows,    │  │  cron jobs    │
  └──────────────┘   │   skills)       │  └───────────────┘
                     └─────────────────┘
```
│                   Spring Boot Backend (:8081)                │
│                                                             │
│  AuthFilter (JWT)  ──►  AgentController                    │
│                               │                             │
│                    ┌──────────▼──────────┐                  │
│                    │   RagAgentGraph      │                  │
│                    │  (LangGraph4j)       │                  │
│                    │                     │                  │
│                    │  START              │                  │
│                    │    └─► analyzeQuery │                  │
│                    │          ├─[RETRIEVE]─► retrieve       │
│                    │          │               ├─[found]──►  │
│                    │          │               └─[empty]──►  │
│                    │          ├─[DIRECT]──► generate ──►END │
│                    │          └─[FALLBACK]─► fallback ──►END│
│                    └─────────────────────┘                  │
│                                                             │
│  ┌──────────────────┐   ┌──────────────┐  ┌─────────────┐  │
│  │ DocumentIngestion│   │  Retrieval   │  │  Fallback   │  │
│  │ Service (Tika +  │   │  Service     │  │  Service    │  │
│  │  Jsoup)          │   │  (Weaviate)  │  │ (Resilience4j)│ │
│  └────────┬─────────┘   └──────┬───────┘  └─────────────┘  │
│           │                    │                             │
│  ┌────────▼────────────────────▼──────┐                     │
│  │        Spring AI Abstraction        │                     │
│  │  EmbeddingModel  │  ChatModel       │                     │
│  └──────┬──────────────────┬──────────┘                     │
│         │                  │                                 │
│   ┌─────▼────┐      ┌──────▼──────┐                         │
│   │ Weaviate │      │ OpenAI /    │                         │
│   │ Vector   │      │ Anthropic / │                         │
│   │ Store    │      │ OpenRouter  │                         │
│   └──────────┘      └─────────────┘                         │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │
│  │ Auth Module  │  │  MCP Server  │  │  Workflow Engine   │ │
│  │ OTP + JWT    │  │  (SSE)       │  │  + Sandbox Service │ │
│  └──────────────┘  └──────────────┘  └────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
          │                    │                   │
  ┌───────▼──────┐   ┌────────▼────────┐  ┌───────▼───────┐
  │  Weaviate    │   │     MySQL       │  │  Go Scheduler │
  │  (vectors)   │   │  (auth, convos, │  │   (:8082)     │
  │              │   │   workflows,    │  │  cron jobs    │
  └──────────────┘   │   skills)       │  └───────────────┘
                     └─────────────────┘
```

### Agent Graph Routing

The LangGraph4j graph determines the execution path per query:

| Route                | Condition                          | Path                                        |
| -------------------- | ---------------------------------- | ------------------------------------------- |
| `RETRIEVE`         | Query needs knowledge base context | analyzeQuery → retrieve → generate → END |
| `RETRIEVE` (empty) | No matching documents found        | analyzeQuery → retrieve → fallback → END |
| `DIRECT`           | Query answerable without retrieval | analyzeQuery → generate → END             |
| `FALLBACK`         | Query out of scope / unsafe        | analyzeQuery → fallback → END             |

### Workflow Engine

Workflows compose multiple AI agents into pipelines with two patterns:

| Pattern        | Description                                               |
| -------------- | --------------------------------------------------------- |
| `ORCHESTRATOR` | One orchestrator agent routes tasks to specialist agents  |
| `TEAM`         | Multiple agents run in `PARALLEL` or `SEQUENTIAL` order  |

Each workflow run executes inside an ephemeral Docker sandbox (`SandboxService`) with CPU/memory resource limits and a watchdog that terminates runaway containers. Agents within a workflow can load user-uploaded **Skills** (code files) to extend their capabilities.

### Scheduler

A lightweight Go microservice (`:8082`) manages cron-scheduled workflow runs. It persists schedules in MySQL, loads them on startup, and triggers the Spring Boot backend on each tick.

### CLI

Install on macOS or Linux with a single command:

```sh
curl -fsSL https://raw.githubusercontent.com/David200308/rag-agent-system/main/cli/install.sh | sh
```

To install a specific version or choose a custom directory:

```sh
VERSION=v1.2.0 curl -fsSL https://raw.githubusercontent.com/David200308/rag-agent-system/main/cli/install.sh | sh
INSTALL_DIR=~/.local/bin curl -fsSL https://raw.githubusercontent.com/David200308/rag-agent-system/main/cli/install.sh | sh
```

**Quick reference:**

```sh
agent-system login <email>          # request OTP
agent-system verify otp <code>      # verify OTP → saves session
agent-system status                 # check login status

agent-system chat ask "your query"  # query the RAG agent
agent-system chat list              # list conversations
agent-system chat ask "..." -c <id> # continue a conversation

agent-system workflow list          # list workflows
agent-system workflow run <id> "task description"

agent-system config                   # show current config
agent-system config --url <url>       # set gateway URL (default :8888)
agent-system config --key-id <id>     # set signing key ID (must match keys.json)
agent-system keygen                         # generate Ed25519 keypair for agent-openapi
agent-system uninstall                      # remove all local CLI data
```

### API Gateway (agent-openapi)

`agent-openapi` (`:8888`) is a public-facing Go gateway that forwards requests to the internal Spring Boot backend using Ed25519 asymmetric signatures.

**Exposed routes:**

| Method | Path               | Backend target            |
| ------ | ------------------ | ------------------------- |
| `POST` | `/v1/agent/query`  | `/api/v1/agent/query`     |
| `GET`  | `/v1/workflow`     | `/api/v1/workflow`        |
| `GET`  | `/health`          | — (local health check)    |

**Signature scheme (per request):**

1. Client constructs the signed string: `METHOD\nPATH\nTIMESTAMP\nhex(sha256(body))`
2. Client signs it with their Ed25519 private key and sends three headers:
   - `X-Key-ID` — identifies the key in `keys.json`
   - `X-Timestamp` — Unix seconds (must be within ±300 s of gateway clock)
   - `X-Signature` — base64-encoded Ed25519 signature
3. Gateway verifies signature, then forwards the request to the backend with `X-Gateway-Key` (static shared secret) and `X-Key-Owner` (email from `keys.json`)

**Key management (`keys.json`):**

```json
{
  "keys": [
    { "id": "mykey", "public_key": "<base64-ed25519-public-key>", "email": "user@example.com" }
  ]
}
```

Generate a keypair with the CLI: `agent-system keygen`. Then register the public key in `keys.json` and set the key ID: `agent-system config --key-id <id>`. The file is hot-reloaded on every request — no gateway restart needed when adding keys.

All CLI traffic goes through agent-openapi. Auth endpoints (OTP) are public pass-throughs; all other endpoints require Ed25519 signature. There is no separate backend URL in the CLI config.

**Startup:**

```sh
BACKEND_URL=http://internal-host:8081 \
INTERNAL_KEY=$(openssl rand -hex 32) \
go run ./agent-openapi
```

Set the same hex string as `AUTH_GATEWAY_KEY` on the Spring Boot backend.

---

### Auth Flow

**End-user (JWT):**

1. User submits email → backend checks whitelist → sends OTP via Resend
2. User submits OTP → backend validates → issues signed JWT
3. All subsequent API calls carry the JWT; `AuthFilter` validates on every request

**API gateway (X-Gateway-Key):**

1. `agent-openapi` forwards each request with `X-Gateway-Key: <shared-secret>` and `X-Key-Owner: <email>`
2. `AuthFilter` checks the header before JWT; if the key matches `AUTH_GATEWAY_KEY`, the owner email is trusted as the authenticated principal
3. Invalid or missing key falls through to normal JWT validation
