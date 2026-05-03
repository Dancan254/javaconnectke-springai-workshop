package ke.javaconnect.lab2.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ke.javaconnect.lab2.dto.ChatResponse;
import ke.javaconnect.lab2.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Lab 2 — RAG Storybook: Story Q&amp;A Endpoints.
 *
 * <p>This controller exposes the RAG capabilities of Lab 2 through three
 * endpoints that together demonstrate the full retrieval-augmented generation
 * flow:
 *
 * <pre>
 * STEP 1 — POST /story/ask?message=&amp;conversationId=
 *   The primary RAG endpoint.
 *   QuestionAnswerAdvisor retrieves relevant story chunks from PgVector,
 *   MessageChatMemoryAdvisor adds conversation history,
 *   Azure OpenAI gpt-4o generates a grounded answer.
 *   Try asking follow-up questions with the same conversationId to
 *   see memory + RAG working together.
 *
 * STEP 2 — GET /story/search?query=&amp;topK=
 *   Raw vector similarity search — no LLM, no memory.
 *   Shows EXACTLY what the QuestionAnswerAdvisor retrieves for any query.
 *   Use this to demonstrate the vector search independently and explain
 *   why certain chunks are more relevant than others (similarity scores).
 *
 * STEP 3 — GET /story/documents?topK=
 *   Lists ingested story chunks in the vector store.
 *   Demonstrates that the ETL pipeline has successfully loaded the story.
 *   Shows content + metadata (source, chunk_index, headers).
 * </pre>
 *
 * <h2>Quick test commands</h2>
 * <pre>
 *   # Ask about a character
 *   curl -X POST "http://localhost:8080/story/ask?message=Who+is+Ayana&amp;conversationId=demo"
 *
 *   # Follow-up using memory
 *   curl -X POST "http://localhost:8080/story/ask?message=What+is+her+special+gift&amp;conversationId=demo"
 *
 *   # Ask about a place
 *   curl -X POST "http://localhost:8080/story/ask?message=Describe+Lake+Baraka&amp;conversationId=demo"
 *
 *   # Raw similarity search (inspect retrieved chunks)
 *   curl "http://localhost:8080/story/search?query=river+spirit"
 *   curl "http://localhost:8080/story/search?query=seeds+and+memory"
 *
 *   # List all stored chunks
 *   curl "http://localhost:8080/story/documents"
 * </pre>
 *
 * @see ke.javaconnect.lab2.config.RagConfig
 * @see ke.javaconnect.lab2.ingestion.StoryIngestionService
 */
@Validated
@RestController
@RequestMapping("/story")
public class StoryController {

    private static final Logger log = LoggerFactory.getLogger(StoryController.class);

    private final ChatClient storyChatClient;
    private final VectorStore vectorStore;

    @Value("${spring.ai.azure.openai.chat.options.deployment-name:gpt-4o}")
    private String model;

    @Value("${app.rag.top-k:5}")
    private int defaultTopK;

    @Value("${app.rag.similarity-threshold:0.65}")
    private double defaultThreshold;

    public StoryController(ChatClient storyChatClient, VectorStore vectorStore) {
        this.storyChatClient = storyChatClient;
        this.vectorStore     = vectorStore;
    }

    // STEP 1 — RAG Chat with Memory

    /**
     * Asks a question about the story with full RAG + memory support.
     *
     * <p>The request flows through the advisor chain registered in
     * {@link ke.javaconnect.lab2.config.RagConfig#storyChatClient}:
     * <ol>
     *   <li>{@code SimpleLoggerAdvisor} captures the raw user message.</li>
     *   <li>{@code MessageChatMemoryAdvisor} loads prior turns for this
     *       {@code conversationId} and injects them as chat history.</li>
     *   <li>{@code QuestionAnswerAdvisor} embeds the question, searches the
     *       vector store for the top-{@code K} semantically similar story
     *       passages, and appends them as context in the prompt.</li>
     *   <li>Azure OpenAI gpt-4o receives the enriched prompt and generates
     *       an answer grounded entirely in the retrieved story passages.</li>
     *   <li>{@code MessageChatMemoryAdvisor} saves the new turn to memory.</li>
     * </ol>
     *
     * <p><b>Workshop tip:</b> run two requests with the same {@code conversationId}
     * and a follow-up like "Tell me more about her". The model will correctly
     * refer to the character from the previous turn even though "her" is ambiguous.
     *
     * @param message        the user's question about the story (1–2000 chars)
     * @param conversationId unique session key; defaults to {@code "default"}
     * @return a {@link ChatResponse} with the grounded answer, model, and timestamp
     */
    @PostMapping("/ask")
    public ChatResponse askAboutStory(
            @RequestParam
            @NotBlank(message = "message must not be blank")
            @Size(max = 2000, message = "message must be 2000 characters or fewer")
            String message,
            @RequestParam(defaultValue = "default") String conversationId) {

        log.info("[RAG][QUERY] conversationId={} | question='{}'", conversationId, message);

        String reply = storyChatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        log.info("[RAG][RESPONSE] conversationId={} | reply preview='{}'",
                conversationId, reply != null && reply.length() > 100
                        ? reply.substring(0, 100) + "..." : reply);

        return ChatResponse.of(conversationId, reply, model);
    }

