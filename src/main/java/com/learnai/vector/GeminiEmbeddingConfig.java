package com.learnai.vector;

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * Gemini embedding connection, created only when {@code spring.ai.model.embedding.text=google-genai}.
 * Spring AI's own connection auto-configuration is excluded because it fails at startup without an API key.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.embedding.text", havingValue = "google-genai")
public class GeminiEmbeddingConfig {

    @Bean
    GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(
            @Value("${spring.ai.google.genai.embedding.api-key:}") String apiKey) {
        Assert.hasText(apiKey, "GEMINI_API_KEY must be set to use Gemini embeddings");
        return GoogleGenAiEmbeddingConnectionDetails.builder().apiKey(apiKey).build();
    }
}
