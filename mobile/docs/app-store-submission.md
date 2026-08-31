# iOS App Store Submission with Expo

This runbook documents a generic, public-safe process for building and submitting
an Expo iOS app to App Store Connect with EAS Build and EAS Submit.

Do not add real Apple account emails, team IDs, App Store Connect app IDs,
bundle identifiers, API keys, certificates, provisioning profiles, or secrets to
this document. Use placeholders in committed docs and store operational values in
EAS, App Store Connect, or ignored local secret files.

## References

- Expo: Submit to app stores: https://docs.expo.dev/deploy/submit-to-app-stores/
- Expo: Build setup: https://docs.expo.dev/build/setup/
- Expo: App credentials: https://docs.expo.dev/app-signing/app-credentials/
- Apple: App privacy details: https://developer.apple.com/app-store/app-privacy-details/

## Prerequisites

1. An Apple Developer Program membership.
2. App Store Connect access with permission to manage the app and submit builds.
3. An Expo account with access to the EAS project.
4. EAS CLI available locally:

```sh
npm install --global eas-cli
```

5. A configured App Store Connect app record.
6. A production EAS build profile in `eas.json`.
7. Required production environment variables stored in EAS or an ignored local
   `.env.production` file.

## Public-Safe Configuration Pattern

Keep committed config generic when possible. Values that identify a person,
organization, Apple team, App Store app, or private account should be supplied
through EAS secrets, local ignored files, or interactive CLI prompts.

Safe committed examples may use placeholders:

```json
{
  "submit": {
    "production": {
      "ios": {
        "ascAppId": "<app-store-connect-app-id>",
        "appleTeamId": "<apple-team-id>"
      }
    }
  }
}
```

Avoid committing:

- Personal Apple ID email addresses
- App Store Connect API private keys (`.p8`)
- Certificates (`.p12`, `.cer`)
- Provisioning profiles (`.mobileprovision`)
- EAS auth tokens
- Production API keys or service credentials

## One-Time Setup

1. Install dependencies:

```sh
cd mobile
npm install
```

2. Sign in to Expo:

```sh
eas login
```

3. Confirm the EAS project is linked:

```sh
eas project:info
```

4. Configure iOS signing credentials. For most Expo projects, use EAS-managed
   credentials unless there is a specific reason to bring your own:

```sh
eas credentials --platform ios
```

5. Configure production environment variables in EAS:

```sh
eas env:create --environment production --name EXAMPLE_PUBLIC_KEY --value "<value>"
```

Use the actual variables required by the app. Do not commit production `.env`
files.

6. If submitting non-interactively, configure App Store Connect API key
   authentication through EAS credentials or EAS secrets. Prefer API keys for CI
   instead of personal Apple ID sessions.

## Preflight Checklist

Before creating a production build:

1. Confirm `app.json` or `app.config.js` has the correct public app metadata:
   app name, icon, splash image, bundle identifier, version, and iOS settings.
2. Increment the user-visible version when shipping user-facing changes.
3. Ensure the iOS build number will be unique. This can be done manually or with
   EAS `autoIncrement`.
4. Run local checks:

```sh
cd mobile
npx tsc --noEmit
npm test
```

5. Confirm required production environment variables are available:

```sh
eas env:list --environment production
```

6. Verify App Store Connect metadata:
   app description, keywords, support URL, privacy policy URL, screenshots, age
   rating, app privacy details, export compliance, and review notes.

## Build

Create a production iOS build:

```sh
cd mobile
eas build --platform ios --profile production
```

Wait for the build to finish successfully. Save the EAS build URL in release
notes or the release checklist, but do not include private account details.

## Submit

Submit the latest production iOS build to App Store Connect:

```sh
cd mobile
eas submit --platform ios --profile production
```

If prompted, choose the completed production build. EAS uploads the build to App
Store Connect, but it does not complete App Review submission for every possible
release workflow. Finish any required metadata, compliance, TestFlight, phased
release, or manual review steps in App Store Connect.

You can also build and submit in one command:

```sh
cd mobile
eas build --platform ios --profile production --auto-submit
```

Use this only when the submit profile and App Store Connect authentication are
already verified.

## App Store Connect Review Steps

After EAS uploads the build:

1. Open App Store Connect and select the app.
2. Confirm the uploaded build finished processing.
3. Attach the build to the intended app version.
4. Complete all required review fields:
   contact information, demo credentials if needed, review notes, privacy
   answers, export compliance, and content rights.
5. Check screenshots and metadata for all required device sizes and locales.
6. Choose manual release, automatic release, or phased release.
7. Submit for review.

## TestFlight Validation

Before App Review, validate a TestFlight build on real devices:

1. Fresh install and first launch.
2. Sign-in, onboarding, or permission prompts.
3. Primary navigation and core user workflows.
4. Push notifications, location, analytics, or other permission-gated features.
5. Offline or poor-network behavior if relevant.
6. External links and support/privacy URLs.
7. Crash-free launch after force quit and relaunch.

## CI Notes

For CI-based releases:

1. Store `EXPO_TOKEN` as a CI secret.
2. Store App Store Connect API key material in EAS credentials or CI secrets.
3. Run checks before building:

```sh
cd mobile
npm ci
npx tsc --noEmit
npm test
```

4. Build with a non-interactive EAS command:

```sh
cd mobile
eas build --platform ios --profile production --non-interactive
```

5. Submit only after build success:

```sh
cd mobile
eas submit --platform ios --profile production --non-interactive
```

## Troubleshooting

- **Apple authentication fails**: Re-run `eas login`, refresh App Store Connect
  API key credentials, or verify the Apple account has app management access.
- **Bundle identifier mismatch**: Confirm the iOS bundle identifier in Expo
  config matches the App Store Connect app record.
- **Build number already used**: Increment the iOS build number or enable EAS
  auto-increment for production builds.
- **Missing credentials**: Run `eas credentials --platform ios` and let EAS
  repair or regenerate credentials when appropriate.
- **Build uploaded but unavailable**: Wait for Apple processing to finish in App
  Store Connect, then refresh the version page.
- **Review blocked by privacy/compliance forms**: Complete the missing App Store
  Connect fields before submitting for review.
