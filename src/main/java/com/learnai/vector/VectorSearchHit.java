package com.learnai.vector;

import java.util.Map;

public record VectorSearchHit(String id, String text, Double score, Map<String, Object> metadata) {
}
