package com.learnai.validation;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.learnai.document.DocumentTextExtractor;
import com.learnai.extraction.DocumentFields;
import com.learnai.extraction.FieldExtractor;
import com.learnai.rules.DocumentContext;
import com.learnai.rules.RuleResult;
import com.learnai.rules.ValidationRule;
import com.learnai.validation.ValidationReport.Verdict;

@Service
public class DocumentValidationService {

    private final DocumentTextExtractor textExtractor;
    private final FieldExtractor fieldExtractor;
    private final List<ValidationRule> rules;

    public DocumentValidationService(DocumentTextExtractor textExtractor, FieldExtractor fieldExtractor,
            List<ValidationRule> rules) {
        this.textExtractor = textExtractor;
        this.fieldExtractor = fieldExtractor;
        this.rules = rules;
    }

    public ValidationReport validate(Resource document) {
        return validateText(textExtractor.extractText(document));
    }

    public ValidationReport validateText(String text) {
        DocumentFields fields = fieldExtractor.extract(text);
        DocumentContext context = new DocumentContext(text, fields);
        List<RuleResult> results = rules.stream().map(rule -> rule.evaluate(context)).toList();
        Verdict verdict = results.stream().allMatch(RuleResult::passed) ? Verdict.PASS : Verdict.FAIL;
        return new ValidationReport(verdict, fieldExtractor.strategy(), fields, results);
    }

    public List<ValidationRule> rules() {
        return rules;
    }
}
