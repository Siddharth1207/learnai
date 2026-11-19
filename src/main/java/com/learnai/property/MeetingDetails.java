package com.learnai.property;

/**
 * Booked site visit attached to a {@link PaidServiceOrder}.
 *
 * @param scheduledAt local date and time of the visit as ISO-8601 with the IST offset,
 *        e.g. {@code 2026-11-25T15:30+05:30} (stored as a string because MongoDB has no default
 *        {@link java.time.OffsetDateTime} codec)
 * @param place where the buyer and seller meet, e.g. the apartment main gate
 */
public record MeetingDetails(String scheduledAt, MeetingMode mode, String place) {
}
