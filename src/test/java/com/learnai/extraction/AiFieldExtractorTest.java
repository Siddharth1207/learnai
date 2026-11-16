package com.learnai.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;

class AiFieldExtractorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void returnsEmptyFieldsForBlankTextWithoutCallingModel(String text) {
        ChatClient chatClient = mock(ChatClient.class);

        assertThat(new AiFieldExtractor(chatClient).extract(text)).isEqualTo(DocumentFields.empty());
        verifyNoInteractions(chatClient);
    }
}
