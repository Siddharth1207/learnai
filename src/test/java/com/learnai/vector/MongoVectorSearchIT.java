package com.learnai.vector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.learnai.vector.VectorSearchService.TextEntry;

/**
 * Runs against the MongoDB started by {@code docker compose up -d}, with a local bag-of-words embedding model instead
 * of Gemini. Enable with {@code LEARNAI_MONGODB_IT=true ./mvnw test -Dtest=MongoVectorSearchIT}.
 */
@EnabledIfEnvironmentVariable(named = "LEARNAI_MONGODB_IT", matches = "true")
@SpringBootTest(properties = { "spring.ai.vectorstore.type=mongodb-atlas",
        "spring.ai.vectorstore.mongodb.collection-name=vector_store_it",
        "spring.ai.vectorstore.mongodb.index-name=vector_index_it" })
class MongoVectorSearchIT {

    @Autowired
    private VectorSearchService service;

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanUp() {
        mongoTemplate.getCollection("vector_store_it").deleteMany(new org.bson.Document());
    }

    @Test
    void storesAndSearchesDocuments() throws Exception {
        service.addTexts(List.of(new TextEntry("invoice payment amount due", Map.of("category", "invoice")),
                new TextEntry("passport nationality birth date", Map.of("category", "identity")),
                new TextEntry("invoice tax total paid", Map.of("category", "invoice"))));

        List<VectorSearchHit> hits = awaitHits(() -> service.search("passport birth", 1, 0.0, null));
        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.text()).contains("passport");
            assertThat(hit.metadata()).containsEntry("category", "identity");
        });

        List<VectorSearchHit> invoices = service.search("passport birth", 5, 0.0, "category == 'invoice'");
        assertThat(invoices).hasSize(2).allSatisfy(hit -> assertThat(hit.metadata()).containsEntry("category", "invoice"));

        service.delete(invoices.stream().map(VectorSearchHit::id).toList());
        assertThat(service.search("invoice", 5, 0.0, "category == 'invoice'")).isEmpty();
    }

    /** The Atlas search index is built asynchronously, so retry until it returns results. */
    private static List<VectorSearchHit> awaitHits(java.util.function.Supplier<List<VectorSearchHit>> search)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (true) {
            try {
                List<VectorSearchHit> hits = search.get();
                if (!hits.isEmpty() || System.nanoTime() > deadline) {
                    return hits;
                }
            }
            catch (RuntimeException e) {
                if (System.nanoTime() > deadline) {
                    throw e;
                }
            }
            Thread.sleep(1000);
        }
    }

    @TestConfiguration
    static class LocalEmbeddingConfig {

        @Bean
        EmbeddingModel embeddingModel() {
            return new BagOfWordsEmbeddingModel();
        }
    }
}
