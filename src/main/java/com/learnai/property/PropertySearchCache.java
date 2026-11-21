package com.learnai.property;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Semantic cache for buyer searches, stored in MongoDB.
 *
 * <p>A cache entry stores the query, its embedding (Gemini), the signature of the hard filters and the full search
 * response. A new request reuses an entry when the filters are identical, the entry is younger than the TTL, the
 * corpus version is unchanged and the query embeddings are at least {@code learnai.search-cache.similarity-threshold}
 * (0.9 by default) similar. Cache hits skip both the vector search and the LLM call.
 *
 * <p>Without an embedding model (free/local setups) the cache falls back to exact, case-insensitive query matching.
 * Creating or selling a listing bumps {@link SearchCorpus}, so changed listings are never served from the cache.
 */
@Component
public class PropertySearchCache {

    /** Bounds the number of embeddings compared for one lookup. */
    static final int MAX_CANDIDATES = 50;

    private static final Logger log = LoggerFactory.getLogger(PropertySearchCache.class);

    private final MongoTemplate mongo;
    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final Clock clock;
    private final boolean enabled;
    private final double similarityThreshold;
    private final Duration ttl;

    @org.springframework.beans.factory.annotation.Autowired
    public PropertySearchCache(MongoTemplate mongo, ObjectProvider<EmbeddingModel> embeddingModel,
            @Value("${learnai.search-cache.enabled:true}") boolean enabled,
            @Value("${learnai.search-cache.similarity-threshold:0.9}") double similarityThreshold,
            @Value("${learnai.search-cache.ttl:PT1H}") Duration ttl) {
        this(mongo, embeddingModel, Clock.systemUTC(), enabled, similarityThreshold, ttl);
    }

    PropertySearchCache(MongoTemplate mongo, ObjectProvider<EmbeddingModel> embeddingModel, Clock clock,
            boolean enabled, double similarityThreshold, Duration ttl) {
        this.mongo = mongo;
        this.embeddingModel = embeddingModel;
        this.clock = clock;
        this.enabled = enabled;
        this.similarityThreshold = similarityThreshold;
        this.ttl = ttl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Returns the cached response for this request, if a fresh and similar enough entry exists. */
    public Optional<PropertySearchResult> lookup(PropertySearch search) {
        if (!enabled || search.query() == null || search.query().isBlank()) {
            return Optional.empty();
        }
        try {
            List<SearchCacheEntry> entries = mongo.find(query(where("filters").is(signature(search))
                    .and("corpusVersion").is(corpusVersion())
                    .and("expiresAt").gt(clock.instant()))
                    .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                    .limit(MAX_CANDIDATES), SearchCacheEntry.class);
            if (entries.isEmpty()) {
                return Optional.empty();
            }
            float[] embedding = embed(search.query());
            SearchCacheEntry best = null;
            double bestScore = -1;
            for (SearchCacheEntry entry : entries) {
                if (embedding != null && entry.embedding() != null && !entry.embedding().isEmpty()) {
                    double score = cosine(embedding, entry.embedding());
                    if (score > bestScore) {
                        best = entry;
                        bestScore = score;
                    }
                }
                else if (embedding == null && sameText(entry.query(), search.query())) {
                    best = entry;
                    bestScore = 1;
                    break;
                }
            }
            if (best == null || bestScore < similarityThreshold || best.result() == null) {
                return Optional.empty();
            }
            mongo.updateFirst(query(where("_id").is(best.id())), new Update().inc("hits", 1), SearchCacheEntry.class);
            PropertySearchResult cached = mongo.getConverter().read(PropertySearchResult.class, best.result());
            log.debug("Search cache hit (score {}) for '{}'", bestScore, search.query());
            return Optional.ofNullable(cached);
        }
        catch (Exception e) {
            log.warn("Search cache lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Stores the response under the filters signature; failures only log, the response is still returned. */
    public void store(PropertySearch search, PropertySearchResult result) {
        if (!enabled || search.query() == null || search.query().isBlank()) {
            return;
        }
        try {
            Instant now = clock.instant();
            List<Double> embedding = toList(embed(search.query()));
            Document cached = new Document();
            // The transcript is request-specific, never part of the cached response.
            mongo.getConverter().write(new PropertySearchResult(null, result.query(), result.answer(),
                    result.results(), result.marketResearch()), cached);
            mongo.save(new SearchCacheEntry(UUID.randomUUID().toString(), signature(search), search.query().trim(),
                    embedding, corpusVersion(), now, now.plus(ttl), 0, cached));
        }
        catch (Exception e) {
            log.warn("Failed to cache search '{}': {}", search.query(), e.getMessage());
        }
    }

    /** Invalidates every cached search: called after a listing is created or sold. */
    public void invalidate() {
        if (!enabled) {
            return;
        }
        try {
            mongo.findAndModify(query(where("_id").is(SearchCorpus.ID)),
                    new Update().inc("version", 1).set("updatedAt", clock.instant()),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), SearchCorpus.class);
        }
        catch (RuntimeException e) {
            log.warn("Failed to bump the search corpus version: {}", e.getMessage());
        }
    }

    long corpusVersion() {
        SearchCorpus corpus = mongo.findById(SearchCorpus.ID, SearchCorpus.class);
        return corpus == null ? 0 : corpus.version();
    }

    /** Hard filters that must be identical for a cached response to be reused. */
    static String signature(PropertySearch search) {
        Coordinates near = search.near();
        return String.join("|",
                Objects.toString(search.propertyType(), "-"),
                Objects.toString(search.listingType(), "-"),
                price(search.minPrice()), price(search.maxPrice()),
                near == null ? "-" : "%.3f,%.3f".formatted(near.latitude(), near.longitude()),
                search.radiusKm() == null ? "-" : "%.3f".formatted(search.radiusKm()),
                String.valueOf(search.limit()));
    }

    private static String price(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private float[] embed(String text) {
        EmbeddingModel model = embeddingModel.getIfAvailable();
        if (model == null) {
            return null;
        }
        try {
            return model.embed(text);
        }
        catch (RuntimeException e) {
            log.warn("Failed to embed search query for the cache: {}", e.getMessage());
            return null;
        }
    }

    private static List<Double> toList(float[] embedding) {
        if (embedding == null) {
            return null;
        }
        Double[] values = new Double[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            values[i] = (double) embedding[i];
        }
        return List.of(values);
    }

    static double cosine(float[] a, List<Double> b) {
        if (b.size() != a.length) {
            return -1;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            double value = b.get(i);
            dot += a[i] * value;
            normA += a[i] * a[i];
            normB += value * value;
        }
        return normA == 0 || normB == 0 ? -1 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static boolean sameText(String cached, String query) {
        return cached != null && query != null && cached.trim().toLowerCase(Locale.ROOT)
                .equals(query.trim().toLowerCase(Locale.ROOT));
    }
}
