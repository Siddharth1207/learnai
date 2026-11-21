package com.learnai.property;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.learnai.research.WebSource;

/**
 * Real-time location report bought with {@link PaidServiceCode#LOCATION_INSIGHTS}: upcoming government projects,
 * infrastructure, market activity, planning risks and an overall buy/rent/hold recommendation.
 *
 * @param location locality, city and state the searches were run for
 * @param suitabilityScore 0 (avoid) to 100 (strongly suitable), derived from the KPI impacts
 * @param summary AI recommendation narrative; sources stay available when no chat model is configured
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocationInsights(String location, Instant generatedAt, LocationRecommendation recommendation,
        int suitabilityScore, String summary, List<LocationKpi> kpis, List<WebSource> sources) {
}
