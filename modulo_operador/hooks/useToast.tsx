"use client";

import { createContext, useCallback, useContext, useState, type ReactNode } from "react";

type TipoToast = "exito" | "error";
interface Toast {
  id: number;
  tipo: TipoToast;
  mensaje: string;
}

const ToastContext = createContext<((tipo: TipoToast, mensaje: string) => void) | null>(null);

let siguienteId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const cerrar = useCallback((id: number) => {
    setToasts((t) => t.filter((x) => x.id !== id));
  }, []);

  const mostrar = useCallback(
    (tipo: TipoToast, mensaje: string) => {
      const id = siguienteId++;
      setToasts((t) => [...t, { id, tipo, mensaje }]);
      setTimeout(() => cerrar(id), 5000);
    },
    [cerrar],
  );

  return (
    <ToastContext.Provider value={mostrar}>
      {children}
      {/* aria-live para que los lectores de pantalla anuncien el aviso */}
      <div
        aria-live="polite"
        className="pointer-events-none fixed bottom-4 right-4 z-[2000] flex w-full max-w-sm flex-col gap-2"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.tipo === "error" ? "alert" : "status"}
            className={`pointer-events-auto flex items-start gap-3 rounded-lg border bg-white px-4 py-3 text-sm shadow-lg ${
              t.tipo === "exito" ? "border-green-300" : "border-red-300"
            }`}
          >
            <span
              aria-hidden
              className={`mt-0.5 inline-block size-2.5 shrink-0 rounded-full ${
                t.tipo === "exito" ? "bg-green-600" : "bg-red-600"
              }`}
            />
            <p className="flex-1 text-slate-800">
              <span className="font-semibold">{t.tipo === "exito" ? "Listo: " : "Error: "}</span>
              {t.mensaje}
            </p>
            <button
              type="button"
              onClick={() => cerrar(t.id)}
              className="rounded text-slate-500 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-slate-700"
              aria-label="Cerrar aviso"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast debe usarse dentro de <ToastProvider>");
  return ctx;
}
