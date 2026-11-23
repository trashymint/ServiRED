import { getSupabase } from "./supabase";
import { ESTADO_RESUELTO, TABLAS, esResuelto } from "./constants";
import { inicioDia, inicioDiaSiguiente } from "./format";
import type { FiltrosReportes, Id, Reporte, ResumenOperador } from "@/types/database";

// Las consultas de reportes filtran por los id de Operador de la empresa del usuario
// (RPC `mis_operadores`, ver más abajo). tipo_falla.id_operador es FK a Operador y lo llena
// Supabase; queda NULL cuando no hay operador registrado para ese municipio.
// Listado, mapa, detalle, opciones de filtro y tarjetas de resumen usan el mismo universo:
// reportes de la empresa + reportes SIN operador (id_operador NULL).

// SUPOSICIÓN: existen FKs tipo_falla.id_ubicacion -> Ubicacion y tipo_falla.id_servicio -> Servicio,
// así PostgREST puede embeber las relaciones. Nunca se selecciona id_usuario (dato personal).
const COLUMNAS =
  "id_falla, fecha, fecha_resolucion, estado, tipo, descripcion, validado, id_operador, id_servicio, id_ubicacion, " +
  "ubicacion:Ubicacion(latitud, longitud, departamento, municipio, codigo_dane)";

/** Con filtro de servicio se usa !inner para que el filtro sobre la relación excluya filas. */
const seleccion = (filtrarServicio: boolean) =>
  `${COLUMNAS}, servicio:Servicio${filtrarServicio ? "!inner" : ""}(tipo)`;

export const FILTROS_VACIOS: FiltrosReportes = {
  estado: "",
  servicio: "",
  tipo: "",
  validado: "",
  desde: "",
  hasta: "",
};

// ---------------------------------------------------------------------------------------
// Filtro por operador: la función RPC `mis_operadores` (creada en Supabase) devuelve los id
// de todas las filas de Operador de la empresa del usuario. Todas las consultas de reportes
// se limitan con .in("id_operador", ids). Se guarda en memoria por usuario.
// ---------------------------------------------------------------------------------------
let cacheIds: { userId: string; ids: Id[] } | null = null;

export function limpiarCacheOperador() {
  cacheIds = null;
}

/** Acepta `returns setof bigint` ([1,2]), `returns table(id ...)` ([{id:1}]) o un escalar. */
function normalizarIds(data: unknown): Id[] {
  const lista = Array.isArray(data) ? data : data == null ? [] : [data];
  return lista
    .map((x) => {
      if (x && typeof x === "object") {
        const o = x as Record<string, unknown>;
        return o.id ?? o.id_operador ?? o.mis_operadores ?? Object.values(o)[0];
      }
      return x;
    })
    .filter((v): v is Id => typeof v === "number" || typeof v === "string");
}

export async function idsMiOperador(): Promise<Id[]> {
  const supabase = getSupabase();
  const { data: sesion } = await supabase.auth.getSession();
  const userId = sesion.session?.user.id;
  if (!userId) return [];
  if (cacheIds?.userId === userId) return cacheIds.ids;

  const { data, error } = await supabase.rpc("mis_operadores");
  if (error) throw error;
  const ids = normalizarIds(data);
  cacheIds = { userId, ids };
  return ids;
}

/** Filtro PostgREST: id_operador en la lista de la empresa O id_operador vacío (NULL). */
function miosOSinAsignar(ids: Id[]): string {
  const sinAsignar = "id_operador.is.null";
  return ids.length > 0 ? `id_operador.in.(${ids.join(",")}),${sinAsignar}` : sinAsignar;
}

function consultaFiltrada(f: FiltrosReportes, conteo: boolean, ids: Id[]) {
  let q = getSupabase()
    .from(TABLAS.falla)
    .select(seleccion(Boolean(f.servicio)), conteo ? { count: "exact" } : undefined)
    .or(miosOSinAsignar(ids));

  if (f.estado) q = q.eq("estado", f.estado);
  if (f.tipo) q = q.eq("tipo", f.tipo);
  // ilike sin comodines = igualdad sin distinguir mayúsculas
  if (f.servicio) q = q.ilike("servicio.tipo", f.servicio);
  if (f.validado === "si") q = q.eq("validado", true);
  // SUPOSICIÓN: validado = null se trata como "no validado". "IS NOT TRUE" incluye false y
  // NULL, y evita un segundo .or() que se mezclaría con el filtro de operador.
  if (f.validado === "no") q = q.not("validado", "is", true);
  if (f.desde) q = q.gte("fecha", inicioDia(f.desde));
  if (f.hasta) q = q.lt("fecha", inicioDiaSiguiente(f.hasta));

  return q.order("fecha", { ascending: false });
}

export function mensajeError(e: unknown): string {
  if (e && typeof e === "object" && "message" in e && typeof e.message === "string") {
    return e.message;
  }
  return "Ocurrió un error inesperado.";
}

