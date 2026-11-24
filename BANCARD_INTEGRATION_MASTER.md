# Pizza Club — Archivo Maestro de Integración de Pagos (Bancard / vPOS 2.0 / QR / Tarjetas)

> **Propósito:** Documento técnico maestro listo para entregar a **Bancard** o a una desarrolladora de integración de pagos en Paraguay para ampliar los medios de cobro de **Pizza Club** sin reescribir la app ni romper el ecosistema sincronizado Cliente ↔ Admin.

---

## 1. Datos del Comercio y Ecosistema Actual

| Campo | Valor actual |
|---|---|
| **Marca** | Pizza Club |
| **Ciudad / Mercado** | Ciudad del Este, Alto Paraná, Paraguay |
| **Moneda** | `PYG` (Guaraníes — código numérico ISO `600`, sin decimales) |
| **Zona horaria** | `America/Asuncion` |
| **Precio base actual** | `50.000 Gs.` por pizza (Pepperoni, Mozzarella, Cuatro quesos, Pollo Catupiri) |
| **WhatsApp operativo** | `+59593551936` |
| **Instagram** | `@Pizza.Club1` |
| **Medio de pago actual (v1)** | Transferencia Bancaria manual (**Itaú Paraguay**, alias `alejandromoises89@gmail.com`) |
| **App Android (Kotlin + Jetpack Compose)** | Package ID: `com.aistudio.pizzaclub.cdepxk` |
| **App Web & iPhone (PWA instalable)** | Carpeta `/web-pwa` · URL Hosting prevista: `https://gen-lang-client-0684931110.web.app` |
| **Proyecto Firebase / Backend** | `gen-lang-client-0684931110` |
| **Base de datos Firestore (Enterprise)** | `ai-studio-android-pizzaclu-26d3b8a1-697e-40e6-a619-f86f22ddfb34` |

---

## 2. Principio Clave de Arquitectura (Sin Fricción para el Cliente)

1. **Sin registro ni login obligatorio para clientes:** el cliente selecciona pizzas, elige preparación (`Lista para comer` o `Lista para hornear en casa`) y entrega (`DELIVERY` / `PICKUP`), genera su preorden y paga.
2. **Doble documento por orden para privacidad PII:**
   - `/orders/{orderId}`: datos completos del cliente y entrega (solo lectura/listado para administradores o backend).
   - `/order_status/{orderId}`: estado no sensible (`orderNumber`, `status`, `whatsappUnlocked`, `totalAmountPyg`, `fulfillmentType`, `updatedAt`) escuchado en tiempo real por el cliente en Android e iPhone/Web.
3. **Máquina de estados única (ya vigente en `firestore.rules` y UI):**
   - `PENDING_PAYMENT` → Preorden creada (`whatsappUnlocked = false`)
   - `PAYMENT_CONFIRMED` → Pago verificado (`whatsappUnlocked = true`, descuento atómico de stock)
   - `PREPARING` → En horno
   - `READY_FOR_PICKUP` / `OUT_FOR_DELIVERY` → Listo / En camino
   - `COMPLETED` → Entregado
   - `CANCELLED` → Cancelado / Rechazado

---

## 3. Contrato de Ampliación para Bancard (vPOS 2.0, Tarjetas y QR)

### 3.1 Nuevos valores de `paymentMethod`

Actualmente `orders/{orderId}.paymentMethod` acepta `'BANK_TRANSFER'`. Para integrar Bancard, habilitar:

| `paymentMethod` | Descripción | Confirmación |
|---|---|---|
| `BANK_TRANSFER` *(vigente)* | Transferencia manual Itaú Paraguay | Manual desde Admin Android / Web |
| `BANCARD_VPOS` *(nuevo)* | Tarjetas Crédito/Débito vía Bancard vPOS 2.0 (`single_buy`) | Automática vía Webhook Bancard |
| `BANCARD_QR` *(nuevo)* | Pago QR Bancard / Zimple / Billeteras | Automática vía Webhook Bancard |
| `CASH_ON_DELIVERY` *(opcional)* | Efectivo al recibir | Confirmación por Admin / Repartidor |

### 3.2 Campos adicionales recomendados en `/orders/{orderId}`

```json
{
  "paymentMethod": "BANCARD_VPOS",
  "paymentProvider": "BANCARD",
  "shopProcessId": 1004521,
  "bancardProcessId": "string_process_id_vpos",
  "bancardTicketNumber": "string_ticket",
  "authorizationNumber": "string_auth_code",
  "cardBrand": "VISA",
  "responseCode": "00",
  "paymentConfirmedAt": "serverTimestamp()",
  "confirmedByAdminId": "SYSTEM_BANCARD_WEBHOOK"
}
```

### 3.3 Regla de Seguridad Crítica (Zero-Trust)

