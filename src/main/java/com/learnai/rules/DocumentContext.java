package com.learnai.rules;

import com.learnai.extraction.DocumentFields;

/**
 * Everything a rule may inspect: the raw document text and the extracted fields.
 */
public record DocumentContext(String text, DocumentFields fields) {
}
