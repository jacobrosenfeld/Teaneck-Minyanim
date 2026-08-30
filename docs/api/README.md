# Teaneck Minyanim — Public REST API v1

**Base path:** `/api/v1/`
**Interactive docs (Scalar):** `/api/docs`
**OpenAPI JSON:** `/api/docs.json`
**Auth:** None required
**Rate limit:** 60 requests / minute / IP; feedback submissions are limited to 5 requests / minute / IP → `429 Too Many Requests`
**Times:** `HH:mm` format, `America/New_York` timezone
**Dates:** ISO-8601 `YYYY-MM-DD`

**Servers:**
- Production: `https://teaneckminyanim.com`
- Dev: `https://dev.teaneckminyanim.com`
- Local development: `http://localhost:8080`

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
  "displayMinyanType": "SHACHARIS",
  "displayMinyanTypeDisplay": "Shacharis",
  "groupMinyanType": "SHACHARIS",
  "groupMinyanTypeDisplay": "Shacharis",
  "linkedMinyanType": null,
  "linkedMinyanTypeDisplay": null,
  "linkedStartTime": null,
  "linkedTarget": false,
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

**Display/group fields** let clients render derived public labels without rewriting schedule logic:
- `displayMinyanType` / `displayMinyanTypeDisplay` are the label for the row itself.
- `groupMinyanType` / `groupMinyanTypeDisplay` are the public filter or section grouping.
- `linkedMinyanType`, `linkedMinyanTypeDisplay`, and `linkedStartTime` describe a related service when the event should mention another minyan time.
- `linkedTarget: true` marks a row that has been folded into a linked public event and should usually be hidden from schedule lists.

For Selichos, imported or manual morning Selichos may be displayed as `Selichos & Shacharis` and grouped with Shacharis. When the service appears shortly before a Shacharis row from the same shul/date, clients should display the linked Shacharis time as approximate, for example `Shacharis at approx. 8:45 AM`, and hide the linked target row. Late-night standalone Selichos uses `NIGHT_SELICHOS` / `Night Selichos`.

---

## Zmanim

### `GET /api/v1/zmanim`
Returns 15 halachic times for a date, calculated for **Teaneck, NJ**.

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

## Feedback

### `POST /api/v1/feedback`
Creates a GitHub issue from user-submitted feedback. The endpoint is public, but GitHub credentials are server-side only and configured from application settings.

The visible client UI should collect only:

| Field | Required | Description |
|---|---|---|
| `message` | Yes | Free-text user feedback. Max 5,000 characters. |
| `category` | Yes | Feedback category. Supported values: `MINYAN_SCHEDULE` or `APP_FUNCTIONALITY`. |
| `email` | No | Optional user email for private follow-up. Never include this in public GitHub issue content. |
| `recaptchaToken` | When configured | Invisible reCAPTCHA response token. Required when reCAPTCHA site and secret keys are configured. |

Clients may send `metadata` gathered automatically in the background. Do not ask users to type this manually.

**Request body:**

```json
{
  "message": "The Shacharis time looks wrong for this shul.",
  "email": "optional-user@example.com",
  "category": "MINYAN_SCHEDULE",
  "recaptchaToken": "recaptcha-response-token",
  "metadata": {
    "platform": "web",
    "screen": "organization-detail",
    "route": "/bnai-yeshurun",
    "url": "https://www.teaneckminyanim.com/bnai-yeshurun",
    "selectedDate": "2026-06-02",
    "organization": {
      "id": "O123",
      "slug": "bnai-yeshurun",
      "name": "Bnai Yeshurun"
    },
    "minyan": {
      "id": "cal-123",
      "type": "SHACHARIS",
      "time": "07:00",
      "date": "2026-06-02",
      "locationName": "Main Shul"
    },
    "posthog": {
      "distinctId": "distinct-id",
      "sessionId": "session-id",
      "sessionReplayUrl": "https://..."
    }
  }
}
```

**Response `data`:**

```json
{
  "feedbackId": "fb-uuid",
  "githubIssueNumber": 245,
  "githubIssueUrl": "https://github.com/jacobrosenfeld/Teaneck-Minyanim/issues/245",
  "userEmailProvided": true,
  "notificationEmailSent": true,
  "notificationEmailMessage": "Email sent successfully."
}
```

**Behavior notes:**

- The shared public website widget is rendered only when Feedback GitHub owner, repository, and token settings are populated.
- Website submissions use an Intercom-style floating popup with a category selector for schedule/data issues versus app/website functionality issues.
- When reCAPTCHA site and secret key settings are populated, website submissions include an invisible reCAPTCHA token and the API verifies it before creating a GitHub issue.
- The public GitHub issue body includes the user message and automatically collected debugging metadata.
- Created GitHub issues are labeled `user feedback`.
- The optional user email is used only for private email notification/follow-up and is not written to the GitHub issue.
- If email delivery fails, the GitHub issue still remains created and `notificationEmailSent` is `false`.
- The created issue email includes the GitHub issue link.

**Error codes:**
- `INVALID_FEEDBACK` — blank message, invalid email, invalid category, or message too long
- `INVALID_RECAPTCHA` — reCAPTCHA is configured but the token is missing or rejected by Google
- `FEEDBACK_NOT_CONFIGURED` — GitHub owner/repo/token settings are missing
- `FEEDBACK_SUBMISSION_FAILED` — GitHub issue creation failed

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
| `INVALID_FEEDBACK` | 400 | Feedback message/email payload is invalid |
| `INVALID_RECAPTCHA` | 400 | reCAPTCHA token is missing or invalid |
| `FEEDBACK_NOT_CONFIGURED` | 503 | Feedback GitHub settings are incomplete |
| `FEEDBACK_SUBMISSION_FAILED` | 502 | GitHub issue creation failed |
| `RATE_LIMITED` | 429 | Exceeded endpoint rate limit — retry after 60 s (`Retry-After` header set) |

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
