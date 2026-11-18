package com.learnai.vector;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Deterministic local embedding model for integration tests (no API key needed). */
public class BagOfWordsEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 64;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            embeddings.add(new Embedding(vectorize(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorize(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private static float[] vectorize(String text) {
        float[] vector = new float[DIMENSIONS];
        vector[0] = 0.01f;
        for (String word : text.toLowerCase().split("\\W+")) {
            if (!word.isEmpty()) {
                vector[Math.floorMod(java.util.Arrays.hashCode(word.getBytes(StandardCharsets.UTF_8)), DIMENSIONS)] += 1;
            }
        }
        return vector;
    }
}
