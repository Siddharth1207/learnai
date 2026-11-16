package com.learnai.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegexFieldExtractorTest {

    private final RegexFieldExtractor extractor = new RegexFieldExtractor();

    @Test
    void extractsNameLine() {
        String text = "Application form\nFull Name: John Doe\nAge: 30";
        assertThat(extractor.extract(text).name()).isEqualTo("John Doe");
    }

    @Test
    void returnsNullWhenNoNameLine() {
        assertThat(extractor.extract("Nothing relevant here").name()).isNull();
    }
}
