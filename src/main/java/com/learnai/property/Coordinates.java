package com.learnai.property;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** WGS84 latitude/longitude in decimal degrees. */
public record Coordinates(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Great-circle (haversine) distance in kilometres. */
    public double distanceKm(Coordinates other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
