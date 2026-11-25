/* ============================================================================
 * Pizza Club PWA — Full Service Worker (sw.js)
 * - Offline shell caching + network-only bypass for Supabase REST/Realtime
 * - IndexedDB state persistence (Supabase config, active orders, dedup keys)
 * - Real-time Web Push (`push`) + `pushsubscriptionchange`
 * - Background Sync (`sync`) & Periodic Background Sync (`periodicsync`)
 * - Direct Supabase `order_status` polling & diff detection when app is closed
 * ============================================================================ */

const CACHE_NAME = "pizza-club-pwa-v13";
const SHELL_ASSETS = [
  "./",
  "./index.html",
  "./admin.html",
  "./manifest.webmanifest",
  "./icon.svg",
  "./icon-192.png",
  "./icon-512.png",
  "./apple-touch-icon.png"
];

const SW_DB_NAME = "pizza_club_sw_db_v1";
const SW_STORE_NAME = "kv";
const SYNC_PERIODIC_TAG = "pizza-club-order-status-sync";
const SYNC_ONEOFF_TAG = "pizza-club-check-orders";
const TERMINAL_STATUSES = new Set(["COMPLETED", "CANCELLED"]);
const HIGH_PRIORITY_MILESTONES = new Set(["PAYMENT_CONFIRMED", "OUT_FOR_DELIVERY"]);

const STATUS_ALERT_COPY = {
  PAYMENT_CONFIRMED: {
    titlePrefix: "✅ ¡Pago confirmado!",
    body: "Verificamos tu transferencia. Tu pedido pasó a cocina y WhatsApp ya está habilitado."
  },
  PREPARING: {
    titlePrefix: "🔥 ¡Tu pizza está en el horno!",
    body: "Masa madre artesanal horneándose al momento."
  },
  READY_FOR_PICKUP: {
    titlePrefix: "📦 ¡Listo para retirar!",
    body: "Tu pedido está empaquetado y te espera en el local."
  },
  OUT_FOR_DELIVERY: {
    titlePrefix: "🛵 ¡Tu pizza va en camino!",
    body: "Nuestro repartidor salió hacia tu dirección en Ciudad del Este."
  },
  COMPLETED: {
    titlePrefix: "🍕 ¡Pedido entregado!",
    body: "¡Gracias por elegir Pizza Club! Recordá escanear el QR sorpresa de tu caja."
  },
  CANCELLED: {
    titlePrefix: "✕ Pedido cancelado",
    body: "Tu preorden fue cancelada. Escribinos por WhatsApp si necesitás asistencia."
  }
};

/* ---------------- IndexedDB helpers (shared with index.html) ---------------- */
function openSwDb() {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === "undefined") {
      reject(new Error("IndexedDB unavailable"));
      return;
    }
    const req = indexedDB.open(SW_DB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(SW_STORE_NAME)) {
        db.createObjectStore(SW_STORE_NAME);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error || new Error("IndexedDB open failed"));
  });
}

async function idbGet(key, fallbackValue = null) {
  try {
    const db = await openSwDb();
    return await new Promise((resolve) => {
      const tx = db.transaction(SW_STORE_NAME, "readonly");
      const store = tx.objectStore(SW_STORE_NAME);
      const req = store.get(key);
      req.onsuccess = () => resolve(req.result !== undefined ? req.result : fallbackValue);
      req.onerror = () => resolve(fallbackValue);
    });
  } catch {
    return fallbackValue;
  }
}