export async function listarReportes(
  filtros: FiltrosReportes,
  pagina: number,
  tamano: number,
): Promise<{ filas: Reporte[]; total: number }> {
  const desde = (pagina - 1) * tamano;
  const ids = await idsMiOperador();
  const { data, error, count } = await consultaFiltrada(filtros, true, ids).range(
    desde,
    desde + tamano - 1,
  );
  if (error) throw error;
  return { filas: (data ?? []) as unknown as Reporte[], total: count ?? 0 };
}

/** Para el mapa general. SUPOSICIÓN: con 2000 marcadores basta; para más, usar clustering. */
export async function listarReportesMapa(filtros: FiltrosReportes): Promise<Reporte[]> {
  const ids = await idsMiOperador();
  const { data, error } = await consultaFiltrada(filtros, false, ids).limit(2000);
  if (error) throw error;
  return (data ?? []) as unknown as Reporte[];
}

export async function obtenerReporte(id: Id): Promise<Reporte | null> {
  const ids = await idsMiOperador();
  const { data, error } = await getSupabase()
    .from(TABLAS.falla)
    .select(seleccion(false))
    .eq("id_falla", id)
    .or(miosOSinAsignar(ids))
    .maybeSingle();
  if (error) throw error;
  return data as unknown as Reporte | null;
}

/**
 * Cambia el estado mediante la función RPC (nunca un update directo).
 * Después confirma en la base que el reporte quedó "Resuelto": si la función terminó sin
 * error pero no actualizó la fila (p. ej. porque solo acepta reportes con operador, o el
 * RLS bloqueó el UPDATE), se informa en vez de fingir éxito.
 */
export async function resolverReporte(id: Id): Promise<void> {
  const supabase = getSupabase();
  const { error } = await supabase.rpc("resolver_reporte", { p_id_falla: id });
  if (error) throw error;

  const { data, error: errorLectura } = await supabase
    .from(TABLAS.falla)
    .select("estado")
    .eq("id_falla", id)
    .maybeSingle();
  if (errorLectura) throw errorLectura;
  if (!esResuelto(data?.estado)) {
    throw new Error(
      "La base no cambió el estado del reporte. Revisa que la función resolver_reporte " +
        "acepte este reporte (incluidos los que no tienen operador) y pueda hacer el UPDATE " +
        "(security definer). Ver docs/supabase-referencia.sql.",
    );
  }
}

export async function obtenerResumen(): Promise<ResumenOperador> {
  const supabase = getSupabase();
  const ids = await idsMiOperador();
  // Mismo universo que el listado: reportes de la empresa + reportes sin operador.
  const conteo = () =>
    supabase
      .from(TABLAS.falla)
      .select("id_falla", { count: "exact", head: true })
      .or(miosOSinAsignar(ids));

  const [total, resueltos] = await Promise.all([
    conteo(),
    conteo().eq("estado", ESTADO_RESUELTO),
  ]);
  if (total.error) throw total.error;
  if (resueltos.error) throw resueltos.error;

  let sumaHoras = 0;
  let n = 0;
  const BLOQUE = 1000;
  for (let desde = 0; desde < 20 * BLOQUE; desde += BLOQUE) {
    const { data, error } = await supabase
      .from(TABLAS.falla)
      .select("fecha, fecha_resolucion")
      .or(miosOSinAsignar(ids))
      .eq("estado", ESTADO_RESUELTO)
      .not("fecha_resolucion", "is", null)
      .order("id_falla")
      .range(desde, desde + BLOQUE - 1);
    if (error) throw error;
    for (const fila of data ?? []) {
      const ms = Date.parse(fila.fecha_resolucion) - Date.parse(fila.fecha);
      if (Number.isFinite(ms) && ms >= 0) {
        sumaHoras += ms / 3_600_000;
        n++;
      }
    }
    if (!data || data.length < BLOQUE) break;
  }

  const t = total.count ?? 0;
  const r = resueltos.count ?? 0;
  return {
    total: t,
    resueltos: r,
    pendientes: Math.max(t - r, 0),
    promedioHoras: n > 0 ? sumaHoras / n : null,
  };
}

/** Valores distintos de estado y tipo de novedad para los selectores de filtro. */
export async function obtenerOpcionesFiltro(): Promise<{ estados: string[]; tipos: string[] }> {
  const ids = await idsMiOperador();
  const { data, error } = await getSupabase()
    .from(TABLAS.falla)
    .select("estado, tipo")
    .or(miosOSinAsignar(ids))
    .limit(1000);
  if (error) throw error;
  const estados = new Set<string>([ESTADO_RESUELTO]);
  const tipos = new Set<string>();
  for (const fila of data ?? []) {
    if (fila.estado) estados.add(fila.estado);
    if (fila.tipo) tipos.add(fila.tipo);
  }
  const orden = (a: string, b: string) => a.localeCompare(b, "es");
  return { estados: [...estados].sort(orden), tipos: [...tipos].sort(orden) };
}
