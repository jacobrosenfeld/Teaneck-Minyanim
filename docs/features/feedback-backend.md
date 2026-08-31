# Feedback Infrastructure

This feature exposes a public feedback submission endpoint and a shared public
website feedback widget. The website widget renders only when the GitHub owner,
repository, and token settings are populated. The Expo app exposes feedback from
the bottom tab bar when the mobile app token is configured.

## Endpoint

`POST /api/v1/feedback`

Default rate limit: `api.ratelimit.feedback-requests-per-minute=5` per IP.

Request body:

```json
{
  "message": "The Shacharis time looks wrong for this shul.",
  "email": "optional-user@example.com",
  "category": "MINYAN_SCHEDULE",
  "recaptchaToken": "recaptcha-response-token",
  "metadata": {
    "platform": "web",
    "screen": "organization-detail",
    "url": "https://www.teaneckminyanim.com/example-shul",
    "organization": {
      "id": "O123",
      "slug": "example-shul",
      "name": "Example Shul"
    },
    "posthog": {
      "distinctId": "distinct-id",
      "sessionId": "session-id",
      "sessionReplayUrl": "https://..."
    }
  }
}
```

The visible UI should only collect `category`, `message`, and optional `email`.
Route, screen, shul, minyan, device, app version, and PostHog context should be
gathered in the background by web/mobile clients. Supported categories are
`MINYAN_SCHEDULE` and `APP_FUNCTIONALITY`.

Website clients send `recaptchaToken` when reCAPTCHA is configured. Native
mobile clients set `metadata.platform` to `ios`, `android`, `mobile`, or
`native`, send the `X-Teaneck-Minyanim-App-Token` header, and omit reCAPTCHA.

## Website Widget

The public web widget is rendered from the shared `frontnavbar` include so it
can appear on public frontend pages that use the standard navigation shell.
It is hidden automatically unless `ApplicationSettingsService.isFeedbackEnabled()`
returns `true`.

The floating launcher opens an Intercom-style popup panel and sends:

- Selected category for minyan time/schedule versus app/website functionality
- User-entered message and optional email
- Current route, URL, screen, selected date, and active filters
- Organization context when an org page exposes the current shul name
- Browser, viewport, timezone, language, and app version metadata
- PostHog distinct/session/replay details when a web PostHog client is present
- Invisible reCAPTCHA response token when reCAPTCHA is configured

## Mobile Client

The Expo app includes a bottom-tab feedback screen when
`EXPO_PUBLIC_FEEDBACK_APP_TOKEN` is present in the build. The app embeds that
value into `expo.extra.feedbackConfig.appToken` as a runtime fallback and sends
it as `X-Teaneck-Minyanim-App-Token` on feedback requests.

The mobile form collects only category, message, and optional email. It gathers
app version, build number, device/OS details, PostHog distinct/session context
when analytics is enabled, and the last non-feedback route in the background. If
the last route includes a shul id or slug that matches the cached organization
list, the matched shul context is attached to the submission.

The backend compares that header to the sensitive `Feedback Mobile App Token`
setting under External Services. This protects the no-reCAPTCHA mobile path from
requests that do not have the app token, but a static token embedded in a mobile
binary can be extracted. For stronger app-only protection, replace or augment
this gate with platform attestation through Apple App Attest and Google Play
Integrity.

## External Service Settings

Configure these under **Admin -> Settings -> External Services**:

- `Feedback GitHub Owner`: defaults to `jacobrosenfeld`
- `Feedback GitHub Repository`: defaults to `Teaneck-Minyanim`
- `Feedback GitHub Token`: sensitive field used only server-side
- `Feedback Mobile App Token`: sensitive server-side token that must match
  `EXPO_PUBLIC_FEEDBACK_APP_TOKEN` in native mobile builds
- `reCAPTCHA Site Key`: public key rendered to website forms
- `reCAPTCHA Secret Key`: sensitive server-side key used to verify submitted tokens

Sensitive token/secret fields are encrypted like other sensitive settings. Set
`APP_SETTINGS_ENCRYPTION_KEY` before saving it.

Use a fine-grained GitHub personal access token scoped to the configured repo
with **Issues: Read and write** permission. The backend calls GitHub's
`POST /repos/{owner}/{repo}/issues` endpoint and never exposes the token to
clients. Created issues are labeled `user feedback`.

When both reCAPTCHA keys are populated, the website uses invisible reCAPTCHA for
feedback, newsletter subscription, and admin login forms. The backend verifies
each token with Google before creating feedback issues, forwarding subscription
requests to Sendy, or processing admin login credentials.

## Email Behavior

Feedback issue creation is the primary action.

- If a user email is provided, the confirmation email is sent to that user and
  the configured support email is CC'd.
- If no user email is provided, the notification email is sent to the configured
  support email.
- The email includes the GitHub issue link, feedback id, category, timestamp,
  and user message.
- The user email address is never written to the public GitHub issue.
- If email delivery is not configured or fails, the GitHub issue still remains
  created and the API response reports that notification email was not sent.

## Privacy Notes

The public GitHub issue body includes the user's message and automatically
collected debugging metadata. Do not include the optional email address in
metadata sent to this endpoint.
