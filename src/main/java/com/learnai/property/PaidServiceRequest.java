package com.learnai.property;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * Buyer details for ordering a paid service. {@code preferredDate} ({@code yyyy-MM-dd}) and
 * {@code preferredTime} ({@code HH:mm}) are required for {@link PaidServiceCode#SITE_VISIT}.
 */
public record PaidServiceRequest(
        @NotBlank String buyerName,
        @NotBlank String buyerContact,
        String preferredDate,
        String preferredTime) {

    @AssertTrue(message = "preferredDate and preferredTime must be given together")
    public boolean isPreferredSlotComplete() {
        return (preferredDate == null || preferredDate.isBlank()) == (preferredTime == null || preferredTime.isBlank());
    }
}
