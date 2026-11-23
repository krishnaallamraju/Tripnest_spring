import type { NextConfig } from "next";

if (process.env.VERCEL) {
  const apiUrl = process.env.NEXT_PUBLIC_API_URL?.trim();
  if (!apiUrl) throw new Error("Set NEXT_PUBLIC_API_URL to the deployed backend URL before deploying TripNest.");
  const parsed = new URL(apiUrl);
  if (parsed.protocol !== "https:" || ["localhost", "127.0.0.1", "[::1]"].includes(parsed.hostname)) {
    throw new Error("NEXT_PUBLIC_API_URL must be a public HTTPS backend URL.");
  }
}

const nextConfig: NextConfig = {
  turbopack: { root: __dirname },
};

export default nextConfig;
