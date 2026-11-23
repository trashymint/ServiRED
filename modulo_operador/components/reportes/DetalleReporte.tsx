"use client";

import { useEffect, useRef, useState } from "react";
import { useConsulta } from "@/hooks/useConsulta";
import { useToast } from "@/hooks/useToast";
import { esResuelto } from "@/lib/constants";
import { formatearFecha } from "@/lib/format";
import { mensajeError, obtenerReporte, resolverReporte } from "@/lib/reportes";
import type { Id } from "@/types/database";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { ErrorEstado, Skeleton } from "../ui/Estados";
import { EstadoBadge, SinAsignarBadge, ValidadoBadge } from "../ui/Insignias";
import { MapaReporte } from "../mapa";
import { ubicacionTexto } from "./TablaReportes";

/**
 * Panel lateral con el detalle de un reporte. Se abre cuando `idFalla` no es null.
 * Tras resolver, llama `onResuelto` para que el padre refresque listas y resumen.
 */
export function DetalleReporte({
  idFalla,
  onCerrar,
  onResuelto,
}: {
  idFalla: Id | null;
  onCerrar: () => void;
  onResuelto: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const abierto = idFalla !== null;

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (abierto && !d.open) d.showModal();
    if (!abierto && d.open) d.close();
  }, [abierto]);

  return (
    <dialog
      ref={ref}
      aria-labelledby="detalle-titulo"
      onCancel={(e) => {
        e.preventDefault();
        onCerrar();
      }}
      onClick={(e) => {
        // Clic en el fondo (fuera del contenido) cierra el panel.
        if (e.target === e.currentTarget) onCerrar();
      }}
      className="fixed inset-y-0 right-0 left-auto m-0 h-dvh max-h-dvh w-full max-w-xl border-l border-slate-200 bg-white p-0 shadow-2xl backdrop:bg-slate-900/30"
    >
      {abierto && (
        <ContenidoDetalle key={String(idFalla)} idFalla={idFalla} onCerrar={onCerrar} onResuelto={onResuelto} />
      )}
    </dialog>
  );
}

function ContenidoDetalle({
  idFalla,
  onCerrar,
  onResuelto,
}: {
  idFalla: Id;
  onCerrar: () => void;
  onResuelto: () => void;
}) {
  const toast = useToast();
  const { data: r, error, cargando, recargar } = useConsulta(
    () => obtenerReporte(idFalla),
    `detalle-${idFalla}`,
  );
  const [confirmando, setConfirmando] = useState(false);
  const [resolviendo, setResolviendo] = useState(false);

  const resuelto = esResuelto(r?.estado);

  async function confirmarResolver() {
    setResolviendo(true);
    try {
      await resolverReporte(idFalla);
      toast("exito", "El reporte quedó marcado como resuelto.");
      setConfirmando(false);
      recargar(); // trae estado y fecha_resolucion calculada por el trigger
      onResuelto();
    } catch (e) {
      toast("error", `No se pudo resolver el reporte. ${mensajeError(e)}`);
      setConfirmando(false);
    } finally {
      setResolviendo(false);
    }
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-4">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Reporte #{String(idFalla)}</p>
          <h2 id="detalle-titulo" className="mt-0.5 text-lg font-semibold text-slate-900">
            {cargando ? "Cargando reporte…" : (r?.tipo ?? "Detalle del reporte")}
          </h2>
        </div>
        <button
          type="button"
          onClick={onCerrar}
          className="rounded-md p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-slate-700"
          aria-label="Cerrar detalle"
        >
          ✕
        </button>
      </header>

      <div className="flex-1 overflow-y-auto px-6 py-5">
        {error ? (
          <ErrorEstado mensaje={error} onReintentar={recargar} titulo="No se pudo cargar el reporte" />
        ) : cargando && !r ? (
          <div className="space-y-4">
            <Skeleton className="h-5 w-40" />
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-56 w-full" />
          </div>
        ) : !r ? (
          <p className="text-sm text-slate-600">
            El reporte no existe o no tienes permiso para verlo.
          </p>
        ) : (
          <div className="space-y-6">
            <div className="flex flex-wrap gap-2">
              <EstadoBadge estado={r.estado} />
              <ValidadoBadge validado={r.validado} />
              {r.id_operador == null && <SinAsignarBadge />}
            </div>

            <section>
              <h3 className="text-sm font-semibold text-slate-900">Descripción</h3>
              <p className="mt-1 whitespace-pre-line text-sm text-slate-700">
                {r.descripcion?.trim() || "Sin descripción."}
              </p>
            </section>

            <dl className="grid grid-cols-1 gap-x-6 gap-y-4 text-sm sm:grid-cols-2">
              <Dato titulo="Fecha del reporte" valor={formatearFecha(r.fecha)} />
              <Dato titulo="Fecha de resolución" valor={formatearFecha(r.fecha_resolucion)} />
              <Dato titulo="Servicio" valor={r.servicio?.tipo ?? "—"} capitalizar />
              <Dato titulo="Tipo de novedad" valor={r.tipo ?? "—"} />
              <Dato titulo="Ubicación" valor={ubicacionTexto(r)} />
              <Dato titulo="Código DANE" valor={r.ubicacion?.codigo_dane ?? "—"} />
            </dl>

            <section>
              <h3 className="mb-2 text-sm font-semibold text-slate-900">Ubicación en el mapa</h3>
              <div className="isolate h-64 overflow-hidden rounded-lg border border-slate-200">
                <MapaReporte reporte={r} />
              </div>
            </section>
          </div>
        )}
      </div>

      <footer className="border-t border-slate-200 px-6 py-4">
        <button
          type="button"
          disabled={!r || resuelto || cargando}
          onClick={() => setConfirmando(true)}
          className="w-full rounded-md bg-green-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-green-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-800"
        >
          {resuelto ? "Reporte ya resuelto" : "Marcar como resuelto"}
        </button>
      </footer>

      <ConfirmDialog
        abierto={confirmando}
        titulo="¿Marcar como resuelto?"
        mensaje="El reporte pasará a estado Resuelto y se registrará la fecha de resolución. Esta acción no se puede deshacer desde el panel."
        textoConfirmar="Sí, marcar como resuelto"
        procesando={resolviendo}
        onConfirmar={confirmarResolver}
        onCancelar={() => setConfirmando(false)}
      />
    </div>
  );
}

function Dato({ titulo, valor, capitalizar }: { titulo: string; valor: string; capitalizar?: boolean }) {
  return (
    <div>
      <dt className="text-slate-500">{titulo}</dt>
      <dd className={`mt-0.5 font-medium text-slate-900 ${capitalizar ? "first-letter:uppercase" : ""}`}>{valor}</dd>
    </div>
  );
}
