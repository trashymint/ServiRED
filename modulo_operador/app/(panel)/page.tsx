"use client";

import Link from "next/link";
import { Encabezado } from "@/components/layout/Encabezado";
import { ResumenTarjetas } from "@/components/ResumenTarjetas";
import { SupersetDashboard } from "@/components/SupersetDashboard";
import { useOperador } from "@/hooks/useAuth";

export default function ResumenPage() {
  const operador = useOperador();

  return (
    <>
      <Encabezado
        titulo="Resumen"
        descripcion={`Indicadores de los reportes asignados a ${operador.NOMBRE ?? "tu operador"}.`}
      >
        <Link
          href="/reportes"
          className="inline-flex items-center rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
        >
          Ver reportes
        </Link>
      </Encabezado>

      <div className="space-y-8">
        <ResumenTarjetas />

        <section className="space-y-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Estadísticas</h2>
            <p className="text-sm text-slate-600">
              Tablero de Apache Superset filtrado automáticamente para tu operador.
            </p>
          </div>
          <SupersetDashboard
            dashboardId={process.env.NEXT_PUBLIC_SUPERSET_DASHBOARD_ID}
            titulo="Estadísticas del operador"
          />
        </section>
      </div>
    </>
  );
}
