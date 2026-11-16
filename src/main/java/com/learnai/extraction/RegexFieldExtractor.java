package com.learnai.extraction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback extractor used when no AI model is configured. Looks for a line such as {@code Name: John Doe}.
 */
public class RegexFieldExtractor implements FieldExtractor {

    private static final Pattern NAME_LINE = Pattern.compile(
            "^\\s*(?:full\\s+)?name\\s*[:=\\-]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Override
    public DocumentFields extract(String documentText) {
        if (documentText == null) {
            return DocumentFields.empty();
        }
        Matcher matcher = NAME_LINE.matcher(documentText);
        return new DocumentFields(matcher.find() ? matcher.group(1) : null);
    }

    @Override
    public String strategy() {
        return "regex";
    }
}
