# Pizza Club — Guía Completa de Publicación, Google Search e iPhone

Todo el ecosistema de **Pizza Club** (Ciudad del Este, Alto Paraná, Paraguay) está conectado en la misma base de datos Firebase para que cliente y propietario operen sincronizados en tiempo real.

---

## 1. Cómo funciona Cliente + Admin en el mismo ecosistema

- **Cliente (`Menú` y `Mis Pedidos`):**
  1. Elige sabores y cantidad (con stock en vivo, sin registro ni contraseña).
  2. Genera la preorden en estado `Pendiente de pago` y ve los datos de transferencia **Itaú Paraguay** (`alejandromoises89@gmail.com`).
  3. En **Mis Pedidos**, el estado de confirmación de pago se actualiza **en tiempo real** apenas el propietario aprueba el depósito.
- **Propietario (`Admin`, acceso exclusivo con Google `alejandromoises89@gmail.com`):**
  1. Revisa los pedidos pendientes en la pestaña **Pedidos**.
  2. Verifica la transferencia real en su cuenta Itaú y presiona **`CONFIRMAR PAGO`**.
  3. En ese instante, la app descuenta el stock atómicamente, registra auditoría y desbloquea automáticamente el botón **`Continuar por WhatsApp`** en la pantalla del cliente.

---

## 2. Publicar en Android (APK directo + Google Play Store)

- **Package ID único:** `com.aistudio.pizzaclub.cdepxk`
- **Proyecto Firebase:** `gen-lang-client-0684931110`

```bash
# APK para descarga directa
gradle :app:assembleDebug

# Bundle firmado para Google Play Console
gradle :app:bundleRelease
```

---

## 3. Instalar en iPhone (iOS Safari) y aparecer en Google Search

1. **Primer deploy de prueba (canal temporal en Firebase Hosting):**
   ```bash
   firebase login
   firebase hosting:channel:deploy prueba --expires 14d
   ```
   Esto genera una URL temporal segura (`https://gen-lang-client-0684931110--prueba-....web.app`) para probar pedidos, historial guardado en `localStorage`, iPhone Safari y confirmación del administrador antes de salir a producción.

2. **Segundo deploy definitivo a producción:**
   ```bash
   firebase deploy --only hosting,firestore:rules
   ```
3. Tu tienda virtual definitiva quedará en **`https://gen-lang-client-0684931110.web.app`**, lista con:
   - **Historial de pedidos en `localStorage` + confirmación en vivo:** el cliente ve sus pedidos anteriores y si ya fueron confirmados por Admin.
   - **Datos estructurados JSON-LD de Google (`Restaurant`, menú en Gs. 50.000, WhatsApp, Instagram `@Pizza.Club1`, ubicación Ciudad del Este)** + `robots.txt` + `sitemap.xml`.
   - **Instalación en iPhone (Safari):** `Compartir ⬆️` → **"Agregar a pantalla de inicio"**.
4. **Google Search Console & Google Business Profile:**
   - Registrá `https://gen-lang-client-0684931110.web.app/` en **Google Search Console** y enviá `/sitemap.xml` para indexación rápida.
   - En **Google Business Profile** (`business.google.com`), vinculá ese mismo enlace web y pegá tu enlace de reseñas (`https://g.page/r/.../review`) en **Admin → Ajustes** de la app.

---

## 4. Kit Publicitario Listo para Publicar

### Bio de Instagram (`@Pizza.Club1`)
> 🍕 Pizzas artesanales de masa madre en Ciudad del Este  
> 🔥 Listas para comer o para hornear en casa (Gs. 50.000)  
> 🎁 Dentro de cada pizza hay un premio sorpresa  
> 👇 Pedí en 30 segundos (iPhone y Android, sin registrarte):  
> `https://gen-lang-client-0684931110.web.app`

### Sticker / Volante dentro de la caja
> **¡GRACIAS POR TU PREFERENCIA! 🍕**  
> **P.D.:** ¡Dentro de cada pizza hay un premio sorpresa!  
> Escaneá este QR o ingresá el código **`PIZZACLUB-ORO`** en tu pedido para descubrir tu regalo.
