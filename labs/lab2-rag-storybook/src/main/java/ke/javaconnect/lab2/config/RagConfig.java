package ke.javaconnect.lab2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * │    • Embeds the question (text-embedding-ada-002)       │
 * │    • Runs cosine similarity search in PgVector          │
 * │    • Injects top-K story chunks into the prompt         │
 * │       │                                                 │
 * │       ▼                                                 │
 * │  MessageChatMemoryAdvisor                               │
 * │    • Injects prior conversation turns                   │
 * │       │                                                 │
 * │       ▼                                                 │
 * │  Azure OpenAI gpt-4o                                    │
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

    // Vector Store

    /**
     * PgVector store backed by Neon PostgreSQL.
     *
     * <p>Configuration notes:
     * <ul>
     *   <li>{@code initializeSchema=false} — Flyway manages the DDL.
     *       The table must exist before this bean is used, which is guaranteed
     *       because Flyway runs in the same Spring context startup phase.</li>
     *   <li>{@code dimensions=1536} — must match text-embedding-ada-002 output.</li>
     *   <li>{@code distanceType=COSINE_DISTANCE} — standard for text embeddings.</li>
     *   <li>{@code indexType=IVFFLAT} — approximate nearest-neighbour index.</li>
     * </ul>
     *
     * @param jdbcTemplate   Spring-managed JDBC connection to Neon
     * @param embeddingModel Azure OpenAI text-embedding-ada-002
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        log.info("[RAG][CONFIG] Initialising PgVectorStore (Neon PostgreSQL, 1536 dims, COSINE)");

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .initializeSchema(false)           // Flyway owns DDL
                .dimensions(1536)                  // text-embedding-ada-002
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.IVFFLAT)
                .build();
    }

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
     *         <li>Embeds the user's question via text-embedding-ada-002.</li>
     *         <li>Runs a cosine similarity search against the vector_store table.</li>
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
     * @param vectorStore the PgVector store from {@link #vectorStore}
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

                        Characters: Ayana (Keeper of Listening), Elder Muthoni, Njoki (River Spirit),
                        Kibaki, Kofi, Zawadi, Baraka, Imara, Omari, Wanjiku, Asha.

                        Places: Karibu Valley, Whispering Baobab, Lake Baraka, Njoki River,
                        Staircase of Voices, Nguvu Ridge, Amani Ridge.
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
