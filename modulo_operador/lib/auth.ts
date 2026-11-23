import { getSupabase } from "./supabase";
import { TABLAS } from "./constants";
import { limpiarCacheOperador } from "./reportes";
import type { Operador } from "@/types/database";

// El usuario que inicia sesión se relaciona con Operador por la columna auth_id.
const COLUMNAS_OPERADOR =
  'id, "NOMBRE", "DEPARTAMENTO_PRESTACION", "MUNICIPIO_PRESTACION", "SERVICIO"';

export async function obtenerOperador(userId: string): Promise<Operador | null> {
  const { data, error } = await getSupabase()
    .from(TABLAS.operador)
    .select(COLUMNAS_OPERADOR)
    .eq("auth_id", userId)
    // Una empresa puede tener varias filas (una por municipio): basta con la primera.
    .order("id")
    .limit(1)
    .maybeSingle();
  if (error) throw error;
  return data as Operador | null;
}

export function esEmailValido(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

/**
 * Inicio de sesión con correo y contraseña mediante Supabase Auth.
 * La contraseña viaja por HTTPS a Supabase, que la compara con el hash guardado en
 * auth.users. El frontend nunca ve ni guarda contraseñas: solo recibe un token de sesión.
 */
export async function iniciarSesion(email: string, password: string) {
  return getSupabase().auth.signInWithPassword({
    email: email.trim().toLowerCase(),
    password,
  });
}

export async function cerrarSesion() {
  limpiarCacheOperador();
  await getSupabase().auth.signOut();
}
