create type public.membership_role as enum (
    'OWNER',
    'MANAGER',
    'STAFF'
);

create table public.profiles (
    user_id uuid primary key references auth.users (id) on delete cascade,
    display_name text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.organizations (
    id uuid primary key default gen_random_uuid(),
    name text not null check (btrim(name) <> ''),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.organization_memberships (
    organization_id uuid not null references public.organizations (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    role public.membership_role not null,
    created_at timestamptz not null default now(),
    primary key (organization_id, user_id)
);

create table public.restaurants (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references public.organizations (id) on delete cascade,
    name text not null check (btrim(name) <> ''),
    currency_code text not null check (currency_code ~ '^[A-Z]{3}$'),
    timezone text not null check (btrim(timezone) <> ''),
    locale_tag text not null check (btrim(locale_tag) <> ''),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.restaurant_memberships (
    restaurant_id uuid not null references public.restaurants (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    role public.membership_role not null,
    created_at timestamptz not null default now(),
    primary key (restaurant_id, user_id)
);

create table public.device_installations (
    id uuid primary key default gen_random_uuid(),
    restaurant_id uuid not null references public.restaurants (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    installation_id text not null check (btrim(installation_id) <> ''),
    platform text not null check (btrim(platform) <> ''),
    device_name text,
    app_version text,
    last_seen_at timestamptz not null default now(),
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    unique (restaurant_id, installation_id)
);

create index organization_memberships_user_id_idx
    on public.organization_memberships (user_id);

create index restaurant_memberships_user_id_idx
    on public.restaurant_memberships (user_id);

create index device_installations_user_id_idx
    on public.device_installations (user_id);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create trigger organizations_set_updated_at
before update on public.organizations
for each row execute function public.set_updated_at();

create trigger restaurants_set_updated_at
before update on public.restaurants
for each row execute function public.set_updated_at();

revoke all on function public.set_updated_at() from public, anon, authenticated;

alter table public.profiles enable row level security;
alter table public.organizations enable row level security;
alter table public.organization_memberships enable row level security;
alter table public.restaurants enable row level security;
alter table public.restaurant_memberships enable row level security;
alter table public.device_installations enable row level security;

create policy profiles_select_own
on public.profiles
for select
to authenticated
using (user_id = (select auth.uid()));

create policy profiles_update_own
on public.profiles
for update
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

create policy organization_memberships_select_own
on public.organization_memberships
for select
to authenticated
using (user_id = (select auth.uid()));

create policy organizations_select_member
on public.organizations
for select
to authenticated
using (
    exists (
        select 1
        from public.organization_memberships as membership
        where membership.organization_id = organizations.id
          and membership.user_id = (select auth.uid())
    )
);

create policy organizations_update_manager
on public.organizations
for update
to authenticated
using (
    exists (
        select 1
        from public.organization_memberships as membership
        where membership.organization_id = organizations.id
          and membership.user_id = (select auth.uid())
          and membership.role in ('OWNER', 'MANAGER')
    )
)
with check (
    exists (
        select 1
        from public.organization_memberships as membership
        where membership.organization_id = organizations.id
          and membership.user_id = (select auth.uid())
          and membership.role in ('OWNER', 'MANAGER')
    )
);

create policy restaurant_memberships_select_own
on public.restaurant_memberships
for select
to authenticated
using (user_id = (select auth.uid()));

create policy restaurants_select_member
on public.restaurants
for select
to authenticated
using (
    exists (
        select 1
        from public.restaurant_memberships as membership
        where membership.restaurant_id = restaurants.id
          and membership.user_id = (select auth.uid())
    )
);

create policy restaurants_update_manager
on public.restaurants
for update
to authenticated
using (
    exists (
        select 1
        from public.restaurant_memberships as membership
        where membership.restaurant_id = restaurants.id
          and membership.user_id = (select auth.uid())
          and membership.role in ('OWNER', 'MANAGER')
    )
)
with check (
    exists (
        select 1
        from public.restaurant_memberships as membership
        where membership.restaurant_id = restaurants.id
          and membership.user_id = (select auth.uid())
          and membership.role in ('OWNER', 'MANAGER')
    )
);

create policy device_installations_select_own
on public.device_installations
for select
to authenticated
using (user_id = (select auth.uid()));

create policy device_installations_insert_own_membership
on public.device_installations
for insert
to authenticated
with check (
    user_id = (select auth.uid())
    and exists (
        select 1
        from public.restaurant_memberships as membership
        where membership.restaurant_id = device_installations.restaurant_id
          and membership.user_id = (select auth.uid())
    )
);

create policy device_installations_update_own_membership
on public.device_installations
for update
to authenticated
using (
    user_id = (select auth.uid())
    and exists (
        select 1
        from public.restaurant_memberships as membership
        where membership.restaurant_id = device_installations.restaurant_id
          and membership.user_id = (select auth.uid())
    )
)
with check (
    user_id = (select auth.uid())
    and exists (
        select 1
        from public.restaurant_memberships as membership
        where membership.restaurant_id = device_installations.restaurant_id
          and membership.user_id = (select auth.uid())
    )
);

revoke all on type public.membership_role from public, anon, authenticated;
revoke all on table public.profiles from public, anon, authenticated;
revoke all on table public.organizations from public, anon, authenticated;
revoke all on table public.organization_memberships from public, anon, authenticated;
revoke all on table public.restaurants from public, anon, authenticated;
revoke all on table public.restaurant_memberships from public, anon, authenticated;
revoke all on table public.device_installations from public, anon, authenticated;

grant usage on type public.membership_role to authenticated;
grant select on table public.profiles to authenticated;
grant update (display_name) on table public.profiles to authenticated;
grant select on table public.organizations to authenticated;
grant update (name) on table public.organizations to authenticated;
grant select on table public.organization_memberships to authenticated;
grant select on table public.restaurants to authenticated;
grant update (name, currency_code, timezone, locale_tag) on table public.restaurants to authenticated;
grant select on table public.restaurant_memberships to authenticated;
grant select on table public.device_installations to authenticated;
grant insert (
    restaurant_id,
    user_id,
    installation_id,
    platform,
    device_name,
    app_version,
    last_seen_at,
    revoked_at
) on table public.device_installations to authenticated;
grant update (
    restaurant_id,
    installation_id,
    platform,
    device_name,
    app_version,
    last_seen_at,
    revoked_at
) on table public.device_installations to authenticated;

create or replace function public.create_organization_with_restaurant(
    organization_name text,
    restaurant_name text,
    currency_code text,
    timezone text,
    locale_tag text
)
returns table (organization_id uuid, restaurant_id uuid)
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_user_id uuid := auth.uid();
    new_organization_id uuid;
    new_restaurant_id uuid;
    normalized_currency_code text;
begin
    if current_user_id is null then
        raise exception 'Authentication is required' using errcode = '42501';
    end if;

    if organization_name is null or btrim(organization_name) = '' then
        raise exception 'Organization name must not be blank' using errcode = '22023';
    end if;
    if restaurant_name is null or btrim(restaurant_name) = '' then
        raise exception 'Restaurant name must not be blank' using errcode = '22023';
    end if;
    if currency_code is null or btrim(currency_code) !~ '^[A-Za-z]{3}$' then
        raise exception 'Currency code must contain exactly three alphabetic characters' using errcode = '22023';
    end if;
    if timezone is null or btrim(timezone) = '' then
        raise exception 'Timezone must not be blank' using errcode = '22023';
    end if;
    if locale_tag is null or btrim(locale_tag) = '' then
        raise exception 'Locale tag must not be blank' using errcode = '22023';
    end if;

    normalized_currency_code := upper(btrim(currency_code));

    insert into public.profiles (user_id)
    values (current_user_id)
    on conflict (user_id) do nothing;

    insert into public.organizations (name)
    values (btrim(organization_name))
    returning id into new_organization_id;

    insert into public.organization_memberships (organization_id, user_id, role)
    values (new_organization_id, current_user_id, 'OWNER');

    insert into public.restaurants (
        organization_id,
        name,
        currency_code,
        timezone,
        locale_tag
    )
    values (
        new_organization_id,
        btrim(restaurant_name),
        normalized_currency_code,
        btrim(timezone),
        btrim(locale_tag)
    )
    returning id into new_restaurant_id;

    insert into public.restaurant_memberships (restaurant_id, user_id, role)
    values (new_restaurant_id, current_user_id, 'OWNER');

    return query
    select new_organization_id, new_restaurant_id;
end;
$$;

revoke all on function public.create_organization_with_restaurant(text, text, text, text, text)
from public, anon;

grant execute on function public.create_organization_with_restaurant(text, text, text, text, text)
to authenticated;
