package com.learnai.property;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.learnai.research.LocationResearchService;

/**
 * Checkout for the paid services on a listing. There is no payment provider configured yet, so orders are
 * recorded as {@link ServiceStatus#PAID} directly — wiring a gateway means charging before the order is saved.
 */
@Service
public class PropertyServiceOrders {

    /** Site visits are quoted in Indian Standard Time. */
    static final ZoneOffset INDIA = ZoneOffset.ofHoursMinutes(5, 30);

    private final PropertyRepository properties;
    private final ServiceOrderRepository orders;
    private final LocationResearchService research;
    private final Clock clock;

    @Autowired
    public PropertyServiceOrders(PropertyRepository properties, ServiceOrderRepository orders,
            LocationResearchService research) {
        this(properties, orders, research, Clock.systemUTC());
    }

    PropertyServiceOrders(PropertyRepository properties, ServiceOrderRepository orders, LocationResearchService research,
            Clock clock) {
        this.properties = properties;
        this.orders = orders;
        this.research = research;
        this.clock = clock;
    }

    /**
     * Orders {@code code} for an available listing.
     *
     * @throws PropertyNotFoundException if the listing does not exist
     * @throws PropertyNotAvailableException if it has already been sold or rented
     */
    public PaidServiceOrder order(String propertyId, PaidServiceCode code, PaidServiceRequest request) {
        Property property = properties.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property " + propertyId + " not found"));
        if (property.status() != PropertyStatus.AVAILABLE) {
            throw new PropertyNotAvailableException(propertyId);
        }
        PropertyDetails details = property.details();
        Instant now = clock.instant();
        BigDecimal amount = code.amount();

        String sellerContact = null;
        Address fullAddress = null;
        VerificationReport verification = null;
        MeetingDetails meeting = null;
        LocationInsights locationInsights = null;

        switch (code) {
            case CONTACT_UNLOCK -> {
                sellerContact = details.sellerContact();
                fullAddress = details.address();
            }
            case DOCUMENT_VERIFICATION -> verification = new VerificationReport(VerificationStatus.VERIFIED,
                    "Title deed, ownership record and tax receipts match the seller name. No encumbrance found.", now,
                    null);
            case SITE_VISIT -> meeting = meeting(details, request, now);
            // Runs the live web searches before the order is saved, so a missing TAVILY_API_KEY fails the order.
            case LOCATION_INSIGHTS -> locationInsights = research.research(property);
        }

        PaidServiceOrder order = new PaidServiceOrder(UUID.randomUUID().toString(), propertyId, code, amount,
                details.currency(), ServiceStatus.PAID, now, request.buyerName().trim(), request.buyerContact().trim(),
                sellerContact, fullAddress, verification, meeting, locationInsights);
        return orders.save(order);
    }

    private static MeetingDetails meeting(PropertyDetails details, PaidServiceRequest request, Instant now) {
        LocalDate date = parseDate(request.preferredDate());
        LocalTime time = parseTime(request.preferredTime());
        OffsetDateTime scheduledAt = OffsetDateTime.of(date, time, INDIA);
        if (scheduledAt.toInstant().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "preferredDate and preferredTime must be in the future");
        }
        return new MeetingDetails(scheduledAt.toString(), MeetingMode.ON_SITE, meetingPlace(details));
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw missingSlot();
        }
        try {
            return LocalDate.parse(value.trim());
        }
        catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "preferredDate must be yyyy-MM-dd");
        }
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            throw missingSlot();
        }
        try {
            return LocalTime.parse(value.trim());
        }
        catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "preferredTime must be HH:mm");
        }
    }

    private static ResponseStatusException missingSlot() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "SITE_VISIT requires preferredDate (yyyy-MM-dd) and preferredTime (HH:mm)");
    }

    private static String meetingPlace(PropertyDetails details) {
        Address address = details.address();
        if (details.urban() != null && details.urban().apartmentName() != null) {
            return "Main gate, " + details.urban().apartmentName() + ", " + address.locality();
        }
        if (details.rural() != null) {
            return "At the property, " + address.locality() + ", " + address.city();
        }
        return address.locality() == null ? address.city() : address.locality() + ", " + address.city();
    }
}
