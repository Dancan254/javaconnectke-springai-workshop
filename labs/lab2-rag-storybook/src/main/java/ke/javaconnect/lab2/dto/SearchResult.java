package ke.javaconnect.lab2.dto;

import org.springframework.ai.document.Document;

import java.util.Map;

/**
 * A single vector similarity search result.
 *
 * <p>Exposes the chunk content, its metadata (source, chapter, chunk index),
 * and the cosine similarity score so the workshop audience can see exactly
 * what context the RAG advisor retrieves for a given query.
 *
 * <h2>Example JSON</h2>
 * <pre>
 * {
 *   "content": "Ayana did not actually fall from the sky...",
 *   "metadata": { "source": "the-chronicles-of-karibu-valley", "chunk_index": 2 },
 *   "score": 0.8923
 * }
 * </pre>
 *
 * @param content  the text chunk that was retrieved
 * @param metadata key-value pairs attached during ingestion (source, chapter, etc.)
 * @param score    cosine similarity score — higher = more relevant (0.0 – 1.0)
 */
public record SearchResult(
        String content,
        Map<String, Object> metadata,
        double score
) {

    /**
     * Builds a {@link SearchResult} from a Spring AI {@link Document}.
     */
    public static SearchResult from(Document document) {
        return new SearchResult(
                document.getText(),
                document.getMetadata(),
                document.getScore() != null ? document.getScore() : 0.0
        );
    }
}
