-- Every future sync apply RPC that allocates from the shared sync cursor must
-- acquire this same transaction-level advisory lock immediately before
-- allocating its change_seq. The lock is held through transaction completion,
-- so sequence allocation order cannot overtake commit visibility order.
create or replace function public.apply_inventory_area_sync(
    p_operation_id uuid,
    p_restaurant_id uuid,
    p_entity_id uuid,
    p_base_server_version bigint,
    p_name text,
    p_normalized_name text,
    p_sort_order integer,
    p_is_active boolean,
    p_created_at timestamptz,
    p_updated_at timestamptz,
    p_deleted_at timestamptz
)
returns table (
    status text,
    entity_id uuid,
    server_version bigint,
    change_seq bigint,
    current_server_version bigint,
    current_change_seq bigint
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid := auth.uid();
    applied_operation public.sync_applied_operations%rowtype;
    current_area public.inventory_areas%rowtype;
    next_server_version bigint;
    next_change_seq bigint;
begin
    if current_user_id is null then
        raise exception 'Authentication is required' using errcode = '42501';
    end if;

    if p_operation_id is null or p_restaurant_id is null or p_entity_id is null then
        raise exception 'Operation, restaurant, and entity IDs are required' using errcode = '22023';
    end if;
    if p_base_server_version is null or p_base_server_version < 0 then
        raise exception 'Base server version must be zero or greater' using errcode = '22023';
    end if;
    if p_name is null or btrim(p_name) = '' then
        raise exception 'Inventory area name must not be blank' using errcode = '22023';
    end if;
    if p_normalized_name is null or btrim(p_normalized_name) = '' then
        raise exception 'Normalized inventory area name must not be blank' using errcode = '22023';
    end if;
    if p_sort_order is null or p_is_active is null or p_created_at is null or p_updated_at is null then
        raise exception 'Inventory area payload is incomplete' using errcode = '22023';
    end if;

    if not exists (
        select 1
        from public.restaurant_memberships as membership
        where membership.restaurant_id = p_restaurant_id
          and membership.user_id = current_user_id
    ) then
        raise exception 'Restaurant membership is required' using errcode = '42501';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            'inventory-area-operation:' || p_restaurant_id::text || ':' || p_operation_id::text,
            0
        )
    );

    select operation.*
    into applied_operation
    from public.sync_applied_operations as operation
    where operation.restaurant_id = p_restaurant_id
      and operation.operation_id = p_operation_id;

    if found then
        if applied_operation.entity_type <> 'INVENTORY_AREA'
           or applied_operation.entity_id <> p_entity_id then
            return query select
                'INVALID_OPERATION'::text,
                p_entity_id,
                null::bigint,
                null::bigint,
                null::bigint,
                null::bigint;
            return;
        end if;

        return query select
            'ALREADY_APPLIED'::text,
            applied_operation.entity_id,
            applied_operation.result_server_version,
            applied_operation.result_change_seq,
            applied_operation.result_server_version,
            applied_operation.result_change_seq;
        return;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('inventory-area-entity:' || p_entity_id::text, 0)
    );

    select area.*
    into current_area
    from public.inventory_areas as area
    where area.id = p_entity_id
    for update;

    if not found then
        if p_base_server_version <> 0 then
            return query select
                'CONFLICT'::text,
                p_entity_id,
                null::bigint,
                null::bigint,
                0::bigint,
                null::bigint;
            return;
        end if;

        next_server_version := 1;

        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended('venkoi-sync-change-seq-commit-order', 0)
        );
        next_change_seq := nextval('public.inventory_area_change_seq'::regclass);

        insert into public.inventory_areas (
            id,
            restaurant_id,
            name,
            normalized_name,
            sort_order,
            is_active,
            created_at,
            updated_at,
            deleted_at,
            server_version,
            change_seq
        ) values (
            p_entity_id,
            p_restaurant_id,
            btrim(p_name),
            btrim(p_normalized_name),
            p_sort_order,
            p_is_active,
            p_created_at,
            p_updated_at,
            p_deleted_at,
            next_server_version,
            next_change_seq
        );
    else
        if current_area.restaurant_id <> p_restaurant_id
           or current_area.server_version <> p_base_server_version then
            return query select
                'CONFLICT'::text,
                p_entity_id,
                null::bigint,
                null::bigint,
                current_area.server_version,
                current_area.change_seq;
            return;
        end if;

        next_server_version := current_area.server_version + 1;

        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended('venkoi-sync-change-seq-commit-order', 0)
        );
        next_change_seq := nextval('public.inventory_area_change_seq'::regclass);

        update public.inventory_areas as area
        set name = btrim(p_name),
            normalized_name = btrim(p_normalized_name),
            sort_order = p_sort_order,
            is_active = p_is_active,
            created_at = p_created_at,
            updated_at = p_updated_at,
            deleted_at = p_deleted_at,
            server_version = next_server_version,
            change_seq = next_change_seq
        where area.id = p_entity_id;
    end if;

    insert into public.sync_applied_operations (
        restaurant_id,
        operation_id,
        entity_type,
        entity_id,
        result_server_version,
        result_change_seq
    ) values (
        p_restaurant_id,
        p_operation_id,
        'INVENTORY_AREA',
        p_entity_id,
        next_server_version,
        next_change_seq
    );

    return query select
        'APPLIED'::text,
        p_entity_id,
        next_server_version,
        next_change_seq,
        next_server_version,
        next_change_seq;
end;
$$;

revoke all on function public.apply_inventory_area_sync(
    uuid, uuid, uuid, bigint, text, text, integer, boolean,
    timestamptz, timestamptz, timestamptz
) from public, anon;

grant execute on function public.apply_inventory_area_sync(
    uuid, uuid, uuid, bigint, text, text, integer, boolean,
    timestamptz, timestamptz, timestamptz
) to authenticated;
