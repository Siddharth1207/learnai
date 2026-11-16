# learnai

Spring Boot service that reads a document (PDF, DOCX, TXT, ... via Apache Tika), extracts structured fields
(with an LLM when configured, regex fallback otherwise) and returns a **PASS / FAIL** verdict based on a set of rules.

## Rules

| # | Id | Description |
|---|----|-------------|
| 1 | `NAME_WITHOUT_SPECIAL_CHARACTERS` | Document must include a name made of letters separated by single spaces (no digits, symbols or punctuation). Pattern configurable via `learnai.rules.name.pattern`. |

### Adding a rule

1. Add any new field the rule needs to `DocumentFields` (and the AI prompt in `AiFieldExtractor` if it needs extraction).
2. Create a `@Component` implementing `ValidationRule` in `com.learnai.rules.impl` (use `@Order` to control order).

All `ValidationRule` beans are picked up automatically; a document passes only if every rule passes.

## Running

Requires Java 17+.

```bash
./mvnw spring-boot:run
```

Enable AI extraction (OpenAI):

```bash
export LEARNAI_CHAT_MODEL=openai
export OPENAI_API_KEY=sk-...
# optional: export OPENAI_MODEL=gpt-4o-mini
./mvnw spring-boot:run
```

Without these variables the app uses `RegexFieldExtractor`, which reads a line like `Name: John Doe`.

## API

```bash
# Validate an uploaded document
curl -F file=@form.pdf http://localhost:8080/api/documents/validate

# Validate raw text
curl -H 'Content-Type: application/json' -d '{"text":"Name: John Doe"}' \
  http://localhost:8080/api/documents/validate-text

# List active rules
curl http://localhost:8080/api/rules
```

Example response:

```json
{
  "verdict": "FAIL",
  "extractionStrategy": "regex",
  "extractedFields": { "name": "John@Doe" },
  "results": [
    {
      "ruleId": "NAME_WITHOUT_SPECIAL_CHARACTERS",
      "description": "Document must include a name without special characters",
      "passed": false,
      "message": "Name 'John@Doe' contains special characters"
    }
  ]
}
```
