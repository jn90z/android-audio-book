# Mobile App Starter

A reusable, buildable Android + Cloudflare foundation extracted from the architecture proven in Chess++.

The Android app intentionally launches to a **blank white screen**. It is a clean starting surface; authentication, API, session, database, hosting, tests, and CI infrastructure live around it without imposing an application UI.

## Stack

- Kotlin / Android, Java 17
- AndroidX Credential Manager + Google Identity
- Cloudflare Workers + TypeScript
- Cloudflare D1
- GitHub Actions
- App-owned opaque bearer sessions

## Build Android

```powershell
gradle --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

CI runs the same unit-test/build path and uploads the APK as an artifact.

## Check the Worker

```powershell
cd edge
npm install
npm run check
```

## Authentication architecture

```text
Android
  -> Google Credential Manager
  -> Google ID token + cryptographic nonce
  -> Cloudflare Worker
       -> verifies signature/audience/issuer/expiry/nonce
       -> maps provider subject to app user
       -> issues random app bearer token
       -> stores only SHA-256 token hash
  -> D1
```

The provider credential proves identity. Normal application API traffic uses the application's own session token.

## Starting a new app

See `docs/NEW_APP_CHECKLIST.md` and `docs/ARCHITECTURE.md`.

Before production, replace the placeholder package/application IDs and Cloudflare configuration, create Google OAuth clients, configure D1, and provide a Keystore-backed implementation of `SessionStore`.

## License

Licensed under the Apache License, Version 2.0. Copyright 2026 Joseph Niksa.
See `LICENSE`, `THIRD_PARTY_NOTICES`, and `docs/LICENSING.md`.

## Reusable source

- `android/GoogleSignIn.kt` — generic Google Credential Manager flow.
- `android/AppApi.kt` — minimal authenticated HTTPS API client.
- `android/SessionStore.kt` — storage abstraction for app bearer sessions.
- `edge/src/index.ts` — generic identity/session Worker.
- `edge/migrations/0001_identity.sql` — reusable user/identity/session schema.


## Optional platform, integrations and examples

The default app still launches to a blank white screen. The feature modules add
realtime/network/storage/diagnostics foundations plus Bluetooth, camera,
location, NFC, USB and capability-based IoT contracts. Reference examples cover
realtime multi-user events, a minimal multiplayer model, analytics and an
ESP32/Raspberry Pi controller architecture. See `docs/MODULES.md`.
