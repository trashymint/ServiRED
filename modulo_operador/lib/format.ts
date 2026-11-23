// Las fechas se guardan con hora de Colombia "marcada como UTC", por eso se leen con los
// getters UTC: así se muestra exactamente la hora almacenada, sin conversión de zona.

const dos = (n: number) => String(n).padStart(2, "0");

/** dd/MM/yyyy HH:mm */
export function formatearFecha(valor: string | null | undefined): string {
  if (!valor) return "—";
  const d = new Date(valor);
  if (Number.isNaN(d.getTime())) return "—";
  return `${dos(d.getUTCDate())}/${dos(d.getUTCMonth() + 1)}/${d.getUTCFullYear()} ${dos(
    d.getUTCHours(),
  )}:${dos(d.getUTCMinutes())}`;
}

export function formatearHoras(horas: number | null): string {
  if (horas === null) return "—";
  return `${horas.toLocaleString("es-CO", { maximumFractionDigits: 1 })} h`;
}

export function formatearNumero(n: number): string {
  return n.toLocaleString("es-CO");
}

/** Convierte "yyyy-MM-dd" al inicio de ese día (en la misma convención "UTC" de la base). */
export function inicioDia(fecha: string): string {
  return `${fecha}T00:00:00Z`;
}

/** Inicio del día siguiente, para usar como límite exclusivo (lt). */
export function inicioDiaSiguiente(fecha: string): string {
  const d = new Date(`${fecha}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString();
}

export function aNumero(v: number | string | null | undefined): number | null {
  if (v === null || v === undefined || v === "") return null;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}
