# Guía Rápida: Push Final a GitHub y Pruebas en iPhone, Android y Notebook

Todo el proyecto (**Android Kotlin + Web PWA + Panel Admin + Base Online Supabase**) está listo para hacer push a GitHub y probarlo inmediatamente desde **iPhone**, **Android** y **Notebook/PC**.

---

## Paso 1: Subir el proyecto a GitHub
1. Desde **Google AI Studio**, abrí el menú superior derecho (**Settings / Export → Push to GitHub**), o desde tu terminal local hacé `git push origin main`.
2. Elegí o creá tu repositorio en GitHub y confirmá el push.

---

## Paso 2: Activar el enlace público para iPhone, Android y Notebook (GitHub Pages)
El workflow `.github/workflows/deploy-web.yml` ya publica automáticamente la carpeta `web-pwa/` en HTTPS:
1. En tu repositorio de GitHub → **Settings → Pages**.
2. En **Build and deployment → Source**, seleccioná **GitHub Actions**.
3. En la pestaña **Actions**, ejecutá **Deploy Pizza Club Web PWA** (se ejecuta automáticamente en cada push a `main` o `master`).
4. Tu enlace público quedará activo en:
   `https://<tu-usuario>.github.io/<tu-repositorio>/`

### Cómo probar en cada dispositivo:
- **iPhone (Safari):** Abrí el enlace → tocá **Compartir ⬆️ → Agregar a pantalla de inicio** para usar la app instalada con alertas y seguimiento en vivo.
- **Android (Chrome o APK nativo):** Abrí el enlace e instalá la PWA, o generá/instalá la app nativa Android desde el proyecto Gradle (`gradle :app:assembleDebug`).
- **Notebook / PC (Chrome, Edge, Safari):** Abrí el mismo enlace en tu navegador para probar pedidos, alertas sonoras, notificaciones y el **Panel Admin** en `./admin.html`.

---

## Paso 3: Activar Base de Datos Online en Tiempo Real (Supabase)
Para que los pedidos realizados en iPhone, Android o Notebook se vean al instante en el Panel Admin desde cualquier lugar:
1. Creá un proyecto gratuito en [supabase.com](https://supabase.com), abrí **SQL Editor → New query**, pegá el contenido de `supabase_schema.sql` y ejecutá **RUN**.
2. Copiá tu **Project URL** y **anon public key** (*Project Settings → API*).
3. Podés conectarlos de cualquiera de estas dos formas:
   - **Directo desde el navegador (sin tocar código):** entrá a `./admin.html`, iniciá sesión con tu PIN (o Face ID) y pegá la URL y Anon Key en **🔌 Base Online**.
   - **O vía GitHub Secret (automático para todos los clientes):** en GitHub → **Settings → Secrets and variables → Actions → New repository secret** creá `DB_CONFIG_JS` con el contenido de `web-pwa/db-config.js` completado con tu `url` y `anonKey`.

---

## Paso 4: Habilitar Notificaciones Push con la App Cerrada (Android e iPhone)
1. Entrá al **Panel Admin** (`./admin.html` → botón **🔔 Push VAPID**, o en la app Android → pestaña **Ajustes → Notificaciones Push (VAPID y Supabase)**).
2. Tocá **⚡ Generar VAPID Keys y Descargar Archivos** para obtener al instante:
   - `supabase-push-secrets.env` (con `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` y `VAPID_SUBJECT`).
   - `db-config.js` (ya prellenado con tu URL de Supabase, Anon Key y `vapidPublicKey`).
3. En **Supabase Dashboard → Edge Functions → Secrets**, cargá las 3 variables de `supabase-push-secrets.env` (o ejecutá `supabase secrets set --env-file supabase-push-secrets.env` y `supabase functions deploy order-status-push`).
4. En **Database → Webhooks → Create a new hook**, conectá la tabla `public.order_status` (`INSERT` y `UPDATE`) hacia la Edge Function `order-status-push`.
5. Subí el `db-config.js` descargado (o guardalo en el secret `DB_CONFIG_JS` de GitHub) y hacé push. En **iPhone**, recordá abrir el sitio en **Safari → Compartir → Agregar a pantalla de inicio** antes de tocar **🔔 Activar alertas**.

