"use client";

import { useCallback, useEffect, useEffectEvent, useState } from "react";
import { mensajeError } from "@/lib/reportes";

interface Estado<T> {
  /** Clave de la petición que produjo este resultado. */
  clave: string | null;
  data: T | null;
  error: string | null;
}

/**
 * Ejecuta una consulta asíncrona cada vez que cambia `clave` (o al llamar `recargar`).
 * `cargando` se deriva comparando la clave pedida con la última respondida, así se
 * descartan respuestas viejas si el usuario cambia filtros rápido.
 */
export function useConsulta<T>(consulta: () => Promise<T>, clave: string) {
  const [version, setVersion] = useState(0);
  const [estado, setEstado] = useState<Estado<T>>({ clave: null, data: null, error: null });
  const clavePedida = `${clave}#${version}`;

  const ejecutar = useEffectEvent(consulta);

  useEffect(() => {
    let vigente = true;
    ejecutar()
      .then((data) => {
        if (vigente) setEstado({ clave: clavePedida, data, error: null });
      })
      .catch((e: unknown) => {
        if (vigente) setEstado((prev) => ({ clave: clavePedida, data: prev.data, error: mensajeError(e) }));
      });
    return () => {
      vigente = false;
    };
  }, [clavePedida]);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  return {
    data: estado.data,
    error: estado.clave === clavePedida ? estado.error : null,
    cargando: estado.clave !== clavePedida,
    recargar,
  };
}
