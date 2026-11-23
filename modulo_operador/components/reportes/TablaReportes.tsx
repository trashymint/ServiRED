"use client";

import { formatearFecha } from "@/lib/format";
import type { Id, Reporte } from "@/types/database";
import { EstadoBadge, SinAsignarBadge, ValidadoBadge } from "../ui/Insignias";
import { Skeleton } from "../ui/Estados";

const COLUMNAS = ["Fecha", "Servicio", "Tipo de novedad", "Ubicación", "Estado", "Validado", ""];

export function ubicacionTexto(r: Pick<Reporte, "ubicacion">) {
  const u = r.ubicacion;
  if (!u) return "—";
  return [u.municipio, u.departamento].filter(Boolean).join(", ") || "—";
}

export function TablaReportes({
  filas,
  cargando,
  filasSkeleton,
  onVer,
}: {
  filas: Reporte[];
  cargando: boolean;
  filasSkeleton: number;
  onVer: (id: Id) => void;
}) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
      <table className="min-w-full text-left text-sm">
        <caption className="sr-only">Reportes de fallas del operador, más recientes primero</caption>
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-600">
          <tr>
            {COLUMNAS.map((c, i) => (
              <th key={i} scope="col" className="whitespace-nowrap px-4 py-3 font-semibold">
                {c || <span className="sr-only">Acciones</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100" aria-busy={cargando}>
          {cargando
            ? Array.from({ length: filasSkeleton }, (_, i) => (
                <tr key={i}>
                  {COLUMNAS.map((_, j) => (
                    <td key={j} className="px-4 py-3.5">
                      <Skeleton className="h-4 w-full max-w-32" />
                    </td>
                  ))}
                </tr>
              ))
            : filas.map((r) => (
                <tr
                  key={String(r.id_falla)}
                  onClick={() => onVer(r.id_falla)}
                  className="cursor-pointer hover:bg-slate-50"
                >
                  <td className="whitespace-nowrap px-4 py-3 tabular-nums text-slate-900">
                    {formatearFecha(r.fecha)}
                  </td>
                  <td className="px-4 py-3 text-slate-800 first-letter:uppercase">{r.servicio?.tipo ?? "—"}</td>
                  <td className="px-4 py-3 text-slate-800">{r.tipo ?? "—"}</td>
                  <td className="px-4 py-3 text-slate-700">{ubicacionTexto(r)}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-col items-start gap-1">
                      <EstadoBadge estado={r.estado} />
                      {r.id_operador == null && <SinAsignarBadge />}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <ValidadoBadge validado={r.validado} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    {/* El botón da acceso por teclado; el clic en la fila es un atajo de ratón. */}
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onVer(r.id_falla);
                      }}
                      className="rounded-md px-2 py-1 text-sm font-medium text-slate-700 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-slate-700"
                    >
                      Ver detalle
                      <span className="sr-only"> del reporte del {formatearFecha(r.fecha)}</span>
                    </button>
                  </td>
                </tr>
              ))}
        </tbody>
      </table>
    </div>
  );
}
