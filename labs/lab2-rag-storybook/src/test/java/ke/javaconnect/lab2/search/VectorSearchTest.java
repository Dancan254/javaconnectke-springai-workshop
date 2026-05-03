package ke.javaconnect.lab2.search;

import ke.javaconnect.lab2.dto.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Vector Search Behaviour")
class VectorSearchTest {

    private final VectorStore vectorStore = mock(VectorStore.class);

    @Test
    @DisplayName("search request is built with the correct topK value")
    void searchRequestUsesCorrectTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        vectorStore.similaritySearch(
                SearchRequest.builder().query("Who is Ayana?").topK(5).similarityThreshold(0.65).build());

        verify(vectorStore).similaritySearch(argThat((SearchRequest req) -> req.getTopK() == 5));
    }

    @Test
    @DisplayName("search request is built with the correct similarity threshold")
    void searchRequestUsesCorrectThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        vectorStore.similaritySearch(
                SearchRequest.builder().query("lake baraka").topK(5).similarityThreshold(0.65).build());

        verify(vectorStore).similaritySearch(argThat((SearchRequest req) -> req.getSimilarityThreshold() == 0.65));
    }

    @Test
    @DisplayName("SearchResult.from() maps text, metadata, and score from document")
    void mapsDocumentContentToSearchResult() {
        Document doc = stubbedDocument(
                "Ayana arrived at the valley during the long rains.",
                Map.of("source", "the-chronicles-of-karibu-valley", "chunk_index", 3),
                0.92);

        SearchResult result = SearchResult.from(doc);

        assertThat(result.content()).isEqualTo("Ayana arrived at the valley during the long rains.");
        assertThat(result.score()).isEqualTo(0.92);
        assertThat(result.metadata()).containsEntry("source", "the-chronicles-of-karibu-valley");
        assertThat(result.metadata()).containsEntry("chunk_index", 3);
    }

    @Test
    @DisplayName("SearchResult.from() defaults score to 0.0 when document score is null")
    void usesZeroScoreWhenDocumentScoreIsNull() {
        Document doc = stubbedDocument("Content without a score.", Map.of(), null);

        SearchResult result = SearchResult.from(doc);

        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("SearchResult.from() preserves all metadata fields unchanged")
    void preservesAllMetadataFields() {
        Map<String, Object> metadata = Map.of(
                "source",      "the-chronicles-of-karibu-valley",
                "story",       "true",
                "chunk_index", 7
        );
        Document doc = stubbedDocument("Some story text.", metadata, 0.78);

        SearchResult result = SearchResult.from(doc);

        assertThat(result.metadata()).containsAllEntriesOf(metadata);
    }

    @Test
    @DisplayName("results returned in descending score order (highest relevance first)")
    void resultsOrderedByScoreDescending() {
        Document high = stubbedDocument("High relevance content",   Map.of(), 0.95);
        Document mid  = stubbedDocument("Medium relevance content", Map.of(), 0.80);
        Document low  = stubbedDocument("Low relevance content",    Map.of(), 0.67);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(high, mid, low));

        List<SearchResult> mapped = vectorStore
                .similaritySearch(SearchRequest.builder().query("test").topK(3).build())
                .stream()
                .map(SearchResult::from)
                .toList();

        assertThat(mapped).extracting(SearchResult::score)
                .containsExactly(0.95, 0.80, 0.67);
    }

    @Test
    @DisplayName("empty list returned when no documents meet the similarity threshold")
    void returnsEmptyListWhenNothingMeetsThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("completely unrelated topic")
                        .topK(5)
                        .similarityThreshold(0.65)
                        .build());

        assertThat(results).isEmpty();
    }

    @ParameterizedTest(name = "query ''{0}'' should retrieve chunk containing ''{1}''")
    @CsvSource({
            "keeper of listening,      Ayana",
            "river spirit Njoki,       Njoki",
            "whispering baobab tree,   baobab",
            "Lake Baraka water colour, copper",
            "Elder who shells beans,   Muthoni"
    })
    @DisplayName("semantic query retrieves expected story keyword")
    void semanticQueryRetrievesExpectedKeyword(String query, String expectedKeyword) {
        String keyword = expectedKeyword.trim();
        Document mockResult = stubbedDocument(
                "Content containing " + keyword + " and more story detail.",
                Map.of("source", "the-chronicles-of-karibu-valley"),
                0.85);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(mockResult));

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(5).similarityThreshold(0.65).build());

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getText()).containsIgnoringCase(keyword);
    }

    private Document stubbedDocument(String text, Map<String, Object> metadata, Double score) {
        Document doc = mock(Document.class);
        when(doc.getText()).thenReturn(text);
        when(doc.getMetadata()).thenReturn(metadata);
        when(doc.getScore()).thenReturn(score);
        return doc;
    }
}
