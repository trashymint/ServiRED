"use client";

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { AuthProvider, useAuth } from "@/hooks/useAuth";
import { ToastProvider } from "@/hooks/useToast";
import { Navegacion } from "@/components/layout/Navegacion";
import { Skeleton } from "@/components/ui/Estados";
import { getSupabase } from "@/lib/supabase";

// Layout de las rutas protegidas: /, /reportes y /mapa.
// La sesión de Supabase vive en el navegador (localStorage), por eso la protección es en cliente;
// la protección real de los datos la hace RLS en la base.
export default function PanelLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <ToastProvider>
        <Guardia>{children}</Guardia>
      </ToastProvider>
    </AuthProvider>
  );
}

function Guardia({ children }: { children: ReactNode }) {
  const { auth, verificar } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (auth.estado !== "anonimo") return;
    let vigente = true;
    const irALogin = () => {
      const motivo = auth.motivo ? `?motivo=${auth.motivo}` : "";
      router.replace(`/login${motivo}`);
    };
    // El estado "anonimo" puede ser viejo (Next conserva el estado de páginas ya visitadas).
    // Antes de expulsar al usuario se pregunta a Supabase si de verdad no hay sesión.
    (async () => {
      try {
        const { data } = await getSupabase().auth.getSession();
        if (!vigente) return;
        if (data.session) verificar();
        else irALogin();
      } catch {
        if (vigente) irALogin();
      }
    })();
    return () => {
      vigente = false;
    };
  }, [auth, router, verificar]);

  if (auth.estado !== "autenticado") {
    return (
      <div className="flex min-h-dvh items-center justify-center" aria-busy="true">
        <div className="w-full max-w-md space-y-3 px-6">
          <span className="sr-only">Verificando sesión…</span>
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-dvh bg-slate-50">
      <a
        href="#contenido"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[3000] focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:shadow"
      >
        Saltar al contenido
      </a>
      <Navegacion />
      <main id="contenido" className="px-4 py-6 sm:px-6 lg:ml-64 lg:px-8 lg:py-8">
        <div className="mx-auto max-w-7xl">{children}</div>
      </main>
    </div>
  );
}
