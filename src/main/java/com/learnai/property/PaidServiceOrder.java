package com.learnai.property;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A paid-service order stored in MongoDB.
 *
 * @param sellerContact full contact returned only for {@link PaidServiceCode#CONTACT_UNLOCK}
 * @param fullAddress full street address returned only for {@link PaidServiceCode#CONTACT_UNLOCK}
 * @param verification verification result returned only for {@link PaidServiceCode#DOCUMENT_VERIFICATION}
 * @param meeting booked slot returned only for {@link PaidServiceCode#SITE_VISIT}
 */
@Document("property_service_orders")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaidServiceOrder(
        @Id String id,
        String propertyId,
        PaidServiceCode service,
        BigDecimal amount,
        String currency,
        ServiceStatus status,
        Instant purchasedAt,
        String buyerName,
        String buyerContact,
        String sellerContact,
        Address fullAddress,
        VerificationReport verification,
        MeetingDetails meeting) {
}
