"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { getSupabase } from "@/lib/supabase";
import { cerrarSesion, obtenerOperador } from "@/lib/auth";
import type { Operador } from "@/types/database";

export type EstadoAuth =
  | { estado: "cargando" }
  | { estado: "anonimo"; motivo?: "no-operador" | "error"; mensaje?: string }
  | { estado: "autenticado"; operador: Operador };

interface ContextoAuth {
  auth: EstadoAuth;
  /** Vuelve a leer la sesión de Supabase y el operador asociado. */
  verificar: () => Promise<void>;
  salir: () => Promise<void>;
}

const AuthContext = createContext<ContextoAuth | null>(null);

// NOTA: Next 16 conserva las páginas ya visitadas (con su estado) y solo vuelve a ejecutar
// sus efectos al mostrarlas. Por eso este estado puede venir "viejo" (p. ej. "anonimo" de
// antes de iniciar sesión); la guardia del layout comprueba la sesión real antes de redirigir.
export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<EstadoAuth>({ estado: "cargando" });
  const montado = useRef(false);

  const verificar = useCallback(async () => {
    try {
      const { data } = await getSupabase().auth.getSession();
      const user = data.session?.user;
      if (!user) {
        if (montado.current) setAuth({ estado: "anonimo" });
        return;
      }
      const operador = await obtenerOperador(user.id);
      if (!montado.current) return;
      if (!operador) {
        // El usuario existe en Auth pero no es operador: no se le permite entrar.
        await cerrarSesion();
        setAuth({ estado: "anonimo", motivo: "no-operador" });
        return;
      }
      setAuth({ estado: "autenticado", operador });
    } catch (e) {
      // Sin sesión válida no se puede seguir: se cierra para no quedar en un estado a medias.
      await cerrarSesion().catch(() => {});
      if (montado.current) setAuth({ estado: "anonimo", motivo: "error", mensaje: (e as Error).message });
    }
  }, []);

  useEffect(() => {
    montado.current = true;
    // En microtarea: el estado se actualiza cuando Supabase responde, no durante el efecto.
    queueMicrotask(() => void verificar());

    let cancelarSuscripcion = () => {};
    try {
      // Solo reaccionamos al cierre de sesión (también si expira o se cierra en otra pestaña).
      const { data } = getSupabase().auth.onAuthStateChange((evento) => {
        if (evento === "SIGNED_OUT" && montado.current) setAuth({ estado: "anonimo" });
      });
      cancelarSuscripcion = () => data.subscription.unsubscribe();
    } catch {
      // Variables de entorno faltantes: verificar() ya dejó el estado en error.
    }

    return () => {
      montado.current = false;
      cancelarSuscripcion();
    };
  }, [verificar]);

  const salir = useCallback(async () => {
    await cerrarSesion();
    setAuth({ estado: "anonimo" });
  }, []);

  return <AuthContext.Provider value={{ auth, verificar, salir }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth debe usarse dentro de <AuthProvider>");
  return ctx;
}

/** Atajo para componentes que solo se renderizan con sesión iniciada. */
export function useOperador(): Operador {
  const { auth } = useAuth();
  if (auth.estado !== "autenticado") throw new Error("No hay operador autenticado");
  return auth.operador;
}
