# Security Policy

## Supported versions
Security fixes are provided for the latest release and current main branch.

## Reporting a vulnerability
Please do not open a public issue for a suspected vulnerability. Use GitHub's
private vulnerability reporting/security advisory feature for this repository.

Do not include production credentials, private keys, access tokens, OAuth
secrets, signing keys, or customer data in issues, commits, logs, examples, or
test fixtures.

## Starter security rules
- Production traffic must use HTTPS/WSS.
- Authentication tokens must use Keystore-backed storage in production apps.
- Server endpoints must authenticate and authorize every protected operation.
- Hardware integrations request permissions only when the consuming app needs them.
- Treat BLE, NFC, USB, LAN and device payloads as untrusted input.
