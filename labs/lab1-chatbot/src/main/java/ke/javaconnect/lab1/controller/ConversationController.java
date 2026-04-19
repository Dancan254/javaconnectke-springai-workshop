package ke.javaconnect.lab1.controller;

import ke.javaconnect.lab1.dto.MessageView;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conversation management endpoints.
 *
 * <p>In a real application users need more than just chat — they need to list
 * their past conversations, inspect what the model has stored, and clear
 * history on logout or session end.  This controller provides those
 * operational endpoints.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * GET    /conversations
 *   List all active conversation IDs known to the memory store.
 *
 * GET    /conversations/{id}
 *   Return the full message history for one conversation.
 *   Useful for debugging what the model actually receives.
 *
 * DELETE /conversations/{id}
 *   Wipe history for one conversation (e.g. on user logout).
 *
 * DELETE /conversations
 *   Wipe ALL conversations — useful for workshop resets.
 * </pre>
 *
 * <h2>Production note</h2>
 * <p>These endpoints should be secured (e.g. Spring Security) so that users
 * can only access their own conversation history.  In this lab they are
 * left open for demo convenience.
 *
 * <h2>Quick test commands</h2>
 * <pre>
 *   curl http://localhost:8080/conversations
 *   curl http://localhost:8080/conversations/demo
 *   curl -X DELETE http://localhost:8080/conversations/demo
 *   curl -X DELETE http://localhost:8080/conversations
 * </pre>
 */
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    /**
     * High-level memory abstraction — used to retrieve and clear messages.
     *
     * @see ke.javaconnect.lab1.config.MemoryConfig#chatMemory(ChatMemoryRepository)
     */
    private final ChatMemory chatMemory;

    /**
     * Low-level repository — used to list all stored conversation IDs.
     *
     * <p>{@link ChatMemory} does not expose a "list all conversations" method
     * by design (it is a per-conversation API).  We go one layer down to the
     * {@link ChatMemoryRepository} for the listing operation.
     *
     * @see ke.javaconnect.lab1.config.MemoryConfig#chatMemoryRepository()
     */
    private final ChatMemoryRepository chatMemoryRepository;

    public ConversationController(ChatMemory chatMemory,
                                  ChatMemoryRepository chatMemoryRepository) {
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * Returns all conversation IDs currently stored in memory.
     *
     * <p>Useful for a session picker UI or for debugging which conversations
     * are consuming memory in the current JVM instance.
     *
     * <pre>
     *   curl http://localhost:8080/conversations
     *   → ["demo", "user-42", "story1"]
     * </pre>
     *
     * @return list of active conversation ID strings
     */
    @GetMapping
    public List<String> listConversations() {
        return chatMemoryRepository.findConversationIds();
    }

    // ── History ───────────────────────────────────────────────────────────────

    /**
     * Returns the stored message history for a given conversation.
     *
     * <p>Each entry shows the role ({@code user} / {@code assistant}) and the
     * message text exactly as stored — this is what gets injected into the
     * next prompt when the conversation continues.
     *
     * <p>Use this alongside {@code SimpleLoggerAdvisor} (enable
     * {@code logging.level.org.springframework.ai.chat.client.advisor=DEBUG}
     * in {@code application.yml}) to get a complete picture of what the model
     * receives on every turn.
     *
     * <pre>
     *   curl http://localhost:8080/conversations/demo
     *   → [
     *       { "role": "user",      "content": "My name is Dan"      },
     *       { "role": "assistant", "content": "Hello Dan, how can I help?" }
     *     ]
     * </pre>
     *
     * @param conversationId the session identifier
     * @return ordered list of messages in the conversation window
     */
    @GetMapping("/{conversationId}")
    public List<MessageView> getHistory(@PathVariable String conversationId) {
        return chatMemory.get(conversationId)
                .stream()
                .map(MessageView::from)
                .toList();
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    /**
     * Clears the message history for a single conversation.
     *
     * <p>Call this on user logout, session expiry, or whenever you want the
     * model to start fresh without knowing anything about a previous session.
     *
     * <pre>
     *   curl -X DELETE http://localhost:8080/conversations/demo
     *   → 204 No Content
     * </pre>
     *
     * @param conversationId the session identifier to wipe
     * @return 204 No Content on success
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        chatMemory.clear(conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clears <em>all</em> conversation histories from the memory store.
     *
     * <p><b>Use with caution in production.</b>  This is primarily useful for
     * workshop resets or integration test teardowns.  In a multi-tenant system
     * this should require an admin role.
     *
     * <pre>
     *   curl -X DELETE http://localhost:8080/conversations
     *   → 204 No Content
     * </pre>
     *
     * @return 204 No Content on success
     */
    @DeleteMapping
    public ResponseEntity<Void> clearAllConversations() {
        chatMemoryRepository.findConversationIds()
                .forEach(chatMemory::clear);
        return ResponseEntity.noContent().build();
    }
}
