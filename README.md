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
# Store text with metadata (max 100 documents per request)
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

## Property listings (sellers) and purchases (buyers)

Sellers list **URBAN** or **RURAL** properties with coordinates, either by filling a form or by speaking. Listings are
stored in MongoDB (`properties` collection) and their text is embedded in the vector store (`category` metadata
`property-urban` / `property-rural`), so buyers can search them in natural language (typed or spoken). The search is
RAG: the best listings are retrieved from MongoDB Atlas Vector Search and Gemini answers the buyer's request from them.

| Endpoint | Description |
|----------|-------------|
| `POST /api/properties` | Create a listing from the form (JSON below) |
| `POST /api/properties/voice` | Create a listing from the seller's voice description (multipart) |
| `GET /api/properties?propertyType=&status=&listingType=` | List listings (`URBAN`/`RURAL`, `AVAILABLE`/`SOLD`, `SALE`/`RENT`) |
| `GET /api/properties/{id}` | Get a listing (masked public view) |
| `POST /api/properties/search` | Buyer search (RAG) with optional filters |
| `POST /api/properties/search/voice` | Buyer search from a spoken request (multipart) |
| `POST /api/properties/{id}/purchase` | Buy or rent a listing at its listed price (409 if already taken) |
| `GET /api/properties/{id}/purchase` | Get the purchase of a sold/rented listing |
| `POST /api/properties/{id}/videos` | Upload an optional walkthrough video (multipart `file`) |
| `POST /api/properties/{id}/services/CONTACT_UNLOCK` | Paid unlock of the full address and seller contact (₹199) |
| `POST /api/properties/{id}/services/DOCUMENT_VERIFICATION` | Paid document verification report (₹499) |
| `POST /api/properties/{id}/services/SITE_VISIT` | Paid site-visit booking (₹199) |

The rent, video, privacy and paid-service contract is specified in
[docs/property-services-api.md](docs/property-services-api.md). The same endpoints are stubbed in
`learningaiui/wiremock/` so the Angular app can run without this service.

Public listing responses mask the seller contact, street/postal address and rural survey number. Full values are only
returned by a purchase or a paid `CONTACT_UNLOCK` order.


Requirements: MongoDB (`docker compose up -d`) for all endpoints. Voice endpoints need `LEARNAI_CHAT_MODEL=google-genai`
and `GEMINI_API_KEY` (503 otherwise). Search needs `LEARNAI_VECTOR_STORE=mongodb-atlas` (503 otherwise); listings created
while the vector store is disabled are saved but not searchable (`vectorDocumentId: null`). The default
`LEARNAI_VECTOR_FILTER_FIELDS` (`source,category`) is enough; no index change is needed.

### Form

```bash
curl -H 'Content-Type: application/json' http://localhost:8080/api/properties -d '{
  "sellerName": "Asha Verma", "sellerContact": "+91 98100 00001", "propertyType": "URBAN",
  "title": "2BHK near Koregaon Park metro", "description": "Sunny flat with a balcony",
  "address": {"street": "12 MG Road", "locality": "Koregaon Park", "city": "Pune", "state": "Maharashtra", "postalCode": "411001", "country": "India"},
  "coordinates": {"latitude": 18.5362, "longitude": 73.8940},
  "area": 1200, "areaUnit": "SQ_FT", "price": 9500000, "currency": "INR",
  "listingType": "SALE",
  "urban": {"buildingType": "apartment", "apartmentName": "Lotus Residency", "tower": "Tower B · Flat 504", "bedrooms": 2, "bathrooms": 2, "floor": 5, "totalFloors": 12, "furnishing": "semi-furnished", "parking": true, "amenities": "Lift, gym, covered parking"}
}'
```

Required: `sellerName`, `sellerContact`, `propertyType`, `address.city`, `coordinates` (latitude -90..90, longitude
-180..180), `area` + `areaUnit` (`SQ_FT`, `SQ_M`, `SQ_YD`, `ACRE`, `HECTARE`) and `price`. `currency` defaults to `INR`
and `title` to e.g. "Urban property in Pune". Use `urban` only for URBAN listings and `rural` only for RURAL ones:

```json
"rural": {"landUse": "orchard", "surveyNumber": "Khasra 145/2", "ownershipType": "Freehold", "crop": "Grapes", "soilType": "black", "waterSource": "borewell", "irrigated": true, "roadAccess": true, "electricity": true}
```

For rent, set `"listingType": "RENT"` and add `"deposit": 300000`; `price` is then the monthly rent.
`listingType` defaults to `SALE`. The seller can pick coordinates on a map in the app, or send device GPS values.

### Voice

