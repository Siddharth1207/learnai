package com.learnai.property;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Monotonic version of the listing corpus, stored in MongoDB. It is bumped whenever a listing is created or sold,
 * which makes every cached search older than the change unusable.
 */
@Document("property_search_state")
record SearchCorpus(@Id String id, long version, Instant updatedAt) {

    static final String ID = "corpus";
}
