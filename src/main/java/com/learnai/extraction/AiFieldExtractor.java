package com.learnai.extraction;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Uses an LLM to pull structured fields out of free-form document text.
 */
public class AiFieldExtractor implements FieldExtractor {

    private static final String SYSTEM_PROMPT = """
            You extract structured fields from documents.
            Return the person's or entity's name exactly as it is written in the document,
            character for character, without correcting, normalising or removing any characters.
            If the document does not contain a name, return null for the name.
            """;

    private final ChatClient chatClient;

    public AiFieldExtractor(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public DocumentFields extract(String documentText) {
        if (documentText == null || documentText.isBlank()) {
            return DocumentFields.empty();
        }
        DocumentFields fields = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(documentText)
                .call()
                .entity(DocumentFields.class);
        return fields != null ? fields : DocumentFields.empty();
    }

    @Override
    public String strategy() {
        return "ai";
    }
}
