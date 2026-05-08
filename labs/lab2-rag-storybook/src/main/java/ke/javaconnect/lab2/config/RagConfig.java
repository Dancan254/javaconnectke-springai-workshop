package ke.javaconnect.lab2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG Infrastructure Configuration — Lab 2.
 *
 * <p>This class wires together the three components that make RAG work:
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │                    RAG Architecture                      │
 * │                                                         │
 * │  User Question                                          │
 * │       │                                                 │
 * │       ▼                                                 │
 * │  QuestionAnswerAdvisor                                  │
 * │    • Embeds the question (text-embedding-3-large)       │
 * │    • Runs cosine similarity search in ChromaDB          │
 * │    • Injects top-K story chunks into the prompt         │
 * │       │                                                 │
 * │       ▼                                                 │
 * │  MessageChatMemoryAdvisor                               │
 * │    • Injects prior conversation turns                   │
 * │       │                                                 │
 * │       ▼                                                 │
 * │  Azure OpenAI gpt-4.1                                   │
 * │    • Generates answer grounded in story context         │
 * │       │                                                 │
 * │       ▼                                                 │
 * │  ChatResponse → caller                                  │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Advisor ordering</h2>
 * <p>Advisors run in the order they are registered. The chain below is:
 * <ol>
 *   <li>{@link SimpleLoggerAdvisor} — logs the raw request first.</li>
 *   <li>{@link MessageChatMemoryAdvisor} — injects chat history.</li>
 *   <li>{@link QuestionAnswerAdvisor} — retrieves and injects story context.</li>
 * </ol>
 * After the model responds, advisors execute in reverse order for post-processing.
 *
 * @see MemoryConfig
 * @see ke.javaconnect.lab2.ingestion.StoryIngestionService
 */
@Configuration
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    @Value("${app.rag.similarity-threshold:0.65}")
    private double similarityThreshold;

    @Value("${app.rag.top-k:5}")
    private int topK;

    // RAG-Capable ChatClient

    /**
     * The primary {@link ChatClient} for Lab 2, pre-wired with RAG + memory.
     *
     * <p>Three advisors are stacked as default advisors on this client:
     *
     * <ol>
     *   <li><b>SimpleLoggerAdvisor</b> — logs the full outgoing prompt at DEBUG
     *       level, including all injected context. Enable in {@code application.yml}
     *       with {@code logging.level.org.springframework.ai.chat.client.advisor: DEBUG}.
     *       This lets workshop attendees see exactly what the model receives.</li>
     *
     *   <li><b>MessageChatMemoryAdvisor</b> — injects prior turns from the
     *       sliding-window memory store. The {@code conversationId} is passed at
     *       call time (per request), so one ChatClient serves many sessions.</li>
     *
     *   <li><b>QuestionAnswerAdvisor</b> (RAG) — the core of Lab 2. Before each
     *       LLM call it:
     *       <ul>
     *         <li>Embeds the user's question via text-embedding-3-large.</li>
     *         <li>Runs a cosine similarity search against the ChromaDB collection.</li>
     *         <li>Appends the top-K most relevant story chunks to the prompt.</li>
     *       </ul>
     *       The model then answers using this context rather than hallucinating.</li>
     * </ol>
     *
     * <h2>System prompt design</h2>
     * <p>The system prompt constrains the model to use only retrieved context.
     * This prevents it from answering from its general training data, which
     * would defeat the purpose of RAG and confuse the workshop demo.
     *
     * @param builder     auto-configured by {@code spring-ai-starter-model-azure-openai}
     * @param vectorStore Spring AI auto-configured PgVector {@link VectorStore}
     * @param chatMemory  the sliding-window memory from {@link MemoryConfig}
     */
    @Bean
    public ChatClient storyChatClient(ChatClient.Builder builder,
                                      VectorStore vectorStore,
                                      ChatMemory chatMemory) {
        log.info("[RAG][CONFIG] Building storyChatClient " +
                "(QuestionAnswerAdvisor topK={}, threshold={})", topK, similarityThreshold);

        return builder
                .defaultSystem("""
                        You are an expert guide and storyteller for "The Chronicles of Karibu Valley."

                        Your role:
                        - Answer questions about the story using ONLY the context provided to you.
                        - If the answer is not in the provided context, say honestly: "I don't have that
                          detail in the story passages I retrieved. Try rephrasing your question."
                        - Reference specific characters, places, and events from the story.
                        - Be vivid, warm, and engaging — match the storytelling tone of the narrative.
                        - Keep answers concise but complete. Use the story's own language where fitting.

                        Exception — conversational meta-questions:
                        If the user asks about the conversation itself (e.g. "What did I just ask?",
                        "What was my previous question?", "What did you say?", "Can you repeat that?"),
                        answer using the conversation history — do not treat these as story questions.
                        """)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),

                        // 2. Inject conversation history (memory)
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),

                        // 3. Retrieve relevant story chunks (RAG)
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .similarityThreshold(similarityThreshold)
                                        .topK(topK)
                                        .build())
                                .build()
                )
                .build();
    }
}
