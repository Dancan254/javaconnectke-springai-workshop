package ke.javaconnect.lab2.integration;

import ke.javaconnect.lab2.dto.ChatResponse;
import ke.javaconnect.lab2.dto.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("RAG Storybook — Live Integration Tests (Azure OpenAI + Neon)")
class RagStorybookIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private VectorStore vectorStore;

    @Value("${azure.openai.api-key:REPLACE_ME}")
    private String azureApiKey;

    @BeforeEach
    void skipWhenCredentialsNotConfigured() {
        assumeFalse(
                "REPLACE_ME".equals(azureApiKey),
                "Skipping integration test — fill in keys.properties with real Azure + Neon credentials."
        );
    }

    @Test
    @Order(1)
    @DisplayName("vector store is populated with story chunks after startup ingestion")
    void vectorStoreIsPopulatedAfterStartup() {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Karibu Valley")
                        .topK(1)
                        .similarityThreshold(0.0)
                        .build()
        );

        assertThat(results)
                .as("Vector store should contain at least one story chunk after ingestion")
                .isNotEmpty();
        assertThat(results.getFirst().getText())
                .as("Ingested chunks should contain real story content")
                .isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("query 'keeper of listening' retrieves Ayana's introduction passage")
    void vectorSearchFindsAyanaForKeeperOfListeningQuery() {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("the keeper of listening")
                        .topK(3)
                        .similarityThreshold(0.60)
                        .build()
        );

        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(d -> d.getText().contains("Ayana")))
                .as("Top results for 'keeper of listening' should mention Ayana")
                .isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("query 'river spirit' retrieves the Njoki passage")
    void vectorSearchFindsNjokiForRiverSpiritQuery() {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("the river spirit who speaks to visitors")
                        .topK(3)
                        .similarityThreshold(0.60)
                        .build()
        );

        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(d -> d.getText().contains("Njoki")))
                .as("Top results for 'river spirit' should mention Njoki")
                .isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("query 'old woman who shells beans' retrieves Elder Muthoni's passage")
    void vectorSearchFindsMuthoniForElderQuery() {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("old woman who shells beans with efficiency")
                        .topK(3)
                        .similarityThreshold(0.55)
                        .build()
        );

        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(d -> d.getText().contains("Muthoni")))
                .as("Top results for 'old woman who shells beans' should mention Muthoni")
                .isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("all retrieved chunks have required metadata fields")
    void retrievedChunksHaveRequiredMetadata() {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Karibu Valley story")
                        .topK(5)
                        .similarityThreshold(0.0)
                        .build()
        );

        assertThat(results).isNotEmpty();
        results.forEach(doc -> {
            assertThat(doc.getMetadata()).as("Each chunk should have 'source' metadata").containsKey("source");
            assertThat(doc.getMetadata()).as("Each chunk should have 'chunk_index' metadata").containsKey("chunk_index");
        });
    }

    @Test
    @Order(6)
    @DisplayName("POST /story/ask returns a grounded answer about Ayana")
    void askEndpointReturnsGroundedAnswerAboutAyana() {
        String url = "http://localhost:" + port + "/story/ask?message=Who+is+Ayana&conversationId=integration-test";

        ResponseEntity<ChatResponse> response = restTemplate.postForEntity(url, null, ChatResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        ChatResponse body = response.getBody();
        assertThat(body.reply()).as("RAG answer should be non-empty").isNotBlank();
        assertThat(body.conversationId()).isEqualTo("integration-test");
        assertThat(body.model()).isNotBlank();
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    @Order(7)
    @DisplayName("GET /story/search returns ranked results with scores")
    void searchEndpointReturnsRankedResultsWithScores() {
        String url = "http://localhost:" + port + "/story/search?query=whispering+baobab+tree";

        ResponseEntity<SearchResult[]> response = restTemplate.getForEntity(url, SearchResult[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        SearchResult[] results = response.getBody();
        assertThat(results).isNotEmpty();
        assertThat(results[0].score()).isGreaterThan(0.0);
        assertThat(results[0].content()).isNotBlank();
    }

    @Test
    @Order(8)
    @DisplayName("multi-turn conversation maintains context across questions")
    void multiTurnConversationMaintainsContext() {
        String base = "http://localhost:" + port + "/story/ask";
        String sessionId = "integration-memory-test";

        restTemplate.postForEntity(
                base + "?message=Who+is+Ayana&conversationId=" + sessionId,
                null, ChatResponse.class);

        ResponseEntity<ChatResponse> followUp = restTemplate.postForEntity(
                base + "?message=What+special+gift+does+she+have&conversationId=" + sessionId,
                null, ChatResponse.class);

        assertThat(followUp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(followUp.getBody()).isNotNull();
        assertThat(followUp.getBody().reply())
                .as("Follow-up question should produce a non-empty, context-aware answer")
                .isNotBlank();
    }
}