async function idbSet(key, value) {
  try {
    const db = await openSwDb();
    await new Promise((resolve, reject) => {
      const tx = db.transaction(SW_STORE_NAME, "readwrite");
      const store = tx.objectStore(SW_STORE_NAME);
      store.put(value, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // Ignore storage quota errors in restricted SW contexts
  }
}

/* ---------------- Supabase config & REST helpers in SW ---------------- */
function isValidSupabaseCfg(cfg) {
  if (!cfg || typeof cfg !== "object") return false;
  const url = String(cfg.url || "").trim().replace(/\/+$/, "");
  const anonKey = String(cfg.anonKey || "").trim();
  const placeholderRe = /YOUR_|REEMPLAZAR|PLACEHOLDER|REPLACE_|PEG[AÁ]|TU_/i;
  if (!/^https:\/\/[a-z0-9.-]+/i.test(url) || placeholderRe.test(url)) return false;
  if (anonKey.length <= 20 || placeholderRe.test(anonKey)) return false;
  return true;
}

async function loadSupabaseConfigInSw() {
  const stored = await idbGet("supabaseCfg", null);
  if (isValidSupabaseCfg(stored)) {
    return {
      url: stored.url.trim().replace(/\/+$/, ""),
      anonKey: stored.anonKey.trim(),
      vapidPublicKey: String(stored.vapidPublicKey || "").trim()
    };
  }
  try {
    const res = await fetch("./db-config.js", { cache: "no-store" });
    if (res.ok) {
      const code = await res.text();
      const urlMatch = code.match(/url\s*:\s*["']([^"']+)["']/);
      const keyMatch = code.match(/anonKey\s*:\s*["']([^"']+)["']/);
      const vapidMatch = code.match(/vapidPublicKey\s*:\s*["']([^"']*)["']/);
      const parsed = {
        url: urlMatch ? urlMatch[1] : "",
        anonKey: keyMatch ? keyMatch[1] : "",
        vapidPublicKey: vapidMatch ? vapidMatch[1] : ""
      };
      if (isValidSupabaseCfg(parsed)) {
        await idbSet("supabaseCfg", parsed);
        return parsed;
      }
    }
  } catch {
    // Offline or db-config.js unavailable
  }
  return null;
}

async function sbRestFetch(cfg, pathWithQuery, options = {}) {
  const headers = {
    apikey: cfg.anonKey,
    Authorization: `Bearer ${cfg.anonKey}`,
    "Content-Type": "application/json",
    ...(options.headers || {})
  };
  const res = await fetch(`${cfg.url}/rest/v1/${pathWithQuery}`, {
    ...options,
    headers
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`Supabase HTTP ${res.status}: ${text.slice(0, 160)}`);
  }
  const body = await res.text();
  return body ? JSON.parse(body) : null;
}

/* ---------------- Notification & transition alert helpers ---------------- */
async function hasNotifiedTransitionInSw(orderId, status) {
  const list = await idbGet("notifiedTransitions", []);
  const key = `${orderId}:${status}`;
  return Array.isArray(list) && list.includes(key);
}

async function markTransitionNotifiedInSw(orderId, status) {
  const list = await idbGet("notifiedTransitions", []);
  const key = `${orderId}:${status}`;
  const next = (Array.isArray(list) ? list : []).filter((x) => x !== key);
  next.push(key);
  await idbSet("notifiedTransitions", next.slice(-80));
}

function buildOrderAlertPayload(order, nextStatus, whatsappUnlocked, overrides = {}) {
  const copy = STATUS_ALERT_COPY[nextStatus] || {
    titlePrefix: "🍕 Actualización de tu pedido",
    body: `Estado actual: ${nextStatus}`
  };
  const orderNumber = order?.orderNumber || order?.order_number || overrides.orderNumber || "Pizza Club";
  const orderId = order?.orderId || order?.id || overrides.orderId || "";
  const title = overrides.title || `${copy.titlePrefix} (${orderNumber})`;
  const body = overrides.body || copy.body;
  const unlocked = Boolean(whatsappUnlocked || order?.whatsappUnlocked);
  const actions = [{ action: "track", title: "Ver pedido" }];
  if (unlocked && nextStatus !== "CANCELLED") {
    actions.push({ action: "whatsapp", title: "Abrir WhatsApp" });
  }

  return {
    title,
    options: {
      body,
      icon: "./icon-192.png",
      badge: "./icon-192.png",
      tag: `pizza-club-${orderId || orderNumber}-${nextStatus}`,
      renotify: true,
      requireInteraction: HIGH_PRIORITY_MILESTONES.has(nextStatus),
      vibrate: [200, 90, 220, 90, 220],
      actions,
      data: {
        orderId,
        orderNumber,
        status: nextStatus,
        whatsappUnlocked: unlocked,
        url: orderId
          ? `./?view=history&orderId=${encodeURIComponent(orderId)}`
          : "./?view=history"
      }
    }
  };
}

async function showStatusChangeNotification(order, prevStatus, nextStatus, whatsappUnlocked, overrides = {}) {
  if (!nextStatus || prevStatus === nextStatus) return false;
  const orderId = order?.orderId || overrides.orderId;
  if (orderId && (await hasNotifiedTransitionInSw(orderId, nextStatus))) {
    return false;
  }
  if (orderId) {
    await markTransitionNotifiedInSw(orderId, nextStatus);
  }
  const { title, options } = buildOrderAlertPayload(order, nextStatus, whatsappUnlocked, overrides);
  await self.registration.showNotification(title, options);
  return true;
}

async function broadcastToClients(message) {
  const clientList = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
  for (const client of clientList) {
    client.postMessage(message);
  }
}

/* ---------------- Core Supabase status polling & diff engine ---------------- */
async function checkSupabaseOrderUpdates(triggerSource = "background") {
  const cfg = await loadSupabaseConfigInSw();
  if (!cfg) return { checked: 0, changes: 0 };

  const storedOrders = await idbGet("orders", []);
  if (!Array.isArray(storedOrders) || storedOrders.length === 0) {
    return { checked: 0, changes: 0 };
  }

  const activeIds = storedOrders
    .filter((o) => o && o.orderId && !TERMINAL_STATUSES.has(o.status))
    .map((o) => o.orderId);

  if (activeIds.length === 0) {
    return { checked: 0, changes: 0 };
  }

  const inExpr = `(${activeIds.map((id) => `"${String(id).replace(/"/g, "")}"`).join(",")})`;
  const rows = await sbRestFetch(cfg, `order_status?id=in.${encodeURIComponent(inExpr)}&select=*`);
  if (!Array.isArray(rows) || rows.length === 0) {
    return { checked: activeIds.length, changes: 0 };
  }

  const rowMap = new Map(rows.map((r) => [r.id, r]));
  const nowIso = new Date().toISOString();
  const transitions = [];
  let anyUpdated = false;

  const nextOrders = storedOrders.map((order) => {
    const row = rowMap.get(order.orderId);
    if (!row) return order;

    const prevStatus = order.status;
    const nextStatus = row.status || prevStatus;
    const nextWhatsapp = Boolean(row.whatsapp_unlocked);

    if (prevStatus !== nextStatus || Boolean(order.whatsappUnlocked) !== nextWhatsapp) {
      anyUpdated = true;
    }

    const updatedOrder = {
      ...order,
      status: nextStatus,
      whatsappUnlocked: nextWhatsapp,
      cloudSynced: true,
      stageStartedAtIso: prevStatus !== nextStatus
        ? nowIso
        : (order.stageStartedAtIso || order.createdAtIso || nowIso),
      lastCheckedIso: nowIso
    };

    if (prevStatus !== nextStatus) {
      transitions.push({
        order: updatedOrder,
        prevStatus,
        nextStatus,
        whatsappUnlocked: nextWhatsapp
      });
    }

    return updatedOrder;
  });

  if (anyUpdated) {
    await idbSet("orders", nextOrders);
  }
  await idbSet("lastCheckedAtMs", Date.now());

  let alertsShown = 0;
  for (const t of transitions) {
    const shown = await showStatusChangeNotification(
      t.order,
      t.prevStatus,
      t.nextStatus,
      t.whatsappUnlocked
    );
    if (shown) alertsShown += 1;
  }

  if (anyUpdated || transitions.length > 0) {
    await broadcastToClients({
      type: "ORDER_STATUS_UPDATED",
      source: triggerSource,
      orders: nextOrders,
      transitions: transitions.map((t) => ({
        orderId: t.order.orderId,
        orderNumber: t.order.orderNumber,
        previousStatus: t.prevStatus,
        newStatus: t.nextStatus,
        whatsappUnlocked: t.whatsappUnlocked
      }))
    });
  }

  return { checked: activeIds.length, changes: transitions.length, alertsShown };
}

/* ---------------- Push payload handling (with app open or closed) ---------------- */
async function handlePushEvent(event) {
  let payload = null;
  if (event.data) {
    try {
      payload = event.data.json();
    } catch {
      try {
        const text = event.data.text();
        payload = text ? { body: text } : null;
      } catch {
        payload = null;
      }
    }
  }

  // Case 1: Empty push ping or explicit Supabase check signal -> fetch latest rows from Supabase
  if (!payload || payload.checkSupabase === true || (!payload.status && !payload.newStatus && !payload.title)) {
    try {
      const result = await checkSupabaseOrderUpdates("push");
      if (result.alertsShown > 0) return;
    } catch {
      // Fall through to generic fallback if Supabase fetch failed offline
    }
    if (!payload?.title) return;
  }

  // Case 2: Structured Supabase webhook / Edge Function push payload
  const record = payload?.record || payload?.new || payload;
  const oldRecord = payload?.old_record || payload?.old || {};
  const orderId = record?.orderId || record?.order_id || record?.id || null;
  const orderNumber = record?.orderNumber || record?.order_number || payload?.orderNumber || "Pizza Club";
  const nextStatus = record?.status || record?.newStatus || payload?.status || null;
  const prevStatus = payload?.previousStatus || oldRecord?.status || null;
  const whatsappUnlocked = Boolean(record?.whatsappUnlocked ?? record?.whatsapp_unlocked ?? payload?.whatsappUnlocked);

  if (orderId && nextStatus) {
    const storedOrders = await idbGet("orders", []);
    const existing = Array.isArray(storedOrders)
      ? storedOrders.find((o) => o.orderId === orderId)
      : null;

    const effectivePrev = prevStatus || existing?.status || null;
    const nowIso = new Date().toISOString();

    if (Array.isArray(storedOrders) && existing) {
      const nextOrders = storedOrders.map((o) =>
        o.orderId === orderId
          ? {
              ...o,
              status: nextStatus,
              whatsappUnlocked: whatsappUnlocked || Boolean(o.whatsappUnlocked),
              cloudSynced: true,
              stageStartedAtIso: o.status !== nextStatus ? nowIso : o.stageStartedAtIso,
              lastCheckedIso: nowIso
            }
          : o
      );
      await idbSet("orders", nextOrders);
      await broadcastToClients({
        type: "ORDER_STATUS_UPDATED",
        source: "push",
        orders: nextOrders,
        transitions: effectivePrev !== nextStatus
          ? [{ orderId, orderNumber, previousStatus: effectivePrev, newStatus: nextStatus, whatsappUnlocked }]
          : []
      });
    }

    await showStatusChangeNotification(
      existing || { orderId, orderNumber, whatsappUnlocked },
      effectivePrev,
      nextStatus,
      whatsappUnlocked,
      {
        orderId,
        orderNumber,
        title: payload?.title,
        body: payload?.body
      }
    );
    return;
  }

  // Case 3: Generic notification payload
  const title = payload?.title || "🍕 Actualización de Pizza Club";
  const body = payload?.body || "Tenés una novedad en tu pedido.";
  await self.registration.showNotification(title, {
    body,
    icon: "./icon-192.png",
    badge: "./icon-192.png",
    tag: payload?.tag || "pizza-club-push",
    renotify: true,
    vibrate: [200, 90, 220],
    data: {
      url: payload?.url || "./?view=history",
      orderId: payload?.orderId || null
    }
  });
}

/* ---------------- Service Worker lifecycle & event listeners ---------------- */
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  const url = event.request.url;

  // Never serve cached Supabase REST / Realtime responses
  if (url.includes("supabase.co") || url.includes("/rest/v1/")) {
    event.respondWith(fetch(event.request));
    return;
  }

  event.respondWith(
    fetch(event.request)
      .then((networkRes) => {
        if (networkRes && networkRes.ok && url.startsWith(self.location.origin)) {
          const copy = networkRes.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy)).catch(() => {});
        }
        return networkRes;
      })
      .catch(() => caches.match(event.request).then((res) => res || caches.match("./index.html")))
  );
});

