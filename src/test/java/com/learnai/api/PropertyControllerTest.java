package com.learnai.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.learnai.audio.TranscriptionResult;
import com.learnai.property.Address;
import com.learnai.property.AreaUnit;
import com.learnai.property.Coordinates;
import com.learnai.property.IncompletePropertyException;
import com.learnai.property.IncompletePropertyException.FieldProblem;
import com.learnai.property.ListingType;
import com.learnai.property.MeetingDetails;
import com.learnai.property.MeetingMode;
import com.learnai.property.PaidServiceCode;
import com.learnai.property.PaidServiceOrder;
import com.learnai.property.PaidServiceRequest;
import com.learnai.property.Property;
import com.learnai.property.PropertyDetails;
import com.learnai.property.PropertyNotAvailableException;
import com.learnai.property.PropertyService;
import com.learnai.property.PropertyServiceOrders;
import com.learnai.property.PropertySource;
import com.learnai.property.PropertyStatus;
import com.learnai.property.PropertyType;
import com.learnai.property.PropertyVideo;
import com.learnai.property.PropertyVideoService;
import com.learnai.property.PurchaseRequest;
import com.learnai.property.ServiceStatus;
import com.learnai.property.UrbanDetails;

@WebMvcTest(PropertyController.class)
class PropertyControllerTest {

    private static final String VALID_URBAN = """
            {"sellerName":"Asha","sellerContact":"+91 1","propertyType":"URBAN",
             "address":{"city":"Pune"},"coordinates":{"latitude":18.53,"longitude":73.89},
             "area":1200,"areaUnit":"SQ_FT","price":9500000,"urban":{"bedrooms":2}}
            """;
    private static final MockMultipartFile AUDIO =
            new MockMultipartFile("file", "clip.mp3", "audio/mpeg", new byte[] {1, 2, 3});

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PropertyService propertyService;

    @MockitoBean
    private PropertyVideoService videos;

    @MockitoBean
    private PropertyServiceOrders serviceOrders;

