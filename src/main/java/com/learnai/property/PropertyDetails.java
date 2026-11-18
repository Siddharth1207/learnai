package com.learnai.property;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Property details entered by a seller, either through the form or extracted from a voice description.
 * {@code urban} applies only to {@link PropertyType#URBAN} and {@code rural} only to {@link PropertyType#RURAL}.
 *
 * @param currency ISO 4217 code; defaults to INR
 */
public record PropertyDetails(
        @NotBlank String sellerName,
        @NotBlank String sellerContact,
        @NotNull PropertyType propertyType,
        String title,
        String description,
        @NotNull @Valid Address address,
        @NotNull @Valid Coordinates coordinates,
        @NotNull @Positive BigDecimal area,
        @NotNull AreaUnit areaUnit,
        @NotNull @Positive BigDecimal price,
        String currency,
        @Valid UrbanDetails urban,
        @Valid RuralDetails rural) {

    @JsonIgnore
    @AssertTrue(message = "urban details are only allowed for URBAN properties and rural details only for RURAL properties")
    public boolean isDetailsMatchingType() {
        return (urban == null || propertyType != PropertyType.RURAL) && (rural == null || propertyType != PropertyType.URBAN);
    }

    /** Returns a copy where every non-null top-level field of {@code overrides} replaces the value in this record. */
    public PropertyDetails withOverrides(PropertyDetails overrides) {
        return new PropertyDetails(
                pick(overrides.sellerName, sellerName),
                pick(overrides.sellerContact, sellerContact),
                pick(overrides.propertyType, propertyType),
                pick(overrides.title, title),
                pick(overrides.description, description),
                pick(overrides.address, address),
                pick(overrides.coordinates, coordinates),
                pick(overrides.area, area),
                pick(overrides.areaUnit, areaUnit),
                pick(overrides.price, price),
                pick(overrides.currency, currency),
                pick(overrides.urban, urban),
                pick(overrides.rural, rural));
    }

    /** Fills in defaults (currency, title) and drops details that do not belong to the property type. */
    PropertyDetails normalized() {
        String normalizedCurrency = currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase();
        String normalizedTitle = title == null || title.isBlank()
                ? "%s property in %s".formatted(propertyType == PropertyType.URBAN ? "Urban" : "Rural", address.city())
                : title;
        return new PropertyDetails(sellerName, sellerContact, propertyType, normalizedTitle, description, address,
                coordinates, area, areaUnit, price, normalizedCurrency,
                propertyType == PropertyType.URBAN ? urban : null,
                propertyType == PropertyType.RURAL ? rural : null);
    }

    public static PropertyDetails empty() {
        return new PropertyDetails(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static <T> T pick(T override, T current) {
        if (override instanceof String s && s.isBlank()) {
            return current;
        }
        return override != null ? override : current;
    }
}
