const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_alejo_001";
const ADMIN_EMAIL = "alejandromoises89@gmail.com";
const OTHER_UID = "customer_user_999";
const VALID_ORDER_ID = "ord_a1b2c3d4e5f6g7h8i9j0k1l2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext(verified = true) {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: verified,
  });
}

function buildValidOrderPayload(overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    orderNumber: "PC-261124-1001",
    customerName: "Carlos Benitez",
    customerPhone: "0981123456",
    desiredDate: "2026-11-24",
    additionalInstructions: "Sin oregano",
    preparationMode: "Lista para comer",
    fulfillmentType: "DELIVERY",
    neighborhood: "Area 1",
    streetAndNumber: "Av. del Lago 450",
    deliveryReference: "Porton negro",
    googleMapsLink: "https://maps.google.com/?q=-25.5097,-54.6111",
    items: [
      {
        productId: "pepperoni",
        productName: "Pepperoni",
        unitPricePyg: 50000,
        quantity: 2,
        lineTotalPyg: 100000,
      },
    ],
    subtotalPyg: 100000,
    totalAmountPyg: 100000,
    status: "PENDING_PAYMENT",
    whatsappUnlocked: false,
    paymentMethod: "BANK_TRANSFER",
    paymentBank: "Itaú Paraguay",
    paymentAlias: "alejandromoises89@gmail.com",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildValidOrderStatusPayload(overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    orderNumber: "PC-261124-1001",
    status: "PENDING_PAYMENT",
    whatsappUnlocked: false,
    totalAmountPyg: 100000,
    fulfillmentType: "DELIVERY",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Catalog & Inventory: public read works, non-admin writes rejected", async () => {
  const now = new Date(Date.now() - 1000);
  const adminDb = adminContext(true).firestore();
  await assertSucceeds(
    adminDb.collection("products").doc("pepperoni").set({
      name: "Pepperoni",
      description: "Pepperoni artesanal con mozzarella",
      pricePyg: 50000,
      active: true,
      imageKey: "pepperoni",
      sortOrder: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("inventory").doc("pepperoni").set({
      productName: "Pepperoni",
      stock: 20,
      lowStockThreshold: 5,
      updatedBy: ADMIN_UID,
      updatedAt: now,
    })
  );

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("products").get());
  await assertSucceeds(unauthDb.collection("inventory").get());
  await assertFails(
    unauthDb.collection("products").doc("pepperoni").update({ pricePyg: 1 })
  );
  await assertFails(
    unauthDb.collection("inventory").doc("pepperoni").update({ stock: 999 })
  );
});

test("Customer pre-order: atomic batch create succeeds with PENDING_PAYMENT; forged status or missing tracker fails", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  // Valid atomic batch (orders + order_status)
  const batch = unauthDb.batch();
  batch.set(unauthDb.collection("orders").doc(VALID_ORDER_ID), buildValidOrderPayload());
  batch.set(unauthDb.collection("order_status").doc(VALID_ORDER_ID), buildValidOrderStatusPayload());
  await assertSucceeds(batch.commit());

  // Customer can read non-PII status tracker by exact ID, but CANNOT list or read PII order
  await assertSucceeds(unauthDb.collection("order_status").doc(VALID_ORDER_ID).get());
  await assertFails(unauthDb.collection("order_status").get());
  await assertFails(unauthDb.collection("orders").doc(VALID_ORDER_ID).get());
  await assertFails(unauthDb.collection("orders").get());

  // Forged PAYMENT_CONFIRMED on create must fail
  const forgedBatch = unauthDb.batch();
  const forgedId = "ord_forged_1234567890123456";
  forgedBatch.set(
    unauthDb.collection("orders").doc(forgedId),
    buildValidOrderPayload({ status: "PAYMENT_CONFIRMED", whatsappUnlocked: true })
  );
  forgedBatch.set(
    unauthDb.collection("order_status").doc(forgedId),
    buildValidOrderStatusPayload({ status: "PAYMENT_CONFIRMED", whatsappUnlocked: true })
  );
  await assertFails(forgedBatch.commit());

  // Non-atomic single write to orders without order_status must fail
  await assertFails(
    unauthDb.collection("orders").doc("ord_single_1234567890123456").set(buildValidOrderPayload())
  );
});

