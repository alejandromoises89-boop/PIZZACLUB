const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();

// Matches the provisioned enterprise database ID in firebase_applet_config.xml
const DATABASE_ID = process.env.FIRESTORE_DATABASE_ID || "ai-studio-android-pizzaclu-26d3b8a1-697e-40e6-a619-f86f22ddfb34";
const db = getFirestore(DATABASE_ID);

const BOOTSTRAP_ADMIN_EMAIL = "alejandromoises89@gmail.com";

const VALID_PREPARATION_MODES = ["Lista para comer", "Lista para hornear en casa"];
const VALID_FULFILLMENT_TYPES = ["DELIVERY", "PICKUP"];
const VALID_STATUSES = [
  "PENDING_PAYMENT",
  "PAYMENT_CONFIRMED",
  "PREPARING",
  "READY_FOR_PICKUP",
  "OUT_FOR_DELIVERY",
  "COMPLETED",
  "CANCELLED",
];

async function assertAdmin(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Debe iniciar sesión como administrador.");
  }
  const email = request.auth.token.email;
  const emailVerified = request.auth.token.email_verified === true;
  if (emailVerified && email === BOOTSTRAP_ADMIN_EMAIL) {
    return { uid: request.auth.uid, email };
  }
  const adminDoc = await db.collection("administrators").doc(request.auth.uid).get();
  if (!adminDoc.exists || adminDoc.data().active !== true) {
    throw new HttpsError("permission-denied", "Acceso exclusivo para administradores autorizados.");
  }
  return { uid: request.auth.uid, email: email || adminDoc.data().email };
}

/**
 * Customer Pre-Order Submission (No registration required).
 * Recalculates prices from Firestore products and verifies stock availability
 * without deducting inventory until an admin confirms payment.
 */
