"use client";

// Mapas con Leaflet "puro" (sin react-leaflet). Next 16 conserva las páginas ocultas y
// vuelve a ejecutar sus efectos al mostrarlas; react-leaflet reutiliza un mapa ya destruido
// y falla ("this.getPane() is undefined"). Aquí cada efecto crea su mapa y lo destruye al
// limpiar, así funciona con StrictMode, <Activity> y paneles que se abren/cierran.
// Este archivo usa `window`, por eso se carga con next/dynamic y ssr:false (ver ../index.tsx).
import "leaflet/dist/leaflet.css";
import * as L from "leaflet";
import { useEffect, useEffectEvent, useRef } from "react";
import { esResuelto } from "@/lib/constants";
import { aNumero, formatearFecha } from "@/lib/format";
import type { Id, Reporte } from "@/types/database";

// Se usan circleMarker (vectoriales) para no depender de las imágenes de íconos de Leaflet.
export const COLOR_PENDIENTE = "#d97706"; // ámbar
export const COLOR_RESUELTO = "#15803d"; // verde

const CENTRO_COLOMBIA: L.LatLngTuple = [4.5709, -74.2973];

export function coordenadas(r: Pick<Reporte, "ubicacion">): L.LatLngTuple | null {
  const lat = aNumero(r.ubicacion?.latitud);
  const lng = aNumero(r.ubicacion?.longitud);
  if (lat === null || lng === null) return null;
  return [lat, lng];
}

const colorEstado = (estado: string | null) => (esResuelto(estado) ? COLOR_RESUELTO : COLOR_PENDIENTE);

/** Escapa texto antes de ponerlo en el HTML de un tooltip. */
function escapar(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

/** Crea un mapa con teselas de OpenStreetMap y lo destruye al desmontar. */
function useMapaLeaflet(centro: L.LatLngTuple, zoom: number, opciones?: L.MapOptions) {
  const contenedor = useRef<HTMLDivElement>(null);
  const mapa = useRef<L.Map | null>(null);
  const crear = useEffectEvent(() => {
    const m = L.map(contenedor.current!, { center: centro, zoom, ...opciones });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19,
    }).addTo(m);
    return m;
  });

  useEffect(() => {
    if (!contenedor.current) return;
    const m = crear();
    mapa.current = m;
    // Recalcula el tamaño cuando el contenedor cambia (paneles animados, cambios de ventana).
    const observador = new ResizeObserver(() => m.invalidateSize());
    observador.observe(contenedor.current);
    return () => {
      observador.disconnect();
      m.remove();
      mapa.current = null;
    };
  }, []);

  return { contenedor, mapa };
}

export function MapaReporte({ reporte }: { reporte: Reporte }) {
  const punto = coordenadas(reporte);
  const { contenedor, mapa } = useMapaLeaflet(punto ?? CENTRO_COLOMBIA, punto ? 15 : 6, {
    scrollWheelZoom: false,
  });
  const clave = punto ? punto.join(",") : "";
  const color = colorEstado(reporte.estado);
  const etiqueta = reporte.tipo ?? "Reporte";

  // Este efecto va después del de creación del mapa, así que mapa.current ya existe.
  useEffect(() => {
    const m = mapa.current;
    if (!m || !clave) return;
    const p = clave.split(",").map(Number) as L.LatLngTuple;
    const marcador = L.circleMarker(p, {
      radius: 10,
      color,
      fillColor: color,
      fillOpacity: 0.7,
      weight: 2,
    })
      .bindTooltip(escapar(etiqueta))
      .addTo(m);
    m.setView(p, 15);
    return () => {
      marcador.remove();
    };
  }, [mapa, clave, color, etiqueta]);

  return (
    <div className="relative h-full w-full">
      <div ref={contenedor} className="h-full w-full" />
      {!punto && (
        <p className="absolute inset-x-0 top-3 z-[1000] mx-auto w-fit rounded bg-white/90 px-3 py-1 text-sm text-slate-700 shadow">
          Este reporte no tiene coordenadas.
        </p>
      )}
    </div>
  );
}

export function MapaReportes({
  reportes,
  onSeleccionar,
}: {
  reportes: Reporte[];
  onSeleccionar: (id: Id) => void;
}) {
  const { contenedor, mapa } = useMapaLeaflet(CENTRO_COLOMBIA, 6);
  const seleccionar = useEffectEvent((id: Id) => onSeleccionar(id));

  useEffect(() => {
    const m = mapa.current;
    if (!m) return;

    const capa = L.layerGroup().addTo(m);
    const puntos: L.LatLngTuple[] = [];

    for (const r of reportes) {
      const p = coordenadas(r);
      if (!p) continue;
      puntos.push(p);
      L.circleMarker(p, {
        radius: 8,
        color: "#ffffff",
        fillColor: colorEstado(r.estado),
        fillOpacity: 0.9,
        weight: 1.5,
      })
        .bindTooltip(
          `<strong>${escapar(r.tipo ?? "Reporte")}</strong><br>${escapar(r.estado ?? "")} · ${formatearFecha(r.fecha)}`,
        )
        .on("click", () => seleccionar(r.id_falla))
        .addTo(capa);
    }

    if (puntos.length === 1) m.setView(puntos[0], 13);
    else if (puntos.length > 1) m.fitBounds(puntos, { padding: [40, 40], maxZoom: 14 });

    return () => {
      capa.remove();
    };
  }, [mapa, reportes]);

  return <div ref={contenedor} className="h-full w-full" />;
}
