# Lab 1 — ChatClient & Chat Memory

A Spring AI chatbot backed by **Groq** that demonstrates three progressively richer interaction patterns: stateless chat, stateful multi-turn chat with memory, and real-time streaming.

---

## Stack

| Component | Version |
|-----------|---------|
| Spring Boot | 4.0.5 |
| Spring AI | 2.0.0-M4 |
| LLM Provider | Groq (`llama-3.1-8b-instant`) |
| Java | 25 |

---

## Prerequisites

1. A [Groq API key](https://console.groq.com)
2. Java 25
3. Maven wrapper (`./mvnw`) — no local Maven install needed

---

## Setup & Run

```bash
export GROQ_API_KEY=your_key_here
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`.

---

## Endpoints

### Chat

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/chat/simple` | Stateless — each request is independent |
| `GET` | `/chat/memory` | Stateful — model remembers previous turns |
| `GET` | `/chat/stream` | Stateful + streaming (Server-Sent Events) |

**Parameters**

| Param | Endpoints | Default | Description |
|-------|-----------|---------|-------------|
| `message` | all | required | The user's message (max 2000 chars) |
| `conversationId` | `/memory`, `/stream` | `"default"` | Unique key per session or user |

**Example requests**

```bash
# Stateless
curl "http://localhost:8080/chat/simple?message=What+is+Spring+AI"

# Stateful — run both and the model remembers the name
curl "http://localhost:8080/chat/memory?message=My+name+is+Dan&conversationId=demo"
curl "http://localhost:8080/chat/memory?message=What+is+my+name&conversationId=demo"

# Streaming
curl -N "http://localhost:8080/chat/stream?message=Tell+me+a+short+story&conversationId=s1"
```

**Response shape (`/simple` and `/memory`)**

```json
{
  "conversationId": "demo",
  "reply": "Hello Dan! How can I help you today?",
  "model": "llama-3.1-8b-instant",
  "timestamp": "2026-05-08T17:00:00.000Z"
}
```

### Conversation Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/conversations` | List all active conversation IDs |
| `GET` | `/conversations/{id}` | Full message history for one conversation |
| `DELETE` | `/conversations/{id}` | Clear one conversation |
| `DELETE` | `/conversations` | Clear all conversations |

```bash
curl http://localhost:8080/conversations
curl http://localhost:8080/conversations/demo
curl -X DELETE http://localhost:8080/conversations/demo
```

---

## Chat Memory

Spring AI's memory system is composed of three independent layers. Each layer can be swapped without touching the others.

```
ChatMemoryRepository  →  ChatMemory  →  ChatMemoryAdvisor
  (where to store)       (how many)      (how to inject)
```

### Layer 1 — Storage Backend (`ChatMemoryRepository`)

Controls where messages are persisted.

| Implementation | Dependency | Notes |
|---|---|---|
| `InMemoryChatMemoryRepository` | none | Default. Zero config, lost on restart |
| `JdbcChatMemoryRepository` | `spring-ai-starter-model-chat-memory-repository-jdbc` + a JDBC driver | Survives restarts, shareable across instances. Spring AI ships DDL scripts for H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, SQLite, and HSQLDB |

To switch to JDBC, uncomment the dependency in `pom.xml`, add a `spring.datasource` block in `application.yml`, and replace the bean in `MemoryConfig`:

```java
@Bean
public ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
    return JdbcChatMemoryRepository.builder()
            .jdbcTemplate(jdbcTemplate)
            .build();
}
```

### Layer 2 — Eviction Policy (`ChatMemory`)

Controls how many messages are kept per conversation.

| Implementation | Behaviour |
|---|---|
| `MessageWindowChatMemory` | Sliding window — keeps the last N messages (USER + ASSISTANT). Oldest messages are evicted when the window is full. |

Window size is configurable without recompiling:

```yaml
app:
  chat:
    memory:
      max-messages: 10  # increase for longer context, watch token costs
```

### Layer 3 — Injection Strategy (`ChatMemoryAdvisor`)

Controls how history is injected into each outgoing prompt.

| Implementation | How it works | Best for |
|---|---|---|
| `MessageChatMemoryAdvisor` | Injects history as typed `UserMessage` / `AssistantMessage` objects in the messages array | Models with native multi-turn support (recommended) |
| `PromptChatMemoryAdvisor` | Serialises history as plain text appended to the system prompt | Instruction-tuned models, or when full control over history formatting is needed |

Switch advisors in `ChatController` with no other changes required:

```java
// MessageChatMemoryAdvisor (current)
.advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())

// PromptChatMemoryAdvisor (alternative)
.advisors(PromptChatMemoryAdvisor.builder(chatMemory).build())
```

### Conversation Isolation

Every `conversationId` is an independent session. Multiple users or browser tabs can chat simultaneously with no shared history — pass a unique ID per session.

---

## Configuration Reference

```yaml
spring:
  ai:
    openai:
      api-key: ${GROQ_API_KEY}
      base-url: https://api.groq.com/openai   # Groq's OpenAI-compatible endpoint
      chat:
        options:
          model: llama-3.1-8b-instant
          temperature: 0.7
          max-tokens: 1024

app:
  chat:
    memory:
      max-messages: 10
```

**Available Groq models** (as of May 2026): `llama-3.1-8b-instant`, `llama3-70b-8192`, `llama3-8b-8192`, `mixtral-8x7b-32768`, `gemma2-9b-it`

---

## Observability

Actuator endpoints are exposed at `/actuator`:

| Endpoint | URL |
|----------|-----|
| Health | `/actuator/health` |
| Prometheus metrics | `/actuator/prometheus` |
| App info | `/actuator/info` |

Distributed tracing (OpenTelemetry) exports spans to Tempo at `http://localhost:4318/v1/traces`. Every `ChatClient` call produces a `gen_ai.*` span including the full prompt and completion (when enabled).

To inspect the exact prompt sent to Groq on each request, enable advisor debug logging:

```yaml
logging:
  level:
    org.springframework.ai.chat.client.advisor: DEBUG
```