exports.submitPreOrder = onCall({ region: "southamerica-east1" }, async (request) => {
  const payload = request.data || {};
  const {
    orderId,
    customerName,
    customerPhone,
    desiredDate,
    additionalInstructions,
    preparationMode,
    fulfillmentType,
    neighborhood,
    streetAndNumber,
    deliveryReference,
    googleMapsLink,
    pickupDate,
    pickupTime,
    items,
  } = payload;

  if (!orderId || typeof orderId !== "string" || orderId.length < 20 || orderId.length > 128) {
    throw new HttpsError("invalid-argument", "ID de pedido inválido.");
  }
  if (!customerName || typeof customerName !== "string" || customerName.trim().length < 2) {
    throw new HttpsError("invalid-argument", "Nombre completo requerido.");
  }
  if (!customerPhone || typeof customerPhone !== "string" || customerPhone.trim().length < 6) {
    throw new HttpsError("invalid-argument", "Teléfono de contacto requerido.");
  }
  if (!VALID_PREPARATION_MODES.includes(preparationMode)) {
    throw new HttpsError("invalid-argument", "Modo de preparación inválido.");
  }
  if (!VALID_FULFILLMENT_TYPES.includes(fulfillmentType)) {
    throw new HttpsError("invalid-argument", "Tipo de entrega inválido.");
  }
  if (!Array.isArray(items) || items.length === 0 || items.length > 20) {
    throw new HttpsError("invalid-argument", "Debe seleccionar al menos una pizza.");
  }

  const settingsSnap = await db.collection("business_settings").doc("default").get();
  const settings = settingsSnap.exists ? settingsSnap.data() : {
    bankName: "Itaú Paraguay",
    paymentAlias: "alejandromoises89@gmail.com",
    acceptingOrders: true,
  };
  if (settings.acceptingOrders === false) {
    throw new HttpsError("failed-precondition", "El local no está recibiendo pedidos en este momento.");
  }

  return db.runTransaction(async (tx) => {
    const validatedItems = [];
    let subtotalPyg = 0;

    for (const item of items) {
      const qty = Number(item.quantity);
      if (!item.productId || !Number.isInteger(qty) || qty <= 0 || qty > 50) {
        throw new HttpsError("invalid-argument", "Cantidad inválida en el pedido.");
      }
      const prodSnap = await tx.get(db.collection("products").doc(item.productId));
      if (!prodSnap.exists || prodSnap.data().active !== true) {
        throw new HttpsError("failed-precondition", `Producto no disponible: ${item.productId}`);
      }
      const invSnap = await tx.get(db.collection("inventory").doc(item.productId));
      const availableStock = invSnap.exists ? Number(invSnap.data().stock || 0) : 0;
      if (qty > availableStock) {
        throw new HttpsError(
          "failed-precondition",
          `Stock insuficiente para ${prodSnap.data().name}. Disponible: ${availableStock}`
        );
      }

      const unitPricePyg = Number(prodSnap.data().pricePyg);
      const lineTotalPyg = unitPricePyg * qty;
      subtotalPyg += lineTotalPyg;
      validatedItems.push({
        productId: item.productId,
        productName: prodSnap.data().name,
        unitPricePyg,
        quantity: qty,
        lineTotalPyg,
      });
    }

    const orderNumber = `PC-${new Date().toISOString().slice(2, 10).replace(/-/g, "")}-${Math.floor(1000 + Math.random() * 9000)}`;
    const now = FieldValue.serverTimestamp();

    const orderDoc = {
      orderNumber,
      customerName: customerName.trim(),
      customerPhone: customerPhone.trim(),
      desiredDate: String(desiredDate || "").trim(),
      ...(additionalInstructions ? { additionalInstructions: String(additionalInstructions).trim() } : {}),
      preparationMode,
      fulfillmentType,
      ...(neighborhood ? { neighborhood: String(neighborhood).trim() } : {}),
      ...(streetAndNumber ? { streetAndNumber: String(streetAndNumber).trim() } : {}),
      ...(deliveryReference ? { deliveryReference: String(deliveryReference).trim() } : {}),
      ...(googleMapsLink ? { googleMapsLink: String(googleMapsLink).trim() } : {}),
      ...(pickupDate ? { pickupDate: String(pickupDate).trim() } : {}),
      ...(pickupTime ? { pickupTime: String(pickupTime).trim() } : {}),
      items: validatedItems,
      subtotalPyg,
      totalAmountPyg: subtotalPyg,
      status: "PENDING_PAYMENT",
      whatsappUnlocked: false,
      paymentMethod: "BANK_TRANSFER",
      paymentBank: settings.bankName || "Itaú Paraguay",
      paymentAlias: settings.paymentAlias || "alejandromoises89@gmail.com",
      createdAt: now,
      updatedAt: now,
    };

    const statusDoc = {
      orderNumber,
      status: "PENDING_PAYMENT",
      whatsappUnlocked: false,
      totalAmountPyg: subtotalPyg,
      fulfillmentType,
      createdAt: now,
      updatedAt: now,
    };

    tx.set(db.collection("orders").doc(orderId), orderDoc);
    tx.set(db.collection("order_status").doc(orderId), statusDoc);

    return { orderId, orderNumber, totalAmountPyg: subtotalPyg, status: "PENDING_PAYMENT" };
  });
});

/**
 * Manual Admin Payment Confirmation with Atomic Inventory Deduction.
 * Guarantees inventory levels NEVER drop below zero and logs an immutable audit entry.
 */
