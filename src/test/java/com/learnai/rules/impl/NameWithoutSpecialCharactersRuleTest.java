package com.learnai.rules.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.learnai.extraction.DocumentFields;
import com.learnai.rules.DocumentContext;

class NameWithoutSpecialCharactersRuleTest {

    private final NameWithoutSpecialCharactersRule rule =
            new NameWithoutSpecialCharactersRule("^\\p{L}+( \\p{L}+)*$");

    @ParameterizedTest
    @ValueSource(strings = {"John", "John Doe", "  Jane Mary Smith  ", "José Álvarez"})
    void passesNamesWithoutSpecialCharacters(String name) {
        assertThat(rule.evaluate(context(name)).passed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"John@Doe", "John_Doe", "J0hn", "John-Doe", "O'Brien", "John  Doe", "John!"})
    void failsNamesWithSpecialCharacters(String name) {
        assertThat(rule.evaluate(context(name)).passed()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void failsWhenNameMissing(String name) {
        assertThat(rule.evaluate(context(name)).message()).isEqualTo("No name found in the document");
    }

    private static DocumentContext context(String name) {
        return new DocumentContext("", new DocumentFields(name));
    }
}
