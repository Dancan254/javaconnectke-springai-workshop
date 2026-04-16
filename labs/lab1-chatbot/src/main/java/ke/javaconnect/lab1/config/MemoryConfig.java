package ke.javaconnect.lab1.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Chat Memory — Configuration &amp; Options Reference.
 *
 * <p>This class is intentionally verbose. It is written to answer every
 * question the workshop audience is likely to ask about memory configuration.
 *
 * <p>Spring AI's memory system is split into <b>three independent layers</b>
 * so that each concern can be swapped without touching the others:
 *
 * <pre>
 * ┌────────────────────────────────────────────────────────────────────────┐
 * │  LAYER 1 — ChatMemoryRepository  (WHERE messages are stored)           │
 * │                                                                        │
 * │  Controls the physical storage backend.                                │
 * │                                                                        │
 * │  ● InMemoryChatMemoryRepository  — ConcurrentHashMap inside the JVM   │
 * │      ✔ Zero config, zero dependencies                                  │
 * │      ✘ Lost on restart                                                 │
 * │                                                                        │
 * │  ● JdbcChatMemoryRepository      — any JDBC-compatible relational DB   │
 * │      ✔ Survives restarts, shareable across instances                   │
 * │      ✔ Ships DDL scripts for 8 databases:                              │
 * │          PostgreSQL, MySQL, MariaDB, H2, HSQLDB,                       │
 * │          Oracle, SQL Server, SQLite                                    │
 * │      ✘ Requires a DataSource bean + the JDBC starter dependency        │
 * └───────────────────────────────┬────────────────────────────────────────┘
 *                                 │ feeds into
 * ┌───────────────────────────────▼────────────────────────────────────────┐
 * │  LAYER 2 — ChatMemory  (HOW MANY messages to keep)                     │
 * │                                                                        │
 * │  Controls the eviction / windowing policy on top of the repository.    │
 * │                                                                        │
 * │  ● MessageWindowChatMemory  — sliding window of the last N messages    │
 * │      ✔ Prevents the context window from overflowing                    │
 * │      ✔ Configurable via maxMessages (default: 20)                      │
 * │      ✘ Simple FIFO eviction — no semantic summarisation                │
 * └───────────────────────────────┬────────────────────────────────────────┘
 *                                 │ used by
 * ┌───────────────────────────────▼────────────────────────────────────────┐
 * │  LAYER 3 — ChatMemoryAdvisor  (HOW history is injected into the prompt)│
 * │                                                                        │
 * │  ● MessageChatMemoryAdvisor  — injects history as Message objects      │
 * │      ✔ Native multi-turn support (USER / ASSISTANT pairs in messages)  │
 * │      ✔ Models that understand roles handle this best                   │
 * │      → Recommended for most use cases                                  │
 * │                                                                        │
 * │  ● PromptChatMemoryAdvisor   — serialises history into the system text │
 * │      ✔ Works with models that do not handle multi-turn natively        │
 * │      ✔ Fully customisable via a PromptTemplate                         │
 * │      → Good for instruct-only models or custom history formatting      │
 * └────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Conversation isolation</h2>
 * <p>Every conversation is identified by a {@code conversationId} string.
 * Spring AI stores messages per-conversation so multiple users, sessions, or
 * threads never see each other's history. You can pass a per-request ID at
 * call time via:
 * <pre>
 *   chatClient.prompt()
 *       .user(message)
 *       .advisors(spec -&gt; spec.param(ChatMemory.CONVERSATION_ID, conversationId))
 *       .call()
 *       .content();
 * </pre>
 *
 * @see org.springframework.ai.chat.memory.ChatMemoryRepository
 * @see org.springframework.ai.chat.memory.ChatMemory
 * @see org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
 * @see org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor
 */
@Configuration
public class MemoryConfig {

    /**
     * The sliding-window size injected from {@code application.yml}.
     *
     * <p>Changing this value in config is enough — no recompile needed.
     * Try setting it to {@code 4} during the demo and observe that the model
     * forgets earlier parts of the conversation after two exchanges.
     */
    @Value("${app.chat.memory.max-messages:10}")
    private int maxMessages;

    // =========================================================================
    // LAYER 1 — Storage Backend
    // =========================================================================

