// Configuración de base de datos online (Supabase PostgreSQL REST) — SIN FIREBASE.
// 1) Creá tu proyecto gratis en https://supabase.com y ejecutá el archivo supabase_schema.sql
// 2) Reemplazá url y anonKey con tu Project URL y tu anon public key (o pegalos desde web-pwa/admin.html)
// 3) Opcional para Push con la app cerrada: pegá tu VAPID Public Key para suscribir navegadores en push_subscriptions
window.PIZZA_CLUB_DB_CONFIG = {
  url: "https://YOUR_PROJECT.supabase.co",
  anonKey: "YOUR_SUPABASE_ANON_KEY",
  vapidPublicKey: ""
};
