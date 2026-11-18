package com.learnai.property;

import com.learnai.audio.TranscriptionResult;

public record VoicePropertyResult(TranscriptionResult transcription, Property property) {
}
