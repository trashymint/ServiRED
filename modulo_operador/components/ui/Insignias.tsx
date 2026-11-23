import { esResuelto } from "@/lib/constants";

// Los estados siempre muestran texto además de color (no se depende solo del color).

export function EstadoBadge({ estado }: { estado: string | null }) {
  const resuelto = esResuelto(estado);
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium ${
        resuelto
          ? "border-green-200 bg-green-50 text-green-800"
          : "border-amber-200 bg-amber-50 text-amber-800"
      }`}
    >
      <span
        aria-hidden
        className={`size-1.5 rounded-full ${resuelto ? "bg-green-600" : "bg-amber-500"}`}
      />
      {estado || "Sin estado"}
    </span>
  );
}

/** Reporte con id_operador NULL: el municipio no tiene operador registrado. */
export function SinAsignarBadge() {
  return (
    <span className="inline-flex items-center rounded-full border border-dashed border-slate-400 bg-white px-2.5 py-0.5 text-xs font-medium text-slate-700">
      Sin operador asignado
    </span>
  );
}

export function ValidadoBadge({ validado }: { validado: boolean | null }) {
  return validado ? (
    <span className="inline-flex items-center gap-1 rounded-full border border-sky-200 bg-sky-50 px-2.5 py-0.5 text-xs font-medium text-sky-800">
      <span aria-hidden>✓</span> Validado
    </span>
  ) : (
    <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-xs font-medium text-slate-600">
      Sin validar
    </span>
  );
}
