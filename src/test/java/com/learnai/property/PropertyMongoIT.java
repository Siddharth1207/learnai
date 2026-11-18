package com.learnai.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MimeType;

import com.learnai.audio.InputLanguage;
import com.learnai.audio.SpeechToText;
import com.learnai.vector.BagOfWordsEmbeddingModel;

/**
 * End-to-end listing, RAG search and purchase against the MongoDB started by {@code docker compose up -d}, using a local
 * embedding model and stubbed speech-to-text/extraction. Enable with
 * {@code LEARNAI_MONGODB_IT=true ./mvnw test -Dtest=PropertyMongoIT}.
 */
@EnabledIfEnvironmentVariable(named = "LEARNAI_MONGODB_IT", matches = "true")
@SpringBootTest(properties = { "MONGODB_DATABASE=learnai_property_it", "spring.ai.vectorstore.type=mongodb-atlas",
        "spring.ai.vectorstore.mongodb.collection-name=vector_store_property_it",
        "spring.ai.vectorstore.mongodb.index-name=vector_index_property_it" })
class PropertyMongoIT {

    @Autowired
    private PropertyService service;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private SpeechToText speechToText;

    @MockitoBean
    private PropertyExtractor extractor;

    @AfterEach
    void cleanUp() {
        mongoTemplate.getCollection("properties").drop();
        mongoTemplate.getCollection("purchases").drop();
        mongoTemplate.getCollection("vector_store_property_it").deleteMany(new org.bson.Document());
    }

    @Test
    void listSearchAndBuyProperties() throws Exception {
        Property flat = service.createFromForm(PropertyFixtures.urbanFlat());
        when(speechToText.transcribe(any(), any(), any())).thenReturn("I am selling a mango orchard farm");
        when(extractor.extract("I am selling a mango orchard farm")).thenReturn(PropertyFixtures.ruralFarm());
        Property farm = service.createFromVoice(new ByteArrayResource(new byte[] {1}), MimeType.valueOf("audio/mpeg"),
                InputLanguage.ENGLISH, PropertyDetails.empty()).property();

        assertThat(service.get(farm.id()).details().price()).isEqualByComparingTo("4000000");
        assertThat(service.list(PropertyType.RURAL, null)).extracting(Property::id).containsExactly(farm.id());
        assertThat(flat.vectorDocumentId()).isNotNull();

        PropertySearch orchard = new PropertySearch("mango orchard farm land", null, null, null, null, null, 1);
        PropertySearchResult result = awaitResults(() -> service.search(orchard));
        assertThat(result.results()).singleElement().satisfies(m -> assertThat(m.property().id()).isEqualTo(farm.id()));

        PropertySearch urbanOnly = new PropertySearch("mango orchard farm land", PropertyType.URBAN, null, null,
                PropertyFixtures.urbanFlat().coordinates(), 5.0, 5);
        assertThat(service.search(urbanOnly).results()).extracting(m -> m.property().id()).containsExactly(flat.id());

        Purchase purchase = service.purchase(farm.id(), new PurchaseRequest("Neha Singh", "neha@example.com"));
        assertThat(purchase.sellerName()).isEqualTo("Ramesh Patil");
        assertThat(service.get(farm.id()).status()).isEqualTo(PropertyStatus.SOLD);
        assertThat(service.getPurchase(farm.id()).buyerName()).isEqualTo("Neha Singh");
        assertThatThrownBy(() -> service.purchase(farm.id(), new PurchaseRequest("Other", "x")))
                .isInstanceOf(PropertyNotAvailableException.class);
        assertThat(service.search(orchard).results()).extracting(m -> m.property().id()).doesNotContain(farm.id());
    }

    /** The Atlas search index is built asynchronously, so retry until it returns results. */
    private static PropertySearchResult awaitResults(Supplier<PropertySearchResult> search) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (true) {
            try {
                PropertySearchResult result = search.get();
                if (!result.results().isEmpty() || System.nanoTime() > deadline) {
                    return result;
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
