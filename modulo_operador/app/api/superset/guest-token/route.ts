import { createClient } from "@supabase/supabase-js";
import { TABLAS } from "@/lib/constants";

/**
 * POST /api/superset/guest-token
 * Emite un "guest token" de Superset para el operador autenticado.
 *
 * 1. Valida el access token de Supabase que envía el navegador.
 * 2. Con el JWT del usuario busca su Operador (por auth_id) y llama la RPC `mis_operadores`
 *    para obtener todos los id de Operador de su empresa.
 * 3. Inicia sesión en Superset con un usuario de servicio (credenciales solo en el servidor).
 * 4. Pide el guest token con una regla RLS "<columna> IN ('<id1>', '<id2>', ...)" para que
 *    el dashboard solo muestre datos de esa empresa, aunque Superset lea la base completa.
 */

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL;
const SUPABASE_KEY = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
const SUPERSET_URL = process.env.SUPERSET_INTERNAL_URL ?? process.env.NEXT_PUBLIC_SUPERSET_URL;
const SUPERSET_USER = process.env.SUPERSET_USERNAME;
const SUPERSET_PASS = process.env.SUPERSET_PASSWORD;
const RLS_COLUMN = process.env.SUPERSET_RLS_COLUMN ?? "id_operador";

// Solo se emiten tokens para los dashboards configurados.
const DASHBOARDS_PERMITIDOS = [
  process.env.NEXT_PUBLIC_SUPERSET_DASHBOARD_ID,
  process.env.NEXT_PUBLIC_SUPERSET_DASHBOARD_MAPA_ID,
].filter((x): x is string => Boolean(x));

const error = (status: number, mensaje: string) => Response.json({ error: mensaje }, { status });

export async function POST(request: Request) {
  if (!SUPABASE_URL || !SUPABASE_KEY || !SUPERSET_URL || !SUPERSET_USER || !SUPERSET_PASS) {
    return error(500, "Faltan variables de entorno de Supabase o Superset en el servidor.");
  }

  const auth = request.headers.get("authorization") ?? "";
  const jwt = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!jwt) return error(401, "No autenticado.");

  const body = (await request.json().catch(() => ({}))) as { dashboardId?: string };
  if (!body.dashboardId || !DASHBOARDS_PERMITIDOS.includes(body.dashboardId)) {
    return error(400, "Dashboard no permitido.");
  }

  // --- 1 y 2: validar usuario y obtener su operador (con RLS) ---
  const supabase = createClient(SUPABASE_URL, SUPABASE_KEY, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: userData, error: userError } = await supabase.auth.getUser(jwt);
  if (userError || !userData.user) return error(401, "Sesión inválida.");

  // Operador.auth_id = auth.users.id (igual que lib/auth.ts). Puede haber varias filas por
  // empresa (una por municipio), por eso se toma la primera solo para el nombre.
  const { data: operador } = await supabase
    .from(TABLAS.operador)
    .select('id, "NOMBRE"')
    .eq("auth_id", userData.user.id)
    .order("id")
    .limit(1)
    .maybeSingle();
  if (!operador) return error(403, "El usuario no es un operador.");

  // Todos los id de Operador de la empresa (misma RPC que usa la web en lib/reportes.ts).
  const { data: idsData, error: idsError } = await supabase.rpc("mis_operadores");
  if (idsError) return error(500, `No se pudieron obtener los operadores: ${idsError.message}`);
  const ids = (Array.isArray(idsData) ? idsData : [idsData])
    .map((x) =>
      x && typeof x === "object"
        ? ((x as Record<string, unknown>).id ?? Object.values(x as Record<string, unknown>)[0])
        : x,
    )
    .filter((v) => v !== null && v !== undefined)
    .map(String);
  if (ids.length === 0) return error(403, "La empresa no tiene operadores asociados.");

  // Defensa contra inyección en la cláusula SQL del RLS.
  if (ids.some((id) => !/^[A-Za-z0-9-]+$/.test(id)) || !/^[A-Za-z_][A-Za-z0-9_]*$/.test(RLS_COLUMN)) {
    return error(500, "Identificador de operador o columna RLS inválidos.");
  }
  const idOperador = String(operador.id);
  const listaIds = ids.map((id) => `'${id}'`).join(", ");

  try {
    // --- 3: login en Superset con el usuario de servicio ---
    const login = await fetch(`${SUPERSET_URL}/api/v1/security/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: SUPERSET_USER, password: SUPERSET_PASS, provider: "db", refresh: false }),
      cache: "no-store",
    });
    if (!login.ok) return error(502, `Superset rechazó el login (${login.status}).`);
    const { access_token } = (await login.json()) as { access_token: string };

    // Superset puede exigir CSRF en el endpoint de guest token: se pide el token y la cookie.
    const csrfRes = await fetch(`${SUPERSET_URL}/api/v1/security/csrf_token/`, {
      headers: { Authorization: `Bearer ${access_token}` },
      cache: "no-store",
    });
    const csrf = csrfRes.ok ? ((await csrfRes.json()) as { result: string }).result : undefined;
    const cookie = csrfRes.headers
      .getSetCookie()
      .map((c) => c.split(";")[0])
      .join("; ");

    // --- 4: guest token con RLS por operador ---
    const guest = await fetch(`${SUPERSET_URL}/api/v1/security/guest_token/`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${access_token}`,
        Referer: SUPERSET_URL,
        ...(csrf ? { "X-CSRFToken": csrf } : {}),
        ...(cookie ? { Cookie: cookie } : {}),
      },
      body: JSON.stringify({
        user: {
          username: `operador_${idOperador}`,
          first_name: String(operador.NOMBRE ?? "Operador"),
          last_name: "",
        },
        resources: [{ type: "dashboard", id: body.dashboardId }],
        // Sin "dataset": aplica a todos los datasets del dashboard (todos deben tener la columna).
        // Igual que la web: reportes de la empresa + reportes sin operador (NULL).
        rls: [{ clause: `(${RLS_COLUMN} IN (${listaIds}) OR ${RLS_COLUMN} IS NULL)` }],
      }),
      cache: "no-store",
    });
    if (!guest.ok) {
      const detalle = await guest.text();
      return error(502, `Superset no emitió el token (${guest.status}): ${detalle.slice(0, 200)}`);
    }
    const { token } = (await guest.json()) as { token: string };
    return Response.json({ token });
  } catch (e) {
    return error(502, `No se pudo contactar a Superset en ${SUPERSET_URL}: ${(e as Error).message}`);
  }
}
