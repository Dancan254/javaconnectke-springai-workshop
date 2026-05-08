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
 * Conversation management:
 *
 *   GET    /conversations           — list all active conversation IDs
 *   GET    /conversations/{id}      — full message history for one conversation
 *   DELETE /conversations/{id}      — clear one conversation (e.g. on logout)
 *   DELETE /conversations           — clear all conversations
 */
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;

    public ConversationController(ChatMemory chatMemory, ChatMemoryRepository chatMemoryRepository) {
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    @GetMapping
    public List<String> listConversations() {
        return chatMemoryRepository.findConversationIds();
    }

    @GetMapping("/{conversationId}")
    public List<MessageView> getHistory(@PathVariable String conversationId) {
        return chatMemory.get(conversationId)
                .stream()
                .map(MessageView::from)
                .toList();
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        chatMemory.clear(conversationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAllConversations() {
        chatMemoryRepository.findConversationIds().forEach(chatMemory::clear);
        return ResponseEntity.noContent().build();
    }
}
