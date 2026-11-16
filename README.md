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

Enable AI (Google Gemini, free tier). Get an API key at https://aistudio.google.com/apikey:

```bash
export LEARNAI_CHAT_MODEL=google-genai
export GEMINI_API_KEY=...
# optional: export GEMINI_MODEL=gemini-2.5-flash
./mvnw spring-boot:run
```

Without these variables the app uses `RegexFieldExtractor`, which reads a line like `Name: John Doe`.

## Audio transcription

`POST /api/audio/transcribe` sends the audio to Gemini, which transcribes it. Requires
`LEARNAI_CHAT_MODEL=google-genai` and `GEMINI_API_KEY`; otherwise it returns 503.

| Param | Description |
|-------|-------------|
| `file` | Audio file (wav, mp3, aiff, aac, ogg, flac; max 20MB). Non-audio files get 415 |
| `inputLanguage` | Spoken language as an ISO-639-1 code (`hi`) or English name (`Hindi`) |

If `inputLanguage` is not English, the transcript is translated to English with a second Gemini call. The English text is
saved to a new file in `learnai.audio.output-dir` (default `./transcripts`, env `LEARNAI_AUDIO_OUTPUT_DIR`).

```bash
curl -F file=@clip.mp3 -F inputLanguage=Hindi http://localhost:8080/api/audio/transcribe
```

```json
{
  "inputLanguage": "hi",
  "transcript": "नमस्ते दुनिया",
  "translated": true,
  "englishText": "Hello world",
  "savedFile": "transcripts/transcript-20261116-210501-1a2b3c4d-en.txt"
}
```

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
