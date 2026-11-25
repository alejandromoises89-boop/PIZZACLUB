-- ============================================================================
-- Pizza Club — Base de Datos Online Cross-Platform (PostgreSQL / Supabase)
-- Cero Firebase · Compatible con Android, iPhone (PWA) y Web Desktop
-- ============================================================================
-- INSTRUCCIONES (1 sola vez, toma ~1 minuto):
-- 1. Creá un proyecto gratuito en https://supabase.com
-- 2. Abrí "SQL Editor" -> "New query"
-- 3. Pegá todo este archivo y tocá "RUN"
-- 4. Copiá tu Project URL y anon public key (Project Settings -> API)
--    en web-pwa/db-config.js (o directamente en el Panel Admin Web / Android)
-- ============================================================================

create table if not exists public.products (
  id text primary key,
  name text not null,
  description text not null default '',
  price_pyg bigint not null default 50000,
  active boolean not null default true,
  image_key text not null default 'pepperoni',
  sort_order bigint not null default 0,
  created_at_ms bigint not null,
  updated_at_ms bigint not null
);

create table if not exists public.inventory (
  id text primary key,
  product_name text not null,
  stock bigint not null default 20 check (stock >= 0),
  low_stock_threshold bigint not null default 5,
  updated_by text not null default 'system',
  updated_at_ms bigint not null
);

create table if not exists public.orders (
  id text primary key,
  order_number text not null,
  customer_name text not null,
  customer_phone text not null,
  desired_date text not null,
  additional_instructions text,
  preparation_mode text not null,
  fulfillment_type text not null,
  neighborhood text,
  street_and_number text,
  delivery_reference text,
  google_maps_link text,
  pickup_date text,
  pickup_time text,
  items jsonb not null default '[]'::jsonb,
  subtotal_pyg bigint not null default 0,
  total_amount_pyg bigint not null default 0,
  status text not null default 'PENDING_PAYMENT',
  whatsapp_unlocked boolean not null default false,
  payment_method text not null default 'BANK_TRANSFER',
  payment_bank text not null default 'Itaú Paraguay',
  payment_alias text not null default 'alejandromoises89@gmail.com',
  payment_confirmed_at_ms bigint,
  confirmed_by_admin_id text,
  confirmed_by_admin_email text,
  cancellation_reason text,
  created_at_ms bigint not null,
  updated_at_ms bigint not null
);

create table if not exists public.order_status (
  id text primary key,
  order_number text not null,
  status text not null default 'PENDING_PAYMENT',
  whatsapp_unlocked boolean not null default false,
  total_amount_pyg bigint not null default 0,
  fulfillment_type text not null default 'DELIVERY',
  created_at_ms bigint not null,
  updated_at_ms bigint not null
);

create table if not exists public.payment_audit_logs (
  id text primary key,
  order_id text not null,
  order_number text not null,
  admin_id text not null,
  admin_email text not null,
  action text not null,
  previous_status text not null,
  new_status text not null,
  amount_pyg bigint not null default 0,
  note text,
  created_at_ms bigint not null
);

create table if not exists public.business_settings (
  id text primary key,
  brand_name text not null default 'Pizza Club',
  instagram_handle text not null default '@Pizza.Club1',
  whatsapp_number text not null default '59593551936',
  bank_name text not null default 'Itaú Paraguay',
  payment_alias text not null default 'alejandromoises89@gmail.com',
  delivery_city text not null default 'Ciudad del Este',
  currency text not null default 'PYG',
  accepting_orders boolean not null default true,
  treasure_code text not null default 'PIZZACLUB-ORO',
  treasure_reward text not null default 'Dip Artesanal + 10% OFF en tu próxima pizza',
  google_review_url text not null default 'https://www.google.com/maps/search/?api=1&query=Pizza+Club+Ciudad+del+Este+Paraguay',
  pwa_install_url text not null default 'https://github.com',
  updated_at_ms bigint not null
);

create index if not exists idx_orders_created_at_ms on public.orders (created_at_ms desc);
create index if not exists idx_audit_created_at_ms on public.payment_audit_logs (created_at_ms desc);

-- ============================================================================
-- Row Level Security (Acceso inmediato sin login de clientes ni Firebase Auth)
-- Nota de producción: si más adelante querés restringir lectura de PII en
-- `orders` solo al admin, mové las escrituras admin a una Supabase Edge Function
-- con verificación de PIN/JWT en servidor.
-- ============================================================================
alter table public.products enable row level security;
alter table public.inventory enable row level security;
alter table public.orders enable row level security;
alter table public.order_status enable row level security;
alter table public.payment_audit_logs enable row level security;
alter table public.business_settings enable row level security;

