export { RealtimeRoom } from "./realtime";
export interface Env {
  APP_DB: D1Database;
  GOOGLE_WEB_CLIENT_ID: string;
}

type GoogleClaims = {
  sub: string;
  aud: string;
  iss: string;
  exp: number;
  nonce?: string;
  name?: string;
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer"
    }
  });

const decodeBase64Url = (value: string) => {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  return Uint8Array.from(atob(padded), c => c.charCodeAt(0));
};

async function verifyGoogleIdToken(
  idToken: string,
  nonce: string,
  clientId: string
): Promise<GoogleClaims | null> {
  const parts = idToken.split(".");
  if (parts.length !== 3) return null;

  let header: { alg?: string; kid?: string };
  let claims: GoogleClaims;
  try {
    header = JSON.parse(new TextDecoder().decode(decodeBase64Url(parts[0])));
    claims = JSON.parse(new TextDecoder().decode(decodeBase64Url(parts[1])));
  } catch {
    return null;
  }

  if (header.alg !== "RS256" || !header.kid) return null;
  if (claims.aud !== clientId) return null;
  if (!["accounts.google.com", "https://accounts.google.com"].includes(claims.iss)) return null;
  if (!claims.sub || claims.exp <= Math.floor(Date.now() / 1000) || claims.nonce !== nonce) return null;

  const response = await fetch("https://www.googleapis.com/oauth2/v3/certs");
  if (!response.ok) return null;
  const jwks = await response.json<{ keys: Array<JsonWebKey & { kid?: string }> }>();
  const jwk = jwks.keys.find(k => k.kid === header.kid);
  if (!jwk) return null;

  try {
    const key = await crypto.subtle.importKey(
      "jwk", jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false, ["verify"]
    );
    return await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      key,
      decodeBase64Url(parts[2]),
      new TextEncoder().encode(parts[0] + "." + parts[1])
    ) ? claims : null;
  } catch {
    return null;
  }
}

const randomToken = () => {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
};

const tokenHash = async (token: string) => {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
};

async function issueSession(env: Env, userId: string) {
  const token = randomToken();
  const hash = await tokenHash(token);
  const expiresAt = new Date(Date.now() + 7 * 86400_000).toISOString();
  await env.APP_DB.prepare(
    "INSERT INTO access_tokens(token_hash,user_id,expires_at) VALUES(?1,?2,?3)"
  ).bind(hash, userId, expiresAt).run();
  return { token, expiresAt };
}

async function currentUser(env: Env, request: Request) {
  const header = request.headers.get("authorization") || "";
  if (!header.startsWith("Bearer ")) return null;
  const hash = await tokenHash(header.slice(7));
  return env.APP_DB.prepare(
    `SELECT u.id,u.username,u.display_name AS displayName
     FROM access_tokens t JOIN users u ON u.id=t.user_id
     WHERE t.token_hash=?1 AND unixepoch(t.expires_at)>unixepoch()
       AND u.deleted_at IS NULL`
  ).bind(hash).first();
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/healthz") {
      const db = await env.APP_DB.prepare("SELECT 1 AS ok").first<{ ok: number }>();
      return json({ status: "ok", database: db?.ok === 1 ? "ok" : "error" });
    }

    if (request.method === "POST" && url.pathname === "/v1/auth/google") {
      let body: { idToken?: unknown; nonce?: unknown };
      try { body = await request.json(); } catch { return json({ error: "invalid_request" }, 400); }
      const idToken = typeof body.idToken === "string" ? body.idToken : "";
      const nonce = typeof body.nonce === "string" ? body.nonce : "";
      if (!idToken || idToken.length > 8192 || !nonce || nonce.length > 256)
        return json({ error: "invalid_request" }, 400);

      const claims = await verifyGoogleIdToken(idToken, nonce, env.GOOGLE_WEB_CLIENT_ID);
      if (!claims) return json({ error: "invalid_google_credential" }, 401);

      const user = await env.APP_DB.prepare(
        `SELECT u.id,u.username,u.display_name AS displayName
         FROM identities i JOIN users u ON u.id=i.user_id
         WHERE i.provider='google' AND i.provider_subject=?1 AND u.deleted_at IS NULL`
      ).bind(claims.sub).first<{ id: string; username: string; displayName: string }>();

      if (!user) {
        return json({
          setupRequired: true,
          displayName: (claims.name?.trim() || "User").slice(0, 80)
        });
      }

      const session = await issueSession(env, user.id);
      return json({ accessToken: session.token, expiresAt: session.expiresAt, user });
    }

    if (request.method === "POST" && url.pathname === "/v1/auth/google/setup") {
      let body: { idToken?: unknown; nonce?: unknown; username?: unknown };
      try { body = await request.json(); } catch { return json({ error: "invalid_request" }, 400); }
      const idToken = typeof body.idToken === "string" ? body.idToken : "";
      const nonce = typeof body.nonce === "string" ? body.nonce : "";
      const username = typeof body.username === "string" ? body.username.trim() : "";
      if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) return json({ error: "invalid_username" }, 400);

      const claims = await verifyGoogleIdToken(idToken, nonce, env.GOOGLE_WEB_CLIENT_ID);
      if (!claims) return json({ error: "invalid_google_credential" }, 401);

      const existing = await env.APP_DB.prepare(
        "SELECT user_id AS userId FROM identities WHERE provider='google' AND provider_subject=?1"
      ).bind(claims.sub).first<{ userId: string }>();
      if (existing) return json({ error: "identity_already_registered" }, 409);

      const id = crypto.randomUUID();
      const displayName = (claims.name?.trim() || "User").slice(0, 80);
      try {
        await env.APP_DB.batch([
          env.APP_DB.prepare("INSERT INTO users(id,username,display_name) VALUES(?1,?2,?3)")
            .bind(id, username, displayName),
          env.APP_DB.prepare("INSERT INTO identities(user_id,provider,provider_subject) VALUES(?1,'google',?2)")
            .bind(id, claims.sub)
        ]);
      } catch {
        return json({ error: "username_unavailable" }, 409);
      }

      const session = await issueSession(env, id);
      return json({
        accessToken: session.token,
        expiresAt: session.expiresAt,
        user: { id, username, displayName }
      }, 201);
    }

    if (request.method === "GET" && url.pathname === "/v1/me") {
      const user = await currentUser(env, request);
      return user ? json(user) : json({ error: "unauthorized" }, 401);
    }

    return json({ error: "not_found" }, 404);
  }
};
