# User management and authentication closeout

## v1.19.0: #246 and #121

`/admin/accounts` is now User Management. Super admins can:

- Create an enabled passwordless account without setting a shared password, then explicitly send its first sign-in link.
- Assign User, Manager, or Super admin access. User/Manager roles require at least one existing shul and may span multiple shuls; Super admin access covers all shuls.
- Change email, assignments, role, and enabled status; view stored passkey count, last login and last link-sent time; send recovery links and revoke all of another user's passkeys.
- Open the existing account page for profile, notifications, password fallback, and deletion.

The primary `ACCOUNT.ORGANIZATION_ID` remains the default landing shul. Additional assignments live in `ACCOUNT_ORGANIZATION`. Changing assignments preserves the primary if it remains selected; otherwise a selected shul becomes primary. Clearing all shuls never implicitly promotes a scoped user.

Multi-shul access is enforced through both existing organization access helpers. The shul switcher, organization account lists, schedule/location/calendar/override access, and scheduled weekly digest delivery honor the assignments. Scheduled digests send one email per assigned enabled shul. The existing manual digest test still defaults to the primary shul.

Only super admins assign permissions or reset another account’s sign-in credentials. Managers may maintain ordinary users only when they manage **all** of that user's shuls, and may not maintain other managers. Disabling/deleting oneself or removing one's own super-admin role is blocked. Disabled/deleted users and stale-role sessions are logged out on their next request; shul access is checked against current database assignments.

Browser mutations require CSRF tokens across the application. Legacy enable, disable, delete, logout, and minyan creation routes are POST-only; only the stateless public API and newsletter subscription endpoints are excluded from CSRF.

### Deployment

1. Back up the database. Apply `docs/migrations/MIGRATION_v1.14.0_auth_foundation.sql`, then `docs/migrations/MIGRATION_v1.19.0_user_management.sql` before application startup when schema changes are managed manually. Resolve any duplicate normalized emails reported by the v1.19.0 preflight before its unique index is created. Hibernate `ddl-auto=update` also creates the mapped tables for development.
2. Set `AUTH_MAGIC_LINK_ENABLED=true`, `AUTH_PASSKEYS_ENABLED=true`, `AUTH_PASSWORD_LOGIN_ENABLED=false`, and `SESSION_COOKIE_SECURE=true` on HTTPS deployments. Configure `SUPERADMIN_EMAIL` only when a bootstrap super admin is needed.
3. Confirm existing accounts still have their original primary shul. No assignment backfill is needed.
4. Configure and test the existing Email Settings delivery provider and canonical site URL. Account creation itself sends no email; use **Send sign-in link** after reviewing access.
5. Check a Manager assigned two shuls can switch between them and cannot open a third shul by changing the URL. Remove one assignment and confirm its URLs are denied immediately.
6. Test User and Manager flows, and verify a disabled account's existing session is rejected on its next request.

## #243 implementation closeout

The implementation scope for #243 is complete in v1.19.0. The release adds Spring WebAuthn/passkeys, magic links using the #35 email handler, normalized unique email identity, login metadata, session setup, passkey self-management, user management, multi-shul assignments, super-admin recovery/revocation, and live disabled-account enforcement.

Hardening completed in this release:

- Default global CSRF with CSRF-aware forms, `fetch`, jQuery requests, and WebAuthn ceremonies.
- Atomic conditional magic-link consumption, replay rejection coverage, scheduled token cleanup, and capped rate-limit caches with IP and identifier limits.
- Account-neutral sign-in method responses and an explicit email-recovery action; passkey cancellation no longer sends email automatically, including the #259 Edge flow.
- Passwordless organization/account creation, explicit email-only super-admin bootstrap, password login disabled by default, and environment rollback flags for password, magic-link, and passkey methods.
- Structured authentication audit records, normalized unique emails, duplicate preflight SQL, consistent legacy account validation, secure cookie settings, and framework forwarding-header support.

### Deployment acceptance required before closing #243

