package ke.javaconnect.lab2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lab 2 — RAG Storybook: Application Entry Point.
 *
 * <p>This lab demonstrates Retrieval-Augmented Generation (RAG) with Spring AI.
 * On startup the application will:
 *
 * <ol>
 *   <li>Run Flyway migration — creates the {@code vector_store} table in Neon PostgreSQL
 *       with the {@code pgvector} extension and an IVFFlat index.</li>
 *   <li>Boot all Spring beans — including PgVectorStore and the Azure OpenAI
 *       chat + embedding models.</li>
 *   <li>Trigger {@link ke.javaconnect.lab2.ingestion.StoryIngestionService}
 *       via {@code ApplicationReadyEvent} — reads {@code story.md}, splits it
 *       into token-sized chunks, embeds them with {@code text-embedding-ada-002},
 *       and stores the vectors in Neon.</li>
 * </ol>
 *
 * <p>Once running, callers can query the story through the REST API:
 * <pre>
 *   POST /story/ask?message=Who+is+Ayana&amp;conversationId=demo
 *   GET  /story/search?query=river+spirit
 *   GET  /story/documents
 * </pre>
 *
 * <p>Prerequisites (fill in {@code keys.properties}):
 * <ul>
 *   <li>Azure OpenAI endpoint + API key</li>
 *   <li>Azure deployments: {@code gpt-4o} and {@code text-embedding-ada-002}</li>
 *   <li>Neon PostgreSQL connection URL, username, password</li>
 * </ul>
 *
 * @see ke.javaconnect.lab2.ingestion.StoryIngestionService
 * @see ke.javaconnect.lab2.config.RagConfig
 * @see ke.javaconnect.lab2.controller.StoryController
 */
@SpringBootApplication
public class Lab2Application {

    public static void main(String[] args) {
        SpringApplication.run(Lab2Application.class, args);
    }
}
