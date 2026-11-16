package com.learnai.audio;

public record TranscriptionResult(String inputLanguage, String transcript, boolean translated, String englishText,
        String savedFile) {
}
