package com.learnai.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PropertyServiceOrdersTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    private final PropertyServiceOrders service = new PropertyServiceOrders(properties, orders,
            Clock.fixed(NOW, ZoneOffset.UTC));

    PropertyServiceOrdersTest() {
        when(orders.save(any(PaidServiceOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(properties.findById(any())).thenReturn(Optional.of(new Property("p1",
                PropertyFixtures.urbanFlat().normalized(), PropertyStatus.AVAILABLE, PropertySource.FORM, null, null,
                NOW, null, null)));
    }

    @Test
    void contactUnlockReturnsFullContactAndAddress() {
        PaidServiceOrder order = service.order("p1", PaidServiceCode.CONTACT_UNLOCK,
                new PaidServiceRequest("Neha Singh", "neha@example.com", null, null));

        assertThat(order.service()).isEqualTo(PaidServiceCode.CONTACT_UNLOCK);
        assertThat(order.amount()).isEqualByComparingTo("199");
        assertThat(order.currency()).isEqualTo("INR");
        assertThat(order.status()).isEqualTo(ServiceStatus.PAID);
        assertThat(order.sellerContact()).isEqualTo("+91 98100 00001");
        assertThat(order.fullAddress().street()).isEqualTo("12 MG Road");
        assertThat(order.fullAddress().postalCode()).isEqualTo("411001");
        assertThat(order.verification()).isNull();
        assertThat(order.meeting()).isNull();
        assertThat(order.buyerName()).isEqualTo("Neha Singh");
    }

    @Test
    void documentVerificationReturnsVerifiedReport() {
        PaidServiceOrder order = service.order("p1", PaidServiceCode.DOCUMENT_VERIFICATION,
                new PaidServiceRequest("Neha Singh", "neha@example.com", null, null));

        assertThat(order.amount()).isEqualByComparingTo("499");
        assertThat(order.verification().status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(order.verification().verifiedAt()).isEqualTo(NOW);
        assertThat(order.verification().summary()).contains("Title deed");
        assertThat(order.sellerContact()).isNull();
    }

    @Test
    void siteVisitBooksIndianTimeSlot() {
        PaidServiceOrder order = service.order("p1", PaidServiceCode.SITE_VISIT,
                new PaidServiceRequest("Neha Singh", "neha@example.com", "2026-11-20", "11:30"));

        assertThat(order.amount()).isEqualByComparingTo("199");
        assertThat(order.meeting().scheduledAt()).isEqualTo("2026-11-20T11:30+05:30");
        assertThat(order.meeting().mode()).isEqualTo(MeetingMode.ON_SITE);
        assertThat(order.meeting().place()).isEqualTo("Main gate, Lotus Residency, Koregaon Park");
    }

    @Test
    void siteVisitRequiresACompleteFutureSlot() {
        assertThatThrownBy(() -> service.order("p1", PaidServiceCode.SITE_VISIT,
                new PaidServiceRequest("Neha", "neha@example.com", null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));

        assertThatThrownBy(() -> service.order("p1", PaidServiceCode.SITE_VISIT,
                new PaidServiceRequest("Neha", "neha@example.com", "2026-11-17", "11:30")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));

        verify(orders, never()).save(any());
    }

    @Test
    void soldListingCannotOrderAService() {
        when(properties.findById("p1")).thenReturn(Optional.of(new Property("p1",
                PropertyFixtures.urbanFlat().normalized(), PropertyStatus.SOLD, PropertySource.FORM, null, null,
                NOW, NOW, "purchase-1")));

        assertThatThrownBy(() -> service.order("p1", PaidServiceCode.CONTACT_UNLOCK,
                new PaidServiceRequest("Neha", "neha@example.com", null, null)))
                .isInstanceOf(PropertyNotAvailableException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void unknownListingIsNotFound() {
        when(properties.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.order("missing", PaidServiceCode.CONTACT_UNLOCK,
                new PaidServiceRequest("Neha", "neha@example.com", null, null)))
                .isInstanceOf(PropertyNotFoundException.class);
    }
}
