# Teaneck Minyanim — REST API v1

**Base path:** `/api/v1/`
**Interactive docs (Scalar):** `/api/docs`
**OpenAPI JSON:** `/api/docs.json`
**Auth:** None for public reads. Admin endpoints need `Authorization: Bearer <token>` (see [Authentication](#authentication))
**Rate limit:** per IP, see [Rate limits](#rate-limits) → `429 Too Many Requests`
**Times:** `HH:mm` format, `America/New_York` timezone
**Dates:** ISO-8601 `YYYY-MM-DD`

**Servers:**
- Production: `https://teaneckminyanim.com`
- Dev: `https://dev.teaneckminyanim.com`
- Local development: `http://localhost:8080`

---

## Endpoint overview

| Method | Path | Auth | Scope | Description |
|---|---|---|---|---|
| GET | `/api/v1/organizations` | public | | List enabled shuls |
| GET | `/api/v1/organizations/{id}` | public | | One shul by ID or slug |
| GET | `/api/v1/organizations/{id}/schedule` | public | | Per-shul schedule (max 30 days) |
| GET | `/api/v1/schedule` | public | | Combined schedule (max 14 days) |
| GET | `/api/v1/zmanim` | public | | Halachic times for a date |
| GET | `/api/v1/notifications` | public | | Active announcements |
| POST | `/api/v1/feedback` | public | | Submit feedback |
| POST | `/api/v1/auth/magic-link/request` | public | | Email a sign-in link |
| POST | `/api/v1/auth/magic-link/exchange` | public | | Exchange a link token for app tokens |
| POST | `/api/v1/auth/passkey/options` | public | | Start a passkey sign-in |
| POST | `/api/v1/auth/passkey/verify` | public | | Finish a passkey sign-in |
| POST | `/api/v1/auth/refresh` | refresh token | | Rotate app tokens |
| POST | `/api/v1/auth/logout` | refresh token | | End a mobile session |
| GET | `/api/v1/me` | bearer | any | Who the token belongs to |
| GET | `/api/v1/organizations/{id}/overrides` | bearer | `schedule:read` | List manual overrides |
| POST | `/api/v1/organizations/{id}/overrides` | bearer | `overrides:write` | Create overrides |
| PATCH | `/api/v1/organizations/{id}/overrides/{eventId}` | bearer | `overrides:write` | Edit or toggle an override |
| DELETE | `/api/v1/organizations/{id}/overrides/{eventId}` | bearer | `overrides:write` | Delete an override |
| GET | `/api/v1/organizations/{id}/minyanim` | bearer | `schedule:read` | List minyanim (incl. disabled) |
| GET | `/api/v1/organizations/{id}/minyanim/{minyanId}` | bearer | `schedule:read` | One minyan |
| PATCH | `/api/v1/organizations/{id}/minyanim/{minyanId}` | bearer | `minyanim:write` | Edit, enable or disable a minyan |
| PATCH | `/api/v1/organizations/{id}` | bearer | `shul:write` | Edit shul details |
| GET | `/api/v1/notifications?includeInactive=true` | bearer (super admin) | `notifications:write` | All announcements |
| POST | `/api/v1/notifications` | bearer (super admin) | `notifications:write` | Create an announcement |
| PATCH | `/api/v1/notifications/{id}` | bearer (super admin) | `notifications:write` | Edit or toggle |
| DELETE | `/api/v1/notifications/{id}` | bearer (super admin) | `notifications:write` | Delete |
| GET | `/.well-known/apple-app-site-association` | public | | iOS universal links and passkeys |
| GET | `/.well-known/assetlinks.json` | public | | Android app links and credentials |

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

## Authentication

Public reads (shuls, schedules, zmanim, active notifications, feedback) need no token. Everything else sends the token in a header:

```
Authorization: Bearer <token>
```

| Token | Prefix | Lifetime | Used for |
|---|---|---|---|
| Personal API key | `tnm_` | Never expires; revoke it to end it | Scripts and integrations |
| Mobile access token | `tnmapp_` | 15 minutes | The mobile app, after sign-in |
| Mobile refresh token | `tnmrt_` | 30 days, rotated on every use | Only `/auth/refresh` and `/auth/logout`. Never accepted as a bearer token |

Anything else in the header returns `401 INVALID_TOKEN`. A missing header on an admin endpoint returns `401 UNAUTHENTICATED`. Every 401 sends `WWW-Authenticate: Bearer`. Tokens are random, and only a SHA-256 hash is stored server-side, so a lost token cannot be recovered.

**Browser session cookies are ignored** on `/api/v1`. A logged-in admin browser tab cannot call an admin endpoint; only a bearer token can. That is also why these endpoints need no CSRF token. The API's CORS rules allow only `GET`, `POST` and `OPTIONS` from any origin, so `PATCH` and `DELETE` cannot be called from a browser on another origin. Use them from the app or from server-side code.

### Where API keys come from
Super admins create keys on **`/admin/api-keys`**. The full key is shown **once**, at creation. Pick scopes (below), optionally restrict the key to some shuls, and revoke it on the same page. A key cannot create or revoke keys.

### Scopes

| Scope | Allows |
|---|---|
| `schedule:read` | Admin reads: list overrides, list and read minyanim |
| `overrides:write` | Create, edit, toggle and delete manual overrides |
| `minyanim:write` | Enable, disable and edit minyanim |
| `notifications:write` | Create, edit, toggle and delete announcements (also needs a super admin owner and an unrestricted key) |
| `shul:write` | Edit shul profile details |

Mobile access tokens and keys created as "same as owner" hold every scope. **Effective permission = the token's scopes ∩ what its owner can do right now.** It is checked live on every request, so a key can never do more than its owner, and demoting an owner narrows their keys immediately.

### Shul restriction
A key can be limited to specific shuls. Requests for any other shul return `403 FORBIDDEN`, and `/me` lists the restriction in `restrictedOrganizationIds`. An empty list means no restriction.

### Revocation
Disabling a user (in the admin panel) revokes **all** of their mobile sessions and API keys at once. Deleting the user removes them. Individual keys and sessions can also be revoked on `/admin/api-keys`.

### Mobile sign-in flows

**Magic link (universal link)**
1. `POST /api/v1/auth/magic-link/request` with the admin's email or username. The reply is always `202`, whether or not the account exists.
2. The email contains `https://<site>/app/auth/magic-link?token=...`. The link works once and expires after 15 minutes. With the app installed, iOS and Android open it in the app (see [Association files](#association-files)). Otherwise a web fallback page is shown. That page never consumes the token.
3. The app calls `POST /api/v1/auth/magic-link/exchange` with the token and a `deviceName`, and receives access and refresh tokens.

**Native passkey**
1. `POST /api/v1/auth/passkey/options` returns a `challengeId` and WebAuthn `publicKey` request options. The challenge lasts 5 minutes.
2. The app signs the challenge with the platform authenticator (the passkey must already be registered in the admin panel).
3. `POST /api/v1/auth/passkey/verify` with `challengeId`, the `credential` JSON and a `deviceName` returns tokens. A challenge works once.

**Refresh and logout**
- Before the 15-minute access token expires (or on a `401 INVALID_TOKEN`), call `POST /api/v1/auth/refresh` with the refresh token and **no Authorization header**. The response holds a new access and refresh pair, and the old pair stops working.
- **Serialize refresh calls.** If two requests present the same refresh token (for example two screens refreshing at once), the second looks like a stolen-token replay, and the server revokes the whole session family. The user must then sign in again. Use one in-flight refresh and let other requests wait for it.
- `POST /api/v1/auth/logout` with the refresh token ends the session. It always returns `200`.
- Store the refresh token in the platform secure store (Keychain, Keystore).

### Association files
For universal links and native passkeys the server publishes two files, driven by environment variables. Each file returns `404` until its variables are set.

| Env var | Property | Used in |
|---|---|---|
| `APP_APPLE_TEAM_ID` | `app.links.apple-team-id` | `/.well-known/apple-app-site-association` |
| `APP_IOS_BUNDLE_ID` | `app.links.ios-bundle-id` | same |
| `APP_ANDROID_PACKAGE` | `app.links.android-package` | `/.well-known/assetlinks.json` |
| `APP_ANDROID_SHA256` | `app.links.android-sha256-fingerprints` | same. Comma-separated, colon-hex SHA-256 signing-certificate fingerprints |

Each Android fingerprint also becomes an allowed passkey origin (`android:apk-key-hash:...`) for native passkey sign-in.

### Configuration

| Property | Env var / default | Description |
|---|---|---|
| `auth.app-token.access-ttl` | `15m` | Access token lifetime |
| `auth.app-token.refresh-ttl` | `30d` | Refresh token lifetime |
| `api.ratelimit.requests-per-minute` | `60` | Reads per IP |
| `api.ratelimit.write-requests-per-minute` | `30` | Non-GET calls per IP (excluding auth and feedback) |
| `api.ratelimit.auth-requests-per-minute` | `10` | `/api/v1/auth/**` per IP |
| `api.ratelimit.feedback-requests-per-minute` | `5` | Feedback per IP |
| `app.links.*` | see above | Association files |

Environments:
- **Local:** the defaults work. Leave the `APP_*` variables unset (association files return 404), and use the magic link's token directly with the exchange endpoint, or sign in with a passkey on `localhost`. Use `http://localhost:8080`.
- **Dev:** set the `APP_*` variables for the dev build of the app (its bundle ID and signing fingerprints). Base URL `https://dev.teaneckminyanim.com`.
- **Production:** set the `APP_*` variables for the store builds. Add every signing fingerprint you use (upload key and Play-signing key). Base URL `https://teaneckminyanim.com`.

Override any property with its environment-variable form (for example `AUTH_APP_TOKEN_ACCESS_TTL=10m`) in the usual Spring way.

### Audit log
Token issues, refreshes, reuse, revocations and sign-in attempts are recorded with event type, account and a SHA-256 hash of the caller's IP. Raw tokens and raw IPs are never logged.

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
| `recaptchaToken` | Web only, when configured | Invisible reCAPTCHA response token. Required for website submissions when reCAPTCHA site and secret keys are configured. Requests with a valid native mobile app token skip reCAPTCHA. |

Clients may send `metadata` gathered automatically in the background. Do not ask users to type this manually.

Native mobile clients must send this header. When this header is present, the API uses the mobile-token validation path and does not require a reCAPTCHA token:

| Header | Required | Description |
|---|---|---|
| `X-Teaneck-Minyanim-App-Token` | Native mobile only | Token that matches the server-side `Feedback Mobile App Token` External Services setting. This is an interim native-app gate for mobile feedback submissions that skip reCAPTCHA. |

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
- Native mobile submissions send `X-Teaneck-Minyanim-App-Token` and do not send a reCAPTCHA token. Mobile metadata should still set `metadata.platform` to `ios`, `android`, `mobile`, or `native` for debugging context.
- The mobile app token blocks unauthenticated scripts but is not a cryptographic proof that the request came from an unmodified app. Platform attestation such as Apple App Attest or Google Play Integrity should be used for stronger app-only assurance.
- The public GitHub issue body includes the user message and automatically collected debugging metadata.
- Created GitHub issues are labeled `user feedback`.
- The optional user email is used only for private email notification/follow-up and is not written to the GitHub issue.
- If email delivery fails, the GitHub issue still remains created and `notificationEmailSent` is `false`.
- The created issue email includes the GitHub issue link.

**Error codes:**
- `INVALID_FEEDBACK` — blank message, invalid email, invalid category, or message too long
- `INVALID_RECAPTCHA` — reCAPTCHA is configured but the token is missing or rejected by Google
- `INVALID_MOBILE_APP_TOKEN` — native mobile token header is missing or invalid
- `FEEDBACK_NOT_CONFIGURED` — GitHub owner/repo/token settings are missing, or a native mobile submission was attempted before the mobile app token was configured
- `FEEDBACK_SUBMISSION_FAILED` — GitHub issue creation failed

---

## Admin endpoints

All admin endpoints need `Authorization: Bearer <token>`, return the standard envelope, and apply the [scope and shul rules](#scopes). `{id}` is a shul ID or slug. An unknown or disabled shul returns `404 NOT_FOUND`. Null fields are omitted from responses.

### `GET /api/v1/me`
Who the token belongs to.

```json
{ "data": {
  "id": "U1", "username": "gabbai", "email": "gabbai@example.com", "role": "ADMIN", "superAdmin": false,
  "organizations": [{ "id": "bmob", "name": "BMOB", "slug": "bmob" }],
  "tokenKind": "APP", "scopes": ["schedule:read", "overrides:write"],
  "sameAsOwner": true, "restrictedOrganizationIds": []
} }
```
**Errors:** `401 UNAUTHENTICATED`, `401 INVALID_TOKEN`.

### Sign-in endpoints

| Endpoint | Request | Success | Errors |
|---|---|---|---|
| `POST /auth/magic-link/request` | `{"identifier":"gabbai@example.com"}` | `202` `{"data":{"message":"If this email is authorized, a sign-in link will be sent shortly."}}` | `429` |
| `POST /auth/magic-link/exchange` | `{"token":"<raw>","deviceName":"Moshe's iPhone"}` | `200` TokenResponse | `400 VALIDATION_ERROR` (no token); `401 INVALID_TOKEN` (invalid, expired, used, wrong purpose, or user disabled) |
| `POST /auth/passkey/options` | none | `200` `{"challengeId","publicKey":{...}}` | `404` when passkeys are disabled |
| `POST /auth/passkey/verify` | `{"challengeId","credential":{...},"deviceName"}` | `200` TokenResponse | `400` malformed (challenge kept); `401 INVALID_TOKEN` any failure (challenge consumed) |
| `POST /auth/refresh` | `{"refreshToken":"tnmrt_..."}` | `200` TokenResponse | `401 INVALID_TOKEN`; reuse revokes the family |
| `POST /auth/logout` | `{"refreshToken":"tnmrt_..."}` | `200` `{"data":{"revoked":true}}` | none |

TokenResponse:
```json
{ "data": {
  "accessToken": "tnmapp_...", "refreshToken": "tnmrt_...", "tokenType": "Bearer",
  "accessExpiresAt": "2026-11-27T17:15:00Z", "refreshExpiresAt": "2026-12-27T17:00:00Z",
  "user": { "...": "same shape as GET /me" }
} }
```

### Overrides
Manual overrides are calendar events with source `MANUAL`. Mode `ADDITIVE` adds a minyan to the day. Mode `FULL_DAY_REPLACE` replaces all of that shul's rules and imported events for the day.

**`GET /api/v1/organizations/{id}/overrides?start=2026-11-27&end=2026-12-05`** (scope `schedule:read`)
Both parameters are required; the range is at most 62 days. Includes disabled overrides.
```json
{ "data": [{
  "id": "4821", "organizationId": "bmob", "date": "2026-11-27", "startTime": "18:45",
  "minyanType": "MINCHA", "overrideMode": "ADDITIVE", "enabled": true, "notes": "Extra early",
  "locationId": "L1", "locationName": "Main Sanctuary", "nusach": "ASHKENAZ",
  "editedBy": "gabbai", "editedAt": "2026-11-20T10:00:00"
}], "meta": { "start": "2026-11-27", "end": "2026-12-05", "count": 1 } }
```

**`POST /api/v1/organizations/{id}/overrides`** (scope `overrides:write`)
```json
{ "date": "2026-12-24", "endDate": "2026-12-26", "startTime": "18:45", "minyanType": "MINCHA",
  "overrideMode": "ADDITIVE", "locationId": "L1", "notes": "Extra early", "nusach": "ASHKENAZ", "enabled": true }
```
Required: `date`, `startTime` (`HH:mm`), `minyanType`. `overrideMode` defaults to `ADDITIVE`, `enabled` to `true`. With `endDate`, one override is created per day. The same date, type and time updates the existing row.
**Response `201`:** `{"data":[OverrideDto, ...],"meta":{"count":3,"created":3,"updated":0}}`

**`PATCH /api/v1/organizations/{id}/overrides/{eventId}`** (scope `overrides:write`)
A partial body with any of `startTime`, `overrideMode`, `enabled`, `notes`, `locationId`, `locationName`, `nusach`. An empty string clears `notes`, `locationId`, `locationName` or `nusach`. `{}` returns `400`. Returns `200` `{"data":OverrideDto}`.

**`DELETE /api/v1/organizations/{id}/overrides/{eventId}`** (scope `overrides:write`)
`200` `{"data":{"deleted":true,"id":"4821"}}`

**Errors:** `400 VALIDATION_ERROR`; `404 NOT_FOUND` (event in another shul); `409 NOT_MANUAL_OVERRIDE` (the row is rules-generated or imported and cannot be changed here).

### Minyanim
**`GET /api/v1/organizations/{id}/minyanim`** and **`GET .../minyanim/{minyanId}`** (scope `schedule:read`). Disabled minyanim are included.
```json
{ "data": {
  "id": "M1", "type": "SHACHARIS", "enabled": true, "locationId": "L1", "locationName": "Main Sanctuary",
  "nusach": "ASHKENAZ", "notes": null, "whatsapp": null,
  "schedule": {
    "sunday": { "type": "FIXED", "time": "08:00" },
    "monday": { "type": "DYNAMIC", "zman": "NETZ", "offsetMinutes": -5 },
    "tuesday": { "type": "ROUNDED", "zman": "PLAG_HAMINCHA", "offsetMinutes": -10 },
    "wednesday": { "type": "NONE" },
    "thursday": { "type": "FIXED", "time": "06:30" }, "friday": { "type": "FIXED", "time": "06:30" },
    "shabbos": { "type": "FIXED", "time": "08:30" },
    "roshChodesh": { "type": "NONE" }, "yomTov": { "type": "NONE" },
    "chanuka": { "type": "NONE" }, "roshChodeshChanuka": { "type": "NONE" }
  } } }
```
Slot types: `FIXED` (`time`), `DYNAMIC` (`zman`, `offsetMinutes`), `ROUNDED` (like dynamic, rounded to 5 minutes) and `NONE`. `offsetMinutes` is within ±240.

**`PATCH /api/v1/organizations/{id}/minyanim/{minyanId}`** (scope `minyanim:write`)
```json
{ "enabled": true, "schedule": { "monday": { "type": "FIXED", "time": "06:45" } },
  "notes": "Winter schedule", "locationId": "L1", "nusach": "ASHKENAZ", "whatsapp": "https://chat.whatsapp.com/..." }
```
Every field is optional, and `schedule` may hold any subset of slots. The change is atomic and the public schedule updates immediately. Unknown fields and wrongly typed values return `400 VALIDATION_ERROR`; a minyan in another shul returns `404`. Returns `200` with the MinyanAdminDto.

### Shul
**`PATCH /api/v1/organizations/{id}`** (scope `shul:write`)
```json
{ "name": "BMOB", "address": "534 Larch Ave, Teaneck NJ", "websiteUrl": "bmob.org", "nusach": "ASHKENAZ",
  "color": "#1a5276", "slug": "bmob", "whatsapp": "https://chat.whatsapp.com/...",
  "calendarUrl": "https://example.com/cal.ics", "useScrapedCalendar": false }
```
All fields are optional. Returns `200` ShulAdminDto: `id, name, slug, color, nusach, nusachDisplay, address, websiteUrl, whatsapp, calendarUrl, useScrapedCalendar, enabled, latitude, longitude`. A duplicate slug or invalid color returns `400 VALIDATION_ERROR`.

### Notifications (announcements)
Site-wide, so they need a **super admin** owner with the `notifications:write` scope, and a shul-restricted key returns `403 FORBIDDEN`.

**`GET /api/v1/notifications?includeInactive=true[&type=BANNER|POPUP]`** returns all announcements, newest first, including expired and disabled ones. Without `includeInactive` it is the public endpoint above.
```json
{ "data": [{ "id": "N1", "title": "Shul closed", "message": "Closed Sunday", "type": "BANNER", "enabled": true,
  "expirationDate": "2026-12-01T00:00:00", "maxDisplays": 3, "createdAt": "...", "updatedAt": "..." }] }
```

**`POST /api/v1/notifications`**: `{"title" (req, max 255), "message" (req, max 5000), "type" (req: BANNER|POPUP), "enabled" (default true), "expirationDate" (ISO), "maxDisplays" (>= 1)}`. Returns `201`.

**`PATCH /api/v1/notifications/{id}`**: a partial body. An explicit `null` clears `expirationDate` or `maxDisplays`.

**`DELETE /api/v1/notifications/{id}`**: `200` `{"data":{"deleted":true,"id":"N1"}}`

---

## Error codes

| Code | HTTP | Meaning |
|---|---|---|
| `VALIDATION_ERROR` | 400 | A field is missing, malformed or out of range, or the body has unknown fields |
| `UNAUTHENTICATED` | 401 | No bearer token on an endpoint that needs one |
| `INVALID_TOKEN` | 401 | Token is malformed, expired, revoked, or its owner is disabled; also failed sign-ins |
| `INSUFFICIENT_SCOPE` | 403 | The key lacks the required scope |
| `FORBIDDEN` | 403 | The owner or key may not touch this shul or action |
| `NOT_FOUND` | 404 | Unknown or disabled shul, or unknown record |
| `NOT_MANUAL_OVERRIDE` | 409 | The event is not a manual override |
| `RATE_LIMITED` | 429 | Too many requests; retry after 60 s (`Retry-After` header) |
| `INTERNAL_ERROR` | 500 | Unexpected error; no details are returned |

The public endpoints keep their own codes (see the [error reference](#error-reference)).

## Rate limits

Per client IP, per minute. Exceeding a limit returns `429 RATE_LIMITED`.

| Requests | Limit |
|---|---|
| Reads (`GET`) | 60 |
| Writes (non-GET, except auth and feedback) | 30 |
| `/api/v1/auth/**` | 10 |
| Feedback | 5 |

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
See [Configuration](#configuration) for the `api.ratelimit.*` properties.

### Admin/API parity
Every admin panel write handler carries `@AdminAction("area.verb")`, and the API endpoint for the same action carries the same tag. `AdminApiParityTest` fails the build if a handler is untagged, if a tag has neither an API endpoint nor an entry in `src/test/resources/admin-api-parity-allowlist.txt` (statuses `pending`, `by-design`, `api-ahead`), or if an API endpoint lacks `@Operation` or the `bearerAuth` security requirement. When you add an API endpoint for a pending action, remove its allowlist line.