    /**
     * Provides the storage backend for all conversations.
     *
     * <p><b>Active:</b> {@link InMemoryChatMemoryRepository} — stores messages in
     * a {@code ConcurrentHashMap} inside the JVM.  Fastest option with no external
     * dependencies, but all history is lost when the application stops.
     *
     * <p><b>To switch to JDBC persistence</b> (messages survive restarts):
     * <ol>
     *   <li>Uncomment the JDBC dependency in {@code pom.xml}.</li>
     *   <li>Add a {@code datasource} block in {@code application.yml}.</li>
     *   <li>Replace the body of this method with the commented block below.</li>
     * </ol>
     *
     * @return the repository bean that {@link MessageWindowChatMemory} delegates to
     */
    @Bean
    public ChatMemoryRepository chatMemoryRepository() {

        // ── OPTION A (active): In-memory ──────────────────────────────────
        // Zero config. Perfect for demos and development.
        return new InMemoryChatMemoryRepository();

        // ── OPTION B: JDBC — H2 file (persists across restarts, single node) ──
        // Requires: spring-ai-starter-model-chat-memory-repository-jdbc + H2 driver
        // Spring AI auto-creates the schema on startup (spring.ai.chat.memory.jdbc.initialize-schema=true)
        //
        // return JdbcChatMemoryRepository.builder()
        //         .jdbcTemplate(jdbcTemplate)   // inject JdbcTemplate
        //         .build();

        // ── OPTION C: JDBC — PostgreSQL (production-ready) ────────────────
        // Same dependency as Option B, just swap the DataSource to a PG pool.
        //
        // return JdbcChatMemoryRepository.builder()
        //         .jdbcTemplate(jdbcTemplate)
        //         .build();
    }

    // =========================================================================
    // LAYER 2 — Windowing / Eviction Policy
    // =========================================================================

    /**
     * Wraps the repository with a sliding-message-window policy.
     *
     * <p>{@link MessageWindowChatMemory} keeps the {@code maxMessages} most
     * recent messages (USER + ASSISTANT combined) per conversation.  When the
     * window is full, the oldest messages are evicted before the next request
     * goes to the model.
     *
     * <p><b>Why does window size matter?</b><br>
     * Every message in the window is sent to the LLM on every request, consuming
     * context-window tokens and adding latency.  A larger window means better
     * long-term memory but higher cost and slower responses.
     *
     * <pre>
     *   Window = 4  →  model "forgets" after ~2 exchanges
     *   Window = 10 →  model remembers a medium-length conversation  (default)
     *   Window = 40 →  model remembers a long session (watch token costs!)
     * </pre>
     *
     * @param repository the storage backend produced by {@link #chatMemoryRepository()}
     * @return the {@code ChatMemory} bean injected into chat advisors
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    /*
     * =========================================================================
     * LAYER 3 — Advisor Reference (not beans — created per ChatClient)
     * =========================================================================
     *
     * Advisors wrap the ChatClient call, injecting memory before the request
     * and persisting the new messages after the response.
     * They are wired into the ChatClient in ChatController, not here.
     *
     * ── OPTION A: MessageChatMemoryAdvisor (recommended) ─────────────────────
     *
     *   Injects history as typed Message objects (UserMessage / AssistantMessage).
     *   The model receives a proper multi-turn messages array:
     *
     *     [ SystemMessage("You are…"),
     *       UserMessage("Hi, I'm Dan"),
     *       AssistantMessage("Hello Dan!"),
     *       UserMessage("What's my name?")   ← current turn
     *     ]
     *
     *   Usage:
     *     MessageChatMemoryAdvisor.builder(chatMemory)
     *         .conversationId("default")     // optional default ID
     *         .order(Ordered.LOWEST_PRECEDENCE) // position in advisor chain
     *         .build();
     *
     * ── OPTION B: PromptChatMemoryAdvisor ────────────────────────────────────
     *
     *   Serialises history as plain text appended to the system prompt:
     *
     *     "Use the following conversation history to answer:
     *      Human: Hi, I'm Dan
     *      Assistant: Hello Dan!
     *      Human: What's my name?"   ← current turn appended inline
     *
     *   Best for: instruction-tuned models without native multi-turn support,
     *             or when you need full control over how history is formatted.
     *
     *   Usage:
     *     PromptChatMemoryAdvisor.builder(chatMemory)
     *         .conversationId("default")
     *         .systemPromptTemplate(new PromptTemplate("...{history}...{input}"))
     *         .build();
     *
     * ── OPTION C: SimpleLoggerAdvisor (debugging) ────────────────────────────
     *
     *   NOT a memory advisor — it logs the full outgoing prompt and the raw
     *   response.  Add it to any ChatClient to inspect exactly what is sent to
     *   the model, including the injected memory context.
     *
     *   Usage (add alongside your memory advisor):
     *     new SimpleLoggerAdvisor()
     *
     *   Required log level in application.yml:
     *     logging:
     *       level:
     *         org.springframework.ai.chat.client.advisor: DEBUG
     *
     * ── Advisor ordering ─────────────────────────────────────────────────────
     *
     *   When multiple advisors are stacked, they execute in ascending order value
     *   (lower = earlier).  A typical chain looks like:
     *
     *     SimpleLoggerAdvisor (order = 0)         ← logs raw request
     *         → MessageChatMemoryAdvisor (order = 1) ← injects history
     *             → [ChatModel call]
     *         ← MessageChatMemoryAdvisor             ← saves response
     *     ← SimpleLoggerAdvisor                   ← logs enriched response
     */
}
