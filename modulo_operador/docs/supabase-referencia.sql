do $$
declare f regprocedure;
begin
  for f in select p.oid::regprocedure from pg_proc p
           join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname = 'resolver_reporte'
  loop
    execute 'drop function ' || f;
  end loop;
end $$;

create function public.resolver_reporte(p_id_falla bigint)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Debes iniciar sesión';
  end if;

  update public.tipo_falla
     set estado = 'Resuelto'
   where id_falla = p_id_falla
     and estado is distinct from 'Resuelto'
     and (
       id_operador is null
       or id_operador in (select * from public.mis_operadores())
     );

  if not found then
    raise exception 'El reporte no existe, no es de tu empresa o ya está resuelto';
  end if;
end;
$$;

revoke all on function public.resolver_reporte(bigint) from public, anon;
grant execute on function public.resolver_reporte(bigint) to authenticated;

notify pgrst, 'reload schema';


