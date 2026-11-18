package com.learnai.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;

import com.learnai.vector.VectorSearchService.TextEntry;

class VectorSearchServiceTest {

    private final VectorStore store = mock(VectorStore.class);

    @Test
    @SuppressWarnings("unchecked")
    void addsTextsWithMetadataAndReturnsIds() {
        List<String> ids = service(store).addTexts(List.of(new TextEntry("hello", Map.of("category", "greeting")),
                new TextEntry("bye", null)));

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).add(captor.capture());
        List<Document> added = captor.getValue();
        assertThat(added).extracting(Document::getText).containsExactly("hello", "bye");
        assertThat(added.get(0).getMetadata()).containsEntry("category", "greeting");
        assertThat(ids).containsExactlyElementsOf(added.stream().map(Document::getId).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void addsFileChunksWithSourceMetadata() {
        ByteArrayResource file = new ByteArrayResource("Invoice for John Doe".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "invoice.txt";
            }
        };

        List<String> ids = service(store).addFile(file, "invoice.txt", Map.of("category", "invoice"));

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).add(captor.capture());
        assertThat(ids).hasSize(captor.getValue().size()).isNotEmpty();
        Document chunk = captor.getValue().get(0);
        assertThat(chunk.getText()).contains("John Doe");
        assertThat(chunk.getMetadata()).containsEntry("source", "invoice.txt").containsEntry("category", "invoice");
    }

    @Test
    void searchesWithTopKThresholdAndFilter() {
        Document hit = Document.builder().id("1").text("hello").metadata(Map.of("category", "greeting")).score(0.9).build();
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(hit));

        List<VectorSearchHit> hits = service(store).search("hi", 3, 0.5, "category == 'greeting'");

        assertThat(hits).containsExactly(new VectorSearchHit("1", "hello", 0.9, Map.of("category", "greeting")));
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertThat(request.getQuery()).isEqualTo("hi");
        assertThat(request.getTopK()).isEqualTo(3);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.5);
        assertThat(request.getFilterExpression()).isNotNull();
    }

    @Test
    void rejectsInvalidFilter() {
        assertThatThrownBy(() -> service(store).search("hi", 3, 0, "category ==="))
                .isInstanceOf(InvalidFilterExpressionException.class);
    }

    @Test
    void deletesByIds() {
        service(store).delete(List.of("1", "2"));

        verify(store).delete(List.of("1", "2"));
    }

    @Test
    void failsWhenVectorStoreNotConfigured() {
        assertThatThrownBy(() -> service(null).search("hi", 3, 0, null))
                .isInstanceOf(VectorStoreNotConfiguredException.class);
    }

    @SuppressWarnings("unchecked")
    private static VectorSearchService service(VectorStore store) {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        return new VectorSearchService(provider);
    }
}
