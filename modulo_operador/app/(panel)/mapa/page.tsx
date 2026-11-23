"use client";

import { useState } from "react";
import { Encabezado } from "@/components/layout/Encabezado";
import { Filtros } from "@/components/reportes/Filtros";
import { DetalleReporte } from "@/components/reportes/DetalleReporte";
import { LeyendaMapa, MapaReportes } from "@/components/mapa";
import { SupersetDashboard } from "@/components/SupersetDashboard";
import { ErrorEstado, Skeleton, VacioEstado } from "@/components/ui/Estados";
import { useOpcionesFiltro, useReportesMapa } from "@/hooks/useReportes";
import { FILTROS_VACIOS } from "@/lib/reportes";
import type { FiltrosReportes, Id } from "@/types/database";

export default function MapaPage() {
  const [filtros, setFiltros] = useState<FiltrosReportes>(FILTROS_VACIOS);
  const [version, setVersion] = useState(0);
  const [seleccionado, setSeleccionado] = useState<Id | null>(null);

  const { data, error, cargando, recargar } = useReportesMapa(filtros, version);
  const opciones = useOpcionesFiltro();
  const dashboardMapa = process.env.NEXT_PUBLIC_SUPERSET_DASHBOARD_MAPA_ID;

  return (
    <>
      <Encabezado
        titulo="Mapa de reportes"
        descripcion="Haz clic en un punto para ver el detalle. El listado de Reportes ofrece el mismo acceso por teclado."
      >
        <LeyendaMapa />
      </Encabezado>

      <div className="space-y-6">
        <Filtros
          valor={filtros}
          onCambio={setFiltros}
          estados={opciones.data?.estados ?? []}
          tipos={opciones.data?.tipos ?? []}
        />

        {error ? (
          <ErrorEstado mensaje={error} onReintentar={recargar} />
        ) : cargando && !data ? (
          <Skeleton className="h-[60vh] min-h-96 w-full rounded-xl" />
        ) : data && data.length === 0 ? (
          <VacioEstado />
        ) : (
          <div className="relative isolate h-[60vh] min-h-96 overflow-hidden rounded-xl border border-slate-200 bg-white">
            <MapaReportes reportes={data ?? []} onSeleccionar={setSeleccionado} />
            {cargando && (
              <p className="absolute right-3 top-3 z-[1000] rounded bg-white/90 px-2 py-1 text-xs text-slate-700 shadow">
                Actualizando…
              </p>
            )}
          </div>
        )}
        {data && (
          <p className="text-sm text-slate-600">
            {data.length.toLocaleString("es-CO")} reportes en el mapa
            {data.length >= 2000 && " (se muestran los 2.000 más recientes)"}.
          </p>
        )}

        {/* Opcional: mapa/estadísticas geográficas construidos en Superset */}
        {dashboardMapa && (
          <section className="space-y-3">
            <h2 className="text-lg font-semibold text-slate-900">Análisis geográfico (Superset)</h2>
            <SupersetDashboard dashboardId={dashboardMapa} titulo="Mapa de Superset" />
          </section>
        )}
      </div>

      <DetalleReporte
        idFalla={seleccionado}
        onCerrar={() => setSeleccionado(null)}
        onResuelto={() => setVersion((v) => v + 1)}
      />
    </>
  );
}
