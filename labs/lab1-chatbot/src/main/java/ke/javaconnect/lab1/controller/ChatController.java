package ke.javaconnect.lab1.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ke.javaconnect.lab1.dto.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
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
 * STEP 1 — GET /chat/simple
 *   Bare ChatClient call, stateless, no memory.
 *   Each request is independent — the model has no
 *   recollection of anything said before.
 *
 * STEP 2 — GET /chat/memory?conversationId=
 *   Adds MessageChatMemoryAdvisor.
 *   The model remembers previous turns in the same
 *   conversation. Try the same ID twice to see context
 *   carry over; a different ID = a fresh slate.
 *
 * STEP 3 — GET /chat/stream?conversationId=
 *   Same as Step 2 but tokens stream back as they are
 *   generated (text/event-stream / Server-Sent Events).
 * </pre>
 *
 * <h2>Quick test commands</h2>
 * <pre>
 *   # Step 1 — stateless
 *   curl "http://localhost:8080/chat/simple?message=What+is+Spring+AI"
 *
 *   # Step 2 — with memory (run both; the model remembers the name)
 *   curl "http://localhost:8080/chat/memory?message=My+name+is+Dan&amp;conversationId=demo"
 *   curl "http://localhost:8080/chat/memory?message=What+is+my+name&amp;conversationId=demo"
 *
 *   # Step 2 — different conversationId = fresh slate
 *   curl "http://localhost:8080/chat/memory?message=What+is+my+name&amp;conversationId=other"
 *
 *   # Step 3 — streaming tokens
 *   curl -N "http://localhost:8080/chat/stream?message=Tell+me+a+short+story&amp;conversationId=s1"
 * </pre>
 *
 * @see ke.javaconnect.lab1.config.MemoryConfig
 */
