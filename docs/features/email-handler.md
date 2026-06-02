# Email Handler

The application can send transactional/admin email through either SMTP or AWS SES API. Configuration is stored in `APPLICATION_SETTINGS` and is editable from the super-admin Email Settings page at `/admin/settings/email`.

Sensitive email credentials are encrypted at rest. Set `APP_SETTINGS_ENCRYPTION_KEY` before saving SMTP or SES credentials.

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

Sensitive values are stored with reversible AES-GCM encryption using the `ENC:v1:` database prefix. Generate a 256-bit key with:

```bash
openssl rand -base64 32
```

Set it as:

```bash
APP_SETTINGS_ENCRYPTION_KEY=base64:<generated-key>
```

Use the same key for every app instance and keep it outside the database. Existing plaintext sensitive settings are encrypted automatically on startup when the key is configured. If the database already contains plaintext or encrypted sensitive settings and the key is missing, startup fails instead of continuing with unsafe or unreadable credentials.
