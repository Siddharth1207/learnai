package com.learnai.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.learnai.audio.TranscriptionResult;
import com.learnai.property.Coordinates;
import com.learnai.property.IncompletePropertyException;
import com.learnai.property.IncompletePropertyException.FieldProblem;
import com.learnai.property.Property;
import com.learnai.property.PropertyDetails;
import com.learnai.property.PropertySearch;
import com.learnai.property.PropertySearchResult;
import com.learnai.property.PropertyService;
import com.learnai.property.PropertyStatus;
import com.learnai.property.PropertyType;
import com.learnai.property.Purchase;
import com.learnai.property.PurchaseRequest;
import com.learnai.property.VoicePropertyResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Sellers list urban or rural properties with a form or by voice; buyers search them (RAG over the vector store,
 * typed or spoken) and buy them.
 */
@RestController
@RequestMapping("/api/properties")
@Validated
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Property create(@Valid @RequestBody PropertyDetails details) {
        return propertyService.createFromForm(details);
    }

    /**
     * Creates a listing from the seller's spoken description. Optional parameters (e.g. the device's GPS location)
     * override what was extracted from the speech. Returns 422 with the extracted draft if required details are missing.
     */
    @PostMapping(path = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VoicePropertyResult createFromVoice(@RequestParam("file") MultipartFile file,
            @RequestParam("inputLanguage") String inputLanguage,
            @RequestParam(name = "sellerName", required = false) String sellerName,
            @RequestParam(name = "sellerContact", required = false) String sellerContact,
            @RequestParam(name = "propertyType", required = false) PropertyType propertyType,
            @RequestParam(name = "latitude", required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @RequestParam(name = "longitude", required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {
        var language = AudioUploads.language(inputLanguage);
        var mimeType = AudioUploads.audioMimeType(file);
        PropertyDetails overrides = new PropertyDetails(sellerName, sellerContact, propertyType, null, null, null,
                coordinates(latitude, longitude), null, null, null, null, null, null);
        return propertyService.createFromVoice(file.getResource(), mimeType, language, overrides);
    }

    @GetMapping
    public List<Property> list(@RequestParam(name = "propertyType", required = false) PropertyType propertyType,
            @RequestParam(name = "status", required = false) PropertyStatus status) {
        return propertyService.list(propertyType, status);
    }

    @GetMapping("/{id}")
    public Property get(@PathVariable String id) {
        return propertyService.get(id);
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PropertySearchResult search(@Valid @RequestBody PropertySearch search) {
        return propertyService.search(search);
    }

    @PostMapping(path = "/search/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PropertySearchResult searchByVoice(@RequestParam("file") MultipartFile file,
            @RequestParam("inputLanguage") String inputLanguage,
            @RequestParam(name = "propertyType", required = false) PropertyType propertyType,
            @RequestParam(name = "minPrice", required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) @Positive BigDecimal maxPrice,
            @RequestParam(name = "latitude", required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @RequestParam(name = "longitude", required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @RequestParam(name = "radiusKm", required = false) @Positive Double radiusKm,
            @RequestParam(name = "topK", required = false) @Min(1) @Max(20) Integer topK) {
        var language = AudioUploads.language(inputLanguage);
        var mimeType = AudioUploads.audioMimeType(file);
        Coordinates near = coordinates(latitude, longitude);
        if (radiusKm != null && near == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm requires latitude and longitude");
        }
        PropertySearch filters = new PropertySearch(null, propertyType, minPrice, maxPrice, near, radiusKm, topK);
        return propertyService.searchByVoice(file.getResource(), mimeType, language, filters);
    }

    @PostMapping(path = "/{id}/purchase", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Purchase purchase(@PathVariable String id, @Valid @RequestBody PurchaseRequest request) {
        return propertyService.purchase(id, request);
    }

    @GetMapping("/{id}/purchase")
    public Purchase getPurchase(@PathVariable String id) {
        return propertyService.getPurchase(id);
    }

    @ExceptionHandler(IncompletePropertyException.class)
    ResponseEntity<IncompletePropertyResponse> incomplete(IncompletePropertyException e) {
        return ResponseEntity.unprocessableContent()
                .body(new IncompletePropertyResponse(e.getMessage(), e.transcription(), e.draft(), e.errors()));
    }

    private static Coordinates coordinates(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return null;
        }
        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude must be given together");
        }
        return new Coordinates(latitude, longitude);
    }

    /** The draft can be completed by the client and submitted to {@code POST /api/properties}. */
    public record IncompletePropertyResponse(String message, TranscriptionResult transcription, PropertyDetails draft,
            List<FieldProblem> errors) {
    }
}
