package com.learnai.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AudioTranscriptionControllerTest {

    private static final MockMultipartFile AUDIO =
            new MockMultipartFile("file", "clip.mp3", "audio/mpeg", new byte[] {1, 2, 3});

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsUnknownLanguage() throws Exception {
        mockMvc.perform(multipart("/api/audio/transcribe").file(AUDIO).param("inputLanguage", "Klingon"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresInputLanguage() throws Exception {
        mockMvc.perform(multipart("/api/audio/transcribe").file(AUDIO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "clip.mp3", "audio/mpeg", new byte[0]);
        mockMvc.perform(multipart("/api/audio/transcribe").file(empty).param("inputLanguage", "hi"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsServiceUnavailableWhenAiNotConfigured() throws Exception {
        mockMvc.perform(multipart("/api/audio/transcribe").file(AUDIO).param("inputLanguage", "hi"))
                .andExpect(status().isServiceUnavailable());
    }
}
