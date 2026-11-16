package com.learnai.api;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.learnai.validation.DocumentValidationService;
import com.learnai.validation.ValidationReport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api")
public class DocumentValidationController {

    private final DocumentValidationService validationService;

    public DocumentValidationController(DocumentValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping(path = "/documents/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ValidationReport validateDocument(@RequestParam("file") MultipartFile file) {
        return validationService.validate(file.getResource());
    }

    @PostMapping(path = "/documents/validate-text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ValidationReport validateText(@Valid @RequestBody TextRequest request) {
        return validationService.validateText(request.text());
    }

    @GetMapping("/rules")
    public List<RuleInfo> rules() {
        return validationService.rules().stream().map(r -> new RuleInfo(r.id(), r.description())).toList();
    }

    public record TextRequest(@NotBlank String text) {
    }

    public record RuleInfo(String id, String description) {
    }
}
