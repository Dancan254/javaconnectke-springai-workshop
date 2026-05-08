package ke.javaconnect.lab1.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ke.javaconnect.lab1.dto.ChatResponse;
import ke.javaconnect.lab1.dto.StreamChunk;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Lab 1 — three progressively richer chat endpoints:
 *
 *   GET /chat/simple          — stateless, no memory
 *   GET /chat/memory          — stateful via MessageChatMemoryAdvisor
 *   GET /chat/stream          — stateful + streaming SSE (JSON chunks)
 */
@Validated
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final String model;

    public ChatController(ChatClient.Builder builder,
                          ChatMemory chatMemory,
                          @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.chatMemory = chatMemory;
        this.model = model;
        this.chatClient = builder
                .defaultSystem("""
                        You are a helpful Java and Spring AI assistant for the
                        JavaConnect KE workshop. Keep answers concise and practical.
                        Use code examples when they add value.
                        """)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    // ── Step 1: Stateless ────────────────────────────────────────────────────

    @GetMapping("/simple")
    public ChatResponse simpleChat(
            @RequestParam @NotBlank @Size(max = 2000) String message) {

        String reply = chatClient.prompt()
                .user(message)
                .call()
                .content();

        return ChatResponse.of("stateless", reply, model);
    }

    // ── Step 2: Stateful with memory ─────────────────────────────────────────
    @GetMapping("/memory")
    public ChatResponse chatWithMemory(
            @RequestParam @NotBlank @Size(max = 2000) String message,
            @RequestParam(defaultValue = "default") String conversationId) {

        String reply = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .call()
                .content();

        return ChatResponse.of(conversationId, reply, model);
    }

    // ── Step 3: Stateful + streaming ─────────────────────────────────────────

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> streamChat(
            @RequestParam @NotBlank @Size(max = 2000) String message,
            @RequestParam(defaultValue = "default") String conversationId) {

        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .stream()
                .content();
    }
}
