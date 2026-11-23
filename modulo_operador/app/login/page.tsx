"use client";

import { useEffect, useId, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { cerrarSesion, esEmailValido, iniciarSesion, obtenerOperador } from "@/lib/auth";
import { mensajeError } from "@/lib/reportes";

const MENSAJES: Record<string, string> = {
  "no-operador": "Este usuario no está registrado como operador. No tiene acceso al módulo.",
  error: "No se pudo verificar la sesión. Intenta de nuevo.",
};

export default function LoginPage() {
  const router = useRouter();
  const id = useId();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Se lee el motivo desde window.location (no useSearchParams) para no forzar un
  // límite de Suspense en esta página; solo se necesita una vez al montar.
  useEffect(() => {
    const motivo = new URLSearchParams(window.location.search).get("motivo");
    if (motivo && MENSAJES[motivo]) queueMicrotask(() => setError(MENSAJES[motivo]));
  }, []);

  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    if (!esEmailValido(email)) {
      setError("Ingresa un correo electrónico válido.");
      return;
    }
    if (!password) {
      setError("Ingresa tu contraseña.");
      return;
    }

    setEnviando(true);
    try {
      const { data, error: authError } = await iniciarSesion(email, password);
      if (authError || !data.user) {
        setError("Correo o contraseña incorrectos.");
        return;
      }
      // Tener cuenta no basta: el usuario debe existir en la tabla Operador.
      const operador = await obtenerOperador(data.user.id);
      if (!operador) {
        await cerrarSesion();
        setError(MENSAJES["no-operador"]);
        return;
      }
      router.replace("/");
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-dvh items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <p className="text-2xl font-bold tracking-tight text-slate-900">SERVI·RED</p>
          <p className="mt-1 text-sm text-slate-600">Módulo del Operador</p>
        </div>

        <form onSubmit={enviar} noValidate className="space-y-5 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <h1 className="text-lg font-semibold text-slate-900">Iniciar sesión</h1>

          {error && (
            <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
              {error}
            </p>
          )}

          <div>
            <label htmlFor={`${id}-email`} className="text-sm font-medium text-slate-700">
              Correo electrónico
            </label>
            <input
              id={`${id}-email`}
              type="email"
              autoComplete="email"
              placeholder="operador@empresa.com"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-slate-500 focus:outline-2 focus:outline-slate-400"
            />
          </div>

          <div>
            <label htmlFor={`${id}-pass`} className="text-sm font-medium text-slate-700">
              Contraseña
            </label>
            <input
              id={`${id}-pass`}
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-slate-500 focus:outline-2 focus:outline-slate-400"
            />
          </div>

          <button
            type="submit"
            disabled={enviando}
            className="w-full rounded-md bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
          >
            {enviando ? "Ingresando…" : "Ingresar"}
          </button>
        </form>
      </div>
    </main>
  );
}
