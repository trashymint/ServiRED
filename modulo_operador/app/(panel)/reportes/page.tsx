"use client";

import { useState } from "react";
import { Encabezado } from "@/components/layout/Encabezado";
import { ResumenTarjetas } from "@/components/ResumenTarjetas";
import { Filtros } from "@/components/reportes/Filtros";
import { TablaReportes } from "@/components/reportes/TablaReportes";
import { Paginacion } from "@/components/reportes/Paginacion";
import { DetalleReporte } from "@/components/reportes/DetalleReporte";
import { ErrorEstado, VacioEstado } from "@/components/ui/Estados";
import { useOpcionesFiltro, useReportes } from "@/hooks/useReportes";
import { FILTROS_VACIOS } from "@/lib/reportes";
import type { FiltrosReportes, Id } from "@/types/database";

export default function ReportesPage() {
  const [filtros, setFiltros] = useState<FiltrosReportes>(FILTROS_VACIOS);
  const [pagina, setPagina] = useState(1);
  const [tamano, setTamano] = useState(10);
  const [version, setVersion] = useState(0);
  const [seleccionado, setSeleccionado] = useState<Id | null>(null);

  const { data, error, cargando, recargar } = useReportes(filtros, pagina, tamano, version);
  const opciones = useOpcionesFiltro();

  const cambiarFiltros = (f: FiltrosReportes) => {
    setFiltros(f);
    setPagina(1);
  };

  return (
    <>
      <Encabezado titulo="Reportes" descripcion="Fallas reportadas en tu zona de prestación, más recientes primero." />

      <div className="space-y-6">
        <ResumenTarjetas version={version} />

        <Filtros
          valor={filtros}
          onCambio={cambiarFiltros}
          estados={opciones.data?.estados ?? []}
          tipos={opciones.data?.tipos ?? []}
        />

        {error ? (
          <ErrorEstado mensaje={error} onReintentar={recargar} />
        ) : !cargando && data && data.filas.length === 0 ? (
          <VacioEstado>
            <button
              type="button"
              onClick={() => cambiarFiltros(FILTROS_VACIOS)}
              className="text-sm font-medium text-slate-700 underline underline-offset-2"
            >
              Limpiar filtros
            </button>
          </VacioEstado>
        ) : (
          <div className="space-y-4">
            <TablaReportes
              filas={data?.filas ?? []}
              cargando={cargando}
              filasSkeleton={tamano}
              onVer={setSeleccionado}
            />
            <Paginacion
              pagina={pagina}
              tamano={tamano}
              total={data?.total ?? 0}
              onPagina={setPagina}
              onTamano={(t) => {
                setTamano(t);
                setPagina(1);
              }}
            />
          </div>
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
