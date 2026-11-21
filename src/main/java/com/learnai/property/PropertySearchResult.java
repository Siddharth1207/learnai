package com.learnai.property;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learnai.audio.TranscriptionResult;

/**
 * @param transcription present only for voice searches
 * @param answer AI summary of the matching listings for the buyer (null when no chat model is configured)
 * @param marketResearch price statistics of the matching listings and Gemini's reading of them (null when the cache
 *        is disabled or when no model is available for the summary)
 */
public record PropertySearchResult(@JsonInclude(JsonInclude.Include.NON_NULL) TranscriptionResult transcription,
        String query, String answer, List<PropertyMatch> results,
        @JsonInclude(JsonInclude.Include.NON_NULL) MarketResearch marketResearch) {

    /** Keeps callers that predate market research. */
    public PropertySearchResult(TranscriptionResult transcription, String query, String answer,
            List<PropertyMatch> results) {
        this(transcription, query, answer, results, null);
    }

    /**
     * @param score match score (0..1): vector similarity, or the share of the request's keywords found in the listing
     * @param distanceKm distance from the search's {@code near} point, if given
     */
    public record PropertyMatch(Property property, Double score, Double distanceKm) {
    }
}
