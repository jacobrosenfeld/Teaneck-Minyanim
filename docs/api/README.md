# Teaneck Minyanim — Public REST API v1

**Base path:** `/api/v1/`
**Interactive docs (Scalar):** `/api/docs`
**OpenAPI JSON:** `/api/docs.json`
**Auth:** None required (all endpoints are public read-only)
**Rate limit:** 60 requests / minute / IP → `429 Too Many Requests`
**Times:** `HH:mm` format, `America/New_York` timezone
**Dates:** ISO-8601 `YYYY-MM-DD`

---

## Response envelope

Every response wraps its payload in a consistent structure:

```json
{
  "data": <payload>,
  "meta": { "count": 5, "windowStart": "2026-02-22", "windowEnd": "2026-05-17" },
  "error": null
}
```

On error, `data` is null and `error` is populated:

```json
{
  "data": null,
  "meta": null,
  "error": { "code": "INVALID_DATE", "message": "Use ISO-8601 format: YYYY-MM-DD" }
}
```

`meta` fields vary by endpoint but always include `count` where a list is returned. Schedule endpoints additionally include `windowStart` and `windowEnd` so clients know the queryable range.

---

## Organizations

### `GET /api/v1/organizations`
Returns all enabled organizations.

**Response `data`:** array of Organization objects

```json
{
  "id": "bmob",
  "name": "BMOB",
  "slug": "bmob",
  "color": "#1a5276",
  "nusach": "ASHKENAZ",
  "nusachDisplay": "Ashkenaz",
  "address": "534 Larch Ave, Teaneck NJ",
  "websiteUrl": "bmob.org",
  "whatsapp": "https://chat.whatsapp.com/..."
}
```

---

### `GET /api/v1/organizations/{id}`
Returns a single organization by internal ID **or** URL slug.

**Path param:** `id` — org ID or slug
**404** if not found or disabled.

---

## Schedule

The schedule is pre-materialized in a rolling **11-week window** (3 past weeks + 8 future weeks). Querying outside this window returns `400 OUT_OF_WINDOW`. Use `meta.windowStart` / `meta.windowEnd` from any response to know the current bounds.

**Precedence:** If any `IMPORTED` events exist for an org on a given date, those are returned exclusively (imported overrides rules). This is applied server-side — the client always gets the canonical view.

### `GET /api/v1/schedule`
Combined schedule across **all organizations**.

**Query params:**

| Param | Required | Description |
|---|---|---|
| `date` | No | Single date `YYYY-MM-DD`. Shorthand for `start=date&end=date`. Defaults to today. |
| `start` | No | Range start `YYYY-MM-DD` |
| `end` | No | Range end `YYYY-MM-DD`. Max 14 days after `start`. |

**Response `data`:** flat array of ScheduleEvent objects, sorted by `date` then `startTime`.

**Error codes:**
- `INVALID_DATE` — unparseable date
- `INVALID_RANGE` — start after end
- `RANGE_TOO_LARGE` — range exceeds 14 days
- `OUT_OF_WINDOW` — date outside materialization window

---

### `GET /api/v1/organizations/{id}/schedule`
Schedule for a **single organization**. Accepts org ID or slug.

Same query params as combined schedule, but max range is **30 days**.

---

### ScheduleEvent object

```json
{
  "id": "cal-4821",
  "date": "2026-03-15",
  "startTime": "07:00",
  "minyanType": "SHACHARIS",
  "minyanTypeDisplay": "Shacharis",
  "organization": {
    "id": "bmob",
    "name": "BMOB",
    "slug": "bmob",
    "color": "#1a5276",
    "whatsapp": null
  },
  "locationName": "Main Sanctuary",
  "notes": null,
  "nusach": "ASHKENAZ",
  "nusachDisplay": "Ashkenaz",
  "dynamicTimeString": null,
  "source": "RULES",
  "whatsapp": null
}
```

**`dynamicTimeString`** is non-null for rule-based events that are tied to a halachic time, e.g. `"NETZ+5min"` or `"PLAG-10min"`. Display as `"7:00 (Netz +5 min)"`.

**`source`** is `RULES`, `IMPORTED`, or `MANUAL` (manual overrides, future feature).

---

## Zmanim

### `GET /api/v1/zmanim`
Returns 14 halachic times for a date, calculated for **Teaneck, NJ**.

