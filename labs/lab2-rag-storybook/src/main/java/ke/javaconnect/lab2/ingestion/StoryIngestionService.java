package ke.javaconnect.lab2.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Story Ingestion Service — Lab 2.
 *
 * <p>This service implements the ETL (Extract → Transform → Load) pipeline
 * that converts the story Markdown file into searchable vector embeddings:
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────────┐
 * │                    ETL Pipeline                              │
 * │                                                              │
 * │  EXTRACT                                                     │
 * │  MarkdownDocumentReader reads story.md                       │
 * │  Sections separated by horizontal rules become Documents     │
 * │  Headers stored as Document metadata (title, category)       │
 * │       │                                                      │
 * │       ▼                                                      │
 * │  TRANSFORM                                                   │
 * │  TokenTextSplitter splits each Document into 800-token chunks │
 * │  Overlapping boundaries preserve sentence context            │
 * │  chunk_index metadata added for ordering/debugging           │
 * │       │                                                      │
 * │       ▼                                                      │
 * │  LOAD                                                        │
 * │  VectorStore.add() calls Azure OpenAI text-embedding-ada-002 │
 * │  Vectors stored in Neon PostgreSQL (vector_store table)      │
 * │  IVFFlat index updated for fast similarity search            │
 * └──────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Idempotency</h2>
 * <p>Before ingesting, the service checks whether vectors already exist in
 * the store by probing for a known story term. If found, ingestion is skipped.
 * This means the app can be restarted without re-embedding (and paying the
 * Azure OpenAI embedding cost again).
 *
 * <p>To force re-ingestion (e.g., after editing the story), delete all rows
 * from the {@code vector_store} table in Neon:
 * <pre>
 *   DELETE FROM vector_store;
 * </pre>
 *
 * <h2>When it runs</h2>
 * <p>The {@link ApplicationReadyEvent} fires after the entire Spring context
 * is initialised, including Flyway migrations and the PgVectorStore bean.
 * This ensures the {@code vector_store} table exists before we try to write to it.
 *
 * @see ke.javaconnect.lab2.config.RagConfig
 */
