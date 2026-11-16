package com.learnai.extraction;

/**
 * Structured fields pulled out of a document. Add new fields here as new rules need them.
 */
public record DocumentFields(String name) {

    public static DocumentFields empty() {
        return new DocumentFields(null);
    }
}
