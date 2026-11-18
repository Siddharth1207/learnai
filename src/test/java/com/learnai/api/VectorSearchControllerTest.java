package com.learnai.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class VectorSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchReturnsServiceUnavailableWhenVectorStoreDisabled() throws Exception {
        mockMvc.perform(post("/api/vectors/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void addReturnsServiceUnavailableWhenVectorStoreDisabled() throws Exception {
        mockMvc.perform(post("/api/vectors/documents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documents\":[{\"text\":\"hello\"}]}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsBlankQuery() throws Exception {
        mockMvc.perform(post("/api/vectors/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOutOfRangeTopK() throws Exception {
        mockMvc.perform(post("/api/vectors/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"hi\",\"topK\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyDocumentList() throws Exception {
        mockMvc.perform(post("/api/vectors/documents").contentType(MediaType.APPLICATION_JSON).content("{\"documents\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyUpload() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);
        mockMvc.perform(multipart("/api/vectors/documents/upload").file(empty))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturnsServiceUnavailableWhenVectorStoreDisabled() throws Exception {
        mockMvc.perform(delete("/api/vectors/documents").param("ids", "1"))
                .andExpect(status().isServiceUnavailable());
    }
}
