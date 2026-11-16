package com.learnai.audio;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * An ISO-639-1 language resolved from either a code ({@code "hi"}) or an English name ({@code "Hindi"}).
 */
public record InputLanguage(String code, String displayName) {

    public static final InputLanguage ENGLISH = new InputLanguage("en", "English");

    public static Optional<InputLanguage> resolve(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String value = input.trim();
        return Arrays.stream(Locale.getISOLanguages())
                .map(Locale::new)
                .filter(locale -> locale.getLanguage().equalsIgnoreCase(value)
                        || locale.getDisplayLanguage(Locale.ENGLISH).equalsIgnoreCase(value))
                .findFirst()
                .map(locale -> new InputLanguage(locale.getLanguage(), locale.getDisplayLanguage(Locale.ENGLISH)));
    }

    public boolean isEnglish() {
        return ENGLISH.code.equals(code);
    }
}
