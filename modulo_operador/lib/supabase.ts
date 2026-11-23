import { createClient, type SupabaseClient } from "@supabase/supabase-js";

// Cliente de navegador con la PUBLISHABLE key. Toda la seguridad la aplica la base (RLS).
// Se crea de forma perezosa para que `next build` no falle si faltan variables.
let cliente: SupabaseClient | null = null;

export function getSupabase(): SupabaseClient {
  if (cliente) return cliente;

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key) {
    throw new Error(
      "Faltan NEXT_PUBLIC_SUPABASE_URL o NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY en .env.local",
    );
  }
  if (key.startsWith("sb_secret_")) {
    throw new Error("No uses la secret key de Supabase en el frontend.");
  }

  cliente = createClient(url, key, {
    auth: { persistSession: true, autoRefreshToken: true },
  });
  return cliente;
}
