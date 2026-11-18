package com.learnai.property;

/**
 * @param landUse e.g. agricultural, farmhouse, orchard, residential plot
 * @param waterSource e.g. borewell, canal, river, well
 */
public record RuralDetails(String landUse, String soilType, String waterSource, Boolean irrigated, Boolean roadAccess,
        Boolean electricity) {
}
