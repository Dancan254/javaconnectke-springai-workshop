package ke.javaconnect.lab2.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat Memory Configuration — Lab 2.
 *
 * <p>Lab 2 reuses the same in-memory chat memory pattern from Lab 1,
 * combined here with the RAG-capable {@code ChatClient} in {@link RagConfig}.
 *
 * <p>The memory system has three independent layers:
 *
 * <pre>
 * LAYER 1 — ChatMemoryRepository  (WHERE to store)
 * -------------------------------------------------
 *  InMemoryChatMemoryRepository (active here)
 *    + Zero config, zero dependencies
 *    - Lost on restart
 *
 * LAYER 2 — ChatMemory  (HOW MANY messages to keep)
 * --------------------------------------------------
 *  MessageWindowChatMemory
 *    + Sliding window of last N messages per conversation
 *    + Configured via app.chat.memory.max-messages in application.yml
 *
 * LAYER 3 — Advisor  (HOW to inject history into prompt)
 * -------------------------------------------------------
 *  MessageChatMemoryAdvisor (wired in RagConfig)
 *    + Injects history as typed Message objects
 *    + Runs BEFORE QuestionAnswerAdvisor in the chain
 * </pre>
 *
 * <h2>Why memory + RAG together?</h2>
 * <p>Without memory, every question is isolated — the user can't say
 * "tell me more about her" after asking about Ayana, because the model
 * has no context of the previous exchange.  With both advisors stacked:
 * <ol>
 *   <li>{@code MessageChatMemoryAdvisor} injects conversation history.</li>
 *   <li>{@code QuestionAnswerAdvisor} retrieves relevant story chunks.</li>
 *   <li>The model answers with both conversation context AND story facts.</li>
 * </ol>
 *
 * @see RagConfig
 */
@Configuration
public class MemoryConfig {

    @Value("${app.chat.memory.max-messages:10}")
    private int maxMessages;

    /**
     * In-memory conversation store — simple, zero-dependency, workshop-ready.
     * All conversation history is lost when the application restarts.
     */
    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    /**
     * Sliding-window memory — keeps the last {@code maxMessages} turns per
     * conversation. Older turns are evicted before each new request.
     *
     * @param repository the in-memory store from {@link #chatMemoryRepository()}
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }
}
