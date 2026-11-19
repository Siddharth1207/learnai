# Property services API (rent, videos, privacy, paid services)

This document is the contract for the rent, video, privacy and paid-service endpoints. They are
implemented in `PropertyController` / `PropertyService` / `PropertyServiceOrders` /
`PropertyVideoService`; the Angular app (`learningaiui`) can point either at this service or at the
WireMock stubs in `learningaiui/wiremock/`.

All paths are under `/api/properties`. JSON unless multipart is stated. Errors keep the existing
problem responses (`404` unknown listing, `409` unavailable, `422` incomplete voice draft).

## Summary of changes

| Area | Change |
|------|--------|
| Listing type | `listingType` (`SALE` or `RENT`) on every listing, default `SALE` |
| Rent | `deposit` (security deposit, INR) allowed for `RENT`; `price` is the monthly rent |
| Urban fields | `urban.apartmentName`, `urban.tower`, `urban.amenities` |
| Rural fields | `rural.surveyNumber`, `rural.ownershipType`, `rural.crop` |
| Video | Optional walkthrough video per listing, uploaded after creation |
| Privacy | Public listing responses mask contact details, full street address and survey number |
| Paid services | Contact/address unlock, document verification and site-visit booking |
| Map | Coordinates already exist; the app now lets the seller pick them on a map instead of relying only on GPS |

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/properties?propertyType=&status=&listingType=` | List listings; `listingType` is a new optional filter |
| `GET` | `/api/properties/{id}` | Public listing (masked, see below) |
| `POST` | `/api/properties` | Create a listing, with the new fields below |
| `POST` | `/api/properties/voice` | Create from voice; accept optional `listingType` override |
| `POST` | `/api/properties/{id}/videos` | Upload one walkthrough video (multipart `file`) |
| `GET` | `/api/properties/{id}/videos/{videoId}` | Stream the video (HTTP Range supported) |
| `POST` | `/api/properties/{id}/services/CONTACT_UNLOCK` | Paid unlock of full address and seller contact |
| `POST` | `/api/properties/{id}/services/DOCUMENT_VERIFICATION` | Paid title/survey/ownership verification |
| `POST` | `/api/properties/{id}/services/SITE_VISIT` | Paid site-visit booking (date + time required) |

Purchases (`POST /{id}/purchase`) keep their current behaviour. A `RENT` listing is still
purchase/booking-like: the buyer commits at the listed monthly rent; `409` when already taken.

## Create / listing body

```json
{
  "sellerName": "Asha Verma",
  "sellerContact": "+91 98100 00001",
  "propertyType": "URBAN",
  "title": "Sunlit 2BHK near Koregaon Park metro",
  "description": "East-facing apartment five minutes from the metro.",
  "address": {
    "street": "12 MG Road, Flat 504, Lotus Residency",
    "locality": "Koregaon Park",
    "city": "Pune",
    "district": "Pune",
    "state": "Maharashtra",
    "postalCode": "411001",
    "country": "India"
  },
  "coordinates": { "latitude": 18.5362, "longitude": 73.894 },
  "area": 1180,
  "areaUnit": "SQ_FT",
  "price": 9500000,
  "currency": "INR",
  "listingType": "SALE",
  "deposit": null,
  "urban": {
    "buildingType": "apartment",
    "apartmentName": "Lotus Residency",
    "tower": "Tower B · Flat 504",
    "bedrooms": 2,
    "bathrooms": 2,
    "floor": 5,
    "totalFloors": 12,
    "furnishing": "semi-furnished",
    "parking": true,
    "amenities": "Lift, power backup, gym, clubhouse, covered parking"
  },
  "rural": null
}
```

Rural example:

```json
"rural": {
  "landUse": "Agriculture",
  "surveyNumber": "Khasra 145/2",
  "ownershipType": "Freehold",
  "crop": "Grapes (Thompson seedless)",
  "soilType": "Black cotton",
  "waterSource": "Borewell + farm pond",
  "irrigated": true,
  "roadAccess": true,
  "electricity": true
}
```

Validation additions:

- `listingType` defaults to `SALE`.
- `deposit` is non-negative and only meaningful for `RENT`; reject on `SALE` or ignore it (do not expose it).
- `urban` fields still only allowed for `URBAN`, `rural` only for `RURAL`.
- Voice: add an optional multipart `listingType` parameter, treated like the other overrides.

## Public listing masking

`GET /api/properties`, `GET /api/properties/{id}` and search results must not expose the complete
address or contact before a paid unlock.

| Field | Public value |
|-------|--------------|
| `details.sellerContact` | Masked, e.g. `+91 ••••• 00001` or `n•••@example.com` |
| `details.address.street` | `null` |
| `details.address.postalCode` | `null` |
| `details.address.locality`, `city`, `district`, `state` | Kept (approximate location) |
| `details.rural.surveyNumber` | `null`; ownership type and crop may stay |
| `details.sellerName` | Kept (owner name is not contact data) |
| `videos` | Included, with public (or short-lived signed) URLs |

Do not rely on the client to mask. The purchase receipt and the paid-service order may contain the
full values because the buyer has already paid/committed.

## Videos

`POST /api/properties/{id}/videos`, `multipart/form-data`, field `file`.

- Accepted: `video/mp4`, `video/quicktime`, `video/webm`; max 50 MB (`415`/`413` otherwise).
- Files are stored under `learnai.videos.dir` (default `videos`, env `LEARNAI_VIDEOS_DIR`) as
  `<propertyId>/<videoId>` and streamed by the API, never served from the classpath.
- Return `201`:

```json
{
  "id": "video-1",
  "url": "/api/properties/urban-sale-1/videos/video-1",
  "contentType": "video/mp4",
  "sizeBytes": 788493,
  "uploadedAt": "2026-11-19T09:10:00Z"
}
```

`Property.videos` is a list of these objects. Deleting/replacing a listing should delete its files.
Only the seller (authenticated owner) may upload; anonymous uploads must not be accepted in production.

## Paid services

`POST /api/properties/{id}/services/{code}` where `code` is `CONTACT_UNLOCK`,
`DOCUMENT_VERIFICATION` or `SITE_VISIT`.

Request:

```json
{
  "buyerName": "Neha Singh",
  "buyerContact": "neha@example.com",
  "preferredDate": "2026-11-25",
  "preferredTime": "15:30"
}
```

`buyerName` and `buyerContact` are required. `preferredDate` + `preferredTime` are required for
`SITE_VISIT` only, must be in the future, and are read as IST (`+05:30`). No payment provider is
configured yet, so orders are recorded as `PAID` immediately (wire a gateway before saving the order);
`REQUESTED` is reserved for an asynchronous provider. The response is a `PaidServiceOrder`:

```json
{
  "id": "order-1",
  "propertyId": "urban-sale-1",
  "service": "CONTACT_UNLOCK",
  "amount": 199,
  "currency": "INR",
  "status": "PAID",
  "purchasedAt": "2026-11-19T09:15:00Z",
  "sellerContact": "+91 98100 00001",
  "fullAddress": {
    "street": "12 MG Road, Flat 504, Lotus Residency",
    "locality": "Koregaon Park",
    "city": "Pune",
    "district": "Pune",
    "state": "Maharashtra",
    "postalCode": "411001",
    "country": "India"
  }
}
```

`CONTACT_UNLOCK` (₹199) returns `sellerContact` + `fullAddress`. It must not return
`surveyNumber`; if survey data is sold separately, add another service code.

`DOCUMENT_VERIFICATION` (₹499) returns a `VERIFIED` report (`reportUrl` is `null` until a report
storage/signing provider exists):

```json
"verification": {
  "status": "VERIFIED",
  "summary": "Title deed, 7/12 extract and tax receipts match the seller name. No encumbrance found.",
  "verifiedAt": "2026-11-19T09:21:00Z",
  "reportUrl": "https://example.com/reports/order-1.pdf"
}
```

`status` is `PENDING`, `VERIFIED` or `FAILED`. `reportUrl` should be a short-lived signed URL.

`SITE_VISIT` (₹199) returns:

```json
"meeting": {
  "scheduledAt": "2026-11-25T15:30:00+05:30",
  "mode": "ON_SITE",
  "place": "Main gate, Lotus Residency, Koregaon Park"
}
```

`mode` is `ON_SITE` or `VIDEO_CALL`. Confirm the slot with the seller before marking it scheduled.
Orders are stored in the `property_service_orders` collection with buyer, property, service, amount,
status and timestamps; add a payment reference and idempotency key when a gateway is wired in.

Privacy and abuse notes:

- Never return full contact/address without a `PAID` order for that buyer and property.
- A paid unlock should reveal only that property, not all listings by the same seller.
- Rate-limit unlock attempts and audit who viewed which address.
- The public listing must keep coordinates (needed for map search) even though the street is hidden.

## WireMock demo data

`learningaiui/wiremock/` contains stubs for all of the above, served on port `8081`:

- `urban-sale-1`: Pune 2BHK, sale, video, unlocked paid-service samples
- `urban-rent-2`: Bengaluru 3BHK, rent with deposit
- `rural-sale-3`: Nashik grape farm, survey number present in storage but hidden by the response
- `rural-rent-4`: Karjat farmhouse, rent with deposit

Start it with `npm run mock:up` from `learningaiui`, then `npm run start:wiremock`.