exports.confirmOrderPayment = onCall({ region: "southamerica-east1" }, async (request) => {
  const admin = await assertAdmin(request);
  const { orderId, note } = request.data || {};
  if (!orderId || typeof orderId !== "string") {
    throw new HttpsError("invalid-argument", "orderId es requerido.");
  }

  return db.runTransaction(async (tx) => {
    const orderRef = db.collection("orders").doc(orderId);
    const statusRef = db.collection("order_status").doc(orderId);
    const orderSnap = await tx.get(orderRef);

    if (!orderSnap.exists) {
      throw new HttpsError("not-found", "Pedido no encontrado.");
    }
    const order = orderSnap.data();
    if (order.status !== "PENDING_PAYMENT") {
      throw new HttpsError("failed-precondition", `El pedido ya está en estado ${order.status}.`);
    }

    // Read all inventory docs first before any writes
    const inventoryUpdates = [];
    for (const item of order.items || []) {
      const invRef = db.collection("inventory").doc(item.productId);
      const invSnap = await tx.get(invRef);
      const currentStock = invSnap.exists ? Number(invSnap.data().stock || 0) : 0;
      const newStock = currentStock - Number(item.quantity);
      if (newStock < 0) {
        throw new HttpsError(
          "failed-precondition",
          `Stock insuficiente para ${item.productName} (disponible: ${currentStock}, solicitado: ${item.quantity}).`
        );
      }
      inventoryUpdates.push({ invRef, newStock });
    }

    const now = FieldValue.serverTimestamp();
    for (const { invRef, newStock } of inventoryUpdates) {
      tx.update(invRef, {
        stock: newStock,
        updatedBy: admin.uid,
        updatedAt: now,
      });
    }

    tx.update(orderRef, {
      status: "PAYMENT_CONFIRMED",
      whatsappUnlocked: true,
      paymentConfirmedAt: now,
      confirmedByAdminId: admin.uid,
      confirmedByAdminEmail: admin.email,
      updatedAt: now,
    });

    tx.update(statusRef, {
      status: "PAYMENT_CONFIRMED",
      whatsappUnlocked: true,
      updatedAt: now,
    });

    const logRef = db.collection("payment_audit_logs").doc();
    tx.set(logRef, {
      orderId,
      orderNumber: order.orderNumber,
      adminId: admin.uid,
      adminEmail: admin.email,
      action: "CONFIRM_PAYMENT",
      previousStatus: "PENDING_PAYMENT",
      newStatus: "PAYMENT_CONFIRMED",
      amountPyg: order.totalAmountPyg,
      note: note ? String(note).slice(0, 300) : "Depósito bancario verificado manualmente",
      createdAt: now,
    });

    return { ok: true, orderId, status: "PAYMENT_CONFIRMED", whatsappUnlocked: true };
  });
});

/**
 * Admin Order Lifecycle Status Transition (or Cancellation).
 */
exports.updateOrderStatus = onCall({ region: "southamerica-east1" }, async (request) => {
  const admin = await assertAdmin(request);
  const { orderId, nextStatus, cancellationReason } = request.data || {};
  if (!orderId || !VALID_STATUSES.includes(nextStatus)) {
    throw new HttpsError("invalid-argument", "Estado o pedido inválido.");
  }

  return db.runTransaction(async (tx) => {
    const orderRef = db.collection("orders").doc(orderId);
    const statusRef = db.collection("order_status").doc(orderId);
    const snap = await tx.get(orderRef);
    if (!snap.exists) {
      throw new HttpsError("not-found", "Pedido no encontrado.");
    }
    const order = snap.data();
    const now = FieldValue.serverTimestamp();
    const whatsappUnlocked = nextStatus === "CANCELLED" ? false : order.whatsappUnlocked === true;

    tx.update(orderRef, {
      status: nextStatus,
      whatsappUnlocked,
      ...(nextStatus === "CANCELLED" && cancellationReason
        ? { cancellationReason: String(cancellationReason).slice(0, 200) }
        : {}),
      updatedAt: now,
    });
    tx.update(statusRef, {
      status: nextStatus,
      whatsappUnlocked,
      updatedAt: now,
    });
    tx.set(db.collection("payment_audit_logs").doc(), {
      orderId,
      orderNumber: order.orderNumber,
      adminId: admin.uid,
      adminEmail: admin.email,
      action: nextStatus === "CANCELLED" ? "CANCEL_ORDER" : "STATUS_CHANGE",
      previousStatus: order.status,
      newStatus: nextStatus,
      amountPyg: order.totalAmountPyg,
      note: cancellationReason || `Estado actualizado a ${nextStatus}`,
      createdAt: now,
    });

    return { ok: true, orderId, status: nextStatus };
  });
});