do $$
begin
  -- products
  if not exists (select 1 from pg_policies where tablename = 'products' and policyname = 'pizza_club_products_all') then
    create policy pizza_club_products_all on public.products for all to anon, authenticated using (true) with check (true);
  end if;
  -- inventory
  if not exists (select 1 from pg_policies where tablename = 'inventory' and policyname = 'pizza_club_inventory_all') then
    create policy pizza_club_inventory_all on public.inventory for all to anon, authenticated using (true) with check (true);
  end if;
  -- orders
  if not exists (select 1 from pg_policies where tablename = 'orders' and policyname = 'pizza_club_orders_all') then
    create policy pizza_club_orders_all on public.orders for all to anon, authenticated using (true) with check (true);
  end if;
  -- order_status
  if not exists (select 1 from pg_policies where tablename = 'order_status' and policyname = 'pizza_club_order_status_all') then
    create policy pizza_club_order_status_all on public.order_status for all to anon, authenticated using (true) with check (true);
  end if;
  -- payment_audit_logs
  if not exists (select 1 from pg_policies where tablename = 'payment_audit_logs' and policyname = 'pizza_club_audit_all') then
    create policy pizza_club_audit_all on public.payment_audit_logs for all to anon, authenticated using (true) with check (true);
  end if;
  -- business_settings
  if not exists (select 1 from pg_policies where tablename = 'business_settings' and policyname = 'pizza_club_settings_all') then
    create policy pizza_club_settings_all on public.business_settings for all to anon, authenticated using (true) with check (true);
  end if;
end $$;

-- ============================================================================
-- Catálogo inicial y configuración del comercio (idempotente)
-- ============================================================================
insert into public.products (id, name, description, price_pyg, active, image_key, sort_order, created_at_ms, updated_at_ms)
values
  ('pepperoni', 'Pepperoni', 'Salsa de tomate San Marzano, mozzarella fior di latte y pepperoni artesanal crocante.', 50000, true, 'pepperoni', 1, extract(epoch from now())::bigint * 1000, extract(epoch from now())::bigint * 1000),
  ('mozzarella', 'Mozzarella', 'Clásica italiana con doble mozzarella cremosa, tomate natural, albahaca fresca y oliva.', 50000, true, 'mozzarella', 2, extract(epoch from now())::bigint * 1000, extract(epoch from now())::bigint * 1000),
  ('four_cheeses', 'Cuatro quesos', 'Mozzarella, gorgonzola, parmesano estacionado y provolone con pimienta negra.', 50000, true, 'four_cheeses', 3, extract(epoch from now())::bigint * 1000, extract(epoch from now())::bigint * 1000),
  ('chicken_catupiry', 'Pollo Catupiri', 'Pollo desmenuzado sazonado, queso cremoso Catupiry original, mozzarella y orégano.', 50000, true, 'chicken_catupiry', 4, extract(epoch from now())::bigint * 1000, extract(epoch from now())::bigint * 1000)
on conflict (id) do nothing;

insert into public.inventory (id, product_name, stock, low_stock_threshold, updated_by, updated_at_ms)
values
  ('pepperoni', 'Pepperoni', 20, 5, 'system', extract(epoch from now())::bigint * 1000),
  ('mozzarella', 'Mozzarella', 20, 5, 'system', extract(epoch from now())::bigint * 1000),
  ('four_cheeses', 'Cuatro quesos', 20, 5, 'system', extract(epoch from now())::bigint * 1000),
  ('chicken_catupiry', 'Pollo Catupiri', 20, 5, 'system', extract(epoch from now())::bigint * 1000)
on conflict (id) do nothing;

insert into public.business_settings (
  id, brand_name, instagram_handle, whatsapp_number, bank_name, payment_alias,
  delivery_city, currency, accepting_orders, treasure_code, treasure_reward,
  google_review_url, pwa_install_url, updated_at_ms
)
values (
  'default',
  'Pizza Club',
  '@Pizza.Club1',
  '59593551936',
  'Itaú Paraguay',
  'alejandromoises89@gmail.com',
  'Ciudad del Este',
  'PYG',
  true,
  'PIZZACLUB-ORO',
  'Dip Artesanal + 10% OFF en tu próxima pizza',
  'https://www.google.com/maps/search/?api=1&query=Pizza+Club+Ciudad+del+Este+Paraguay',
  'https://github.com',
  extract(epoch from now())::bigint * 1000
)
on conflict (id) do nothing;

-- ============================================================================
-- Suscripciones Web Push (Service Worker sw.js con la app cerrada)
-- ============================================================================
create table if not exists public.push_subscriptions (
  id text primary key,
  endpoint text not null unique,
  p256dh text not null default '',
  auth text not null default '',
  subscription jsonb not null default '{}'::jsonb,
  order_ids jsonb not null default '[]'::jsonb,
  user_agent text,
  updated_at_ms bigint not null
);

alter table public.push_subscriptions enable row level security;

do $$
begin
  if not exists (select 1 from pg_policies where tablename = 'push_subscriptions' and policyname = 'pizza_club_push_subscriptions_all') then
    create policy pizza_club_push_subscriptions_all on public.push_subscriptions for all to anon, authenticated using (true) with check (true);
  end if;
end $$;

-- Habilita Realtime en order_status para actualizaciones instantáneas
do $$
begin
  alter publication supabase_realtime add table public.order_status;
exception when others then
  null;
end $$;
