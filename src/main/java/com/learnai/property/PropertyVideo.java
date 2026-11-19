package com.learnai.property;

import java.time.Instant;

/**
 * A walkthrough video attached to a listing.
 *
 * @param url URL at which the video can be streamed by buyers
 */
public record PropertyVideo(String id, String url, String contentType, Long sizeBytes, Instant uploadedAt) {
}
