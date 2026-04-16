# JavaConnect KE — Spring AI Workshop

A hands-on workshop introducing [Spring AI](https://docs.spring.io/spring-ai/reference/) through three independent lab projects. Each lab is a self-contained Spring Boot application that you can run and extend on your own machine.

## What You'll Build

1. **Lab 1** — A conversational chatbot backed by a local LLM (Ollama), with multi-turn memory
2. **Lab 2** — A RAG-powered Q&A app that answers questions about your own documents
3. **Lab 3** — An AI agent that calls real tools, exposes them over MCP, and reasons with Embabel

## Quick Start

```bash
# 1. Start infrastructure (Ollama + Qdrant)
cd infra && docker compose up -d

# 2. Pull models
docker compose exec ollama ollama pull llama3.2
docker compose exec ollama ollama pull nomic-embed-text

# 3. Run a lab
cd ../labs/lab1-chatbot
./mvnw spring-boot:run
```

## Prerequisites

| Tool | Minimum Version | Check |
|---|---|---|
| Java | 25 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker & Compose | 27+ / v2 | `docker compose version` |
| (Optional) NVIDIA GPU | CUDA 12+ | `nvidia-smi` |

> **No GPU? No problem.** Ollama runs on CPU — responses will be slower but fully functional on a laptop with 16 GB RAM.

## Repository Structure

```
javaconnectke-springai-workshop/
├── infra/                     # Docker Compose for local backing services
│   ├── docker-compose.yml
│   └── README.md
└── labs/
    ├── lab1-chatbot/          # ChatClient, Advisors, Chat Memory (Ollama)
    ├── lab2-rag/              # RAG, ETL pipeline, VectorStore (Qdrant)
    └── lab3-tools-mcp/        # Tool Calling, MCP, Embabel intro
```

See [`infra/README.md`](infra/INFRA.md) for detailed setup instructions.

---

## Lab 1 — ChatClient & Chat Memory with Ollama

**Theme: Build your first AI chatbot, talk to it locally.**

### What you will learn
- The Spring AI `ChatClient` API and its fluent builder
- Stateless vs. stateful conversations
- `MessageChatMemoryAdvisor` for multi-turn memory
- Running a local LLM with **Ollama** — no API keys, no cloud costs

### Running the Lab

```bash
cd labs/lab1-chatbot
./mvnw spring-boot:run
```

### Key Concepts

| Concept | Description |
|---|---|
| `ChatClient` | Central Spring AI interface for sending messages and streaming responses |
| `ChatClient.Builder` | Auto-configured bean for building a `ChatClient` with defaults |
| `Advisor` | Interceptor-style component that wraps `ChatClient` calls |
| `MessageChatMemoryAdvisor` | Advisor that injects conversation history into each request |
| `InMemoryChatMemory` | Simple in-memory store for conversation turns |

### Demo Walkthrough
1. A bare `ChatClient` call — single question, no memory
2. Add `MessageChatMemoryAdvisor` — the model now remembers the conversation
3. Swap the Ollama model via `application.properties` without touching Java code

---

## Lab 2 — Retrieval-Augmented Generation (RAG)

**Theme: Give your AI knowledge it was never trained on.**

### What you will learn
- What RAG is and why it matters
- The ETL (Extract → Transform → Load) pipeline in Spring AI
- `DocumentReader`, `TextSplitter`, and `VectorStore`
- `QuestionAnswerAdvisor` for context-aware responses
- Persisting vectors in **Qdrant**

### Running the Lab

```bash
cd labs/lab2-rag
./mvnw spring-boot:run
```

### RAG Architecture

```
 Your Documents
      │
      ▼
 DocumentReader          (PDF, text, web pages, …)
      │
      ▼
 TextSplitter            (chunk into overlapping segments)
      │
      ▼
 EmbeddingModel          (convert chunks to vectors)
      │
      ▼
 VectorStore             (store + index vectors in Qdrant)
      │
      ▼
 QuestionAnswerAdvisor   (retrieve relevant chunks at query time)
      │
      ▼
 ChatClient              (augmented prompt → LLM → answer)
```

### Key Concepts

| Concept | Description |
|---|---|
| `DocumentReader` | Loads raw content (PDF, Markdown, web, etc.) into `Document` objects |
| `TextSplitter` | Splits large documents into smaller, overlapping chunks |
| `EmbeddingModel` | Converts text chunks into high-dimensional vector representations |
| `VectorStore` | Stores and retrieves vectors by semantic similarity |
| `QuestionAnswerAdvisor` | Advisor that performs similarity search and stuffs context into the prompt |

### Demo Walkthrough
1. Load a PDF / text file with `DocumentReader`
2. Chunk and embed it into Qdrant via `VectorStore`
3. Ask questions about the document and observe grounded answers
4. Compare answers with and without RAG context

---

## Lab 3 — Tool Calling, MCP & Embabel

**Theme: Let your AI take actions in the real world.**

### What you will learn
- Defining `@Tool`-annotated methods that the LLM can invoke
- How Spring AI serializes tool schemas and parses LLM-requested calls
- An introduction to the **Model Context Protocol (MCP)** and why it standardises tool ecosystems
- A brief look at **Embabel** — an agent framework built on Spring AI

### Running the Lab

```bash
cd labs/lab3-tools-mcp
./mvnw spring-boot:run
```

### Key Concepts

| Concept | Description |
|---|---|
| `@Tool` | Marks a method as a callable tool; Spring AI generates the JSON schema automatically |
| `ToolCallback` | Programmatic alternative to `@Tool` for dynamic tool registration |
| Tool execution loop | The back-and-forth cycle between the LLM deciding to call a tool and receiving its result |
| MCP (Model Context Protocol) | Open protocol for exposing tools, resources, and prompts to any MCP-compatible model |
| `SseServerTransport` | Spring AI MCP server transport over Server-Sent Events |
| Embabel | Agent framework layered on Spring AI — goals, plans, and multi-step reasoning |

### Demo Walkthrough
1. A `ChatClient` with a simple `@Tool` (e.g. current date/time, web search)
2. Observe the tool-call round-trip in logs
3. Expose the same tool as an MCP server and connect an MCP client
4. Brief Embabel demo — define a goal and let the agent plan its own tool calls

---

## The Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.x |
| AI Layer | Spring AI |
| Agents | Embabel |
| LLM | Ollama + Llama 3.2 |
| Embeddings | nomic-embed-text |
| Vector DB | Qdrant |

## Workshop Format

| Lab | Theory | Demo |
|---|---|---|
| Lab 1 — ChatClient & Memory | Presenter | Attendees follow along |
| Lab 2 — RAG | Attendees follow along | Presenter |
| Lab 3 — Tool Calling & MCP | Mix & match | Mix & match |

## Useful Links

| Resource | URL |
|---|---|
| Spring AI Reference Docs | https://docs.spring.io/spring-ai/reference/ |
| Spring AI GitHub | https://github.com/spring-projects/spring-ai |
| Ollama | https://ollama.com |
| Model Context Protocol | https://modelcontextprotocol.io |
| Embabel Agent Framework | https://github.com/embabel/embabel-agent |
| Qdrant | https://qdrant.tech |
| Spring Initializr | https://start.spring.io |

---

*Built for the JavaConnect KE community.*
