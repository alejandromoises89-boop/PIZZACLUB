# Pizza Club — Arquitectura del Sistema, Esquema de Base de Datos y Guía de Despliegue

## 1. Visión General de la Arquitectura

La plataforma **Pizza Club** (Ciudad del Este, Alto Paraná, Paraguay) está implementada como una aplicación nativa Android (**Kotlin + Jetpack Compose + Material Design 3**) respaldada por **Firebase Authentication (Google Sign-In via Credential Manager)**, **Cloud Firestore (Enterprise Edition)** y **Firebase Cloud Functions v2** (opcional/recomendada para despliegues Blaze).

### Flujo de Confianza Cero (Zero-Trust)
1. **Cliente sin registro**: Explora catálogo e inventario en tiempo real (`/products`, `/inventory`), arma su preorden, elige preparación (*Lista para comer* o *Lista para hornear en casa*) y modalidad (*Delivery* o *Retiro en local*), e ingresa sus datos.
2. **Aislamiento PII (Split Collection)**:
   - Al confirmar la preorden, la app escribe en una transacción/lote atómico dos documentos con un ID criptográfico aleatorio de 24+ caracteres:
     - `/orders/{orderId}`: Contiene datos personales (nombre, teléfono, dirección, link de Maps). **Solo los administradores autenticados pueden leer o listar esta colección.**
     - `/order_status/{orderId}`: Proyección pública sin PII (solo `orderNumber`, `status`, `whatsappUnlocked`, `totalAmountPyg`, `fulfillmentType`). El cliente escucha en tiempo real este documento sin necesidad de crear cuenta.
3. **Verificación Manual de Transferencia (Itaú Paraguay)**:
   - Toda orden nace estrictamente en `PENDING_PAYMENT` con `whatsappUnlocked = false`.
   - Las reglas de Firestore impiden que cualquier cliente cree una orden pagada o modifique su estado.
4. **Panel Administrativo Privado**:
   - Protegido por **Google Sign-In via Jetpack Credential Manager** (`GetSignInWithGoogleOption`).
   - Las reglas de seguridad verifican `request.auth.token.email_verified == true && request.auth.token.email == 'alejandromoises89@gmail.com'` o la existencia del UID en `/administrators/{uid}`.
   - Al presionar **CONFIRMAR PAGO**, una transacción atómica verifica que haya stock suficiente en `/inventory/{productId}`, descuenta el inventario (impidiendo valores negativos), actualiza `/orders/{orderId}` y `/order_status/{orderId}` a `PAYMENT_CONFIRMED` con `whatsappUnlocked = true`, y registra un evento inmutable en `/payment_audit_logs`.
5. **Desbloqueo de WhatsApp**:
   - Al recibir `whatsappUnlocked == true`, la pantalla del cliente habilita el botón **"Continuar por WhatsApp"** (`https://wa.me/59593551936?text=...`) con todo el resumen formateado. El chat nunca se abre automáticamente sin clic del usuario.

---

## 2. Esquema de Firestore (`firebase-blueprint.json`)

| Colección | Acceso Lectura | Acceso Escritura | Propósito |
|---|---|---|---|
| `/products/{productId}` | Público (`get`, `list`) | Solo Admin | Sabores, descripciones, precios en PYG y estado activo |
| `/inventory/{productId}` | Público (`get`, `list`) | Solo Admin | Stock por sabor (`>= 0`) y alerta de bajo stock |
| `/business_settings/default` | Público (`get`, `list`) | Solo Admin | Marca, WhatsApp, banco, alias y estado del local |
| `/orders/{orderId}` | Solo Admin (`get`, `list`) | Crear: Cliente (`PENDING_PAYMENT` + `existsAfter(order_status)`). Actualizar: Solo Admin | Preórdenes completas con PII |
| `/order_status/{orderId}` | Público (`get` por ID >= 20 chars). Listar: Solo Admin | Crear: Cliente en batch con `orders`. Actualizar: Solo Admin | Estado en vivo sin PII |
| `/administrators/{uid}` | Propio usuario o Admin | Solo Admin | Lista blanca adicional de administradores |
| `/payment_audit_logs/{logId}` | Solo Admin | Crear: Solo Admin (inmutable, sin update/delete) | Auditoría de pagos, cancelaciones y ajustes |

---

## 3. Estructura de Carpetas

```text
├── app/src/main/java/com/example/
│   ├── MainActivity.kt                  # Entry point edge-to-edge + AppNavigation
│   ├── data/
│   │   ├── FirestoreModels.kt           # Modelos @Keep @IgnoreExtraProperties + mappers
│   │   ├── FirestoreErrorHandler.kt     # Manejo estructurado JSON de errores Firestore
│   │   ├── LocalOrderStore.kt           # DataStore para persistir preórdenes del cliente sin login
│   │   └── PizzaClubRepository.kt       # Queries en tiempo real, transacciones de pago e inventario
│   ├── ui/
│   │   ├── theme/                       # Paleta Pizza Club (#C83D2D, #E7AD48, #F7F4EF, #171411) + Outfit & Plus Jakarta Sans
│   │   ├── components/                  # Tarjetas, badges de estado, stepper de progreso, botones
│   │   ├── customer/                    # Catálogo, carrito, checkout, transferencia Itaú, tracker + WhatsApp
│   │   └── admin/                       # Google Sign-In, Dashboard KPIs, pedidos, confirmación de pago, productos, stock, CSV
│   └── viewmodel/
│       ├── CustomerViewModel.kt         # Estado reactivo del catálogo, carrito y seguimiento de órdenes
│       └── AdminViewModel.kt            # Estado autenticado del panel administrativo
├── functions/
│   ├── index.js                         # Cloud Functions v2 (submitPreOrder, confirmOrderPayment, updateOrderStatus)
│   └── package.json
├── firebase-blueprint.json              # Esquema IR de Firestore
├── firestore.rules                      # Reglas Zero-Trust validadas
├── firestore.test.js                    # Pruebas de reglas contra el emulador (node --test)
└── firebase.json                        # Configuración de base de datos única para emulador
```

---

## 4. Despliegue de Cloud Functions (Opcional / Plan Blaze)

Si deseás ejecutar `submitPreOrder`, `confirmOrderPayment` y `updateOrderStatus` como Cloud Functions HTTPS en Firebase:

1. Actualizá el proyecto Firebase al plan **Blaze** (requerido para Cloud Functions v2).
2. Instalá dependencias y desplegá:
   ```bash
   cd functions && npm install
   firebase deploy --only functions,firestore:rules
   ```
3. Para el panel web futuro (si se agrega junto a la app Android), usá la misma instancia de Firestore (`ai-studio-android-pizzaclu-26d3b8a1-697e-40e6-a619-f86f22ddfb34`) y las mismas reglas en `firestore.rules`.
