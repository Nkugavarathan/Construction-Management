# ADR-0002: In-memory access token, rotating HttpOnly refresh cookie, same-origin proxy

- Status: Proposed
- Date: 2026-10-20

## Context
The browser app needs short sessions that survive page reloads, can be revoked (logout, disabled member,
stolen token), and do not expose long-lived credentials to JavaScript. Frontend (Vercel) and backend
(container host) will run on different hosts. Browsers increasingly block third-party cookies.

## Decision
1. **Access token**: JWT (HS256, secret from env, at least 256 bits), 15-minute lifetime, claims `sub` (user ID), `org` (active org),
   `iat`, `exp`, `jti`. It is held **only in JavaScript memory** (React context), never in localStorage or sessionStorage.
   The role is *not* in the token. It is loaded from `organization_memberships` per request so revocation is immediate.
2. **Refresh token**: 256-bit random opaque value, stored as a SHA-256 hash in `refresh_tokens`. It is sent as the cookie
   `cf_refresh; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=7d`.
   - Every `/auth/refresh` **rotates** it: the old row is marked replaced and a new one is issued in the same family.
   - Presenting an already-rotated token is treated as theft: the **whole family is revoked**.
   - Families have a 30-day absolute lifetime. Logout and password change revoke families.
3. **Same origin**: Next.js rewrites `/api/v1/:path*` to the backend. The browser only talks to the frontend origin,
   so the cookie is first-party and production needs no CORS.
4. **CSRF**: bearer-authenticated endpoints cannot be forged cross-site because browsers never attach the
   `Authorization` header automatically. The two cookie-authenticated endpoints (`refresh`, `logout`) rely on
   `SameSite=Strict` plus an `Origin` header allow-list check. Spring Security's CSRF token is disabled with this reasoning.
5. On page load the app calls `/auth/refresh` to obtain an access token. The API client retries a request **once** after a
   `401` by refreshing. Concurrent 401s share a single in-flight refresh promise.

## Consequences
- XSS cannot read the refresh token. It could still use the in-memory access token during the attack, which no
  browser-side storage prevents, so CSP and React escaping remain essential.
- Data-fetching pages are Client Components, because the Next.js server never sees the access token.
  We give up server-rendering private data in exchange for a simpler, safer token flow.
- The Vercel rewrite adds one network hop. Acceptable.
- Rejected: server sessions (Spring Session), which work well but teach less about token lifecycles; localStorage tokens
  (readable by XSS); a full BFF storing tokens server-side, which adds complexity for a portfolio app.
