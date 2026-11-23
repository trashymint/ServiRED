# SERVI·RED · Módulo del Operador

Panel web para que los operadores de servicios públicos domiciliarios (energía, gas,
acueducto, alcantarillado, aseo) monitoreen y resuelvan los reportes de fallas de su zona.

**Stack:** Next.js 16 (App Router) + React 19 + TypeScript, Tailwind CSS 4, Supabase
(`@supabase/supabase-js`), Leaflet + OpenStreetMap, Apache Superset embebido (opcional).

> El enunciado pedía Vite + React Router; este proyecto usa la base de Next.js, así que el
> enrutamiento es por carpetas de `app/` y las variables `VITE_*` pasan a ser `NEXT_PUBLIC_*`.

## Instalación

```bash
npm install
cp .env.example .env.local   # en Windows: copy .env.example .env.local
npm run dev                  # http://localhost:3000
```

## Variables de entorno (`.env.local`)

| Variable | Obligatoria | Descripción |
|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | Sí | URL del proyecto Supabase. |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | Sí | Publishable key (`sb_publishable_...`). **Nunca** la secret key. |
| `NEXT_PUBLIC_SUPERSET_URL` | No | URL de Superset vista por el navegador (`http://localhost:8088`). |
| `NEXT_PUBLIC_SUPERSET_DASHBOARD_ID` | No | UUID *embedded* del dashboard de estadísticas (Resumen). |
| `NEXT_PUBLIC_SUPERSET_DASHBOARD_MAPA_ID` | No | UUID *embedded* de un dashboard de mapas (página Mapa). |
| `SUPERSET_INTERNAL_URL`, `SUPERSET_USERNAME`, `SUPERSET_PASSWORD`, `SUPERSET_RLS_COLUMN` | No | Solo servidor: usuario de servicio para emitir guest tokens. |

Guía completa de Superset: [docs/SUPERSET.md](docs/SUPERSET.md).
SQL de referencia (RPC `resolver_reporte` y políticas RLS): [docs/supabase-referencia.sql](docs/supabase-referencia.sql).

## Estructura

```
app/
  layout.tsx                    layout raíz (es-CO)
  login/page.tsx                inicio de sesión (correo y contraseña)
  (panel)/layout.tsx            rutas protegidas: AuthProvider, guardia, navegación
  (panel)/page.tsx              Resumen (tarjetas + Superset)
  (panel)/reportes/page.tsx     tabla, filtros, paginación, detalle
  (panel)/mapa/page.tsx         mapa general Leaflet (+ Superset opcional)
  api/superset/guest-token/     emite guest tokens de Superset con RLS por operador
components/                     UI (reportes/, mapa/, layout/, ui/)
hooks/                          useAuth, useConsulta, useReportes, useToast
lib/                            supabase.ts, auth.ts, reportes.ts, format.ts, constants.ts
types/database.ts               tipos de las tablas
```

## Decisiones y suposiciones

- **Seguridad:** el frontend no filtra por operador; confía en el RLS de Supabase. Nunca se
  consulta `id_usuario` ni datos personales.
- **Login:** correo y contraseña con Supabase Auth (`signInWithPassword`). Supabase valida la
  contraseña contra su hash; la app solo recibe el token de sesión.
- **Operador:** se busca por `Operador.auth_id` = `auth.users.id`. Si el usuario no tiene fila en `Operador`, se
  cierra la sesión y se muestra un aviso.
- **Reportes sin operador:** `tipo_falla.id_operador` (FK a `Operador`, lo asigna Supabase)
  puede ser `NULL` si el municipio no tiene operador registrado. Esos reportes se muestran a
  todos los operadores (listado, mapa, tarjetas y Superset) con la insignia "Sin operador
  asignado", y se pueden resolver si `resolver_reporte` lo permite (ver el SQL de referencia).
- **Estados:** `"Resuelto"` es el estado final; cualquier otro cuenta como pendiente (ámbar).
- **Fechas:** se guardan con hora de Colombia marcada como UTC, por eso se muestran con los
  getters UTC (`dd/MM/yyyy HH:mm`) sin conversión. La fecha de resolución la pone el trigger.
- **Resolver:** siempre vía `supabase.rpc('resolver_reporte', { p_id_falla })`.
- **Servicios:** el filtro compara `Servicio.tipo` (sin distinguir mayúsculas) con los valores
  de `lib/constants.ts`; energía se guarda como `"Energia electrica"`.
- **Promedio de resolución:** se calcula en el cliente leyendo en bloques los reportes
  resueltos; con mucho volumen conviene una vista o RPC agregada.
