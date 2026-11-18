package com.learnai.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.learnai.audio.TranscriptionResult;
import com.learnai.property.IncompletePropertyException;
import com.learnai.property.IncompletePropertyException.FieldProblem;
import com.learnai.property.PropertyDetails;
import com.learnai.property.PropertyNotAvailableException;
import com.learnai.property.PropertyService;
import com.learnai.property.PurchaseRequest;

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
}
