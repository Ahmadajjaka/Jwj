const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_owner_1";
const BOB_UID = "bob_owner_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTs() {
  return new Date(Date.now() - 5000);
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

test("Unauthenticated caller cannot read or write store data", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1").set({
    syncId: "inv_1",
  }));
});

test("Authenticated owner can create and update StoreSyncProfile, and cannot inject ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set({
    userId: ALICE_UID,
    storeName: "خردوات المامو",
    managerName: "أحمد نوار المامو",
    currencyCode: "SYP",
    costingMethod: "WEIGHTED_AVERAGE",
    createdAt: serverTs(),
    updatedAt: serverTs(),
  }));

  await assertSucceeds(profileRef.update({
    currencyCode: "USD",
    updatedAt: serverTs(),
  }));

  // Shadow update with ghost field must fail
  await assertFails(profileRef.update({
    isAdmin: true,
    updatedAt: serverTs(),
  }));
});

test("Cross-user isolation blocks Bob from reading or writing Alice's store", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set({
    syncId: "prod_1",
    name: "مفك براغي",
    category: "عدد يدوية",
    averageCost: 5000,
    sellingPrice: 7500,
    quantity: 10,
    minStockLevel: 2,
    isArchived: false,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_2").set({
    syncId: "prod_2",
    name: "مطرقة",
    category: "عدد يدوية",
    averageCost: 10000,
    sellingPrice: 15000,
    quantity: 5,
    minStockLevel: 1,
    isArchived: false,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  }));
});

test("Owner can delete own invoices while CANCELLED invoices remain update-locked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const invRef = aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_100");

  await assertSucceeds(invRef.set({
    syncId: "inv_100",
    invoiceNumber: "S-0001",
    invoiceType: "SALE",
    partyName: "زبون نقدي",
    status: "POSTED",
    paymentMethod: "CASH",
    subtotal: 20000,
    discountAmount: 0,
    netTotal: 20000,
    paidAmount: 20000,
    remainingAmount: 0,
    totalCost: 12000,
    staffName: "أحمد نوار المامو",
    invoiceDateMs: 1763200000000,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  }));

  // Cancel transition allowed
  await assertSucceeds(invRef.update({
    status: "CANCELLED",
    updatedAt: serverTs(),
  }));

  // Further update after CANCELLED must fail
  await assertFails(invRef.update({
    status: "POSTED",
    updatedAt: serverTs(),
  }));

  // Cross-user and unauthenticated delete must fail; owner delete succeeds
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_100").delete());
  const anonDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(anonDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_100").delete());
  await assertSucceeds(invRef.delete());
});

test("Ledger audit entries are immutable to edits, deletable by owner only, while STAFF_USER entries can update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const auditRef = aliceDb.collection("users").doc(ALICE_UID).collection("ledger_entries").doc("aud_1");
  const staffRef = aliceDb.collection("users").doc(ALICE_UID).collection("ledger_entries").doc("staff_1");

  await assertSucceeds(auditRef.set({
    syncId: "aud_1",
    entryKind: "AUDIT_LOG",
    title: "إنشاء فاتورة بيع",
    category: "SALES",
    amount: 20000,
    quantityDelta: -2,
    referenceId: "inv_100",
    staffName: "أحمد نوار المامو",
    occurredAtMs: 1763200000000,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  }));

  // Audit log cannot be mutated; cross-user delete fails; owner delete succeeds
  await assertFails(auditRef.update({ title: "تعديل مشبوه", updatedAt: serverTs() }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("ledger_entries").doc("aud_1").delete());
  await assertSucceeds(auditRef.delete());

  await assertSucceeds(staffRef.set({
    syncId: "staff_1",
    entryKind: "STAFF_USER",
    title: "محاسب المتجر",
    category: "ACCOUNTANT",
    amount: 0,
    quantityDelta: 0,
    staffName: "محمود",
    occurredAtMs: 1763200000000,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  }));

  await assertSucceeds(staffRef.update({
    category: "OWNER",
    updatedAt: serverTs(),
  }));
});

test("Owner can delete own products, parties, and profile while cross-user delete fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  const prodRef = profileRef.collection("products").doc("prod_del");
  const partyRef = profileRef.collection("parties").doc("party_del");

  await assertSucceeds(profileRef.set({
    userId: ALICE_UID,
    storeName: "خردوات المامو",
    managerName: "أحمد نوار المامو",
    currencyCode: "SYP",
    costingMethod: "WEIGHTED_AVERAGE",
    createdAt: serverTs(),
    updatedAt: serverTs(),
  }));
  await assertSucceeds(prodRef.set({
    syncId: "prod_del",
    name: "مفتاح إنجليزي",
    category: "عدد",
    averageCost: 1000,
    sellingPrice: 1500,
    quantity: 5,
    minStockLevel: 1,
    isArchived: false,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  }));
  await assertSucceeds(partyRef.set({
    syncId: "party_del",
    partyType: "CUSTOMER",
    name: "زبون",
    totalAmount: 0,
    paidAmount: 0,
    balanceDue: 0,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  }));

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_del").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("parties").doc("party_del").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());

  await assertSucceeds(prodRef.delete());
  await assertSucceeds(partyRef.delete());
  await assertSucceeds(profileRef.delete());
});
