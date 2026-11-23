export type Id = number | string;

export interface TipoFalla {
  id_falla: Id;
  fecha: string;
  fecha_resolucion: string | null;
  estado: string;
  tipo: string | null;
  descripcion: string | null;
  validado: boolean | null;
  id_operador: Id | null;
  id_usuario?: Id | null;
  id_servicio: Id | null;
  id_ubicacion: Id | null;
}

export interface Ubicacion {
  id_ubicacion: Id;
  latitud: number | string | null;
  longitud: number | string | null;
  departamento: string | null;
  municipio: string | null;
  codigo_dane: string | null;
}

export interface Servicio {
  id_servicio: Id;
  tipo: string;
  id_ubicacion: Id | null;
}

export interface Operador {
  id: string;
  NOMBRE: string | null;
  DEPARTAMENTO_PRESTACION: string | null;
  MUNICIPIO_PRESTACION: string | null;
  SERVICIO: string | null;
}

export interface Reporte
  extends Pick<
    TipoFalla,
    | "id_falla"
    | "fecha"
    | "fecha_resolucion"
    | "estado"
    | "tipo"
    | "descripcion"
    | "validado"
    | "id_operador"
    | "id_servicio"
    | "id_ubicacion"
  > {
  servicio: Pick<Servicio, "tipo"> | null;
  ubicacion: Pick<
    Ubicacion,
    "latitud" | "longitud" | "departamento" | "municipio" | "codigo_dane"
  > | null;
}

export interface FiltrosReportes {
  estado: string;
  servicio: string;
  tipo: string;
  validado: "" | "si" | "no";
  /** yyyy-MM-dd (input type=date) */
  desde: string;
  hasta: string;
}

export interface ResumenOperador {
  total: number;
  pendientes: number;
  resueltos: number;
  /** null si aún no hay reportes resueltos */
  promedioHoras: number | null;
}
