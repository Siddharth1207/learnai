package com.learnai.api;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.learnai.audio.AudioTranscriptionService;
import com.learnai.audio.InputLanguage;
import com.learnai.audio.TranscriptionResult;

@RestController
@RequestMapping("/api/audio")
public class AudioTranscriptionController {

    private final AudioTranscriptionService transcriptionService;

    public AudioTranscriptionController(AudioTranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @PostMapping(path = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResult transcribe(@RequestParam("file") MultipartFile file,
            @RequestParam("inputLanguage") String inputLanguage) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file is empty");
        }
        InputLanguage language = InputLanguage.resolve(inputLanguage)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported inputLanguage '" + inputLanguage + "'. Use an ISO-639-1 code (e.g. hi) or name (e.g. Hindi)."));
        return transcriptionService.transcribe(file.getResource(), audioMimeType(file), language);
    }

    private static MimeType audioMimeType(MultipartFile file) {
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
}
