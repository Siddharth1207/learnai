package com.learnai.vector;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class VectorStoreNotConfiguredException extends RuntimeException {

    public VectorStoreNotConfiguredException() {
        super("Vector search is disabled. Set LEARNAI_VECTOR_STORE=mongodb-atlas, LEARNAI_CHAT_MODEL=google-genai "
                + "and GEMINI_API_KEY, and start MongoDB with 'docker compose up -d'.");
    }
}
