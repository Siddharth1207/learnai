package com.learnai.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.learnai.vector.VectorSearchHit;
import com.learnai.vector.VectorSearchService;
import com.learnai.vector.VectorSearchService.TextEntry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/vectors")
@Validated
public class VectorSearchController {

    private final VectorSearchService vectorSearchService;

    public VectorSearchController(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @PostMapping(path = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AddResponse addTexts(@Valid @RequestBody AddTextsRequest request) {
        List<TextEntry> entries = request.documents().stream()
                .map(doc -> new TextEntry(doc.text(), doc.metadata()))
                .toList();
        return new AddResponse(vectorSearchService.addTexts(entries));
    }

    @PostMapping(path = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AddResponse addFile(@RequestParam("file") MultipartFile file,
            @RequestParam(name = "category", required = false) String category) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        Map<String, Object> metadata = category == null || category.isBlank() ? Map.of() : Map.of("category", category);
        String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        return new AddResponse(vectorSearchService.addFile(file.getResource(), fileName, metadata));
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<VectorSearchHit> search(@Valid @RequestBody SearchRequest request) {
        int topK = request.topK() == null ? 4 : request.topK();
        double threshold = request.similarityThreshold() == null ? 0.0 : request.similarityThreshold();
        return vectorSearchService.search(request.query(), topK, threshold, request.filter());
    }

    @DeleteMapping("/documents")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam("ids") @NotEmpty List<String> ids) {
        vectorSearchService.delete(ids);
    }

    public record TextDocument(@NotBlank String text, Map<String, Object> metadata) {
    }

    public record AddTextsRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid TextDocument> documents) {
    }

    public record AddResponse(List<String> ids) {
    }

    /**
     * @param filter optional Spring AI filter expression on metadata, e.g. {@code category == 'invoice'}. Fields must be
     *     listed in {@code spring.ai.vectorstore.mongodb.metadata-fields-to-filter}.
     */
    public record SearchRequest(@NotBlank String query, @Min(1) @Max(100) Integer topK,
            @DecimalMin("0.0") @DecimalMax("1.0") Double similarityThreshold, String filter) {
    }
}
