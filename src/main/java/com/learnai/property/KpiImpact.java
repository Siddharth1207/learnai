package com.learnai.property;

/** How a researched KPI affects the suitability of buying or renting a listing. */
public enum KpiImpact {
    /** Upcoming or existing development that should support demand and value. */
    POSITIVE,
    /** Neutral or already reflected in the price. */
    NEUTRAL,
    /** A risk such as land acquisition, demolition, flooding or a traffic bottleneck. */
    NEGATIVE,
    /** The public sources did not confirm anything for this KPI. */
    UNCERTAIN
}
