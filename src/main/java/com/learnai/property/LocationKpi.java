package com.learnai.property;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.learnai.research.WebSource;

/**
 * One researched KPI of the paid {@link PaidServiceCode#LOCATION_INSIGHTS} report.
 *
 * @param summary short AI summary of what the sources say about this KPI near the listing
 * @param sources live web sources backing the summary (municipal, state government and news pages)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocationKpi(String key, String label, KpiImpact impact, String summary, List<WebSource> sources) {
}
