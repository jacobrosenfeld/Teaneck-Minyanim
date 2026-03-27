# Mobile Analytics + Tracking Compliance (Issue #146)

Last updated: March 26, 2026

## Scope

This document defines the shipped behavior for mobile analytics and tracking compliance.

- Analytics provider: PostHog Cloud free tier
- Platforms: iOS and Android
- Consent model:
  - In-app disclosure is required before analytics starts.
  - iOS requires in-app acceptance for analytics capture; ATT authorization controls advertising ID usage only.
  - Android requires in-app acceptance.
- App functionality must remain available if tracking is declined.

## Runtime Consent Model

Persisted flags:

- `consent = accepted | declined | unknown`
- `platform_tracking_permission = authorized | denied | unknown | not_required`
- `analytics_enabled` is computed at runtime and is not stored as an override.

Gating:

- iOS: analytics enabled when `consent=accepted`; advertising ID is attached only when `platform_tracking_permission=authorized`.
- Android: analytics enabled only when `consent=accepted`.
- Global kill switch: `EXPO_PUBLIC_ANALYTICS_ENABLED=false` disables all analytics regardless of consent.

## Identifier Policy

- Allowed when analytics is enabled:
  - PostHog distinct ID
  - Advertising ID (`IDFA` / `AAID`) only under consent-permitted state
- Not allowed:
  - Device fingerprinting
  - Hidden fallback identifiers for cross-app tracking

## Event Coverage for #146

Implemented events:

- `app_open`
- `screen_view`
- `minyan_card_tap`
- `filter_chip_selected`
- `pull_to_refresh`
- `whatsapp_tap`
- `directions_tap`

Data hygiene rules:

- No raw email / phone / names
- No precise coordinate payloads
- Session replay masking defaults enabled

## App Store Connect Checklist (iOS)

1. Privacy labels in App Store Connect exactly match app behavior.
2. If tracking categories are declared, ATT is requested after in-app acceptance.
3. `NSUserTrackingUsageDescription` text matches the in-app disclosure intent.
4. App Review notes explain where the ATT prompt appears (after first-launch acceptance).
5. Confirm app remains fully usable when ATT is denied.

## Play Console Checklist (Android)

1. Data Safety form matches shipped behavior and consent gating.
2. Prominent disclosure text in-app matches policy declarations.
3. User data/collection purposes align with actual event payloads.
4. AD_ID usage is declared only for analytics/tracking behavior that is consent-gated.
5. Confirm app remains fully usable when disclosure is declined.

## Release/QA Sign-off

Before submission, verify all of the following:

1. Fresh install iOS: disclosure appears before analytics traffic.
2. iOS decline ATT: analytics still flows after in-app acceptance, but advertising ID is not attached.
3. iOS allow ATT: events visible in PostHog without PII fields; advertising ID may be attached.
4. Fresh install Android: disclosure accept/decline correctly gates analytics.
5. Kill switch (`EXPO_PUBLIC_ANALYTICS_ENABLED=false`) disables capture immediately.
