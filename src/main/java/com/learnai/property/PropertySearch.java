package com.learnai.property;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A buyer's natural-language request plus optional hard filters.
 *
 * @param near centre point for {@code radiusKm}; also adds the distance to each result
 * @param topK number of listings to return (default 5)
 */
public record PropertySearch(
        @NotBlank String query,
        PropertyType propertyType,
        @PositiveOrZero BigDecimal minPrice,
        @Positive BigDecimal maxPrice,
        @Valid Coordinates near,
        @Positive Double radiusKm,
        @Min(1) @Max(20) Integer topK,
        ListingType listingType) {

    static final int DEFAULT_TOP_K = 5;

    /** Keeps older callers that predate the rent filter. */
    public PropertySearch(String query, PropertyType propertyType, BigDecimal minPrice, BigDecimal maxPrice,
            Coordinates near, Double radiusKm, Integer topK) {
        this(query, propertyType, minPrice, maxPrice, near, radiusKm, topK, null);
    }

    @JsonIgnore
    @AssertTrue(message = "radiusKm requires near coordinates")
    public boolean isRadiusWithCentre() {
        return radiusKm == null || near != null;
    }

    PropertySearch withQuery(String newQuery) {
        return new PropertySearch(newQuery, propertyType, minPrice, maxPrice, near, radiusKm, topK, listingType);
    }

    int limit() {
        return topK == null ? DEFAULT_TOP_K : topK;
    }
}
