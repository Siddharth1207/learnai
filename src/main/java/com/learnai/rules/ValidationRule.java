package com.learnai.rules;

/**
 * A single pass/fail check applied to a document. Register a new rule by adding a
 * Spring {@code @Component} that implements this interface.
 */
public interface ValidationRule {

    String id();

    String description();

    RuleResult evaluate(DocumentContext context);
}
