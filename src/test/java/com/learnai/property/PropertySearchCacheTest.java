package com.learnai.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.learnai.audio.TranscriptionResult;

class PropertySearchCacheTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(1);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MongoConverter converter = mock(MongoConverter.class);

    private final PropertySearchCache cache = new PropertySearchCache(mongo, provider,
            Clock.fixed(NOW, ZoneOffset.UTC), true, 0.9, TTL);

    PropertySearchCacheTest() {
        when(mongo.getConverter()).thenReturn(converter);
    }

    @Test
    void lookupReturnsCachedResponseForSimilarEmbedding() {
        PropertySearchResult cached = cachedResult();
        when(mongo.findById(SearchCorpus.ID, SearchCorpus.class)).thenReturn(new SearchCorpus("corpus", 7, NOW));
        when(provider.getIfAvailable()).thenReturn(model);
        when(model.embed("flat in pune")).thenReturn(new float[] {1f, 0f});
        when(mongo.find(any(Query.class), eq(SearchCacheEntry.class))).thenReturn(List.of(entry(
                "c1", "2bhk in pune", List.of(0.99, 0.01), 7, NOW.plusSeconds(3600))));
        when(converter.read(eq(PropertySearchResult.class), any(Document.class))).thenReturn(cached);

        Optional<PropertySearchResult> hit = cache.lookup(search("flat in pune"));

        assertThat(hit).containsSame(cached);
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(SearchCacheEntry.class));
    }

    @Test
    void lookupSkipsEntriesBelowTheSimilarityThreshold() {
        when(mongo.findById(SearchCorpus.ID, SearchCorpus.class)).thenReturn(new SearchCorpus("corpus", 7, NOW));
        when(provider.getIfAvailable()).thenReturn(model);
        when(model.embed("flat in pune")).thenReturn(new float[] {1f, 0f});
        when(mongo.find(any(Query.class), eq(SearchCacheEntry.class))).thenReturn(List.of(entry(
                "c1", "farm in nashik", List.of(0.0, 1.0), 7, NOW.plusSeconds(3600))));

        assertThat(cache.lookup(search("flat in pune"))).isEmpty();
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(SearchCacheEntry.class));
    }

    @Test
    void lookupIgnoresEntriesOfAnOlderCorpusVersion() {
        when(mongo.findById(SearchCorpus.ID, SearchCorpus.class)).thenReturn(new SearchCorpus("corpus", 8, NOW));
        when(mongo.find(any(Query.class), eq(SearchCacheEntry.class))).thenReturn(List.of());

        assertThat(cache.lookup(search("flat in pune"))).isEmpty();
    }

    @Test
    void lookupFallsBackToExactTextWithoutEmbeddingModel() {
        when(mongo.findById(SearchCorpus.ID, SearchCorpus.class)).thenReturn(new SearchCorpus("corpus", 0, NOW));
        when(provider.getIfAvailable()).thenReturn(null);
        PropertySearchResult cached = cachedResult();
        when(mongo.find(any(Query.class), eq(SearchCacheEntry.class))).thenReturn(List.of(entry(
                "c1", "Flat in Pune", null, 0, NOW.plusSeconds(600))));
        when(converter.read(eq(PropertySearchResult.class), any(Document.class))).thenReturn(cached);

        assertThat(cache.lookup(search("flat in pune"))).containsSame(cached);
    }

    @Test
    void storeSavesEmbeddingTtlAndResponseWithoutTranscript() {
        when(mongo.findById(SearchCorpus.ID, SearchCorpus.class)).thenReturn(new SearchCorpus("corpus", 3, NOW));
        when(provider.getIfAvailable()).thenReturn(model);
        when(model.embed("flat in pune")).thenReturn(new float[] {0.5f, 0.5f});
        PropertySearchResult result = new PropertySearchResult(
                new TranscriptionResult("en", "flat", false, "flat", "t.txt"), "flat in pune", "Answer",
                List.of(), new MarketResearch(0, null, null, null, null, null, null, Map.of(), null));

        cache.store(search("flat in pune"), result);

        ArgumentCaptor<SearchCacheEntry> saved = ArgumentCaptor.forClass(SearchCacheEntry.class);
        verify(mongo).save(saved.capture());
        SearchCacheEntry entry = saved.getValue();
        assertThat(entry.filters()).isEqualTo(PropertySearchCache.signature(search("flat in pune")));
        assertThat(entry.query()).isEqualTo("flat in pune");
        assertThat(entry.embedding()).containsExactly(0.5, 0.5);
        assertThat(entry.corpusVersion()).isEqualTo(3);
        assertThat(entry.createdAt()).isEqualTo(NOW);
        assertThat(entry.expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(entry.result()).isNotNull();

        ArgumentCaptor<Object> written = ArgumentCaptor.forClass(Object.class);
        verify(converter).write(written.capture(), any(org.bson.conversions.Bson.class));
        assertThat(written.getValue()).isInstanceOfSatisfying(PropertySearchResult.class,
                cached -> assertThat(cached.transcription()).isNull());
    }

    @Test
    void invalidateBumpsTheCorpusVersion() {
        cache.invalidate();

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class),
                eq(SearchCorpus.class));
        org.bson.Document changes = update.getValue().getUpdateObject();
        assertThat(changes.keySet()).contains("$inc", "$set");
        assertThat((org.bson.Document) changes.get("$inc")).containsKey("version");
    }

    @Test
    void signatureSeparatesDifferentFilters() {
        String base = PropertySearchCache.signature(search("flat"));
        assertThat(base).isEqualTo(PropertySearchCache.signature(search("flat")));

        PropertySearch differentPrice = new PropertySearch("flat", null, null, new BigDecimal("5000000"), null, null, 5);
        assertThat(PropertySearchCache.signature(differentPrice)).isNotEqualTo(base);

        PropertySearch differentLimit = new PropertySearch("flat", null, null, null, null, null, 10);
        assertThat(PropertySearchCache.signature(differentLimit)).isNotEqualTo(base);

        PropertySearch rent = new PropertySearch("flat", null, null, null, null, null, 5, ListingType.RENT);
        assertThat(PropertySearchCache.signature(rent)).isNotEqualTo(base);
    }

    @Test
    void disabledCacheDoesNothing() {
        PropertySearchCache disabled = new PropertySearchCache(mongo, provider, Clock.fixed(NOW, ZoneOffset.UTC),
                false, 0.9, TTL);

        assertThat(disabled.lookup(search("flat"))).isEmpty();
        disabled.store(search("flat"), cachedResult());
        disabled.invalidate();

        verify(mongo, never()).find(any(Query.class), eq(SearchCacheEntry.class));
        verify(mongo, never()).save(any(SearchCacheEntry.class));
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(SearchCorpus.class));
    }

    private static PropertySearch search(String query) {
        return new PropertySearch(query, null, null, null, null, null, 5);
    }

    private static SearchCacheEntry entry(String id, String query, List<Double> embedding, long version, Instant expires) {
        return new SearchCacheEntry(id, PropertySearchCache.signature(search("flat in pune")), query, embedding, version,
                NOW.minusSeconds(60), expires, 2, new Document());
    }

    private static PropertySearchResult cachedResult() {
        return new PropertySearchResult(null, "flat in pune", "Cached answer", List.of(),
                new MarketResearch(1, new BigDecimal("9500000"), new BigDecimal("9500000"),
                        new BigDecimal("9500000"), new BigDecimal("9500000"), null, null, Map.of("Pune", 1L), null));
    }
}
