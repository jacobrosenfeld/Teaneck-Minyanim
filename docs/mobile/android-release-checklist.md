# Android Release Checklist (Issue #132)

This checklist documents the first production Android release flow for the Expo app in `mobile/`.

## 1) Google Maps SDK Key Setup

1. In Google Cloud Console:
   - Enable billing for the project.
   - Enable **Maps SDK for Android**.
2. Create an API key restricted to Android apps:
   - Package name: `com.teaneckminyanim.app`
   - SHA-1 fingerprints:
     - Play App Signing certificate (required for production)
     - Optional: upload/debug certs for local or internal builds
3. Store the key in EAS environment variables:
   - Name: `GOOGLE_MAPS_API_KEY`
   - Environment: production (and preview if needed)

Notes:
- The Expo config now injects this key via `mobile/app.config.js`.
- Production builds fail fast if `GOOGLE_MAPS_API_KEY` is missing.

## 2) Play Console + Credentials

1. Create the app in Play Console if it does not exist.
2. Create a Google Play Developer API service account and grant release permissions.
3. Download the JSON key and store it locally at:
   - `mobile/.secrets/google-play-service-account.json`
4. Ensure `mobile/.secrets/` stays untracked (already ignored in `mobile/.gitignore`).

## 3) Build and Submit

Before building production:
- Create `mobile/.env.production` with release analytics values:
  - `EXPO_PUBLIC_ANALYTICS_ENABLED=true` (or `false` to disable analytics)
  - `EXPO_PUBLIC_POSTHOG_KEY=...`
  - `EXPO_PUBLIC_POSTHOG_HOST=https://us.i.posthog.com` (or your PostHog host)
  - `EXPO_PUBLIC_SESSION_REPLAY_ENABLED=false|true`
- `mobile/app.config.js` loads `.env.production` for production profiles and will fail if analytics is enabled but `EXPO_PUBLIC_POSTHOG_KEY` is empty.

1. Build production Android binary (AAB):
   - `cd mobile && eas build --platform android --profile production`
2. If this is the first Android publish:
   - Upload once manually in Play Console.
3. For subsequent releases, submit with EAS:
   - `cd mobile && eas submit --platform android --profile production`

Current submit defaults in `mobile/eas.json`:
- Track: `production`
- Release status: `inProgress`
- Rollout: `0.2` (20% staged rollout)

## 4) Pre-Release Play Console Checks

Complete before pushing rollout to 100%:
- Store listing (title, descriptions, screenshots, icon, feature graphic)
- App content forms:
  - Privacy policy URL
  - Data safety
  - Content rating
  - Ads declaration
- Permission declarations:
  - Location usage rationale
  - AD_ID usage rationale if retained

## 5) QA Validation on Android

Validate on a release build:
- Map renders tiles and markers (no blank or gray tiles)
- Location permission prompt appears and nearby behavior works
- "View schedule" navigation works from map pins
- "Get directions" opens native maps intent

Monitor production health during staged rollout:
- Crash/ANR rates
- Play pre-launch report warnings
