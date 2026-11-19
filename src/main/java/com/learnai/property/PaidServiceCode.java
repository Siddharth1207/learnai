package com.learnai.property;

import java.math.BigDecimal;

/** Paid services a buyer can order for a listing. Payment is simulated until a provider is wired in. */
public enum PaidServiceCode {

    /** Returns the full street address, postal code and seller contact. */
    CONTACT_UNLOCK(new BigDecimal("199")),
    /** Orders a title/document verification report. */
    DOCUMENT_VERIFICATION(new BigDecimal("499")),
    /** Books a site visit (requires {@code preferredDate} and {@code preferredTime}). */
    SITE_VISIT(new BigDecimal("199"));

    private final BigDecimal amount;

    PaidServiceCode(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal amount() {
        return amount;
    }
}
