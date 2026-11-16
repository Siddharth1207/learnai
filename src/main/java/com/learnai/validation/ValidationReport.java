package com.learnai.validation;

import java.util.List;

import com.learnai.extraction.DocumentFields;
import com.learnai.rules.RuleResult;

public record ValidationReport(Verdict verdict, String extractionStrategy, DocumentFields extractedFields,
        List<RuleResult> results) {

    public enum Verdict {
        PASS, FAIL
    }
}
