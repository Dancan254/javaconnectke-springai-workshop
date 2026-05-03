package ke.javaconnect.lab2.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoryIngestionService")
class StoryIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private StoryIngestionService ingestionService;

    @Captor
    private ArgumentCaptor<List<Document>> documentsCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                ingestionService,
                "storyResource",
                new ClassPathResource("story/the-chronicles-of-karibu-valley.md")
        );
    }

    @Nested
    @DisplayName("Idempotency Guard")
    class IdempotencyTests {

        @Test
        @DisplayName("skips ingestion when story is already in vector store")
        void skipsIngestionWhenAlreadyIngested() {
            Document existingDoc = new Document("Karibu Valley content already here",
                    Map.of("source", "the-chronicles-of-karibu-valley"));
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(existingDoc));

            ingestionService.ingestStory();

            verify(vectorStore, never()).add(any());
        }

        @Test
        @DisplayName("runs full ETL when vector store is empty")
        void runsIngestionWhenStoreIsEmpty() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(Collections.emptyList());

            ingestionService.ingestStory();

            verify(vectorStore, times(1)).add(documentsCaptor.capture());
            assertThat(documentsCaptor.getValue()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ETL Pipeline")
    class EtlPipelineTests {

        @BeforeEach
        void setUpEmpty() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("produces multiple chunks from the story markdown")
        void producesMultipleChunksFromStory() {
            ingestionService.ingestStory();

            verify(vectorStore).add(documentsCaptor.capture());
            assertThat(documentsCaptor.getValue()).hasSizeGreaterThan(5);
        }

        @Test
        @DisplayName("all chunks have the required 'source' metadata field")
        void allChunksHaveSourceMetadata() {
            ingestionService.ingestStory();

            verify(vectorStore).add(documentsCaptor.capture());
            List<Document> chunks = documentsCaptor.getValue();

            assertThat(chunks).allSatisfy(doc ->
                    assertThat(doc.getMetadata())
                            .containsKey("source")
                            .extractingByKey("source")
                            .asString()
                            .isEqualTo("the-chronicles-of-karibu-valley")
            );
        }

        @Test
        @DisplayName("all chunks have a 'chunk_index' metadata field")
        void allChunksHaveChunkIndexMetadata() {
            ingestionService.ingestStory();

            verify(vectorStore).add(documentsCaptor.capture());
            assertThat(documentsCaptor.getValue()).allSatisfy(doc ->
                    assertThat(doc.getMetadata()).containsKey("chunk_index")
            );
        }

        @Test
        @DisplayName("chunk indices are sequential starting from 0")
        void chunkIndicesAreSequential() {
            ingestionService.ingestStory();

            verify(vectorStore).add(documentsCaptor.capture());
            List<Document> chunks = documentsCaptor.getValue();

            for (int i = 0; i < chunks.size(); i++) {
                int finalI = i;
                assertThat(chunks.get(i).getMetadata())
                        .extractingByKey("chunk_index")
                        .isEqualTo(finalI);
            }
        }

        @Test
        @DisplayName("no chunk has blank content")
        void noChunkHasBlankContent() {
            ingestionService.ingestStory();

            verify(vectorStore).add(documentsCaptor.capture());
            assertThat(documentsCaptor.getValue()).allSatisfy(doc ->
                    assertThat(doc.getText()).isNotBlank()
            );
        }

        @Test
        @DisplayName("story content is present in chunks")
        void storyContentPresentInChunks() {
            ingestionService.ingestStory();

            verify(vectorStore).add(documentsCaptor.capture());
            List<Document> chunks = documentsCaptor.getValue();

            boolean containsAyana = chunks.stream()
                    .anyMatch(d -> d.getText().contains("Ayana"));
            assertThat(containsAyana)
                    .as("At least one chunk should mention character 'Ayana'")
                    .isTrue();
        }

        @Test
        @DisplayName("story content contains known place names")
        void storyContentContainsPlaceNames() {
            ingestionService.ingestStory();

            verify(vectorStore).add(documentsCaptor.capture());
            List<Document> chunks = documentsCaptor.getValue();

            boolean containsKaribu = chunks.stream()
                    .anyMatch(d -> d.getText().contains("Karibu"));
            assertThat(containsKaribu)
                    .as("At least one chunk should reference 'Karibu'")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Metadata Integrity")
    class MetadataTests {

        @BeforeEach
        void setUpEmpty() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("story marker metadata is present on all chunks")
        void storyMarkerMetadataPresentOnAllChunks() {
            ingestionService.ingestStory();

            verify(vectorStore).add(documentsCaptor.capture());
            assertThat(documentsCaptor.getValue()).allSatisfy(doc ->
                    assertThat(doc.getMetadata())
                            .containsKey("story")
                            .extractingByKey("story")
                            .isEqualTo("true")
            );
        }
    }
}
