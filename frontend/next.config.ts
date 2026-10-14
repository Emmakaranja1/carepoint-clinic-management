import type { NextConfig } from "next";

// ADR-0002: same-origin session — the frontend calls /api/* on its own
// origin and Next proxies to the backend, so the httpOnly cookie session
// works with no CORS and no credentials in the browser.
const API_TARGET = process.env.API_PROXY_TARGET ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API_TARGET}/api/:path*` }];
  },
};

export default nextConfig;