The audio goes through the same speech-to-text and translation as `/api/audio/transcribe`, then Gemini extracts the
property fields from the English text. Optional form fields override what was spoken; send the device's GPS location
as `latitude`/`longitude`, since sellers rarely speak coordinates.

```bash
curl -F file=@listing.mp3 -F inputLanguage=Hindi -F sellerContact="+91 98100 00002" -F listingType=RENT \
  -F latitude=18.8268 -F longitude=74.3741 http://localhost:8080/api/properties/voice
# 201 -> {"transcription": {...}, "property": {...}}
```

If required details are missing, the response is **422** with the transcript, the extracted `draft` and the missing
fields. Complete the draft and submit it to `POST /api/properties`:

```json
{"message": "The voice description is missing required property details",
 "transcription": {"inputLanguage": "hi", "englishText": "I want to sell 5 acres of mango orchard ...", ...},
 "draft": {"propertyType": "RURAL", "area": 5, "areaUnit": "ACRE", "price": null, ...},
 "errors": [{"field": "price", "message": "must not be null"}]}
```

### Buyer search (RAG)

```bash
curl -H 'Content-Type: application/json' http://localhost:8080/api/properties/search -d '{
  "query": "farm land with water near Pune under 50 lakh",
  "propertyType": "RURAL", "listingType": "SALE", "maxPrice": 5000000,
  "near": {"latitude": 18.52, "longitude": 73.86}, "radiusKm": 100, "topK": 5
}'
# -> {"query": "...", "answer": "Listing 9f2c... fits best: 5-acre irrigated orchard ...",
#     "results": [{"property": {...}, "score": 0.81, "distanceKm": 58.3}]}
```

Only `query` is required. Sold listings are never returned. `answer` is null when no chat model is configured.
Spoken search takes the same filters as form fields:

```bash
curl -F file=@request.mp3 -F inputLanguage=hi -F maxPrice=5000000 -F latitude=18.52 -F longitude=73.86 -F radiusKm=100 \
  http://localhost:8080/api/properties/search/voice
```

### Purchase

```bash
curl -H 'Content-Type: application/json' -d '{"buyerName": "Neha Singh", "buyerContact": "neha@example.com"}' \
  http://localhost:8080/api/properties/{id}/purchase
# 201 -> {"id": "...", "propertyId": "...", "price": 9500000, "currency": "INR", "coordinates": {...},
#         "sellerName": "Asha Verma", "buyerName": "Neha Singh", "purchasedAt": "..."}
```

The listing is marked `SOLD` atomically, so only one buyer can succeed; others get 409. The purchase is stored in the
`purchases` collection and the listing is removed from the vector store.

`PropertyMongoIT` covers listing, search and purchase against the Docker MongoDB without an API key:
`LEARNAI_MONGODB_IT=true ./mvnw test -Dtest=PropertyMongoIT`.

### Walkthrough videos

```bash
curl -F file=@walkthrough.mp4 http://localhost:8080/api/properties/{id}/videos
# 201 -> {"id": "...", "url": "/api/properties/{id}/videos/...", "contentType": "video/mp4", ...}
# Stream it (HTTP Range works, so <video> can seek):
curl -o clip.mp4 http://localhost:8080/api/properties/{id}/videos/{videoId}
```

MP4, MOV and WebM up to 50 MB are accepted (`415`/`413` otherwise). Files are stored under
`learnai.videos.dir` (default `videos`, env `LEARNAI_VIDEOS_DIR`) and the metadata on the listing.

### Paid services

No payment provider is configured, so orders are recorded as `PAID` immediately. A paid service requires the
listing to be `AVAILABLE` (409 otherwise).

```bash
# Full address and seller contact (₹199)
curl -H 'Content-Type: application/json' -d '{"buyerName": "Neha Singh", "buyerContact": "neha@example.com"}' \
  http://localhost:8080/api/properties/{id}/services/CONTACT_UNLOCK

# Document-verification report (₹499)
curl -H 'Content-Type: application/json' -d '{"buyerName": "Neha Singh", "buyerContact": "neha@example.com"}' \
  http://localhost:8080/api/properties/{id}/services/DOCUMENT_VERIFICATION

# Site visit (₹199); date and time are IST and must be in the future
curl -H 'Content-Type: application/json' \
  -d '{"buyerName": "Neha Singh", "buyerContact": "neha@example.com", "preferredDate": "2026-11-25", "preferredTime": "15:30"}' \
  http://localhost:8080/api/properties/{id}/services/SITE_VISIT
```

Orders are stored in `property_service_orders`. `CONTACT_UNLOCK` returns `sellerContact` and `fullAddress`,
`DOCUMENT_VERIFICATION` returns a `verification` report and `SITE_VISIT` returns a `meeting` with
`scheduledAt` in IST.

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
