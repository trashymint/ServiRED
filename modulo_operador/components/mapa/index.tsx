"use client";

import dynamic from "next/dynamic";
import { Skeleton } from "../ui/Estados";

// Leaflet necesita `window`: se carga únicamente en el navegador.
const cargando = () => <Skeleton className="h-full w-full rounded-none" />;

export const MapaReporte = dynamic(
  () => import("./leaflet/MapasLeaflet").then((m) => m.MapaReporte),
  { ssr: false, loading: cargando },
);

export const MapaReportes = dynamic(
  () => import("./leaflet/MapasLeaflet").then((m) => m.MapaReportes),
  { ssr: false, loading: cargando },
);

export function LeyendaMapa() {
  return (
    <ul className="flex flex-wrap items-center gap-4 text-sm text-slate-700" aria-label="Leyenda del mapa">
      <li className="flex items-center gap-2">
        <span aria-hidden className="inline-block size-3 rounded-full" style={{ background: "#d97706" }} />
        Pendiente
      </li>
      <li className="flex items-center gap-2">
        <span aria-hidden className="inline-block size-3 rounded-full" style={{ background: "#15803d" }} />
        Resuelto
      </li>
    </ul>
  );
}
