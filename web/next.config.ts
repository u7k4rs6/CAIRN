import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Standalone output for the Docker image: bundles only the traced production
  // dependencies into .next/standalone instead of shipping full node_modules.
  output: "standalone",
  // Proxies same-origin browser calls to the private API so the session cookie
  // stays first-party (see lib/apiBase.ts). Scoped to /api/* only - git's
  // smart-HTTP paths are hit directly on the API's public URL by git clients and
  // never pass through this app at all.
  async rewrites() {
    const internalApiUrl = process.env.INTERNAL_API_URL || "http://localhost:8080";
    return [
      {
        source: "/api/:path*",
        destination: `${internalApiUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
