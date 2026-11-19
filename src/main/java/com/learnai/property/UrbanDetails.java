package com.learnai.property;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * @param buildingType e.g. apartment, independent house, villa, commercial, plot
 * @param apartmentName apartment complex or project name
 * @param tower tower, block or flat number
 * @param furnishing e.g. furnished, semi-furnished, unfurnished
 * @param amenities free-text list of amenities
 */
public record UrbanDetails(String buildingType, String apartmentName, String tower, @PositiveOrZero Integer bedrooms,
        @PositiveOrZero Integer bathrooms, Integer floor, @PositiveOrZero Integer totalFloors, String furnishing,
        Boolean parking, String amenities) {

    /** Keeps older callers that predate apartment/tower/amenities. */
    public UrbanDetails(String buildingType, Integer bedrooms, Integer bathrooms, Integer floor, Integer totalFloors,
            String furnishing, Boolean parking) {
        this(buildingType, null, null, bedrooms, bathrooms, floor, totalFloors, furnishing, parking, null);
    }
}
