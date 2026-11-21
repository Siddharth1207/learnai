package com.learnai.api;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
import com.learnai.property.ListingType;
import com.learnai.property.PaidServiceCode;
import com.learnai.property.PaidServiceOrder;
import com.learnai.property.PaidServiceRequest;
import com.learnai.property.Property;
import com.learnai.property.PropertyDetails;
import com.learnai.property.PropertySearch;
import com.learnai.property.PropertySearchResult;
import com.learnai.property.PropertySearchResult.PropertyMatch;
import com.learnai.property.PropertyService;
import com.learnai.property.PropertyServiceOrders;
import com.learnai.property.PropertyStatus;
import com.learnai.property.PropertyType;
import com.learnai.property.PropertyVideo;
import com.learnai.property.PropertyVideoService;
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
 * Sellers list urban or rural properties with a form or by voice; buyers search them (MongoDB first, with optional
 * vector matches and an LLM answer, typed or spoken) and buy them.
 */
@RestController
@RequestMapping("/api/properties")
@Validated
public class PropertyController {

    private final PropertyService propertyService;
    private final PropertyVideoService videos;
    private final PropertyServiceOrders serviceOrders;

    public PropertyController(PropertyService propertyService, PropertyVideoService videos,
            PropertyServiceOrders serviceOrders) {
        this.propertyService = propertyService;
        this.videos = videos;
        this.serviceOrders = serviceOrders;
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
            @RequestParam(name = "listingType", required = false) ListingType listingType,
            @RequestParam(name = "latitude", required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @RequestParam(name = "longitude", required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {
        var language = AudioUploads.language(inputLanguage);
        var mimeType = AudioUploads.audioMimeType(file);
        PropertyDetails overrides = new PropertyDetails(sellerName, sellerContact, propertyType, null, null, null,
                coordinates(latitude, longitude), null, null, null, null, listingType, null, null, null);
        return propertyService.createFromVoice(file.getResource(), mimeType, language, overrides);
    }

    @GetMapping
    public List<Property> list(@RequestParam(name = "propertyType", required = false) PropertyType propertyType,
            @RequestParam(name = "status", required = false) PropertyStatus status,
            @RequestParam(name = "listingType", required = false) ListingType listingType) {
        return propertyService.list(propertyType, status, listingType).stream().map(Property::publicView).toList();
    }

    @GetMapping("/{id}")
    public Property get(@PathVariable String id) {
        return propertyService.get(id).publicView();
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PropertySearchResult search(@Valid @RequestBody PropertySearch search) {
        return publicView(propertyService.search(search));
    }

    @PostMapping(path = "/search/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PropertySearchResult searchByVoice(@RequestParam("file") MultipartFile file,
            @RequestParam("inputLanguage") String inputLanguage,
            @RequestParam(name = "propertyType", required = false) PropertyType propertyType,
            @RequestParam(name = "listingType", required = false) ListingType listingType,
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
        PropertySearch filters = new PropertySearch(null, propertyType, minPrice, maxPrice, near, radiusKm, topK,
                listingType);
        return publicView(propertyService.searchByVoice(file.getResource(), mimeType, language, filters));
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

    /** Uploads a walkthrough video (MP4, MOV or WebM, max 50 MB) for a listing. */
    @PostMapping(path = "/{id}/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyVideo uploadVideo(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        return videos.upload(id, file);
    }

    /** Streams a walkthrough video; supports HTTP Range requests so the browser can seek. */
    @GetMapping("/{id}/videos/{videoId}")
    public ResponseEntity<Resource> streamVideo(@PathVariable String id, @PathVariable String videoId,
            @RequestHeader(name = HttpHeaders.RANGE, required = false) String range) throws IOException {
        PropertyVideoService.StoredVideo stored = videos.get(id, videoId);
        Path path = stored.path();
        long size = Files.size(path);
        MediaType contentType = MediaType.parseMediaType(stored.video().contentType());

        if (range == null || range.isBlank()) {
            return ResponseEntity.ok().contentType(contentType).contentLength(size)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes").body(new FileSystemResource(path));
        }

        long[] requested = parseRange(range, size);
        long start = requested[0];
        long end = requested[1];
        byte[] bytes = new byte[Math.toIntExact(end - start + 1)];
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(start);
            file.readFully(bytes);
        }
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).contentType(contentType)
                .contentLength(bytes.length).header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(start, end, size))
                .body(new ByteArrayResource(bytes));
    }

    /** Orders a paid service: CONTACT_UNLOCK, DOCUMENT_VERIFICATION or SITE_VISIT. */
    @PostMapping(path = "/{id}/services/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PaidServiceOrder orderService(@PathVariable String id, @PathVariable PaidServiceCode code,
            @Valid @RequestBody PaidServiceRequest request) {
        return serviceOrders.order(id, code, request);
    }

    private static long[] parseRange(String range, long size) {
        try {
            String value = range.trim();
            if (value.startsWith("bytes=")) {
                value = value.substring("bytes=".length());
            }
            int dash = value.indexOf('-');
            if (dash < 0) {
                throw new NumberFormatException();
            }
            String startPart = value.substring(0, dash).trim();
            String endPart = value.substring(dash + 1).trim();
            long start;
            long end;
            if (startPart.isEmpty()) {
                long suffix = Long.parseLong(endPart);
                start = Math.max(0, size - suffix);
                end = size - 1;
            }
            else {
                start = Long.parseLong(startPart);
                end = endPart.isEmpty() ? size - 1 : Long.parseLong(endPart);
            }
            if (start < 0 || start >= size || end < start) {
                throw new NumberFormatException();
            }
            return new long[] {start, Math.min(end, size - 1)};
        }
        catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid Range header");
        }
    }

    /** Public search and list responses hide the seller contact, exact address and survey number. */
    private static PropertySearchResult publicView(PropertySearchResult result) {
        List<PropertyMatch> matches = result.results().stream()
                .map(m -> new PropertyMatch(m.property().publicView(), m.score(), m.distanceKm()))
                .toList();
        return new PropertySearchResult(result.transcription(), result.query(), result.answer(), matches,
                result.marketResearch());
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
