import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    const controlPlane = process.env.CONTROL_PLANE_INTERNAL_URL
      ?? (process.env.NODE_ENV === "development" ? "http://localhost:8080" : "http://control-plane:8080");
    return [{ source: "/control-api/:path*", destination: `${controlPlane}/api/v1/:path*` }];
  },
};

export default nextConfig;
