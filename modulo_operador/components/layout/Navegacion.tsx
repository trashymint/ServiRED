"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth, useOperador } from "@/hooks/useAuth";

const ENLACES = [
  { href: "/", texto: "Resumen" },
  { href: "/reportes", texto: "Reportes" },
  { href: "/mapa", texto: "Mapa" },
];

function Enlaces({ vertical }: { vertical: boolean }) {
  const ruta = usePathname();
  return (
    <ul className={vertical ? "space-y-1" : "flex gap-1"}>
      {ENLACES.map((e) => {
        const activo = e.href === "/" ? ruta === "/" : ruta.startsWith(e.href);
        return (
          <li key={e.href}>
            <Link
              href={e.href}
              aria-current={activo ? "page" : undefined}
              className={`block rounded-md px-3 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-700 ${
                activo ? "bg-slate-900 text-white" : "text-slate-700 hover:bg-slate-100"
              }`}
            >
              {e.texto}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

function Marca() {
  return (
    <div>
      <p className="text-base font-bold tracking-tight text-slate-900">SERVI·RED</p>
      <p className="text-xs text-slate-500">Módulo del Operador</p>
    </div>
  );
}

function BotonSalir() {
  const { salir } = useAuth();
  return (
    <button
      type="button"
      onClick={salir}
      className="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-700"
    >
      Cerrar sesión
    </button>
  );
}

/** Barra lateral en escritorio (lg+) y encabezado con navegación en tablet/móvil. */
export function Navegacion() {
  const operador = useOperador();
  const nombre = operador.NOMBRE ?? "Operador";
  const zona = [operador.MUNICIPIO_PRESTACION, operador.DEPARTAMENTO_PRESTACION].filter(Boolean).join(", ");

  return (
    <>
      <aside className="fixed inset-y-0 left-0 hidden w-64 flex-col border-r border-slate-200 bg-white px-4 py-6 lg:flex">
        <Marca />
        <nav aria-label="Principal" className="mt-8 flex-1">
          <Enlaces vertical />
        </nav>
        <div className="border-t border-slate-200 pt-4">
          <p className="text-sm font-semibold text-slate-900">{nombre}</p>
          {operador.SERVICIO && <p className="text-xs text-slate-600 first-letter:uppercase">{operador.SERVICIO}</p>}
          {zona && <p className="text-xs text-slate-500">{zona}</p>}
          <div className="mt-3">
            <BotonSalir />
          </div>
        </div>
      </aside>

      <header className="sticky top-0 z-[1100] border-b border-slate-200 bg-white/95 backdrop-blur lg:hidden">
        <div className="flex items-center justify-between gap-4 px-4 py-3">
          <Marca />
          <div className="flex items-center gap-3">
            <p className="hidden text-sm font-medium text-slate-700 sm:block">{nombre}</p>
            <BotonSalir />
          </div>
        </div>
        <nav aria-label="Principal" className="overflow-x-auto px-4 pb-2">
          <Enlaces vertical={false} />
        </nav>
      </header>
    </>
  );
}
