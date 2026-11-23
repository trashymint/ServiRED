"use client";

import { useConsulta } from "./useConsulta";
import { listarReportes, listarReportesMapa, obtenerOpcionesFiltro } from "@/lib/reportes";
import type { FiltrosReportes } from "@/types/database";

export function useReportes(filtros: FiltrosReportes, pagina: number, tamano: number, version: number) {
  return useConsulta(
    () => listarReportes(filtros, pagina, tamano),
    JSON.stringify({ filtros, pagina, tamano, version }),
  );
}

export function useReportesMapa(filtros: FiltrosReportes, version: number) {
  return useConsulta(() => listarReportesMapa(filtros), JSON.stringify({ filtros, version, mapa: 1 }));
}

export function useOpcionesFiltro() {
  return useConsulta(obtenerOpcionesFiltro, "opciones-filtro");
}