test("Email spoofing & non-admin isolation: unverified email or customer cannot access admin actions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("orders").doc(VALID_ORDER_ID).set(buildValidOrderPayload());
    await db.collection("order_status").doc(VALID_ORDER_ID).set(buildValidOrderStatusPayload());
  });

  const spoofedAdminDb = adminContext(false).firestore();
  await assertFails(spoofedAdminDb.collection("orders").get());
  await assertFails(
    spoofedAdminDb.collection("orders").doc(VALID_ORDER_ID).update({
      status: "PAYMENT_CONFIRMED",
      whatsappUnlocked: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const otherUserDb = testEnv.authenticatedContext(OTHER_UID, {
    email: "attacker@example.com",
    email_verified: true,
  }).firestore();
  await assertFails(otherUserDb.collection("orders").get());
  await assertFails(otherUserDb.collection("payment_audit_logs").get());
});

test("Admin payment confirmation: transactional update unlocks WhatsApp, deducts stock, logs audit; negative stock fails", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("inventory").doc("pepperoni").set({
      productName: "Pepperoni",
      stock: 20,
      lowStockThreshold: 5,
      updatedBy: "seed",
      updatedAt: created,
    });
    await db.collection("orders").doc(VALID_ORDER_ID).set(buildValidOrderPayload({ createdAt: created, updatedAt: created }));
    await db.collection("order_status").doc(VALID_ORDER_ID).set(buildValidOrderStatusPayload({ createdAt: created, updatedAt: created }));
  });

  const adminDb = adminContext(true).firestore();
  const now = new Date(Date.now() - 1000);

  // Admin confirms payment + updates status tracker + deducts stock + writes audit log
  const confirmBatch = adminDb.batch();
  confirmBatch.update(adminDb.collection("orders").doc(VALID_ORDER_ID), {
    status: "PAYMENT_CONFIRMED",
    whatsappUnlocked: true,
    paymentConfirmedAt: now,
    confirmedByAdminId: ADMIN_UID,
    confirmedByAdminEmail: ADMIN_EMAIL,
    updatedAt: now,
  });
  confirmBatch.update(adminDb.collection("order_status").doc(VALID_ORDER_ID), {
    status: "PAYMENT_CONFIRMED",
    whatsappUnlocked: true,
    updatedAt: now,
  });
  confirmBatch.update(adminDb.collection("inventory").doc("pepperoni"), {
    stock: 18,
    updatedBy: ADMIN_UID,
    updatedAt: now,
  });
  confirmBatch.set(adminDb.collection("payment_audit_logs").doc("log_001"), {
    orderId: VALID_ORDER_ID,
    orderNumber: "PC-261124-1001",
    adminId: ADMIN_UID,
    adminEmail: ADMIN_EMAIL,
    action: "CONFIRM_PAYMENT",
    previousStatus: "PENDING_PAYMENT",
    newStatus: "PAYMENT_CONFIRMED",
    amountPyg: 100000,
    note: "Transferencia Itaú verificada",
    createdAt: now,
  });
  await assertSucceeds(confirmBatch.commit());

  // Negative inventory must ALWAYS fail
  await assertFails(
    adminDb.collection("inventory").doc("pepperoni").update({
      stock: -1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Audit logs are immutable
  await assertFails(
    adminDb.collection("payment_audit_logs").doc("log_001").update({ amountPyg: 0 })
  );
  await assertFails(
    adminDb.collection("payment_audit_logs").doc("log_001").delete()
  );
});

test("Business settings: admin can configure treasureCode and treasureReward; non-admin rejected", async () => {
  const adminDb = adminContext(true).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    adminDb.collection("business_settings").doc("default").set({
      brandName: "Pizza Club",
      instagramHandle: "@Pizza.Club1",
      whatsappNumber: "59593551936",
      bankName: "Itaú Paraguay",
      paymentAlias: "alejandromoises89@gmail.com",
      deliveryCity: "Ciudad del Este",
      currency: "PYG",
      acceptingOrders: true,
      treasureCode: "PIZZACLUB-ORO",
      treasureReward: "Dip Artesanal + 10% OFF en tu próxima pizza",
      googleReviewUrl: "https://www.google.com/maps/search/?api=1&query=Pizza+Club+Ciudad+del+Este+Paraguay",
      pwaInstallUrl: "https://gen-lang-client-0684931110.web.app",
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("business_settings").doc("default").update({
      treasureCode: "TESORO-CDE",
      treasureReward: "Bebida 1.5L gratis en tu próxima orden",
      googleReviewUrl: "https://g.page/r/pizzaclub/review",
      pwaInstallUrl: "https://pizzaclub.py",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(unauthDb.collection("business_settings").doc("default").get());
  await assertFails(
    unauthDb.collection("business_settings").doc("default").update({
      treasureCode: "HACKED",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
