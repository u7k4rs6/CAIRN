import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Standalone output for the Docker image: bundles only the traced production
  // dependencies into .next/standalone instead of shipping full node_modules.
  output: "standalone",
};

export default nextConfig;