**Query params:**

| Param | Required | Description |
|---|---|---|
| `date` | No | `YYYY-MM-DD`. Defaults to today. |

**Response:**

```json
{
  "data": {
    "date": "2026-03-15",
    "hebrewDate": "כ״ה אדר תשפ״ו",
    "times": {
      "alotHashachar": "05:42",
      "misheyakir": "06:07",
      "netz": "06:31",
      "sofZmanShmaGra": "09:38",
      "sofZmanShmaMga": "08:49",
      "sofZmanTfilaGra": "10:41",
      "sofZmanTfilaMga": "10:07",
      "chatzos": "12:47",
      "minchaGedola": "13:18",
      "minchaKetana": "16:02",
      "plagHamincha": "17:13",
      "shekiya": "19:03",
      "tzeis": "19:30",
      "chatzosLaila": "00:47"
    }
  },
  "meta": {
    "timezone": "America/New_York",
    "location": "Teaneck, NJ"
  }
}
```

---

## Notifications

### `GET /api/v1/notifications`
Returns all currently active announcements.

**Query params:**

| Param | Required | Description |
|---|---|---|
| `type` | No | Filter by `BANNER` or `POPUP`. Returns both if omitted. |

**Response `data`:** array of Notification objects

```json
{
  "id": "uuid-here",
  "title": "Shul Closed for Renovation",
  "message": "The main sanctuary is closed this Shabbos. Minyanim will be held in the social hall.",
  "type": "BANNER",
  "expiresAt": "2026-03-21T23:59:00",
  "maxDisplays": null
}
```

**`maxDisplays`** — if set, the mobile app should stop showing the notification after the user has seen it N times (mirrors website behavior using cookie tracking).

---

## Mobile app usage patterns

### Initial load
```
GET /api/v1/organizations           ← load shul list once, cache it
GET /api/v1/schedule?date=today     ← Today screen
GET /api/v1/zmanim                  ← Today's halachic times
GET /api/v1/notifications           ← Check for active banners/popups
```

### Paginating forward (week at a time)
```
GET /api/v1/schedule?start=2026-03-22&end=2026-03-28
GET /api/v1/schedule?start=2026-03-29&end=2026-04-04
```

### Org detail screen
```
GET /api/v1/organizations/bmob
GET /api/v1/organizations/bmob/schedule?start=2026-03-15&end=2026-03-21
```

---

## Error reference

| Code | HTTP | Description |
|---|---|---|
| `NOT_FOUND` | 404 | Organization not found or disabled |
| `INVALID_DATE` | 400 | Date param is not valid ISO-8601 |
| `INVALID_RANGE` | 400 | `start` is after `end` |
| `RANGE_TOO_LARGE` | 400 | Range exceeds the allowed max (14 or 30 days) |
| `OUT_OF_WINDOW` | 400 | Requested dates outside materialization window |
| `INVALID_TYPE` | 400 | Notification type is not BANNER or POPUP |
| `RATE_LIMITED` | 429 | Exceeded 60 req/min — retry after 60 s (`Retry-After` header set) |

---

## Maintaining this API

> **Rule:** Any time an endpoint is added, removed, or its request/response shape changes, **both** of the following must be updated:
> 1. The `@Operation` / `@Tag` Swagger annotations in the relevant controller under `com.tbdev.teaneckminyanim.api/`
> 2. This file (`docs/api/README.md`)
>
> The Swagger UI at `/api/docs` is auto-generated from the annotations. The markdown here is the human-readable reference for external developers and the mobile team.

### Adding a new endpoint
1. Create or update a `@RestController` in `com.tbdev.teaneckminyanim.api/`
2. Annotate with `@Tag` (controller level) and `@Operation` + `@Parameter` (method level)
3. Add a DTO in `com.tbdev.teaneckminyanim.api.dto/` — never expose JPA entities directly
4. Permit the path in `WebSecurityConfiguration` under the `/api/v1/**` block (already covered by the wildcard)
5. Update this file
6. Verify the change renders correctly in Scalar at `/api/docs`

### Breaking changes
- Bump the API version (`/v1/` → `/v2/`) for any change that removes fields, renames fields, or changes semantics
- Non-breaking additions (new optional fields, new endpoints) can be added to v1

### Rate limit configuration
`api.ratelimit.requests-per-minute` in `application.properties` (default: 60).