    @Test
    void createsListingFromForm() throws Exception {
        mockMvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON).content(VALID_URBAN))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsListingWithoutCoordinates() throws Exception {
        mockMvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON)
                .content(VALID_URBAN.replace("\"coordinates\":{\"latitude\":18.53,\"longitude\":73.89},", "")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(propertyService);
    }

    @Test
    void rejectsOutOfRangeLatitude() throws Exception {
        mockMvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON)
                .content(VALID_URBAN.replace("18.53", "91")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsRuralDetailsOnUrbanListing() throws Exception {
        mockMvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON)
                .content(VALID_URBAN.replace("\"urban\"", "\"rural\":{\"landUse\":\"farm\"},\"urban\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownPropertyType() throws Exception {
        mockMvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON)
                .content(VALID_URBAN.replace("URBAN", "SUBURBAN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void voiceListingRejectsNonAudio() throws Exception {
        MockMultipartFile text = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});
        mockMvc.perform(multipart("/api/properties/voice").file(text).param("inputLanguage", "hi"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void voiceListingRequiresBothCoordinates() throws Exception {
        mockMvc.perform(multipart("/api/properties/voice").file(AUDIO).param("inputLanguage", "hi")
                .param("latitude", "18.5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void incompleteVoiceListingReturns422WithDraft() throws Exception {
        TranscriptionResult transcript = new TranscriptionResult("en", "Farm", false, "Farm", "t.txt");
        when(propertyService.createFromVoice(any(), any(), any(), any())).thenThrow(new IncompletePropertyException(
                transcript, PropertyDetails.empty(), List.of(new FieldProblem("price", "must not be null"))));

        mockMvc.perform(multipart("/api/properties/voice").file(AUDIO).param("inputLanguage", "en"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.transcription.englishText").value("Farm"))
                .andExpect(jsonPath("$.errors[0].field").value("price"))
                .andExpect(jsonPath("$.draft").exists());
    }

    @Test
    void searchRequiresCentreForRadius() throws Exception {
        mockMvc.perform(post("/api/properties/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"farm\",\"radiusKm\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void voiceSearchRequiresCentreForRadius() throws Exception {
        mockMvc.perform(multipart("/api/properties/search/voice").file(AUDIO).param("inputLanguage", "en")
                .param("radiusKm", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purchaseRequiresBuyerDetails() throws Exception {
        mockMvc.perform(post("/api/properties/p1/purchase").contentType(MediaType.APPLICATION_JSON)
                .content("{\"buyerName\":\"Neha\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purchaseOfSoldListingReturnsConflict() throws Exception {
        when(propertyService.purchase(eq("p1"), any(PurchaseRequest.class)))
                .thenThrow(new PropertyNotAvailableException("p1"));
        mockMvc.perform(post("/api/properties/p1/purchase").contentType(MediaType.APPLICATION_JSON)
                .content("{\"buyerName\":\"Neha\",\"buyerContact\":\"neha@example.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listingResponsesMaskPrivateDetails() throws Exception {
        when(propertyService.list(null, null, ListingType.RENT)).thenReturn(List.of(sampleProperty()));

        mockMvc.perform(get("/api/properties").param("listingType", "RENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p1"))
                .andExpect(jsonPath("$[0].details.sellerContact").value("+91 ••••• 00001"))
                .andExpect(jsonPath("$[0].details.address.street").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].details.address.postalCode").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].details.address.locality").value("Koregaon Park"));
    }

    @Test
    void detailResponseMasksPrivateDetails() throws Exception {
        when(propertyService.get("p1")).thenReturn(sampleProperty());

        mockMvc.perform(get("/api/properties/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.sellerContact").value("+91 ••••• 00001"))
                .andExpect(jsonPath("$.details.address.street").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void contactUnlockReturnsFullDetails() throws Exception {
        when(serviceOrders.order(eq("p1"), eq(PaidServiceCode.CONTACT_UNLOCK), any(PaidServiceRequest.class)))
                .thenReturn(new PaidServiceOrder("o1", "p1", PaidServiceCode.CONTACT_UNLOCK, new BigDecimal("199"),
                        "INR", ServiceStatus.PAID, Instant.parse("2026-11-19T09:15:00Z"), "Neha", "neha@example.com",
                        "+91 98100 00001", fullAddress(), null, null));

        mockMvc.perform(post("/api/properties/p1/services/CONTACT_UNLOCK").contentType(MediaType.APPLICATION_JSON)
                .content("{\"buyerName\":\"Neha\",\"buyerContact\":\"neha@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.service").value("CONTACT_UNLOCK"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.sellerContact").value("+91 98100 00001"))
                .andExpect(jsonPath("$.fullAddress.street").value("12 MG Road"));
    }

    @Test
    void siteVisitReturnsBookedMeeting() throws Exception {
        when(serviceOrders.order(eq("p1"), eq(PaidServiceCode.SITE_VISIT), any(PaidServiceRequest.class)))
                .thenReturn(new PaidServiceOrder("o2", "p1", PaidServiceCode.SITE_VISIT, new BigDecimal("199"), "INR",
                        ServiceStatus.PAID, Instant.parse("2026-11-19T09:25:00Z"), "Neha", "neha@example.com", null,
                        null, null, new MeetingDetails("2026-11-20T11:30+05:30",
                                MeetingMode.ON_SITE, "Main gate, Lotus Residency")));

        mockMvc.perform(post("/api/properties/p1/services/SITE_VISIT").contentType(MediaType.APPLICATION_JSON)
                .content("{\"buyerName\":\"Neha\",\"buyerContact\":\"neha@example.com\","
                        + "\"preferredDate\":\"2026-11-20\",\"preferredTime\":\"11:30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meeting.mode").value("ON_SITE"))
                .andExpect(jsonPath("$.meeting.scheduledAt").value("2026-11-20T11:30+05:30"));
    }

    @Test
    void serviceOrderRequiresBuyerDetails() throws Exception {
        mockMvc.perform(post("/api/properties/p1/services/DOCUMENT_VERIFICATION")
                .contentType(MediaType.APPLICATION_JSON).content("{\"buyerName\":\"Neha\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(serviceOrders);
    }

    @Test
    void uploadsVideoForListing() throws Exception {
        when(videos.upload(eq("p1"), any())).thenReturn(new PropertyVideo("v1", "/api/properties/p1/videos/v1",
                "video/mp4", 3L, Instant.parse("2026-11-19T09:10:00Z")));

        mockMvc.perform(multipart("/api/properties/p1/videos")
                .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[] {1, 2, 3})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("v1"))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.url").value("/api/properties/p1/videos/v1"));
    }

    private static Property sampleProperty() {
        PropertyDetails details = new PropertyDetails("Asha Verma", "+91 98100 00001", PropertyType.URBAN, "2BHK",
                "Sunny flat", fullAddress(), new Coordinates(18.5362, 73.8940), new BigDecimal("1200"),
                AreaUnit.SQ_FT, new BigDecimal("9500000"), "INR", ListingType.RENT, new BigDecimal("100000"),
                new UrbanDetails("apartment", "Lotus Residency", "Tower B · Flat 504", 2, 2, 5, 12, "furnished",
                        true, "Lift, gym"),
                null);
        return new Property("p1", details, PropertyStatus.AVAILABLE, PropertySource.FORM, null, null,
                Instant.parse("2026-11-10T10:15:00Z"), null, null);
    }

    private static Address fullAddress() {
        return new Address("12 MG Road", "Koregaon Park", "Pune", "Pune", "Maharashtra", "411001", "India");
    }
}
