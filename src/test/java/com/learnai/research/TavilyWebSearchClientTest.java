package com.learnai.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TavilyWebSearchClientTest {

    private static final String BODY = """
            {"query":"metro near Pune","results":[
              {"title":"Metro line 3","url":"https://pune.gov.in/metro-3","content":"Approved corridor.",
               "published_date":"2026-11-01"},
              {"title":"No URL","content":"ignored"},
              {"title":"Second","url":"https://pmc.gov.in/notice","content":"Notice"},
              {"title":"Third","url":"https://news.example.com/3","content":"Third"}]}
            """;

    @Test
    void postsTheConfiguredQueryAndParsesTheResults() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.tavily.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TavilyWebSearchClient client = new TavilyWebSearchClient(builder.build(), "test-key", 2);
        server.expect(requestTo("https://api.tavily.com/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"api_key\"")))
                .andExpect(content().string(containsString("test-key")))
                .andExpect(content().string(containsString("metro near Pune")))
                .andExpect(content().string(containsString("gov.in")))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        List<WebSource> sources = client.search("metro near Pune", List.of("gov.in"));

        server.verify();
        assertThat(sources).extracting(WebSource::url)
                .containsExactly("https://pune.gov.in/metro-3", "https://pmc.gov.in/notice");
        assertThat(sources.get(0).publishedDate()).isEqualTo("2026-11-01");
    }

    @Test
    void searchErrorsReturnNoSourcesInsteadOfFailingTheReport() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.tavily.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TavilyWebSearchClient client = new TavilyWebSearchClient(builder.build(), "test-key", 4);
        server.expect(requestTo("https://api.tavily.com/search"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(client.search("metro near Pune", List.of())).isEmpty();
        server.verify();
    }

    @Test
    void withoutAnApiKeyTheClientIsDisabledAndUnavailable() {
        TavilyWebSearchClient client = new TavilyWebSearchClient(RestClient.builder().build(), " ", 4);

        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.search("anything", List.of()))
                .isInstanceOf(ResearchNotConfiguredException.class)
                .hasMessageContaining("TAVILY_API_KEY");
    }

    @Test
    void blankResponsesAndUnknownJsonAreIgnored() {
        TavilyWebSearchClient client = new TavilyWebSearchClient(RestClient.builder().build(), "key", 4);

        assertThat(client.parse(null)).isEmpty();
        assertThat(client.parse(" ")).isEmpty();
        assertThat(client.parse("{\"results\":[]}")).isEmpty();
    }
}
