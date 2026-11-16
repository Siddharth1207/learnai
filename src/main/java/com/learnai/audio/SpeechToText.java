package com.learnai.audio;

import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

public interface SpeechToText {

    String transcribe(Resource audio, MimeType mimeType, InputLanguage language);
}
