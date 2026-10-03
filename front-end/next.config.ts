import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Dev only: the dev server allows just `localhost` by default, so opening the
  // app at 127.0.0.1 would have its hot-reload websocket refused
  allowedDevOrigins: ["127.0.0.1"],
};

export default nextConfig;