- **NUNCA** guardar `public_key` ni `private_key` de Bancard dentro de la app Android (`BuildConfig`) ni en `/web-pwa`.
- **NUNCA** permitir que el cliente marque `status = 'PAYMENT_CONFIRMED'` ni `whatsappUnlocked = true` desde el navegador o APK.
- La creación del pago (`single_buy`) y la recepción del webhook (`confirmation_url`) deben vivir exclusivamente en **Cloud Functions (Node.js / Firebase Functions v2)** con credenciales en **Google Cloud / Firebase Secret Manager** (`BANCARD_PUBLIC_KEY`, `BANCARD_PRIVATE_KEY`).

---

## 4. Flujo Técnico Paso a Paso (Bancard vPOS 2.0 + Pizza Club)

```text
[Cliente Android / Web PWA]
   │ 1. Crea preorden en PENDING_PAYMENT (orders/{orderId} + order_status/{orderId})
   ▼
[Cloud Function: POST /api/payments/bancard/single-buy]
   │ 2. Valida monto real en Firestore (items * pricePyg) y genera shop_process_id único
   │ 3. Calcula token MD5: md5(private_key + shop_process_id + amount + "PYG")
   │ 4. Llama a Bancard /vpos/api/0.3/single_buy -> obtiene process_id
   ▼
[Checkout Bancard]
   │ - Web/iPhone PWA: carga bancard-checkout.js con process_id
   │ - Android Compose: abre Custom Tabs / WebView seguro con URL de retorno deep-link
   ▼
[Webhook: POST /api/payments/bancard/confirm (confirmation_url)]
   │ 5. Valida firma MD5 de Bancard:
   │    md5(private_key + shop_process_id + "confirm" + amount + currency)
   │ 6. Ejecuta db.runTransaction() con Admin SDK:
   │    a) Verifica que orders/{orderId}.status == 'PENDING_PAYMENT'
   │    b) Descuenta stock en inventory/{productId} garantizando stock >= 0
   │    c) Actualiza orders/{orderId} y order_status/{orderId}:
   │       status = 'PAYMENT_CONFIRMED', whatsappUnlocked = true
   │    d) Registra auditoría inmutable en payment_audit_logs/{logId}
   ▼
[UI Cliente (Android + Web PWA)]
   │ 7. Listener en tiempo real (order_status/{orderId}) detecta PAYMENT_CONFIRMED
   │ 8. Muestra animación estilo PedidosYa, desbloquea WhatsApp y habilita QR sorpresa
```

---

## 5. Puntos Exactos a Conectar en el Código Existente

1. **Reglas Firestore (`/firestore.rules`):**
   - Ampliar `isValidOrderPayloadFields(data)` para permitir `data.paymentMethod in ['BANK_TRANSFER', 'BANCARD_VPOS', 'BANCARD_QR', 'CASH_ON_DELIVERY']` y los campos opcionales de auditoría Bancard.
   - Mantener bloqueado el cambio a `PAYMENT_CONFIRMED` desde clientes (el webhook usa Firebase Admin SDK, que opera con privilegios de servidor).
2. **App Android:**
   - `app/src/main/java/com/example/data/FirestoreModels.kt` → agregar `enum class PaymentMethod` (`BANK_TRANSFER`, `BANCARD_VPOS`, `BANCARD_QR`).
   - `app/src/main/java/com/example/ui/customer/CheckoutScreen.kt` → selector visual de método de pago (Transferencia Itaú / Tarjeta o QR Bancard).
   - `app/src/main/java/com/example/data/PizzaClubRepository.kt` → llamada HTTP (Ktor/OkHttp) a la Cloud Function `single-buy` y apertura del checkout Bancard en Chrome Custom Tab.
3. **App Web / iPhone (`/web-pwa/index.html`):**
   - En `#checkout-modal`, agregar selector de medio de pago y montar `Bancard.Checkout.createForm(...)` cuando el cliente elija tarjeta/QR.
   - Al regresar del checkout, `order_status/{orderId}` ya reflejará `PAYMENT_CONFIRMED` en el historial de `localStorage`.

---

## 6. Checklist para Solicitar a Bancard / Desarrolladora

- [ ] Alta de comercio **Pizza Club** (Ciudad del Este) en entorno **Staging (`vpos.infonet.com.py:8888` / `staging`)** y luego **Producción**.
- [ ] Entrega de `public_key` y `private_key` (para guardar en Firebase Secrets, nunca en código).
- [ ] Activación de **vPOS Tarjetas (Visa / Mastercard / locales)** + **QR Bancard / Zimple**.
- [ ] Configuración de `return_url` (`https://gen-lang-client-0684931110.web.app/?view=history`) y `cancel_url`.
- [ ] Implementación de Cloud Functions:
  - `createBancardSingleBuy`
  - `bancardConfirmationWebhook` (con transacción atómica de stock + idempotencia)
  - `bancardRollback` (reverso automático si no hay stock al momento del webhook).
- [ ] Prueba integral end-to-end en Staging con tarjetas de prueba antes del deploy productivo final.
