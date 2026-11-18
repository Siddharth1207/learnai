package com.learnai.api;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.learnai.audio.InputLanguage;

/** Validation shared by endpoints that accept an audio upload with its spoken language. */
final class AudioUploads {

    private AudioUploads() {
    }

    static MimeType audioMimeType(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file is empty");
        }
        MimeType mimeType = Optional.ofNullable(file.getContentType())
                .map(MimeTypeUtils::parseMimeType)
                .filter(type -> "audio".equals(type.getType()))
                .or(() -> MediaTypeFactory.getMediaType(file.getOriginalFilename()))
                .orElse(null);
        if (mimeType == null || !"audio".equals(mimeType.getType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File must be audio (e.g. mp3, wav, ogg, flac, aac, aiff)");
        }
        return mimeType;
    }

    static InputLanguage language(String inputLanguage) {
        return InputLanguage.resolve(inputLanguage)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported inputLanguage '" + inputLanguage + "'. Use an ISO-639-1 code (e.g. hi) or name (e.g. Hindi)."));
    }
}
