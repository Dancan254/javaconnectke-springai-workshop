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
 *   <li>Connect to ChromaDB — the vector store running locally via Docker
 *       ({@code docker compose up -d}). Spring AI auto-creates the
 *       {@code karibu-stories} collection on first run.</li>
 *   <li>Boot all Spring beans — including ChromaVectorStore and the Azure OpenAI
 *       chat + embedding models.</li>
 *   <li>Trigger {@link ke.javaconnect.lab2.ingestion.StoryIngestionService}
 *       via {@code ApplicationReadyEvent} — reads {@code story.md}, splits it
 *       into token-sized chunks, embeds them with {@code text-embedding-3-large},
 *       and stores the vectors in ChromaDB.</li>
 * </ol>
 *
 * <p>Once running, callers can query the story through the REST API:
 * <pre>
 *   POST /story/ask?message=Who+is+Ayana&amp;conversationId=demo
 *   GET  /story/search?query=river+spirit
 *   GET  /story/documents
 * </pre>
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Docker Desktop running — start ChromaDB with {@code docker compose up -d}</li>
 *   <li>Azure OpenAI endpoint + API key in {@code keys.properties}</li>
 *   <li>Azure deployments: {@code gpt-4.1} and {@code text-embedding-3-large}</li>
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
