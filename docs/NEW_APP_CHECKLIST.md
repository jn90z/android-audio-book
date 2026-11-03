# New App Checklist

1. Choose an Android application ID and Worker name.
2. Create Google OAuth clients for Android and Web.
3. Configure Android with the Web client ID used for ID-token audience validation.
4. Create a Cloudflare D1 database.
5. Replace placeholders in `edge/wrangler.jsonc`.
6. Install edge dependencies and run `npm run check`.
7. Apply D1 migrations locally, then remotely.
8. Deploy the Worker and verify `GET /healthz`.
9. Set the Android API base URL to the HTTPS Worker endpoint.
10. Implement `SessionStore` with Keystore-backed protection before production.
11. Add app-specific database migrations and API routes separately from auth.
12. Add negative authentication tests before release.

## Design rule

Provider authentication answers **who is this?** Application sessions answer **may this request use my API?** Keep those concerns separate.
