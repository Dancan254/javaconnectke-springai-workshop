package ke.javaconnect.lab2.controller;

import ke.javaconnect.lab2.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StoryController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.ai.azure.openai.chat.options.deployment-name=gpt-4o",
        "app.rag.top-k=5",
        "app.rag.similarity-threshold=0.65"
})
@DisplayName("StoryController — Web Layer Tests")
class StoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private ChatClient storyChatClient;

    @Nested
    @DisplayName("POST /story/ask")
    class AskEndpointTests {

        @Test
        @DisplayName("returns 200 with full chat response for a valid question")
        void returns200ForValidQuestion() throws Exception {
            mockChatClientReply("Ayana is the Keeper of Listening who arrived in the valley.");

            mockMvc.perform(post("/story/ask")
                            .param("message", "Who is Ayana?")
                            .param("conversationId", "test-session"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.conversationId").value("test-session"))
                    .andExpect(jsonPath("$.reply").value("Ayana is the Keeper of Listening who arrived in the valley."))
                    .andExpect(jsonPath("$.model").value("gpt-4o"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        @DisplayName("defaults conversationId to 'default' when not provided")
        void defaultsConversationIdToDefault() throws Exception {
            mockChatClientReply("The valley welcomes all who arrive.");

            mockMvc.perform(post("/story/ask")
                            .param("message", "Tell me about the valley"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conversationId").value("default"));
        }

        @Test
        @DisplayName("returns 400 when message is blank whitespace")
        void returns400ForBlankMessage() throws Exception {
            mockMvc.perform(post("/story/ask")
                            .param("message", "   "))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when message parameter is missing entirely")
        void returns400WhenMessageMissing() throws Exception {
            mockMvc.perform(post("/story/ask"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when message exceeds 2000 characters")
        void returns400WhenMessageTooLong() throws Exception {
            mockMvc.perform(post("/story/ask")
                            .param("message", "A".repeat(2001)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("accepts message at exactly 2000 characters")
        void accepts2000CharMessage() throws Exception {
            mockChatClientReply("Valid response.");

            mockMvc.perform(post("/story/ask")
                            .param("message", "A".repeat(2000)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /story/search")
    class SearchEndpointTests {

        @Test
        @DisplayName("returns 200 with ranked search results for a valid query")
        void returns200WithResultsForValidQuery() throws Exception {
            Document doc1 = stubbedDocument(
                    "Ayana arrived at the northern entrance of the valley.",
                    Map.of("source", "the-chronicles-of-karibu-valley", "chunk_index", 0),
                    0.92);
            Document doc2 = stubbedDocument(
                    "She carried a leather satchel and a sense of certainty.",
                    Map.of("source", "the-chronicles-of-karibu-valley", "chunk_index", 1),
                    0.85);
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(doc1, doc2));

            mockMvc.perform(get("/story/search")
                            .param("query", "Who arrived at the valley"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].content").isNotEmpty())
                    .andExpect(jsonPath("$[0].score").isNumber())
                    .andExpect(jsonPath("$[0].metadata").isMap());
        }

        @Test
        @DisplayName("returns empty array when no documents match the threshold")
        void returnsEmptyListWhenNoResults() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

            mockMvc.perform(get("/story/search")
                            .param("query", "quantum physics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("returns 400 when query is empty string")
        void returns400ForBlankQuery() throws Exception {
            mockMvc.perform(get("/story/search").param("query", ""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("passes custom topK to the vector store")
        void forwardsCustomTopKToVectorStore() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

            mockMvc.perform(get("/story/search")
                            .param("query", "Karibu Valley")
                            .param("topK", "3"))
                    .andExpect(status().isOk());

            verify(vectorStore).similaritySearch(argThat((SearchRequest req) -> req.getTopK() == 3));
        }

        @Test
        @DisplayName("returns 400 when query exceeds 500 characters")
        void returns400WhenQueryTooLong() throws Exception {
            mockMvc.perform(get("/story/search")
                            .param("query", "Q".repeat(501)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /story/documents")
    class DocumentsEndpointTests {

        @Test
        @DisplayName("returns stored document chunks with content and metadata")
        void returnsStoredDocumentsWithMetadata() throws Exception {
            Document doc = stubbedDocument(
                    "Karibu Valley content",
                    Map.of("source", "the-chronicles-of-karibu-valley", "chunk_index", 0),
                    0.75);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

            mockMvc.perform(get("/story/documents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].content").value("Karibu Valley content"))
                    .andExpect(jsonPath("$[0].metadata.source")
                            .value("the-chronicles-of-karibu-valley"));
        }

        @Test
        @DisplayName("uses default topK of 20 when no parameter given")
        void usesDefaultTopK20() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

            mockMvc.perform(get("/story/documents")).andExpect(status().isOk());

            verify(vectorStore).similaritySearch(argThat((SearchRequest req) -> req.getTopK() == 20));
        }

        @Test
        @DisplayName("passes custom topK to the vector store")
        void forwardsCustomTopKToVectorStore() throws Exception {
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

            mockMvc.perform(get("/story/documents").param("topK", "5"))
                    .andExpect(status().isOk());

            verify(vectorStore).similaritySearch(argThat((SearchRequest req) -> req.getTopK() == 5));
        }
    }

    private Document stubbedDocument(String text, Map<String, Object> metadata, double score) {
        Document doc = mock(Document.class);
        when(doc.getText()).thenReturn(text);
        when(doc.getMetadata()).thenReturn(metadata);
        when(doc.getScore()).thenReturn(score);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private void mockChatClientReply(String reply) {
        ChatClient.ChatClientRequestSpec promptSpec  = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec userSpec    = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec advisorSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec      callSpec    = mock(ChatClient.CallResponseSpec.class);

        when(storyChatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(userSpec);
        when(userSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(advisorSpec);
        when(advisorSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(reply);
    }
}
