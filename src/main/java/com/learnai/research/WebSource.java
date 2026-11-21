package com.learnai.research;

/**
 * One public web page found for a research query.
 *
 * @param snippet short extract of the page content returned by the search provider
 * @param publishedDate provider-specific publication date string, often null
 */
public record WebSource(String title, String url, String snippet, String publishedDate) {
}
