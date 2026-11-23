// Nombres de tablas tal como existen en Postgres (sensibles a mayúsculas).
export const TABLAS = {
  falla: "tipo_falla",
  ubicacion: "Ubicacion",
  servicio: "Servicio",
  operador: "Operador",
} as const;

// SUPOSICIÓN: el estado final se guarda exactamente como "Resuelto" (lo usa el trigger)
// y cualquier otro estado ("Pendiente", "En proceso", ...) cuenta como pendiente.
export const ESTADO_RESUELTO = "Resuelto";

// `valor` debe coincidir con Servicio.tipo en la base 
export const SERVICIOS = [
  { valor: "Energia electrica", etiqueta: "energia" },
  { valor: "Gas natural", etiqueta: "gas" },
  { valor: "Acueducto", etiqueta: "Acueducto" },
  { valor: "Alcantarillado", etiqueta: "Alcantarillado" },
  { valor: "Aseo", etiqueta: "Aseo" },
] as const;

export const TAMANOS_PAGINA = [10, 20] as const;

export const esResuelto = (estado: string | null | undefined) =>
  (estado ?? "").trim().toLowerCase() === ESTADO_RESUELTO.toLowerCase();
