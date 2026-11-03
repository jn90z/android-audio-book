# Architecture Walkthrough

## Boundaries

The starter deliberately separates four concerns:

- **UI**: screens and user interaction.
- **Provider authentication**: Android Credential Manager obtains a Google ID token.
- **Application authentication**: the Worker verifies Google and issues an app-owned opaque session.
- **Domain logic**: app-specific features live outside the identity/session layer.

## Login sequence

```text
User
 -> Android GoogleSignIn
 -> Credential Manager
 -> Google
 <- signed ID token
 -> Worker POST /v1/auth/google { idToken, nonce }
 -> Google JWKS (public keys)
 -> verify RS256 + aud + iss + exp + nonce
 -> D1 identity lookup
 <- app access token + app user
```

For a new identity, the Worker returns `setupRequired`; the client chooses a username and calls the setup endpoint.

## Why two tokens?

The Google ID token is evidence from an identity provider. It should not become the application's permanent authorization model. After verification, the backend issues a random application token. This decouples API authorization and session lifetime from Google and allows additional identity providers later.

## Data model

`users` is the application account. `identities` links provider subjects to that account. `access_tokens` represents application sessions. A future account can therefore link Google, Apple, Microsoft, or another provider without changing its internal user ID.

## Native C++ pattern from Chess++

Chess++ also demonstrates a reusable native-code boundary:

```text
Kotlin UI -> Kotlin wrapper -> JNI adapter -> C++ domain library
```

Keep the C++ library platform-independent and keep JNI thin. C++ should own portable domain logic; Kotlin should own Android lifecycle/UI; the server should remain authoritative for security-sensitive multiplayer state.

## Libraries used in Chess++

Android: AndroidX Credential Manager, Google Identity `googleid`, Kotlin coroutines, Android NDK/JNI, CMake.

Backend: Cloudflare Workers, TypeScript, Wrangler, D1, Web Crypto. Chess++ additionally uses `chess.js` for authoritative server-side chess validation; that dependency is intentionally not part of this generic starter.
