package com.learnai.vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser.FilterExpressionParseException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Stores text as embeddings in the MongoDB Atlas vector store and runs semantic similarity searches.
 * The vector store exists only when {@code spring.ai.vectorstore.type=mongodb-atlas} and an embedding model is configured.
 */
@Service
public class VectorSearchService {

    public static final String SOURCE_METADATA = "source";

    private final ObjectProvider<VectorStore> vectorStore;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

    public VectorSearchService(ObjectProvider<VectorStore> vectorStore) {
        this.vectorStore = vectorStore;
    }

    public boolean isEnabled() {
        return vectorStore.getIfAvailable() != null;
    }

    public List<String> addTexts(List<TextEntry> entries) {
        List<Document> documents = entries.stream()
                .map(entry -> new Document(entry.text(), entry.metadata() == null ? Map.of() : entry.metadata()))
                .toList();
        store().add(documents);
        return documents.stream().map(Document::getId).toList();
    }

    /**
     * Extracts text from the file with Apache Tika, splits it into chunks and stores each chunk with
     * {@code source} metadata set to the file name.
     */
    public List<String> addFile(Resource file, String fileName, Map<String, Object> metadata) {
        VectorStore store = store();
        List<Document> chunks = splitter.apply(new TikaDocumentReader(file).get()).stream()
                .filter(chunk -> chunk.getText() != null && !chunk.getText().isBlank())
                .map(chunk -> {
                    Map<String, Object> chunkMetadata = new HashMap<>(chunk.getMetadata());
                    chunkMetadata.putAll(metadata);
                    chunkMetadata.put(SOURCE_METADATA, fileName);
                    return new Document(chunk.getText(), chunkMetadata);
                })
                .toList();
        if (!chunks.isEmpty()) {
            store.add(chunks);
        }
        return chunks.stream().map(Document::getId).toList();
    }

    public List<VectorSearchHit> search(String query, int topK, double similarityThreshold, String filter) {
        VectorStore store = store();
        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold);
        if (filter != null && !filter.isBlank()) {
            try {
                request.filterExpression(filter);
            }
            catch (FilterExpressionParseException e) {
                throw new InvalidFilterExpressionException(filter, e);
            }
        }
        return store.similaritySearch(request.build()).stream()
                .map(doc -> new VectorSearchHit(doc.getId(), doc.getText(), doc.getScore(), doc.getMetadata()))
                .toList();
    }

    public void delete(List<String> ids) {
        store().delete(ids);
    }

    private VectorStore store() {
        VectorStore store = vectorStore.getIfAvailable();
        if (store == null) {
            throw new VectorStoreNotConfiguredException();
        }
        return store;
    }

    public record TextEntry(String text, Map<String, Object> metadata) {
    }
}
