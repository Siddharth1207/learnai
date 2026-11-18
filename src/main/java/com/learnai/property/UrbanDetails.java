package com.learnai.property;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * @param buildingType e.g. apartment, independent house, villa, commercial, plot
 * @param furnishing e.g. furnished, semi-furnished, unfurnished
 */
public record UrbanDetails(String buildingType, @PositiveOrZero Integer bedrooms, @PositiveOrZero Integer bathrooms,
        Integer floor, @PositiveOrZero Integer totalFloors, String furnishing, Boolean parking) {
}
