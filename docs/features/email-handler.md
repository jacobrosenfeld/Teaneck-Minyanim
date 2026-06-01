# Email Handler

The application can send transactional/admin email through either SMTP or AWS SES API. Configuration is stored in `APPLICATION_SETTINGS` and is editable from the super-admin Email Settings page at `/admin/settings/email`.

## Provider Selection

Set `email.provider` to one of:

- `SMTP`
- `SES`

Leave `email.provider` blank to disable email sending. Invalid or incomplete configuration fails gracefully and returns a sanitized error to admin callers.

## Shared Settings

- `email.from.address`: required for both providers.
- `email.from.name`: optional display name.
- `email.reply-to`: optional reply-to address.

## SMTP Settings

- `email.smtp.host`: SMTP server host.
- `email.smtp.port`: SMTP server port, commonly `587`.
- `email.smtp.username`: SMTP username.
- `email.smtp.password`: SMTP password.
- `email.smtp.starttls.enabled`: `true` or `false`.

## AWS SES Settings

- `email.ses.region`: AWS region, for example `us-east-1`.
- `email.ses.access-key-id`: access key ID with SES send permissions.
- `email.ses.secret-access-key`: matching secret access key.
- `email.ses.configuration-set`: optional SES configuration set.

## Admin UI

Open `/admin/settings`, then choose **Email Settings**. Each field saves independently with its own check button. Sensitive values are never displayed back to the browser; leave a sensitive field blank to keep the saved value unchanged.

## Test Endpoint

Super admins can send a test email with:

```bash
curl -X POST "https://example.com/admin/email/test" \
  -d "recipient=admin@example.com"
```

The endpoint requires an authenticated super-admin session. It returns JSON with `success`, `provider`, and a sanitized `message`.

## Secret Handling

SMTP and SES credentials are marked sensitive in the settings schema. They are masked in the settings page and in application logs. Leaving a sensitive setting blank in the admin edit modal keeps the existing value unchanged.
