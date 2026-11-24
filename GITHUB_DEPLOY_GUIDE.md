# Guía Rápida: Conectar con GitHub y Desplegar Pizza Club

Por seguridad, este entorno de Google AI Studio no tiene acceso directo a tu cuenta personal de GitHub ni a tus contraseñas. Todo el proyecto ya quedó preparado con **GitHub Actions** en `.github/workflows/` para que apenas lo subas a tu repositorio se despliegue solo.

---

## Paso 1: Subir el proyecto a GitHub desde Google AI Studio
1. En la esquina superior derecha de **Google AI Studio**, abrí el menú **Settings / Export** y elegí **Push to GitHub** (o conectá tu cuenta de GitHub si aún no la vinculaste).
2. Creá un repositorio nuevo (por ejemplo `pizza-club-cde`) y confirmá el push.

---

## Paso 2: Activar el enlace público Web/iPhone en GitHub Pages (Gratis, 1 clic)
1. Entrá a tu nuevo repositorio en GitHub → **Settings** → **Pages**.
2. En **Build and deployment → Source**, seleccioná **GitHub Actions**.
3. Andá a la pestaña **Actions**, abrí el workflow **Deploy Pizza Club Web PWA** y tocá **Re-run all jobs** (o hacé un nuevo push).
4. En menos de 1 minuto GitHub te dará tu enlace estable HTTPS:
   `https://<tu-usuario>.github.io/<tu-repositorio>/`
   - Desde **iPhone (Safari)**: Compartir → *Agregar a pantalla de inicio*.
   - Desde **Android (Chrome)**: *Instalar app*.
   - **Panel Admin Web**: `https://<tu-usuario>.github.io/<tu-repositorio>/admin.html`.

---

## Paso 3: Descargar el APK de Android compilado por GitHub
Cada vez que hagas push a `main` o `master`, el workflow **Build Pizza Club Android APK** corre los tests y genera el instalador:
1. Entrá a GitHub → pestaña **Actions** → **Build Pizza Club Android APK**.
2. Al final de la ejecución, descargá el archivo **`pizza-club-android-debug-apk`** (`app-debug.apk`).

---

## Opcional: Conectar sincronización en vivo con Firebase Web y Firebase Hosting
Si querés que la Web PWA y `admin.html` lean/confirmen pedidos en tiempo real con Firestore (y además se desplieguen en `https://gen-lang-client-0684931110.web.app`):

1. En **Firebase Console** (`gen-lang-client-0684931110`) → Configuración del proyecto → agregá una app **Web** para obtener tu `firebaseConfig`.
2. En tu repositorio de GitHub → **Settings → Secrets and variables → Actions → New repository secret**:
   - **`FIREBASE_CONFIG_JS`**: pegá el contenido completo de `web-pwa/firebase-config.js` ya completado con tus claves reales (`apiKey`, `messagingSenderId` y `appId`).
   - **`FIREBASE_SERVICE_ACCOUNT_GEN_LANG_CLIENT_0684931110`** *(opcional, para Firebase Hosting)*: pegá el JSON de la cuenta de servicio generada desde `firebase init hosting:github` o Google Cloud IAM (con permiso *Firebase Hosting Admin*).
3. Agregá tu dominio de GitHub Pages (`<tu-usuario>.github.io`) en **Firebase Console → Authentication → Settings → Authorized domains** para que Google Sign-In funcione en `admin.html`.
