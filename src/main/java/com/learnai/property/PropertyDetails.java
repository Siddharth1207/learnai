package com.learnai.property;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Property details entered by a seller, either through the form or extracted from a voice description.
 * {@code urban} applies only to {@link PropertyType#URBAN} and {@code rural} only to {@link PropertyType#RURAL}.
 *
 * @param currency ISO 4217 code; defaults to INR
 * @param listingType SALE (default) or RENT
 * @param deposit security deposit; only kept for RENT listings
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
        ListingType listingType,
        @PositiveOrZero BigDecimal deposit,
        @Valid UrbanDetails urban,
        @Valid RuralDetails rural) {

    /** Keeps older callers that predate rent. */
    public PropertyDetails(String sellerName, String sellerContact, PropertyType propertyType, String title,
            String description, Address address, Coordinates coordinates, BigDecimal area, AreaUnit areaUnit,
            BigDecimal price, String currency, UrbanDetails urban, RuralDetails rural) {
        this(sellerName, sellerContact, propertyType, title, description, address, coordinates, area, areaUnit, price,
                currency, null, null, urban, rural);
    }

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
                pick(overrides.listingType, listingType),
                pick(overrides.deposit, deposit),
                pick(overrides.urban, urban),
                pick(overrides.rural, rural));
    }

    /** Fills in defaults (currency, title, listing type) and drops details that do not belong to the property type. */
    PropertyDetails normalized() {
        String normalizedCurrency = currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase();
        String normalizedTitle = title == null || title.isBlank()
                ? "%s property in %s".formatted(propertyType == PropertyType.URBAN ? "Urban" : "Rural", address.city())
                : title;
        ListingType normalizedListingType = listingType == null ? ListingType.SALE : listingType;
        return new PropertyDetails(sellerName, sellerContact, propertyType, normalizedTitle, description, address,
                coordinates, area, areaUnit, price, normalizedCurrency, normalizedListingType,
                normalizedListingType == ListingType.RENT ? deposit : null,
                propertyType == PropertyType.URBAN ? urban : null,
                propertyType == PropertyType.RURAL ? rural : null);
    }

    public static PropertyDetails empty() {
        return new PropertyDetails(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Public view shown to buyers: the seller contact, the exact street/postal address and the rural survey number
     * are hidden until a purchase or a paid CONTACT_UNLOCK order returns the full values.
     */
    public PropertyDetails masked() {
        return new PropertyDetails(sellerName, maskContact(sellerContact), propertyType, title, description,
                address == null ? null : address.masked(), coordinates, area, areaUnit, price, currency,
                listingType == null ? ListingType.SALE : listingType, deposit, urban,
                rural == null ? null : new RuralDetails(rural.landUse(), null, rural.ownershipType(), rural.crop(),
                        rural.soilType(), rural.waterSource(), rural.irrigated(), rural.roadAccess(), rural.electricity()));
    }

    /** {@code +91 98100 00001} becomes {@code +91 ••••• 00001}; e-mail local parts are masked too. */
    static String maskContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return contact;
        }
        String value = contact.trim();
        int at = value.indexOf('@');
        if (at > 0) {
            return value.charAt(0) + "•••" + value.substring(at);
        }
        String[] parts = value.split("\\s+");
        if (parts.length >= 2) {
            String tail = parts[parts.length - 1];
            String visible = tail.length() > 5 ? tail.substring(tail.length() - 5) : tail;
            return parts[0] + " ••••• " + visible;
        }
        return value.length() <= 4 ? "••••" : "••••••" + value.substring(value.length() - 4);
    }

    private static <T> T pick(T override, T current) {
        if (override instanceof String s && s.isBlank()) {
            return current;
        }
        return override != null ? override : current;
    }
}
