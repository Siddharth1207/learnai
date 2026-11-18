package com.learnai.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
        InputLanguage language = AudioUploads.language(inputLanguage);
        return transcriptionService.transcribe(file.getResource(), AudioUploads.audioMimeType(file), language);
    }
}
