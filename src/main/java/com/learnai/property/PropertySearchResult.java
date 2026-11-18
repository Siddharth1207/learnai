package com.learnai.property;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learnai.audio.TranscriptionResult;

/**
 * @param transcription present only for voice searches
 * @param answer AI summary of the matching listings for the buyer (null when no chat model is configured)
 */
public record PropertySearchResult(@JsonInclude(JsonInclude.Include.NON_NULL) TranscriptionResult transcription,
        String query, String answer, List<PropertyMatch> results) {

    /**
     * @param score vector similarity (0..1)
     * @param distanceKm distance from the search's {@code near} point, if given
     */
    public record PropertyMatch(Property property, Double score, Double distanceKm) {
    }
}
