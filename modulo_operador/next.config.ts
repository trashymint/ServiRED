import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  experimental: {
    agentFeedback: true,
    // El panel se dibuja solo en el navegador tras verificar la sesión de Supabase, así que
    // el validador de "instant navigation" no puede renderizar las páginas en desarrollo.
    // Con 'manual-warning' solo valida los segmentos que exporten `instant` explícitamente.
    instantInsights: {
      validationLevel: "manual-warning",
    },
  },
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
