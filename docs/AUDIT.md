# v1.0.0 release audit

Audit scope: current release-candidate tree.

## Credential and project-data review
- Cloudflare D1 ID is a placeholder.
- Google OAuth client ID is a placeholder.
- No JKS/keystore, environment file, access token, private key or production API URL is tracked.
- Android package IDs use the generic `com.example` namespace.
- Chess++ is mentioned only as architecture provenance in documentation; no Chess++ production credential or endpoint is included.

## Security posture
- Android manifest disables cleartext traffic in the starter app.
- Provider ID tokens are signature/audience/issuer/expiry/nonce checked by the Worker.
- App bearer tokens are random and only SHA-256 hashes are stored server-side.
- Hardware modules keep permissions scoped to the optional module.
- SECURITY.md directs vulnerabilities to private reporting.

## Release blockers
A release is not approved until the exact release commit passes Android Build,
Edge Check and Full Template Check. Public visibility should follow the final
history/secrets review, not precede it.

## Follow-up hardening
- Provide a concrete Android Keystore-backed SessionStore before a consuming app stores production sessions.
- Add application-specific authorization and rate limiting to realtime rooms.
- Pin npm dependency resolution with a lockfile when generated and reviewed.
- Maintain THIRD_PARTY_NOTICES as dependencies change.
