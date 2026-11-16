package com.learnai.rules;

public record RuleResult(String ruleId, String description, boolean passed, String message) {

    public static RuleResult pass(ValidationRule rule, String message) {
        return new RuleResult(rule.id(), rule.description(), true, message);
    }

    public static RuleResult fail(ValidationRule rule, String message) {
        return new RuleResult(rule.id(), rule.description(), false, message);
    }
}
