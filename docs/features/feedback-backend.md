# Feedback Backend Infrastructure

This backend feature exposes a public feedback submission endpoint without adding
any website or mobile UI yet.

## Endpoint

`POST /api/v1/feedback`

Default rate limit: `api.ratelimit.feedback-requests-per-minute=5` per IP.

Request body:

```json
{
  "message": "The Shacharis time looks wrong for this shul.",
  "email": "optional-user@example.com",
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

The visible UI should only collect `message` and optional `email`. Route,
screen, shul, minyan, device, app version, and PostHog context should be
gathered in the background by the eventual web/mobile clients.

## GitHub Settings

Configure these under **Admin -> Settings -> External Services**:

- `Feedback GitHub Owner`: defaults to `jacobrosenfeld`
- `Feedback GitHub Repository`: defaults to `Teaneck-Minyanim`
- `Feedback GitHub Token`: sensitive field used only server-side

The token field is encrypted like other sensitive settings. Set
`APP_SETTINGS_ENCRYPTION_KEY` before saving it.

Use a fine-grained GitHub personal access token scoped to the configured repo
with **Issues: Read and write** permission. The backend calls GitHub's
`POST /repos/{owner}/{repo}/issues` endpoint and never exposes the token to
clients.

## Email Behavior

Feedback issue creation is the primary action.

- If a user email is provided, the confirmation email is sent to that user and
  the configured support email is CC'd.
- If no user email is provided, the notification email is sent to the configured
  support email.
- The email includes the GitHub issue link, feedback id, timestamp, and user
  message.
- The user email address is never written to the public GitHub issue.
- If email delivery is not configured or fails, the GitHub issue still remains
  created and the API response reports that notification email was not sent.

## Privacy Notes

The public GitHub issue body includes the user's message and automatically
collected debugging metadata. Do not include the optional email address in
metadata sent to this endpoint.