@Service
public class StoryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StoryIngestionService.class);

    private static final String IDEMPOTENCY_PROBE = "Karibu Valley";

    private final VectorStore vectorStore;

    @Value("${app.story.resource:classpath:story/the-chronicles-of-karibu-valley.md}")
    private Resource storyResource;

    public StoryIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // Startup Ingestion
    /**
     * Runs the ETL pipeline once the Spring context is fully started.
     *
     * <p>The method is guarded by an idempotency check so it only performs
     * expensive embedding calls on the first run. Subsequent restarts log
     * a skip message and return immediately.
     *
     * <p>Workshop note: watch the console output! Each step logs its progress
     * so you can see the pipeline running in real time.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ingestStory() {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║         RAG Story Ingestion Pipeline — Starting          ║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        if (alreadyIngested()) {
            log.info("[RAG][INGEST] Story already ingested — skipping embedding.");
            log.info("[RAG][INGEST] To re-ingest: DELETE FROM vector_store; and restart.");
            return;
        }

        List<Document> rawDocs    = extract();
        List<Document> chunks     = transform(rawDocs);
        load(chunks);

        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║         RAG Story Ingestion Pipeline — Complete          ║");
        log.info("╚══════════════════════════════════════════════════════════╝");
    }

    /**
     * Reads the story Markdown file and converts it into a list of
     * raw {@link Document} objects.
     *
     * <p>{@link MarkdownDocumentReader} with {@code horizontalRuleCreateDocument=true}
     * treats each {@code ---} separator in the Markdown as a document boundary.
     * The story is structured with horizontal rules between major sections,
     * so each "act" of the narrative becomes its own Document before splitting.
     *
     * <p>Headers (h1-h6) are stored in the document's metadata map,
     * allowing the vector store to be filtered by chapter later.
     */
    private List<Document> extract() {
        log.info("[RAG][INGEST][1/3] EXTRACT — Reading story from: {}", storyResource);

        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(false)
                .withIncludeBlockquote(true)
                .withAdditionalMetadata("source", "the-chronicles-of-karibu-valley")
                .withAdditionalMetadata("story", "true")
                .build();

        List<Document> docs = new MarkdownDocumentReader(storyResource, config).get();
        log.info("[RAG][INGEST][1/3] EXTRACT — Read {} raw sections from story.md", docs.size());
        docs.forEach(d -> log.debug("[RAG][INGEST]   section metadata={}", d.getMetadata()));

        return docs;
    }

    /**
     * Splits raw documents into token-sized chunks and enriches their metadata.
     *
     * <p>{@link TokenTextSplitter} uses the CL100K_BASE encoding (same as
     * OpenAI's GPT-4 and text-embedding-ada-002), so chunk sizes correspond
     * directly to the embedding model's token budget.
     *
     * <p>Parameters chosen for the story:
     * <ul>
     *   <li>{@code chunkSize=800} — each chunk is ≤800 tokens, well under
     *       text-embedding-ada-002's 8191-token limit.</li>
     *   <li>{@code minChunkSizeChars=200} — discard very small fragments
     *       that would produce low-quality embeddings.</li>
     *   <li>{@code keepSeparator=true} — preserve paragraph breaks in chunks,
     *       which the model can use as formatting hints.</li>
     * </ul>
     *
     * <p>After splitting, each chunk receives a {@code chunk_index} metadata
     * field so results can be ordered or filtered by position in the story.
     */
    private List<Document> transform(List<Document> rawDocs) {
        log.info("[RAG][INGEST][2/3] TRANSFORM — Splitting {} sections into token chunks", rawDocs.size());

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(10)
                .withKeepSeparator(true)
                .build();

        List<Document> chunks = splitter.apply(rawDocs);

        // Enrich each chunk with a sequential index for ordering + debugging
        IntStream.range(0, chunks.size()).forEach(i ->
                chunks.get(i).getMetadata().put("chunk_index", i));

        log.info("[RAG][INGEST][2/3] TRANSFORM — Produced {} chunks ready for embedding", chunks.size());
        chunks.forEach(c -> log.debug("[RAG][INGEST]   chunk[{}] length={}chars metadata={}",
                c.getMetadata().get("chunk_index"),
                c.getText().length(),
                c.getMetadata()));

        return chunks;
    }

    /**
     * Embeds the chunks and stores them in the PgVector store.
     *
     * <p>Internally, {@link VectorStore#add} calls the configured
     * {@link org.springframework.ai.embedding.EmbeddingModel} (Azure OpenAI
     * text-embedding-ada-002) to convert each chunk's text into a 1536-dimension
     * floating-point vector, then persists the vector alongside the original text
     * and metadata in the {@code vector_store} table.
     *
     * <p>This is the most expensive step — one Azure OpenAI API call per batch
     * of chunks. The model batches internally, but watch the Azure portal's
     * token usage counter during the workshop to see it fire.
     */
    private void load(List<Document> chunks) {
        log.info("[RAG][INGEST][3/3] LOAD — Embedding {} chunks via Azure OpenAI (text-embedding-ada-002)", chunks.size());
        log.info("[RAG][INGEST][3/3] LOAD — Writing vectors to Neon PostgreSQL (vector_store)...");

        vectorStore.add(chunks);

        log.info("[RAG][INGEST][3/3] LOAD — Done. {} story chunks stored in PgVector.", chunks.size());
        log.info("[RAG][INGEST] Vector store is ready. You can now ask questions about the story.");
        log.info("[RAG][INGEST] Try: POST /story/ask?message=Who+is+Ayana&conversationId=demo");
    }

    /**
     * Returns {@code true} if the vector store already contains story data.
     *
     * <p>Probes for a known term from the story. If at least one result comes
     * back, we assume the full ingestion was completed in a previous run.
     */
    private boolean alreadyIngested() {
        log.info("[RAG][INGEST] Checking if story is already ingested...");
        List<Document> probe = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(IDEMPOTENCY_PROBE)
                        .topK(1)
                        .build()
        );
        boolean alreadyDone = !probe.isEmpty();
        log.info("[RAG][INGEST] Already ingested: {}", alreadyDone);
        return alreadyDone;
    }
}
