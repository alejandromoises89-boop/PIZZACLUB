/**
 * Initial Firestore Seeding Script for Pizza Club (Ciudad del Este, Paraguay)
 *
 * Populates:
 *  - products/{productId} (Pepperoni, Mozzarella, Cuatro quesos, Pollo Catupiri)
 *  - inventory/{productId} (20 units initial stock per flavor)
 *  - business_settings/default (Brand, Itaú Paraguay alias, WhatsApp, PYG)
 *
 * Usage (Emulator):
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 node scripts/seed-firestore.js
 *
 * Usage (Production with Service Account):
 *   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json node scripts/seed-firestore.js
 */

const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const PROJECT_ID = process.env.GCP_PROJECT || "gen-lang-client-0684931110";
const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-pizzaclu-26d3b8a1-697e-40e6-a619-f86f22ddfb34";

initializeApp({
  projectId: PROJECT_ID,
  ...(process.env.FIRESTORE_EMULATOR_HOST ? {} : { credential: applicationDefault() }),
});

const db = getFirestore(DATABASE_ID);

const INITIAL_PRODUCTS = [
  {
    id: "pepperoni",
    name: "Pepperoni",
    description:
      "Salsa de tomate San Marzano, mozzarella fior di latte y pepperoni artesanal crocante.",
    pricePyg: 50000,
    active: true,
    imageKey: "pepperoni",
    sortOrder: 1,
    initialStock: 20,
  },
  {
    id: "mozzarella",
    name: "Mozzarella",
    description:
      "Clásica italiana con doble mozzarella cremosa, tomate natural, albahaca fresca y oliva.",
    pricePyg: 50000,
    active: true,
    imageKey: "mozzarella",
    sortOrder: 2,
    initialStock: 20,
  },
  {
    id: "four_cheeses",
    name: "Cuatro quesos",
    description:
      "Mozzarella, gorgonzola, parmesano estacionado y provolone con pimienta negra.",
    pricePyg: 50000,
    active: true,
    imageKey: "four_cheeses",
    sortOrder: 3,
    initialStock: 20,
  },
  {
    id: "chicken_catupiry",
    name: "Pollo Catupiri",
    description:
      "Pollo desmenuzado sazonado, queso cremoso Catupiry original, mozzarella y orégano.",
    pricePyg: 50000,
    active: true,
    imageKey: "chicken_catupiry",
    sortOrder: 4,
    initialStock: 20,
  },
];

async function seed() {
  const batch = db.batch();
  const now = FieldValue.serverTimestamp();

  for (const prod of INITIAL_PRODUCTS) {
    batch.set(
      db.collection("products").doc(prod.id),
      {
        name: prod.name,
        description: prod.description,
        pricePyg: prod.pricePyg,
        active: prod.active,
        imageKey: prod.imageKey,
        sortOrder: prod.sortOrder,
        createdAt: now,
        updatedAt: now,
      },
      { merge: true }
    );

    batch.set(
      db.collection("inventory").doc(prod.id),
      {
        productName: prod.name,
        stock: prod.initialStock,
        lowStockThreshold: 5,
        updatedBy: "seed_script",
        updatedAt: now,
      },
      { merge: true }
    );
  }

  batch.set(
    db.collection("business_settings").doc("default"),
    {
      brandName: "Pizza Club",
      instagramHandle: "@Pizza.Club1",
      whatsappNumber: "59593551936",
      bankName: "Itaú Paraguay",
      paymentAlias: "alejandromoises89@gmail.com",
      deliveryCity: "Ciudad del Este",
      currency: "PYG",
      acceptingOrders: true,
      updatedAt: now,
    },
    { merge: true }
  );

  await batch.commit();
  console.log(`Seeded ${INITIAL_PRODUCTS.length} products, inventory, and business_settings into ${DATABASE_ID}.`);
}

seed().catch((err) => {
  console.error("Seed failed:", err);
  process.exit(1);
});
