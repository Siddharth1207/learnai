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
| `file` | Audio file (wav, mp3, aiff, aac, ogg, flac; max 14MB). Non-audio files get 415 |
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

## Vector search (MongoDB Atlas Vector Search)

Text is embedded with Gemini (`gemini-embedding-001`) and stored in MongoDB; searches use `$vectorSearch`.
Locally, MongoDB runs in Docker using the `mongodb/mongodb-atlas-local` image (supports Atlas Vector Search).

1. Start MongoDB (Docker Desktop on Windows, PowerShell or cmd, from the project folder):

   ```powershell
   docker compose up -d
   docker compose ps   # wait until learnai-mongodb is "healthy"
   ```

   It listens on `localhost:27017` with user/password `learnai`/`learnai`. Data is kept in Docker volumes;
   `docker compose down -v` deletes it.

2. Run the app with AI and vector search enabled (PowerShell):

   ```powershell
   $env:LEARNAI_CHAT_MODEL="google-genai"
   $env:GEMINI_API_KEY="..."
   $env:LEARNAI_VECTOR_STORE="mongodb-atlas"
   .\mvnw.cmd spring-boot:run
   ```

   On startup the app creates the `vector_store` collection and the `vector_index` search index.

| Env var | Default | Description |
|---------|---------|-------------|
| `LEARNAI_VECTOR_STORE` | `none` | `mongodb-atlas` enables vector search; otherwise `/api/vectors/**` returns 503 |
| `MONGODB_URI` | built from the vars below | Full connection string (overrides the vars below) |
| `MONGODB_HOST` / `MONGODB_PORT` | `localhost` / `27017` | MongoDB address |
| `MONGODB_USERNAME` / `MONGODB_PASSWORD` | `learnai` / `learnai` | Also used by `docker-compose.yml` to create the user |
| `MONGODB_DATABASE` | `learnai` | Database name |
| `LEARNAI_EMBEDDING_MODEL` | value of `LEARNAI_CHAT_MODEL` | `google-genai` enables Gemini embeddings |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-001` | Embedding model (index dimensions are set from it) |
| `LEARNAI_VECTOR_COLLECTION` / `LEARNAI_VECTOR_INDEX` | `vector_store` / `vector_index` | Collection and search index names |
| `LEARNAI_VECTOR_FILTER_FIELDS` | `source,category` | Metadata fields usable in `filter` |

Changing the embedding model or filter fields requires dropping the existing search index (or using a new index name).

```bash
# Store text with metadata
curl -H 'Content-Type: application/json' \
  -d '{"documents":[{"text":"Invoice 42 for John Doe, total 100 EUR","metadata":{"category":"invoice"}}]}' \
  http://localhost:8080/api/vectors/documents
# -> {"ids":["4c1f..."]}

# Upload a file (PDF, DOCX, TXT, ...); it is split into chunks with metadata source=<file name>
curl -F file=@form.pdf -F category=form http://localhost:8080/api/vectors/documents/upload

# Semantic search (topK default 4, similarityThreshold 0..1 default 0, filter optional)
curl -H 'Content-Type: application/json' \
  -d '{"query":"how much does John owe?","topK":3,"filter":"category == '\''invoice'\''"}' \
  http://localhost:8080/api/vectors/search
# -> [{"id":"4c1f...","text":"Invoice 42 ...","score":0.83,"metadata":{"category":"invoice"}}]

# Delete by id
curl -X DELETE 'http://localhost:8080/api/vectors/documents?ids=4c1f...'
```

The integration test `MongoVectorSearchIT` runs against the Docker MongoDB with a local fake embedding model (no API key):
`LEARNAI_MONGODB_IT=true ./mvnw test -Dtest=MongoVectorSearchIT`.

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
