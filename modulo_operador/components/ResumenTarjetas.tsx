"use client";

import { useConsulta } from "@/hooks/useConsulta";
import { obtenerResumen } from "@/lib/reportes";
import { formatearHoras, formatearNumero } from "@/lib/format";
import { ErrorEstado, Skeleton } from "./ui/Estados";

/** Tarjetas de resumen. `version` permite forzar recarga desde el padre (p. ej. tras resolver). */
export function ResumenTarjetas({ version = 0 }: { version?: number }) {
  const { data, error, cargando, recargar } = useConsulta(obtenerResumen, `resumen-${version}`);

  if (error) return <ErrorEstado mensaje={error} onReintentar={recargar} />;

  const tarjetas = [
    { titulo: "Total de reportes", valor: data ? formatearNumero(data.total) : "", acento: "bg-slate-500" },
    { titulo: "Pendientes", valor: data ? formatearNumero(data.pendientes) : "", acento: "bg-amber-500" },
    { titulo: "Resueltos", valor: data ? formatearNumero(data.resueltos) : "", acento: "bg-green-600" },
    {
      titulo: "Tiempo promedio de resolución",
      valor: data ? formatearHoras(data.promedioHoras) : "",
      acento: "bg-sky-600",
    },
  ];

  return (
    <section aria-label="Resumen de reportes" className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {tarjetas.map((t) => (
        <div key={t.titulo} className="relative overflow-hidden rounded-xl border border-slate-200 bg-white p-5">
          <span aria-hidden className={`absolute inset-y-0 left-0 w-1 ${t.acento}`} />
          <p className="text-sm font-medium text-slate-600">{t.titulo}</p>
          {cargando ? (
            <Skeleton className="mt-3 h-8 w-24" />
          ) : (
            <p className="mt-2 text-3xl font-semibold tabular-nums text-slate-900">{t.valor}</p>
          )}
        </div>
      ))}
    </section>
  );
}