self.addEventListener("push", (event) => {
  event.waitUntil(handlePushEvent(event));
});

self.addEventListener("pushsubscriptionchange", (event) => {
  event.waitUntil((async () => {
    const cfg = await loadSupabaseConfigInSw();
    if (!cfg) return;
    try {
      const rawVapid = String(cfg.vapidPublicKey || "").trim();
      const base64 = rawVapid.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (rawVapid.length % 4)) % 4);
      const bin = rawVapid ? atob(base64) : "";
      const appServerKey = rawVapid ? Uint8Array.from(bin, (ch) => ch.charCodeAt(0)) : null;
      const options = event.oldSubscription?.options || (
        appServerKey
          ? { userVisibleOnly: true, applicationServerKey: appServerKey }
          : { userVisibleOnly: true }
      );
      const newSub = await self.registration.pushManager.subscribe(options);
      const json = newSub.toJSON();
      const orders = await idbGet("orders", []);
      const orderIds = Array.isArray(orders) ? orders.map((o) => o.orderId).filter(Boolean) : [];
      const subId = "sub_" + btoa(newSub.endpoint).replace(/[^a-zA-Z0-9]/g, "").slice(-24);
      await sbRestFetch(cfg, "push_subscriptions?on_conflict=endpoint", {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
        body: JSON.stringify({
          id: subId,
          endpoint: newSub.endpoint,
          p256dh: json.keys?.p256dh || "",
          auth: json.keys?.auth || "",
          subscription: json,
          order_ids: orderIds,
          user_agent: "sw-resubscribe",
          updated_at_ms: Date.now()
        })
      });
    } catch {
      // Ignore resubscribe failure if VAPID key is unavailable
    }
  })());
});

