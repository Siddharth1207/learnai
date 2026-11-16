package com.learnai.extraction;

public interface FieldExtractor {

    DocumentFields extract(String documentText);

    String strategy();
}
