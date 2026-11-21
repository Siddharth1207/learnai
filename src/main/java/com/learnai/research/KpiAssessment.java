package com.learnai.research;

import com.learnai.property.KpiImpact;

/** The model's verdict and summary for one KPI of the location report. */
public record KpiAssessment(KpiImpact impact, String summary) {
}
