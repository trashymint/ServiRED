"use client";

import { useEffect, useRef, useState } from "react";
import { getSupabase } from "@/lib/supabase";
import { ErrorEstado, Skeleton } from "./ui/Estados";

/**
 * Inserta un dashboard de Apache Superset con el Embedded SDK.
 * El token de invitado lo emite nuestro servidor (/api/superset/guest-token), que valida la
 * sesión de Supabase y agrega un filtro RLS con el id del operador. Ver docs/SUPERSET.md.
 */
export function SupersetDashboard({
  dashboardId,
  titulo,
  alto = "h-[720px]",
}: {
  dashboardId: string | undefined;
  titulo: string;
  alto?: string;
}) {
  const contenedor = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [listo, setListo] = useState(false);
  const [intento, setIntento] = useState(0);
  const dominio = process.env.NEXT_PUBLIC_SUPERSET_URL;

  useEffect(() => {
    if (!dashboardId || !dominio || !contenedor.current) return;
    let cancelado = false;
    let desmontar: (() => void) | undefined;

    (async () => {
      try {
        // Import dinámico: el SDK usa `window` y solo debe cargarse en el navegador.
        const { embedDashboard } = await import("@superset-ui/embedded-sdk");
        const embebido = await embedDashboard({
          id: dashboardId,
          supersetDomain: dominio,
          mountPoint: contenedor.current!,
          fetchGuestToken: () => pedirGuestToken(dashboardId),
          dashboardUiConfig: {
            hideTitle: true,
            hideChartControls: false,
            filters: { expanded: false },
          },
        });
        if (cancelado) embebido.unmount();
        else {
          desmontar = () => embebido.unmount();
          setListo(true);
        }
      } catch (e) {
        if (!cancelado) setError((e as Error).message);
      }
    })();

    return () => {
      cancelado = true;
      desmontar?.();
    };
  }, [dashboardId, dominio, intento]);

  if (!dashboardId || !dominio) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 bg-white p-6 text-sm text-slate-600">
        <p className="font-medium text-slate-800">{titulo}: Superset no está configurado.</p>
        <p className="mt-1">
          Define <code className="rounded bg-slate-100 px-1">NEXT_PUBLIC_SUPERSET_URL</code> y el id del
          dashboard embebido en <code className="rounded bg-slate-100 px-1">.env.local</code> (ver
          docs/SUPERSET.md).
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <ErrorEstado
        titulo={`No se pudo cargar "${titulo}"`}
        mensaje={error}
        onReintentar={() => {
          setError(null);
          setListo(false);
          setIntento((i) => i + 1);
        }}
      />
    );
  }

  return (
    <div className={`relative overflow-hidden rounded-xl border border-slate-200 bg-white ${alto}`}>
      {!listo && <Skeleton className="absolute inset-0 rounded-none" />}
      {/* El SDK crea un <iframe> dentro de este div */}
      <div
        ref={contenedor}
        title={titulo}
        className="h-full w-full [&>iframe]:h-full [&>iframe]:w-full [&>iframe]:border-0"
      />
    </div>
  );
}

async function pedirGuestToken(dashboardId: string): Promise<string> {
  const { data } = await getSupabase().auth.getSession();
  const accessToken = data.session?.access_token;
  if (!accessToken) throw new Error("La sesión expiró. Vuelve a iniciar sesión.");

  const res = await fetch("/api/superset/guest-token", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify({ dashboardId }),
  });
  const json = (await res.json().catch(() => ({}))) as { token?: string; error?: string };
  if (!res.ok || !json.token) throw new Error(json.error ?? `Error ${res.status} al pedir el token de Superset`);
  return json.token;
}
