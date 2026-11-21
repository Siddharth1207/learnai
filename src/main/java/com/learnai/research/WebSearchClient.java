package com.learnai.research;

import java.util.List;

/** Real-time web search provider used by the paid location research report. */
public interface WebSearchClient {

    /** False when the provider is not configured (for example no API key), so callers can answer 503. */
    boolean isEnabled();

    /**
     * Runs one query and returns the most relevant recent pages.
     *
     * @param includeDomains optional provider-side domain restriction, e.g. {@code gov.in}
     * @throws ResearchNotConfiguredException when the provider is not configured
     */
    List<WebSource> search(String query, List<String> includeDomains);
}
