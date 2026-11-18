package com.learnai.property;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A completed purchase of a listing at its listed price. */
@Document("purchases")
public record Purchase(@Id String id, String propertyId, PropertyType propertyType, String title, Address address,
        Coordinates coordinates, BigDecimal price, String currency, String sellerName, String sellerContact,
        String buyerName, String buyerContact, Instant purchasedAt) {
}
