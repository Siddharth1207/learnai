package com.learnai.document;

import java.util.stream.Collectors;

import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Extracts plain text from PDF, DOCX, TXT and other formats supported by Apache Tika.
 */
@Component
public class DocumentTextExtractor {

    public String extractText(Resource resource) {
        return new TikaDocumentReader(resource).get().stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n"))
                .trim();
    }
}
