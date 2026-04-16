package ke.javaconnect.lab1.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Lab 1 — ChatClient &amp; Chat Memory: REST endpoints demo.
 *
 * <p>This controller exposes three endpoints that progressively build on each
 * other, walking the audience through the core Spring AI programming model:
 *
 * <pre>
 * ┌────────────────────────────────────────────────────────────────────────┐
 * │  STEP 1 — GET /chat/simple                                             │
 * │  A bare ChatClient call.  Stateless — no memory.                       │
 * │  Every request is independent; the model has no idea what you said     │
 * │  a moment ago.                                                         │
 * │                                                                        │
 * │  STEP 2 — GET /chat/memory                                             │
 * │  Adds MessageChatMemoryAdvisor with a conversationId.                  │
 * │  The model now remembers previous turns in the same conversation.      │
 * │  Try the same conversationId twice to see context carry over.          │
 * │                                                                        │
 * │  STEP 3 — GET /chat/stream                                             │
 * │  Same as Step 2 but tokens stream back as they are generated.          │
 * │  Returns a Server-Sent Events (text/event-stream) response.            │
 * └────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Quick test commands</h2>
 * <pre>
 *   # Step 1 — stateless
 *   curl "http://localhost:8080/chat/simple?message=What+is+Spring+AI"
 *
 *   # Step 2 — with memory (run both lines, notice the model remembers the name)
 *   curl "http://localhost:8080/chat/memory?message=My+name+is+Dan&amp;conversationId=demo"
 *   curl "http://localhost:8080/chat/memory?message=What+is+my+name&amp;conversationId=demo"
 *
 *   # Step 2 — new conversationId = fresh slate
 *   curl "http://localhost:8080/chat/memory?message=What+is+my+name&amp;conversationId=other"
 *
 *   # Step 3 — streaming (tokens arrive as they are generated)
 *   curl -N "http://localhost:8080/chat/stream?message=Tell+me+a+short+story&amp;conversationId=story1"
 * </pre>
 *
 * @see ke.javaconnect.lab1.config.MemoryConfig
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    /**
     * The base {@link ChatClient} used by all endpoints.
     *
     * <p>Built once at construction time with a default system prompt.
     * Individual endpoints layer additional behaviour (memory, streaming)
     * on top of this base client by adding advisors at call time.
     */
    private final ChatClient chatClient;

    /**
     * The {@link ChatMemory} bean defined in {@link ke.javaconnect.lab1.config.MemoryConfig}.
     *
     * <p>Injected here so that memory advisors can be constructed per-request,
     * letting each request pass its own {@code conversationId}.
     */
    private final ChatMemory chatMemory;

    /**
     * Constructs the controller and builds the shared {@link ChatClient}.
     *
     * <p>Spring Boot auto-configures a {@link ChatClient.Builder} bean that is
     * already wired to the Ollama model defined in {@code application.yml}.
     * We call {@code .build()} here — no model-specific code in our classes.
     *
     * <p>The {@code defaultSystem} sets a persona that applies to <em>all</em>
     * requests made through this client unless overridden at call time.
     *
     * @param builder    auto-configured by {@code spring-ai-starter-model-ollama}
     * @param chatMemory the windowed memory store from {@link ke.javaconnect.lab1.config.MemoryConfig}
     */
    public ChatController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatClient = builder
                // The system prompt shapes the assistant's identity and tone.
                // It is injected as the first message on every request.
                .defaultSystem("""
                        You are a helpful Java and Spring AI assistant for the
                        JavaConnect KE workshop. Keep answers concise and practical.
                        Use code examples when they add value.
                        """)
                // SimpleLoggerAdvisor logs every request/response at DEBUG level.
                // Invaluable for seeing exactly what is sent to the model,
                // including injected memory context.
                // Enable with: logging.level.org.springframework.ai.chat.client.advisor=DEBUG
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    // =========================================================================
    // STEP 1 — Stateless Chat
    // =========================================================================

    /**
     * Sends a single message to the model and returns its response.
     *
     * <p>This is the simplest possible Spring AI interaction:
     * <ol>
     *   <li>The user message is wrapped in a {@code UserMessage}.</li>
     *   <li>The system prompt is prepended automatically.</li>
     *   <li>The combined prompt is sent to Ollama.</li>
     *   <li>The response text is returned directly.</li>
     * </ol>
     *
     * <p>There is <b>no memory</b> here.  Send the same message twice and you
     * may get different answers.  Ask a follow-up question referencing the
     * previous response and the model will have no idea what you mean.
     *
     * <pre>
     *   curl "http://localhost:8080/chat/simple?message=What+is+Spring+AI"
     *   curl "http://localhost:8080/chat/simple?message=Tell+me+more"  ← no context!
     * </pre>
     *
     * @param message the user's input text
     * @return the model's plain-text response
     */
    @GetMapping("/simple")
    public String simpleChat(@RequestParam String message) {
        return chatClient
                .prompt()
                .user(message)
                .call()
                .content();
    }

    // =========================================================================
    // STEP 2 — Stateful Chat with Memory
    // =========================================================================

    /**
     * Sends a message and maintains conversation history per {@code conversationId}.
     *
     * <p>This endpoint wires {@link MessageChatMemoryAdvisor} into the call.
     * Before the request reaches the model, the advisor:
     * <ol>
     *   <li>Loads stored messages for the given {@code conversationId}.</li>
     *   <li>Appends them to the messages list (USER/ASSISTANT pairs).</li>
     *   <li>Forwards the enriched prompt to the model.</li>
     * </ol>
     * After the model responds, the advisor saves both the new user message and
     * the assistant's reply to {@link ChatMemory} for future turns.
     *
     * <p><b>Conversation isolation:</b> each unique {@code conversationId} is
     * an independent conversation.  Different users should use different IDs.
     *
     * <p><b>Clearing memory:</b> call
     * {@link org.springframework.ai.chat.memory.ChatMemory#clear(String)}
     * with the conversation ID to wipe history (e.g. on logout or session end).
     *
     * <pre>
     *   # Turn 1
     *   curl "http://localhost:8080/chat/memory?message=My+name+is+Dan&amp;conversationId=demo"
     *
     *   # Turn 2 — the model remembers
     *   curl "http://localhost:8080/chat/memory?message=What+is+my+name&amp;conversationId=demo"
     *
     *   # Different session — fresh slate
     *   curl "http://localhost:8080/chat/memory?message=What+is+my+name&amp;conversationId=other"
     * </pre>
     *
     * @param message        the user's input text
     * @param conversationId unique session identifier; defaults to {@code "default"}
     * @return the model's plain-text response, informed by prior conversation turns
     */
    @GetMapping("/memory")
    public String chatWithMemory(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String conversationId) {

        return chatClient
                .prompt()
                .user(message)
                .advisors(spec ->
                        // Pass the conversationId at call time so the same ChatClient
                        // instance can serve multiple independent conversations.
                        spec.param(ChatMemory.CONVERSATION_ID, conversationId)
                )
                .advisors(
                        // MessageChatMemoryAdvisor injects stored messages as typed
                        // Message objects (UserMessage / AssistantMessage).
                        // This is the recommended advisor for models with native
                        // multi-turn support (virtually all modern chat models).
                        //
                        // ── Alternative: PromptChatMemoryAdvisor ─────────────────
                        // Injects history as text in the system prompt instead.
                        // Better for instruction-tuned models without multi-turn
                        // support, or when you need a custom history format.
                        //
                        //   PromptChatMemoryAdvisor.builder(chatMemory)
                        //       .build()
                        //
                        // The conversationId is still passed via spec.param() above.
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .call()
                .content();
    }

    // =========================================================================
    // STEP 3 — Streaming Chat with Memory
    // =========================================================================

    /**
     * Streams the model's response token-by-token as a Server-Sent Events stream.
     *
     * <p>Uses the same {@link MessageChatMemoryAdvisor} as Step 2, so the model
     * still has full conversation context.  The only difference is the transport:
     * instead of waiting for the complete response, tokens arrive incrementally
     * via a reactive {@link Flux}.
     *
     * <p><b>Why streaming matters in production:</b>
     * <ul>
     *   <li>Users see the first token in ~100–300 ms instead of waiting 5–15 s
     *       for the full response from a local model.</li>
     *   <li>Perceived latency drops dramatically, especially for long answers.</li>
     *   <li>Works naturally with modern front-ends (SSE, WebSocket, fetch with
     *       ReadableStream).</li>
     * </ul>
     *
     * <p>The endpoint produces {@code text/event-stream}.  Use {@code curl -N}
     * (no buffering) or a browser {@code EventSource} to consume it:
     *
     * <pre>
     *   curl -N "http://localhost:8080/chat/stream?message=Tell+me+a+story&amp;conversationId=s1"
     * </pre>
     *
     * @param message        the user's input text
     * @param conversationId unique session identifier; defaults to {@code "default"}
     * @return a reactive stream of response tokens
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String conversationId) {

        return chatClient
                .prompt()
                .user(message)
                .advisors(spec ->
                        spec.param(ChatMemory.CONVERSATION_ID, conversationId)
                )
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .stream()          // returns ChatClientResponse as a Flux
                .content();        // map each chunk to its text content
    }
}
