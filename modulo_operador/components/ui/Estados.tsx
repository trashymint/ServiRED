// Estados genéricos de la interfaz: carga (skeleton), error y vacío.

export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden className={`animate-pulse rounded bg-slate-200 ${className}`} />;
}

export function ErrorEstado({
  mensaje,
  onReintentar,
  titulo = "No se pudieron cargar los datos",
}: {
  mensaje: string;
  onReintentar?: () => void;
  titulo?: string;
}) {
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-6 text-center">
      <p className="font-semibold text-red-900">{titulo}</p>
      <p className="mt-1 text-sm text-red-800">{mensaje}</p>
      {onReintentar && (
        <button
          type="button"
          onClick={onReintentar}
          className="mt-4 rounded-md border border-red-300 bg-white px-4 py-2 text-sm font-medium text-red-900 hover:bg-red-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-700"
        >
          Reintentar
        </button>
      )}
    </div>
  );
}

export function VacioEstado({
  mensaje = "No hay reportes con estos filtros",
  children,
}: {
  mensaje?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 bg-white p-10 text-center">
      <p className="font-medium text-slate-700">{mensaje}</p>
      {children && <div className="mt-3">{children}</div>}
    </div>
  );
}
