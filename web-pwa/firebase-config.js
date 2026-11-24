/**
 * Plantilla de configuración Firebase Web para Pizza Club (PWA Cliente + Panel Admin Web).
 *
 * Cómo activar la sincronización completa en la nube (Firestore + Google Auth Admin):
 * 1. Abrí Firebase Console -> Configuración del proyecto (gen-lang-client-0684931110)
 *    -> Tus apps -> Agregá una app Web (o ejecutá: `firebase apps:sdkconfig web --project gen-lang-client-0684931110`).
 * 2. Reemplazá los valores placeholder (`YOUR_FIREBASE_*`) con tus claves Web reales.
 *    (Las claves Web de Firebase son identificadores públicos del cliente; la seguridad vive en `firestore.rules`.)
 * 3. Si desplegás por GitHub Actions, podés dejar este archivo como plantilla en el repo
 *    y guardar el contenido completo en el secreto `FIREBASE_CONFIG_JS` de GitHub.
 * 4. Para usar `admin.html` en un dominio propio o GitHub Pages, agregá ese dominio en
 *    Firebase Console -> Authentication -> Settings -> Authorized domains.
 */
window.PIZZA_CLUB_FIREBASE_CONFIG = {
  apiKey: "YOUR_FIREBASE_WEB_API_KEY",
  authDomain: "gen-lang-client-0684931110.firebaseapp.com",
  projectId: "gen-lang-client-0684931110",
  storageBucket: "gen-lang-client-0684931110.firebasestorage.app",
  messagingSenderId: "YOUR_FIREBASE_MESSAGING_SENDER_ID",
  appId: "YOUR_FIREBASE_WEB_APP_ID"
};

window.PIZZA_CLUB_DATABASE_ID =
  "ai-studio-android-pizzaclu-26d3b8a1-697e-40e6-a619-f86f22ddfb34";
