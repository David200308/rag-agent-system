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

---

### Auth Flow

**End-user (JWT):**

1. User submits email → backend checks whitelist → sends OTP via Resend
2. User submits OTP → backend validates → issues signed JWT
3. All subsequent API calls carry the JWT; `AuthFilter` validates on every request