    // STEP 2 — Raw Similarity Search

    /**
     * Performs a raw vector similarity search and returns matching story chunks.
     *
     * <p>This endpoint bypasses the LLM entirely and talks directly to the
     * {@link VectorStore}. It is the best way to demonstrate what the
     * {@code QuestionAnswerAdvisor} retrieves "under the hood" for any query.
     *
     * <p>Every result includes:
     * <ul>
     *   <li>The raw text of the matching story passage.</li>
     *   <li>Metadata: source, chunk_index, and any Markdown headers.</li>
     *   <li>A cosine similarity score between 0.0 and 1.0 — higher = closer match.</li>
     * </ul>
     *
     * <p><b>Workshop tip:</b> Run the same question through both {@code /story/search}
     * (to see what is retrieved) and {@code /story/ask} (to see how the model
     * uses the retrieved context). Compare the chunks in the search result with
     * the references in the model's answer.
     *
     * <pre>
     *   curl "http://localhost:8080/story/search?query=the+river+spirit+Njoki"
     *   curl "http://localhost:8080/story/search?query=Elder+Muthoni+and+her+wisdom"
     *   curl "http://localhost:8080/story/search?query=the+trial+of+flames"
     * </pre>
     *
     * @param query the search phrase (1–500 chars)
     * @param topK  maximum number of results to return (default: from config)
     * @return ordered list of {@link SearchResult} objects, best match first
     */
    @GetMapping("/search")
    public List<SearchResult> search(
            @RequestParam
            @NotBlank(message = "query must not be blank")
            @Size(max = 500, message = "query must be 500 characters or fewer")
            String query,
            @RequestParam(required = false) Integer topK) {

        int k = topK != null ? topK : defaultTopK;
        log.info("[RAG][SEARCH] query='{}' topK={} threshold={}", query, k, defaultThreshold);

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(k)
                        .similarityThreshold(defaultThreshold)
                        .build()
        );

        log.info("[RAG][SEARCH] Found {} results for query='{}'", results.size(), query);
        results.forEach(d -> log.debug("[RAG][SEARCH]   score={} chunk={}",
                d.getScore(),
                d.getText().substring(0, Math.min(80, d.getText().length()))));

        return results.stream()
                .map(SearchResult::from)
                .toList();
    }

    // STEP 3 — List Stored Documents

    /**
     * Returns a sample of stored story chunks from the vector store.
     *
     * <p>Uses a broad similarity search with a very low threshold to retrieve
     * a representative set of stored documents. Useful at the start of the
     * workshop to confirm the ETL pipeline ran successfully and to show the
     * audience what the vector store contains.
     *
     * <p><b>Note:</b> this returns a representative sample, not all documents.
     * For a full inventory, query the Neon database directly:
     * <pre>
     *   SELECT id, content, metadata FROM vector_store ORDER BY metadata->>'chunk_index';
     * </pre>
     *
     * @param topK number of sample chunks to return (default: 20)
     * @return list of stored story chunks with metadata
     */
    @GetMapping("/documents")
    public List<SearchResult> listDocuments(
            @RequestParam(defaultValue = "20") int topK) {

        log.info("[RAG][DOCS] Listing up to {} stored story chunks from vector store", topK);

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Karibu Valley story")
                        .topK(topK)
                        .similarityThreshold(0.0)   // return everything regardless of score
                        .build()
        );

        log.info("[RAG][DOCS] Retrieved {} chunks from vector store", docs.size());

        return docs.stream()
                .map(SearchResult::from)
                .toList();
    }
}