@Validated   // enables @NotBlank / @Size on @RequestParam fields
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
     * The {@link ChatMemory} bean defined in
     * {@link ke.javaconnect.lab1.config.MemoryConfig}.
     *
     * <p>Injected here so that memory advisors can be constructed per-request,
     * allowing each request to carry its own {@code conversationId}.
     */
    private final ChatMemory chatMemory;

    /**
     * The configured Ollama model tag, included in every {@link ChatResponse}
     * so callers know exactly which model produced the reply.
     *
     * <p>Bound from {@code spring.ai.ollama.chat.model} in
     * {@code application.yml}.  Changing the model in config is enough —
     * no code change is required.
     */
    private final String model;

    /**
     * Constructs the controller and builds the shared {@link ChatClient}.
     *
     * <p>Spring Boot auto-configures a {@link ChatClient.Builder} bean that is
     * already wired to the Ollama model defined in {@code application.yml}.
     * We call {@code .build()} here — no model-specific code needed.
     *
     * <p>The {@code defaultSystem} sets a persona applied to <em>all</em>
     * requests through this client unless overridden at call time.
     *
     * @param builder    auto-configured by {@code spring-ai-starter-model-ollama}
     * @param chatMemory the windowed memory store from
     *                   {@link ke.javaconnect.lab1.config.MemoryConfig}
     * @param model      the configured Ollama model tag
     */
    public ChatController(ChatClient.Builder builder,
                          ChatMemory chatMemory,
                          @Value("${spring.ai.ollama.chat.model}") String model) {
        this.chatMemory = chatMemory;
        this.model = model;
        this.chatClient = builder
                // System prompt shapes the assistant's identity and tone.
                // Injected as the first message on every request.
                .defaultSystem("""
                        You are a helpful Java and Spring AI assistant for the
                        JavaConnect KE workshop. Keep answers concise and practical.
                        Use code examples when they add value.
                        """)
                // SimpleLoggerAdvisor logs every request/response at DEBUG level.
                // Shows exactly what is sent to the model, including injected memory.
                // Enable: logging.level.org.springframework.ai.chat.client.advisor=DEBUG
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    // =========================================================================
    // STEP 1 — Stateless Chat
    // =========================================================================

    /**
     * Sends a single message to the model and returns a structured response.
     *
     * <p>The simplest possible Spring AI interaction:
     * <ol>
     *   <li>The user message is wrapped in a {@code UserMessage}.</li>
     *   <li>The system prompt is prepended automatically.</li>
     *   <li>The combined prompt is sent to Ollama.</li>
     *   <li>The reply is wrapped in a {@link ChatResponse} with metadata.</li>
     * </ol>
     *
     * <p>There is <b>no memory</b> here — ask a follow-up question and the
     * model will have no idea what was said a moment ago.
     *
     * <pre>
     *   curl "http://localhost:8080/chat/simple?message=What+is+Spring+AI"
     *   curl "http://localhost:8080/chat/simple?message=Tell+me+more" ← no context!
     * </pre>
     *
     * @param message the user's input (1–2000 characters, must not be blank)
     * @return a {@link ChatResponse} containing the reply, model, and timestamp
     */
    @GetMapping("/simple")
    public ChatResponse simpleChat(
            @RequestParam
            @NotBlank(message = "message must not be blank")
            @Size(max = 2000, message = "message must be 2000 characters or fewer")
            String message) {

        String reply = chatClient
                .prompt()
                .user(message)
                .call()
                .content();

        return ChatResponse.of("stateless", reply, model);
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
     *   <li>Appends them as USER/ASSISTANT pairs to the outgoing prompt.</li>
     *   <li>Forwards the enriched prompt to Ollama.</li>
     * </ol>
     * After the model responds, the advisor persists both the new user message
     * and the assistant reply to {@link ChatMemory} for future turns.
     *
     * <p><b>Conversation isolation:</b> each unique {@code conversationId} is
     * an independent session.  Use one ID per user / tab / session.
     *
     * <p><b>Inspect stored history:</b> {@code GET /conversations/{id}}<br>
     * <b>Clear history:</b> {@code DELETE /conversations/{id}}
     *
     * <pre>
     *   curl "http://localhost:8080/chat/memory?message=My+name+is+Dan&amp;conversationId=demo"
     *   curl "http://localhost:8080/chat/memory?message=What+is+my+name&amp;conversationId=demo"
     * </pre>
     *
     * @param message        the user's input (1–2000 chars, must not be blank)
     * @param conversationId unique session key; defaults to {@code "default"}
     * @return a {@link ChatResponse} containing the reply, model, conversationId,
     *         and timestamp
     */
    @GetMapping("/memory")
    public ChatResponse chatWithMemory(
            @RequestParam
            @NotBlank(message = "message must not be blank")
            @Size(max = 2000, message = "message must be 2000 characters or fewer")
            String message,
            @RequestParam(defaultValue = "default") String conversationId) {

        String reply = chatClient
                .prompt()
                .user(message)
                .advisors(spec ->
                        // Pass conversationId at call time — one ChatClient instance
                        // serves many independent conversations simultaneously.
                        spec.param(ChatMemory.CONVERSATION_ID, conversationId)
                )
                .advisors(
                        // MessageChatMemoryAdvisor — RECOMMENDED for most models.
                        // Injects history as typed Message objects (USER/ASSISTANT).
                        //
                        // Alternative — PromptChatMemoryAdvisor:
                        // Serialises history as text in the system prompt.
                        // Better for instruct-only models or custom history formats.
                        //
                        //   PromptChatMemoryAdvisor.builder(chatMemory).build()
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .call()
                .content();

        return ChatResponse.of(conversationId, reply, model);
    }

    // =========================================================================
    // STEP 3 — Streaming Chat with Memory
    // =========================================================================

    /**
     * Streams the model's response token-by-token as Server-Sent Events.
     *
     * <p>Uses the same {@link MessageChatMemoryAdvisor} as Step 2 so the model
     * still has full conversation context.  The difference is transport: tokens
     * arrive incrementally via a reactive {@link Flux} rather than all at once.
     *
     * <p><b>Why streaming matters in production:</b>
     * <ul>
     *   <li>First token arrives in ~100–300 ms instead of waiting 5–15 s for
     *       the full response from a local model.</li>
     *   <li>Perceived latency drops dramatically, especially for long answers.</li>
     *   <li>Integrates naturally with browser {@code EventSource} / fetch
     *       {@code ReadableStream} for real-time chat UIs.</li>
     * </ul>
     *
     * <p>Note: streaming responses cannot be wrapped in {@link ChatResponse}
     * because the full reply is not known until the stream completes.  The
     * conversationId and model can be sent as the first SSE event if needed.
     *
     * <pre>
     *   curl -N "http://localhost:8080/chat/stream?message=Tell+me+a+story&amp;conversationId=s1"
     * </pre>
     *
     * @param message        the user's input (1–2000 chars, must not be blank)
     * @param conversationId unique session key; defaults to {@code "default"}
     * @return a reactive stream of response token strings
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> streamChat(
            @RequestParam
            @NotBlank(message = "message must not be blank")
            @Size(max = 2000, message = "message must be 2000 characters or fewer")
            String message,
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
                .stream()
                .content();
    }
}
