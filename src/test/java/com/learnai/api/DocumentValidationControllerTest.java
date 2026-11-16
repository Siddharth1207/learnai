package com.learnai.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void passesUploadedDocumentWithValidName() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "form.txt", MediaType.TEXT_PLAIN_VALUE,
                "Application\nName: John Doe\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("PASS"))
                .andExpect(jsonPath("$.extractionStrategy").value("regex"))
                .andExpect(jsonPath("$.extractedFields.name").value("John Doe"));
    }

    @Test
    void failsTextWithSpecialCharactersInName() throws Exception {
        mockMvc.perform(post("/api/documents/validate-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Name: John@Doe#\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("FAIL"))
                .andExpect(jsonPath("$.results[0].ruleId").value("NAME_WITHOUT_SPECIAL_CHARACTERS"))
                .andExpect(jsonPath("$.results[0].passed").value(false));
    }

    @Test
    void listsRules() throws Exception {
        mockMvc.perform(get("/api/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("NAME_WITHOUT_SPECIAL_CHARACTERS"));
    }
}
