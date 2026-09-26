import { defineConfig } from "vite";

export default defineConfig(({ command, isPreview }) => ({
  server: {
    // Allow changing Quick Tunnel hostnames only for the development server.
    allowedHosts:
      command === "serve" && !isPreview ? [".trycloudflare.com"] : [],
  },
}));
