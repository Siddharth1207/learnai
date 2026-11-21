package com.learnai.property;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Market context for a buyer's search, computed from the available listings stored in MongoDB.
 * For {@link ListingType#RENT} listings {@code averagePrice}, {@code medianPrice}, {@code minPrice} and
 * {@code maxPrice} describe the monthly rent.
 *
 * @param averagePricePerSqFt average sale price or monthly rent per square foot (null when no listing has an area)
 * @param averageDeposit average security deposit of the rent listings in the cohort (null for sale searches)
 * @param listingsByCity number of cohort listings per city, largest first
 * @param aiSummary Gemini's short reading of the numbers (null when no chat model is configured)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarketResearch(
        int listingCount,
        BigDecimal averagePrice,
        BigDecimal medianPrice,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal averagePricePerSqFt,
        BigDecimal averageDeposit,
        Map<String, Long> listingsByCity,
        String aiSummary) {

    public MarketResearch withAiSummary(String summary) {
        return new MarketResearch(listingCount, averagePrice, medianPrice, minPrice, maxPrice, averagePricePerSqFt,
                averageDeposit, listingsByCity, summary);
    }
}
