package com.learnai.property;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One cached property search, stored in MongoDB. Entries are only reused for the same hard filters and the same
 * corpus version, and are removed by the TTL index when they expire.
 *
 * @param filters signature of the hard filters (type, listing type, price range, centre, radius, topK)
 * @param embedding embedding of the cached query, or null when no embedding model was available
 * @param result the cached search response (without the voice transcript) as a BSON document
 */
@Document("property_search_cache")
record SearchCacheEntry(@Id String id, String filters, String query, List<Double> embedding, long corpusVersion,
        Instant createdAt, @Indexed(expireAfter = "0s") Instant expiresAt, long hits, org.bson.Document result) {
}
