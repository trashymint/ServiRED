"use client";

import { useId } from "react";
import { TAMANOS_PAGINA } from "@/lib/constants";
import { formatearNumero } from "@/lib/format";

const claseBoton =
  "rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-700";

export function Paginacion({
  pagina,
  tamano,
  total,
  onPagina,
  onTamano,
}: {
  pagina: number;
  tamano: number;
  total: number;
  onPagina: (p: number) => void;
  onTamano: (t: number) => void;
}) {
  const id = useId();
  const paginas = Math.max(1, Math.ceil(total / tamano));
  const desde = total === 0 ? 0 : (pagina - 1) * tamano + 1;
  const hasta = Math.min(pagina * tamano, total);

  return (
    <nav
      aria-label="Paginación"
      className="flex flex-col items-start justify-between gap-3 text-sm text-slate-600 sm:flex-row sm:items-center"
    >
      <p aria-live="polite">
        Mostrando <strong className="text-slate-900">{formatearNumero(desde)}</strong>–
        <strong className="text-slate-900">{formatearNumero(hasta)}</strong> de{" "}
        <strong className="text-slate-900">{formatearNumero(total)}</strong> reportes
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <label htmlFor={`${id}-tam`} className="whitespace-nowrap">
          Filas por página
        </label>
        <select
          id={`${id}-tam`}
          value={tamano}
          onChange={(e) => onTamano(Number(e.target.value))}
          className="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm text-slate-900"
        >
          {TAMANOS_PAGINA.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <button type="button" className={claseBoton} disabled={pagina <= 1} onClick={() => onPagina(pagina - 1)}>
          Anterior
        </button>
        <span className="whitespace-nowrap tabular-nums">
          Página {pagina} de {paginas}
        </span>
        <button
          type="button"
          className={claseBoton}
          disabled={pagina >= paginas}
          onClick={() => onPagina(pagina + 1)}
        >
          Siguiente
        </button>
      </div>
    </nav>
  );
}
