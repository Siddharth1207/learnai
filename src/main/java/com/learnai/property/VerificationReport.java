package com.learnai.property;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Document-verification result attached to a {@link PaidServiceOrder}.
 *
 * @param reportUrl link to the downloadable report (null while {@code status} is PENDING)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationReport(VerificationStatus status, String summary, Instant verifiedAt, String reportUrl) {
}
