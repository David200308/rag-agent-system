# RAG Agent System

| Chat Mode                | Workflow Mode                    |
| ------------------------ | -------------------------------- |
| ![chat](./images/chat.png) | ![workflow](./images/workflow.png) |

## Tech Stack

### Backend

| Layer            | Technology                                         |
| ---------------- | -------------------------------------------------- |
| Runtime          | Java 21 (virtual threads)                          |
| Framework        | Spring Boot 3.4.5                                  |
| AI orchestration | Spring AI 1.1                                      |
| Agent graph      | LangGraph4j 1.7                                    |
| LLM providers    | OpenAI / OpenRouter / Anthropic / DeepSeek / Local |
| Vector store     | Weaviate                                           |
| Embeddings       | Spring AI embedding abstraction                    |
| Document parsing | Apache Tika (PDF, text, HTML)                      |
| HTML scraping    | Jsoup                                              |
| Circuit breaker  | Resilience4j 2.2                                   |
| Auth             | OTP email (Resend) + JJWT stateless tokens         |
| Persistence      | MySQL 8 + Spring Data JPA                          |
| MCP server       | Spring AI MCP WebMVC SSE transport                 |
| API docs         | SpringDoc OpenAPI (Swagger UI)                     |

### Scheduler

| Layer            | Technology                             |
| ---------------- | -------------------------------------- |
| Runtime          | Go 1.24                                |
| Task queue       | Asynq (Redis-backed)                   |
| Schedule storage | MySQL (shared with app DB)             |
| Retry policy     | MaxRetry=3, task timeout=5 min (Asynq) |

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
| Relational DB    | MySQL (Docker) — app + schedule data       |
| Task queue       | Redis 7 (Docker) — Asynq backend           |
| Scheduler        | Go microservice backed by Asynq (`:8082`) |
| Containerization | Docker Compose                              |

---

## System Architecture

```
  ┌──────────────────────────┐
  │     Frontend (Next.js)   │
  │  /  /upload  /workflow…  │
  └────────────┬─────────────┘
               │ HTTP / SSE
               │
┌──────────────▼───────────────────────────────────────────────┐
│                   Spring Boot Backend (:8081)                 │
│                                                              │
│  AuthFilter (JWT)  ──►  AgentController                     │
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
│  │ OTP + JWT    │  │  (SSE)       │  │  + Sandbox +       │ │
│  └──────────────┘  └──────────────┘  │  SCHEDULE tool     │ │
│                                      └────────────────────┘ │
│  ┌──────────────┐  ┌──────────────┐                         │
│  │  Connectors  │  │    Model     │                         │
│  │  (Google /   │  │  Selection   │                         │
│  │   Telegram)  │  │  (per-user)  │                         │
│  └──────────────┘  └──────────────┘                         │
└─────────────────────────────────────────────────────────────┘
          │                    │                   │
  ┌───────▼──────┐   ┌────────▼────────┐  ┌───────▼─────────────────┐
  │  Weaviate    │   │     MySQL       │  │  Go Scheduler (:8082)   │
  │  (vectors)   │   │  (auth, convos, │  │                         │
  │              │   │   workflows,    │  │  Asynq Scheduler        │
  └──────────────┘   │   skills,       │  │    └─ enqueues tasks    │
                     │   schedules,    │  │  Asynq Worker           │
                     │   model_configs,│  │    └─ rag:trigger       │
                     │   connectors)   │  │         (MaxRetry=3)    │
                     └─────────────────┘  │                         │
                                          │  ┌─────────────────┐    │
                                          │  │  Redis (:6379)  │    │
                                          │  │  (task queue)   │    │
                                          │  └─────────────────┘    │
                                          └─────────────────────────┘
```

---

## Agent Graph Routing

The LangGraph4j graph determines the execution path per query:

| Route                | Condition                          | Path                                        |
| -------------------- | ---------------------------------- | ------------------------------------------- |
| `RETRIEVE`         | Query needs knowledge base context | analyzeQuery → retrieve → generate → END |
| `RETRIEVE` (empty) | No matching documents found        | analyzeQuery → retrieve → fallback → END |
| `DIRECT`           | Query answerable without retrieval | analyzeQuery → generate → END             |
| `FALLBACK`         | Query out of scope / unsafe        | analyzeQuery → fallback → END             |

---

## Workflow Engine

Workflows compose multiple AI agents into pipelines with two patterns:

| Pattern          | Description                                                 |
| ---------------- | ----------------------------------------------------------- |
| `ORCHESTRATOR` | One orchestrator agent routes tasks to specialist agents    |
| `TEAM`         | Multiple agents run in `PARALLEL` or `SEQUENTIAL` order |

Each workflow run executes inside an ephemeral Docker sandbox (`SandboxService`) with CPU/memory resource limits and a watchdog that terminates runaway containers. Agents can load user-uploaded **Skills** (code files) to extend their capabilities.

### Available Tools

| Tool              | Description                                                        |
| ----------------- | ------------------------------------------------------------------ |
| `BASH`          | Execute shell commands in the sandbox                              |
| `CURL`          | HTTP requests from the sandbox                                     |
| `GIT`           | Git operations in the sandbox                                      |
| `GREP`          | Text search in the sandbox                                         |
| `PYTHON`        | Run Python scripts in the sandbox                                  |
| `NODE`          | Run Node.js scripts in the sandbox                                 |
| `SCHEDULE`      | Create, list, or delete scheduled RAG queries (calls Go scheduler) |
| `GOOGLE_DOCS`   | Read and write Google Docs via connected OAuth token               |
| `GOOGLE_SHEETS` | Read and write Google Sheets via connected OAuth token             |
| `GOOGLE_SLIDES` | Read and write Google Slides via connected OAuth token             |
| `TELEGRAM`      | Send messages to a connected Telegram chat                         |

The `SCHEDULE` tool lets a workflow agent manage schedules on behalf of the user:

```xml
<!-- Create a scheduled query -->
<use_tool name="SCHEDULE">
{"action":"create","conversationId":"<id>","message":"Daily market summary","cron":"0 9 * * 1-5","timezone":"America/New_York","topK":5,"useKnowledgeBase":true,"useWebFetch":true}
</use_tool>

<!-- List existing schedules -->
<use_tool name="SCHEDULE">
{"action":"list","conversationId":"<id>"}
</use_tool>

<!-- Delete a schedule -->
<use_tool name="SCHEDULE">
{"action":"delete","scheduleId":"<schedule-id>"}
</use_tool>
```

---

## Conversation Sharing

Conversations can be shared via a link with configurable access controls.

| Field          | Values                          | Description                                   |
| -------------- | ------------------------------- | --------------------------------------------- |
| `shareMode`  | `READ_ONLY` / `INTERACTIVE` | Viewer can read only, or also send messages   |
| `accessType` | `EVERYONE` / `WHITELIST`    | Public link or restricted to specified emails |

When `accessType` is `WHITELIST`, only listed emails may access the shared link. `INTERACTIVE` shares notify the conversation owner via Telegram (if connected) when a new participant joins.

---

## Scheduler

The Go microservice (`:8082`) uses **Asynq** (Redis-backed) for durable cron scheduling, with schedule state persisted in MySQL.

### How it works

```
Frontend / Workflow Agent
        │
        │  REST (JWT or service-key)
        ▼
Go Scheduler (:8082)
  ├── REST API  →  cronmgr (asynq.Scheduler)  →  Redis
  └── Asynq Worker (same process)
           └── rag:trigger handler
                 └── POST Spring Boot /api/v1/scheduler/trigger
                           (MaxRetry=3, Timeout=5 min)
```

On startup the scheduler reloads all active schedules from MySQL into the Asynq in-process cron engine. When a cron fires, Asynq enqueues a `rag:trigger` task to Redis. The worker picks it up and POSTs to Spring Boot. If the call fails, Asynq retries up to 3 times automatically. Each run is recorded in `schedule_runs` (MySQL).
