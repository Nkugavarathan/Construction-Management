/**
 * Next.js configuration.
 *
 * - The browser only ever talks to this origin. `/api/v1/**` is rewritten to the Spring Boot API, so
 *   the refresh-token cookie is first-party and production needs no CORS (ADR-0002).
 * - BACKEND_URL is server-only (no NEXT_PUBLIC_ prefix), so it never reaches the client bundle.
 * - cacheComponents is off on purpose: every authenticated page fetches in the browser with an
 *   in-memory access token, so there is nothing for "use cache" to cache.
 */
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
const isDev = process.env.NODE_ENV !== "production";

const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactCompiler: true,
  // The Docker image runs the self-contained server (NEXT_OUTPUT=standalone); `next start` is used locally.
  output: process.env.NEXT_OUTPUT === "standalone" ? "standalone" : undefined,
  poweredByHeader: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/v1/:path*", destination: `${backendUrl}/api/v1/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: contentSecurityPolicy },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
