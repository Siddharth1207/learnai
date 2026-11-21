package com.learnai.research;

import java.time.Duration;
import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Tavily search API client (https://docs.tavily.com). Tavily has a free tier and returns recent web results
 * with short content extracts, which is enough for grounded location research without crawling pages ourselves.
 * Configured with {@code TAVILY_API_KEY}; without it {@link #isEnabled()} is false and the paid report answers 503.
 */
@Component
public class TavilyWebSearchClient implements WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(TavilyWebSearchClient.class);
    private static final int SNIPPET_LENGTH = 500;

    private final RestClient restClient;
    private final String apiKey;
    private final int maxResults;

    @Autowired
    public TavilyWebSearchClient(
            @Value("${learnai.research.tavily.base-url:https://api.tavily.com}") String baseUrl,
            @Value("${learnai.research.tavily.api-key:}") String apiKey,
            @Value("${learnai.research.max-results:4}") int maxResults) {
        this(buildRestClient(baseUrl), apiKey, maxResults);
    }

    TavilyWebSearchClient(RestClient restClient, String apiKey, int maxResults) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxResults = maxResults;
    }

    @Override
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    @Override
    public List<WebSource> search(String query, List<String> includeDomains) {
        if (!isEnabled()) {
            throw new ResearchNotConfiguredException(
                    "Internet research is not configured. Set TAVILY_API_KEY to enable the location report.");
        }
        Document request = new Document("api_key", apiKey)
                .append("query", query)
                .append("search_depth", "basic")
                .append("topic", "general")
                .append("max_results", maxResults)
                .append("time_range", "year")
                .append("include_answer", false)
                .append("include_raw_content", false);
        if (includeDomains != null && !includeDomains.isEmpty()) {
            request.append("include_domains", includeDomains);
        }
        try {
            String response = restClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request.toJson())
                    .retrieve()
                    .body(String.class);
            return parse(response);
        }
        catch (RestClientException e) {
            log.warn("Tavily search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
        catch (RuntimeException e) {
            log.warn("Could not read the Tavily response for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    List<WebSource> parse(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        Document body = Document.parse(response);
        List<Document> results = body.getList("results", Document.class);
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .filter(result -> result.getString("url") != null)
                .limit(maxResults)
                .map(result -> new WebSource(text(result.getString("title")), result.getString("url"),
                        text(result.getString("content")), result.getString("published_date")))
                .toList();
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= SNIPPET_LENGTH ? trimmed : trimmed.substring(0, SNIPPET_LENGTH) + "…";
    }

    private static RestClient buildRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