self.addEventListener("periodicsync", (event) => {
  if (event.tag === SYNC_PERIODIC_TAG) {
    event.waitUntil(checkSupabaseOrderUpdates("periodicsync").catch(() => {}));
  }
});

self.addEventListener("sync", (event) => {
  if (event.tag === SYNC_ONEOFF_TAG) {
    event.waitUntil(checkSupabaseOrderUpdates("sync").catch(() => {}));
  }
});

self.addEventListener("message", (event) => {
  const data = event.data;
  if (!data || typeof data !== "object") return;

  if (data.type === "SKIP_WAITING") {
    self.skipWaiting();
    return;
  }

  if (data.type === "SYNC_SW_STATE") {
    event.waitUntil((async () => {
      if (data.supabaseCfg && isValidSupabaseCfg(data.supabaseCfg)) {
        await idbSet("supabaseCfg", data.supabaseCfg);
      }
      if (Array.isArray(data.orders)) {
        await idbSet("orders", data.orders);
      }
      if (Array.isArray(data.notifiedTransitions)) {
        await idbSet("notifiedTransitions", data.notifiedTransitions.slice(-80));
      }
    })());
    return;
  }

  if (data.type === "CHECK_ORDERS_NOW") {
    event.waitUntil(
      checkSupabaseOrderUpdates("manual")
        .then((res) => {
          event.ports?.[0]?.postMessage({ ok: true, ...res });
        })
        .catch((err) => {
          event.ports?.[0]?.postMessage({ ok: false, error: err?.message || "sync failed" });
        })
    );
    return;
  }

  if (data.type === "SHOW_ORDER_NOTIFICATION" && data.order && data.nextStatus) {
    event.waitUntil(
      showStatusChangeNotification(
        data.order,
        data.previousStatus || null,
        data.nextStatus,
        Boolean(data.order.whatsappUnlocked)
      )
    );
  }
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const notifData = event.notification?.data || {};
  const orderId = notifData.orderId || null;

  if (event.action === "whatsapp") {
    const waText = encodeURIComponent(
      `¡Hola Pizza Club! Te escribo por mi pedido ${notifData.orderNumber || ""} 🍕`
    );
    event.waitUntil(self.clients.openWindow(`https://wa.me/59593551936?text=${waText}`));
    return;
  }

  const targetUrl = notifData.url || "./?view=history";
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ("focus" in client) {
          client.postMessage({
            type: "OPEN_ORDER_HISTORY",
            orderId
          });
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl);
      }
      return undefined;
    })
  );
});
