"use client";

import { useId } from "react";
import { SERVICIOS } from "@/lib/constants";
import { FILTROS_VACIOS } from "@/lib/reportes";
import type { FiltrosReportes } from "@/types/database";

const claseCampo =
  "mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-slate-500 focus:outline-2 focus:outline-offset-0 focus:outline-slate-400";

export function Filtros({
  valor,
  onCambio,
  estados,
  tipos,
}: {
  valor: FiltrosReportes;
  onCambio: (f: FiltrosReportes) => void;
  estados: string[];
  tipos: string[];
}) {
  const id = useId();
  const set = <K extends keyof FiltrosReportes>(k: K, v: FiltrosReportes[K]) =>
    onCambio({ ...valor, [k]: v });
  const hayFiltros = Object.entries(valor).some(([, v]) => v !== "");

  return (
    <form
      role="search"
      aria-label="Filtros de reportes"
      onSubmit={(e) => e.preventDefault()}
      className="rounded-xl border border-slate-200 bg-white p-4"
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        <div>
          <label htmlFor={`${id}-estado`} className="text-sm font-medium text-slate-700">
            Estado
          </label>
          <select
            id={`${id}-estado`}
            className={claseCampo}
            value={valor.estado}
            onChange={(e) => set("estado", e.target.value)}
          >
            <option value="">Todos</option>
            {estados.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor={`${id}-servicio`} className="text-sm font-medium text-slate-700">
            Servicio
          </label>
          <select
            id={`${id}-servicio`}
            className={claseCampo}
            value={valor.servicio}
            onChange={(e) => set("servicio", e.target.value)}
          >
            <option value="">Todos</option>
            {SERVICIOS.map((s) => (
              <option key={s.valor} value={s.valor}>
                {s.etiqueta}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor={`${id}-tipo`} className="text-sm font-medium text-slate-700">
            Tipo de novedad
          </label>
          <select
            id={`${id}-tipo`}
            className={claseCampo}
            value={valor.tipo}
            onChange={(e) => set("tipo", e.target.value)}
          >
            <option value="">Todos</option>
            {tipos.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor={`${id}-validado`} className="text-sm font-medium text-slate-700">
            Validado
          </label>
          <select
            id={`${id}-validado`}
            className={claseCampo}
            value={valor.validado}
            onChange={(e) => set("validado", e.target.value as FiltrosReportes["validado"])}
          >
            <option value="">Todos</option>
            <option value="si">Sí</option>
            <option value="no">No</option>
          </select>
        </div>

        <div>
          <label htmlFor={`${id}-desde`} className="text-sm font-medium text-slate-700">
            Desde
          </label>
          <input
            id={`${id}-desde`}
            type="date"
            className={claseCampo}
            value={valor.desde}
            max={valor.hasta || undefined}
            onChange={(e) => set("desde", e.target.value)}
          />
        </div>

        <div>
          <label htmlFor={`${id}-hasta`} className="text-sm font-medium text-slate-700">
            Hasta
          </label>
          <input
            id={`${id}-hasta`}
            type="date"
            className={claseCampo}
            value={valor.hasta}
            min={valor.desde || undefined}
            onChange={(e) => set("hasta", e.target.value)}
          />
        </div>
      </div>

      <div className="mt-4 flex justify-end">
        <button
          type="button"
          onClick={() => onCambio(FILTROS_VACIOS)}
          disabled={!hayFiltros}
          className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-700"
        >
          Limpiar filtros
        </button>
      </div>
    </form>
  );
}
