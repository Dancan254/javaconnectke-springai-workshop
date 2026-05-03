package ke.javaconnect.lab2.controller;

import ke.javaconnect.lab2.dto.MessageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Conversation management endpoints — Lab 2.
 *
 * <p>Exposes CRUD operations over the in-memory chat history so attendees
 * can inspect what the model "remembers" between turns, reset a session,
 * and verify that {@code MessageChatMemoryAdvisor} is storing messages correctly.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * GET    /conversations
 *   List all active conversation IDs currently in memory.
 *
 * GET    /conversations/{id}
 *   Return the full message history for one conversation.
 *   Shows exactly what gets injected into the prompt on the next turn.
 *
 * DELETE /conversations/{id}
 *   Clear history for one conversation (e.g. start fresh mid-demo).
 *
 * DELETE /conversations
 *   Clear ALL conversations — useful for workshop resets.
 * </pre>
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

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;

    public ConversationController(ChatMemory chatMemory,
                                  ChatMemoryRepository chatMemoryRepository) {
        this.chatMemory           = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    /**
     * Returns all conversation IDs currently held in memory.
     */
    @GetMapping
    public List<String> listConversations() {
        List<String> ids = chatMemoryRepository.findConversationIds();
        log.debug("[MEMORY] Active conversations: {}", ids);
        return ids;
    }

    /**
     * Returns the stored message history for a given conversation.
     *
     * <p>Each entry shows the role ({@code user} / {@code assistant}) and the
     * exact text stored — this is what gets injected into the next prompt.
     */
    @GetMapping("/{conversationId}")
    public List<MessageView> getHistory(@PathVariable String conversationId) {
        log.debug("[MEMORY] Fetching history for conversationId={}", conversationId);
        return chatMemory.get(conversationId)
                .stream()
                .map(MessageView::from)
                .toList();
    }

    /**
     * Clears history for a single conversation.
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        log.info("[MEMORY] Clearing conversationId={}", conversationId);
        chatMemory.clear(conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clears ALL conversation histories — useful for workshop resets.
     */
    @DeleteMapping
    public ResponseEntity<Void> clearAllConversations() {
        List<String> ids = chatMemoryRepository.findConversationIds();
        log.info("[MEMORY] Clearing all {} conversations", ids.size());
        ids.forEach(chatMemory::clear);
        return ResponseEntity.noContent().build();
    }
}
