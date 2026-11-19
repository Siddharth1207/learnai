package com.learnai.property;

/**
 * @param landUse e.g. agricultural, farmhouse, orchard, residential plot
 * @param surveyNumber khasra/gat/survey number, private in public responses
 * @param ownershipType e.g. freehold, leasehold, ancestral
 * @param crop main crop, if any
 * @param waterSource e.g. borewell, canal, river, well
 */
public record RuralDetails(String landUse, String surveyNumber, String ownershipType, String crop, String soilType,
        String waterSource, Boolean irrigated, Boolean roadAccess, Boolean electricity) {

    /** Keeps older callers that predate the survey/ownership/crop fields. */
    public RuralDetails(String landUse, String soilType, String waterSource, Boolean irrigated, Boolean roadAccess,
            Boolean electricity) {
        this(landUse, null, null, null, soilType, waterSource, irrigated, roadAccess, electricity);
    }
}