- Confirm the production/dev passkey RP/origin policy. Current code uses `teaneckminyanim.com` with production and dev origins, and localhost for local development. Verify actual browser registration and returning login on each supported origin.
- Confirm the production email provider and sender, deliverability, and link host. Run invitation, recovery, expiration/replay, and disabled-account checks using real email delivery.
- Confirm the retained User route permissions and Manager account-maintenance policy. Permission assignment and invite/revoke actions are super-admin-only.
- Keep password login disabled (`AUTH_PASSWORD_LOGIN_ENABLED=false`) after verifying at least two enabled super admins have usable passkeys and recovery email access. The documented emergency recovery action is to temporarily enable that environment flag for existing password hashes.
- Apply/review the auth foundation and multi-shul SQL migrations, then execute browser-based passkey registration/login/removal, email recovery, logout, unauthorized-shul, and CSRF regression checks. Test real devices as well as automated controller/service coverage.

Retaining documented SQL migrations follows the repository's existing practice; introducing Flyway is an independent operational decision rather than a prerequisite of #246.

## Verification for this change

- Full Maven test suite, including multi-shul persistence/query/removal against H2 in MariaDB compatibility mode, scoped authorization, session invalidation, disabled-account credential revocation, and weekly email recipients.
- MockMvc form requests with and without CSRF, plus actual Thymeleaf/layout rendering and generated hidden CSRF fields.
- Browser inspection of rendered fixture data at desktop and 390px phone width, including expanding user cards and filtering by shul. This does not substitute for live MariaDB deployment or real email/passkey acceptance testing.

## Related open-issue review (2026-10-12)

Reviewed all 38 currently open issues, including comments on the closest matches.

| Issue | Recommendation for this release | Current status / completion requirement |
| --- | --- | --- |
| #203 — Create user from the user page | Include in release closure list alongside #246 and #121 | Implemented here: super admins create users and assign one or multiple shuls. Close after release acceptance. |
| #259 — Passkeys on mobile Edge | Include in release closure list with #243 | Implemented: the first Continue always attempts passkey when enabled, and failure exposes an explicit email action without sending a link automatically. Verify the first attempt on the reported Edge device after dev deployment. |
| #217 — Keep form values and show validation errors | Include in release closure list | Implemented for organization and new-user creation: non-secret inputs are retained, the affected editor is reopened, and actionable validation errors are displayed. |
| #219 — Normalize toast argument order | Include in release closure list | Implemented: affected Settings and minyan update calls now use `showToast(message, type)`. |
| #238 — Mobile overflow/navigation | Include acceptance verification, not a duplicate rebuild | Reported implementation commits 3c4d707c and 0517399a are ancestors of this branch; responsive wrappers and sidebar destinations exist. Verify Organizations table actions and navigation on the deployed mobile viewport before closing. The new management page and multi-shul switcher need the same regression check. |
| #104 — Relevant/security tests | Record partial progress; keep broader issue open | This build adds permission, CSRF, stale-session, persistence, and recovery tests. The issue also asks for a suite/process-wide audit that this change does not complete. |
| #207 / #242 — Shul terminology and pill alignment | Optional bounded polish | Use consistent terminology/alignment in touched pages. Do not mark these site-wide requests complete without checking all admin pages. |
| #244 / #94 / #210 / #208 / #113 | Separate UI work | Global navigation redesign, broader admin redesign, organization/notification styling, and a real shul dashboard are not prerequisites of user management. |
| #131 / #134 / #261 | Separate API/mobile release | JWT/mobile admin and API-key writes add new authentication and authorization surfaces. Update their plans to reflect passwordless login and multi-shul access before implementing; their existing single-shul/password assumptions should not become a competing account model. |

Recommended release grouping: #246 + #121 + #203 + #243 + #259 + #217 + #219, with #238 as a release verification/possible closeout item. The code work for that group is implemented; close #243 after the deployment acceptance above. No GitHub issue statuses were changed by this build.
