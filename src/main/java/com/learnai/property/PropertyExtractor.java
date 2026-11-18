package com.learnai.property;

/** Turns a seller's spoken (already transcribed, English) description into structured property details. */
public interface PropertyExtractor {

    /** Returns the details mentioned in the text; fields that were not mentioned are null. */
    PropertyDetails extract(String description);
}
