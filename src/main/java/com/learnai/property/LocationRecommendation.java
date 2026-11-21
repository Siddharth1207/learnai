package com.learnai.property;

/** What the location research suggests doing with the listing. */
public enum LocationRecommendation {
    BUY,
    RENT,
    /** Wait: the location has unresolved risks or too little evidence. */
    HOLD
}
