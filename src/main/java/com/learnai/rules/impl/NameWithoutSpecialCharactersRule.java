package com.learnai.rules.impl;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.learnai.rules.DocumentContext;
import com.learnai.rules.RuleResult;
import com.learnai.rules.ValidationRule;

/**
 * Rule 1: the document must contain a name made up only of letters and single spaces.
 */
@Component
@Order(1)
public class NameWithoutSpecialCharactersRule implements ValidationRule {

    private final Pattern allowedName;

    public NameWithoutSpecialCharactersRule(
            @Value("${learnai.rules.name.pattern:^\\p{L}+( \\p{L}+)*$}") String allowedNamePattern) {
        this.allowedName = Pattern.compile(allowedNamePattern);
    }

    @Override
    public String id() {
        return "NAME_WITHOUT_SPECIAL_CHARACTERS";
    }

    @Override
    public String description() {
        return "Document must include a name without special characters";
    }

    @Override
    public RuleResult evaluate(DocumentContext context) {
        String name = context.fields().name();
        if (name == null || name.isBlank()) {
            return RuleResult.fail(this, "No name found in the document");
        }
        String trimmed = name.trim();
        if (!allowedName.matcher(trimmed).matches()) {
            return RuleResult.fail(this, "Name '" + trimmed + "' contains special characters");
        }
        return RuleResult.pass(this, "Name '" + trimmed + "' is valid");
    }
}
