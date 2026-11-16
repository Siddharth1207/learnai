package com.learnai.audio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class InputLanguageTest {

    @ParameterizedTest
    @CsvSource({"en,en", "EN,en", "English,en", "hi,hi", "Hindi,hi", " french ,fr"})
    void resolvesCodesAndNames(String input, String expectedCode) {
        assertThat(InputLanguage.resolve(input)).get().extracting(InputLanguage::code).isEqualTo(expectedCode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "xx", "Klingon"})
    void rejectsUnknownLanguages(String input) {
        assertThat(InputLanguage.resolve(input)).isEmpty();
    }

    @Test
    void onlyEnglishIsEnglish() {
        assertThat(InputLanguage.resolve("English").orElseThrow().isEnglish()).isTrue();
        assertThat(InputLanguage.resolve("Hindi").orElseThrow().isEnglish()).isFalse();
    }
}
