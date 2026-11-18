package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.AccountingDao
import com.example.data.local.AuditLogEntity
import com.example.data.local.CashTransactionEntity
import com.example.data.local.DebtPaymentEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.data.local.StaffUserEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.StoreSettingsEntity
import com.example.data.local.UltimateDao
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@IgnoreExtraProperties
data class StoreSyncProfileDoc(
    val userId: String = "",
    val storeName: String = "",
    val managerName: String = "",
    val currencyCode: String = "SYP",
    val costingMethod: String = "WEIGHTED_AVERAGE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedProductDoc(
    val syncId: String = "",
    val name: String = "",
    val category: String = "",
    val barcode: String? = null,
    val averageCost: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val quantity: Double = 0.0,
    val minStockLevel: Double = 0.0,
    val isArchived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedPartyDoc(
    val syncId: String = "",
    val partyType: String = "",
    val name: String = "",
    val phone: String? = null,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedInvoiceDoc(
    val syncId: String = "",
    val invoiceNumber: String = "",
    val invoiceType: String = "",
    val partySyncId: String? = null,
    val partyName: String = "",
    val status: String = "",
    val paymentMethod: String = "",
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val netTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val totalCost: Double = 0.0,
    val staffName: String = "",
    val invoiceDateMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedLedgerEntryDoc(
    val syncId: String = "",
    val entryKind: String = "",
    val title: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val quantityDelta: Double = 0.0,
    val referenceId: String? = null,
    val staffName: String = "",
    val occurredAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CloudSyncSummary(
    val syncedProducts: Int = 0,
    val syncedParties: Int = 0,
    val syncedInvoices: Int = 0,
    val syncedLedgerEntries: Int = 0,
    val syncedAtMs: Long = System.currentTimeMillis()
)

data class CloudDatabaseSnapshot(
    val profile: StoreSyncProfileDoc? = null,
    val products: List<SyncedProductDoc> = emptyList(),
    val parties: List<SyncedPartyDoc> = emptyList(),
    val invoices: List<SyncedInvoiceDoc> = emptyList(),
    val ledgerEntries: List<SyncedLedgerEntryDoc> = emptyList(),
    val fetchedAtMs: Long = System.currentTimeMillis()
)

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google قبل المزامنة السحابية.")
    }

    fun observeStoreProfile(): Flow<StoreSyncProfileDoc?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(StoreSyncProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeSyncedProducts(): Flow<List<SyncedProductDoc>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("products")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(SyncedProductDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertStoreProfile(settings: StoreSettingsEntity): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "storeName" to settings.storeName.take(120).ifBlank { "خردوات المامو" },
                    "managerName" to settings.managerName.take(120).ifBlank { "أحمد نوار المامو" },
                    "currencyCode" to settings.currencyCode.take(12).ifBlank { "SYP" },
                    "costingMethod" to settings.costingMethod,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "storeName" to settings.storeName.take(120).ifBlank { "خردوات المامو" },
                    "managerName" to settings.managerName.take(120).ifBlank { "أحمد نوار المامو" },
                    "currencyCode" to settings.currencyCode.take(12).ifBlank { "SYP" },
                    "costingMethod" to settings.costingMethod,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun pushProductIdempotent(product: ProductEntity): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("products").document(product.syncId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("syncId", product.syncId)
                    put("name", product.name.take(160))
                    put("category", product.category.take(100).ifBlank { "عام" })
                    if (product.barcode.isNotBlank()) put("barcode", product.barcode.take(80))
                    put("averageCost", product.averageCost.coerceAtLeast(0.0))
                    put("sellingPrice", product.sellingPrice.coerceAtLeast(0.0))
                    put("quantity", product.quantity)
                    put("minStockLevel", product.minStockLevel.coerceAtLeast(0.0))
                    put("isArchived", product.isArchived)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updatePayload = buildMap<String, Any> {
                    put("name", product.name.take(160))
                    put("category", product.category.take(100).ifBlank { "عام" })
                    if (product.barcode.isNotBlank()) put("barcode", product.barcode.take(80))
                    put("averageCost", product.averageCost.coerceAtLeast(0.0))
                    put("sellingPrice", product.sellingPrice.coerceAtLeast(0.0))
                    put("quantity", product.quantity)
                    put("minStockLevel", product.minStockLevel.coerceAtLeast(0.0))
                    put("isArchived", product.isArchived)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun pushPartyIdempotent(party: PartyEntity): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("parties").document(party.syncId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("syncId", party.syncId)
                    put("partyType", party.partyType)
                    put("name", party.name.take(160))
                    if (party.phone.isNotBlank()) put("phone", party.phone.take(40))
                    put("totalAmount", party.totalAmount.coerceAtLeast(0.0))
                    put("paidAmount", party.paidAmount.coerceAtLeast(0.0))
                    put("balanceDue", party.balanceDue)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updatePayload = buildMap<String, Any> {
                    put("name", party.name.take(160))
                    if (party.phone.isNotBlank()) put("phone", party.phone.take(40))
                    put("totalAmount", party.totalAmount.coerceAtLeast(0.0))
                    put("paidAmount", party.paidAmount.coerceAtLeast(0.0))
                    put("balanceDue", party.balanceDue)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun pushInvoiceIdempotent(invoice: InvoiceEntity): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("invoices").document(invoice.syncId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("syncId", invoice.syncId)
                    put("invoiceNumber", invoice.invoiceNumber.take(60))
                    put("invoiceType", invoice.invoiceType)
                    if (invoice.partySyncId.isNotBlank()) put("partySyncId", invoice.partySyncId.take(128))
                    put("partyName", invoice.partyName.take(160).ifBlank { "زبون نقدي" })
                    put("status", invoice.status)
                    put("paymentMethod", invoice.paymentMethod)
                    put("subtotal", invoice.subtotal.coerceAtLeast(0.0))
                    put("discountAmount", invoice.discountAmount.coerceAtLeast(0.0))
                    put("netTotal", invoice.netTotal.coerceAtLeast(0.0))
                    put("paidAmount", invoice.paidAmount.coerceAtLeast(0.0))
                    put("remainingAmount", invoice.remainingAmount.coerceAtLeast(0.0))
                    put("totalCost", invoice.totalCost.coerceAtLeast(0.0))
                    put("staffName", invoice.staffName.take(120).ifBlank { "أحمد نوار المامو" })
                    put("invoiceDateMs", invoice.invoiceDateMs.coerceAtLeast(1L))
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else if (existing.getString("status") == "POSTED") {
                val updatePayload = buildMap<String, Any> {
                    if (invoice.partySyncId.isNotBlank()) put("partySyncId", invoice.partySyncId.take(128))
                    put("partyName", invoice.partyName.take(160).ifBlank { "زبون نقدي" })
                    put("status", invoice.status)
                    put("paymentMethod", invoice.paymentMethod)
                    put("subtotal", invoice.subtotal.coerceAtLeast(0.0))
                    put("discountAmount", invoice.discountAmount.coerceAtLeast(0.0))
                    put("netTotal", invoice.netTotal.coerceAtLeast(0.0))
                    put("paidAmount", invoice.paidAmount.coerceAtLeast(0.0))
                    put("remainingAmount", invoice.remainingAmount.coerceAtLeast(0.0))
                    put("totalCost", invoice.totalCost.coerceAtLeast(0.0))
                    put("staffName", invoice.staffName.take(120).ifBlank { "أحمد نوار المامو" })
                    put("invoiceDateMs", invoice.invoiceDateMs.coerceAtLeast(1L))
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun pushLedgerEntryIdempotent(entry: SyncedLedgerEntryDoc): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("ledger_entries").document(entry.syncId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("syncId", entry.syncId)
                    put("entryKind", entry.entryKind)
                    put("title", entry.title.take(240).ifBlank { "-" })
                    put("category", entry.category.take(100).ifBlank { "GENERAL" })
                    put("amount", entry.amount)
                    put("quantityDelta", entry.quantityDelta)
                    if (!entry.referenceId.isNullOrBlank()) put("referenceId", entry.referenceId.take(128))
                    put("staffName", entry.staffName.take(120).ifBlank { "أحمد نوار المامو" })
                    put("occurredAtMs", entry.occurredAtMs.coerceAtLeast(1L))
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else if (entry.entryKind == "STAFF_USER") {
                val updatePayload = mapOf(
                    "title" to entry.title.take(240).ifBlank { "-" },
                    "category" to entry.category.take(100).ifBlank { "GENERAL" },
                    "amount" to entry.amount,
                    "staffName" to entry.staffName.take(120).ifBlank { "أحمد نوار المامو" },
                    "occurredAtMs" to entry.occurredAtMs.coerceAtLeast(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncAllUnsyncedFromRoom(
        dao: AccountingDao,
        ultimateDao: UltimateDao? = null
    ): Result<CloudSyncSummary> {
        return try {
            requireUserId()
            val settings = dao.getStoreSettings() ?: StoreSettingsEntity()
            upsertStoreProfile(settings).getOrThrow()
            val now = System.currentTimeMillis()

            var productsCount = 0
            for (product in dao.getAllProducts().filter { !it.isSynced }) {
                pushProductIdempotent(product).getOrThrow()
                dao.markProductSynced(product.syncId)
                ultimateDao?.markEntitySyncedInQueue(product.syncId, now)
                productsCount++
            }

            var partiesCount = 0
            for (party in dao.getAllParties().filter { !it.isSynced }) {
                pushPartyIdempotent(party).getOrThrow()
                dao.markPartySynced(party.syncId)
                ultimateDao?.markEntitySyncedInQueue(party.syncId, now)
                partiesCount++
            }

            var invoicesCount = 0
            for (invoice in dao.getAllInvoices().filter { !it.isSynced }) {
                pushInvoiceIdempotent(invoice).getOrThrow()
                dao.markInvoiceSynced(invoice.syncId)
                ultimateDao?.markEntitySyncedInQueue(invoice.syncId, now)
                invoicesCount++
            }

            var ledgerCount = 0
            for (tx in dao.getAllCashTransactions().filter { !it.isSynced }) {
                pushLedgerEntryIdempotent(
                    SyncedLedgerEntryDoc(
                        syncId = tx.syncId,
                        entryKind = "CASH_TX",
                        title = tx.description,
                        category = tx.txType,
                        amount = tx.amountSigned,
                        quantityDelta = 0.0,
                        referenceId = tx.referenceId.ifBlank { null },
                        staffName = tx.staffName,
                        occurredAtMs = tx.occurredAtMs
                    )
                ).getOrThrow()
                dao.markCashTxSynced(tx.syncId)
                ultimateDao?.markEntitySyncedInQueue(tx.syncId, now)
                ledgerCount++
            }

            for (exp in dao.getAllExpenses().filter { !it.isSynced }) {
                pushLedgerEntryIdempotent(
                    SyncedLedgerEntryDoc(
                        syncId = exp.syncId,
                        entryKind = "EXPENSE",
                        title = exp.description.ifBlank { exp.category },
                        category = exp.category,
                        amount = exp.amount,
                        quantityDelta = 0.0,
                        referenceId = null,
                        staffName = exp.staffName,
                        occurredAtMs = exp.expenseDateMs
                    )
                ).getOrThrow()
                dao.markExpenseSynced(exp.syncId)
                ultimateDao?.markEntitySyncedInQueue(exp.syncId, now)
                ledgerCount++
            }

            for (pay in dao.getAllDebtPayments().filter { !it.isSynced }) {
                pushLedgerEntryIdempotent(
                    SyncedLedgerEntryDoc(
                        syncId = pay.syncId,
                        entryKind = "DEBT_PAYMENT",
                        title = "${pay.partyName} - ${pay.notes.ifBlank { "دفعة دين" }}",
                        category = pay.partyType,
                        amount = pay.amount,
                        quantityDelta = 0.0,
                        referenceId = pay.partySyncId,
                        staffName = pay.staffName,
                        occurredAtMs = pay.paymentDateMs
                    )
                ).getOrThrow()
                dao.markDebtPaymentSynced(pay.syncId)
                ultimateDao?.markEntitySyncedInQueue(pay.syncId, now)
                ledgerCount++
            }

            for (mv in dao.getAllStockMovements().filter { !it.isSynced }) {
                pushLedgerEntryIdempotent(
                    SyncedLedgerEntryDoc(
                        syncId = mv.syncId,
                        entryKind = "STOCK_MOVEMENT",
                        title = "${mv.productName}: ${mv.reason}",
                        category = mv.movementType,
                        amount = mv.unitCost,
                        quantityDelta = mv.quantityDelta,
                        referenceId = mv.productSyncId,
                        staffName = mv.staffName,
                        occurredAtMs = mv.occurredAtMs
                    )
                ).getOrThrow()
                dao.markStockMovementSynced(mv.syncId)
                ultimateDao?.markEntitySyncedInQueue(mv.syncId, now)
                ledgerCount++
            }

            for (staff in dao.getAllStaffUsers().filter { !it.isSynced }) {
                pushLedgerEntryIdempotent(
                    SyncedLedgerEntryDoc(
                        syncId = staff.syncId,
                        entryKind = "STAFF_USER",
                        title = staff.name,
                        category = staff.role,
                        amount = if (staff.isActive) 1.0 else 0.0,
                        quantityDelta = 0.0,
                        referenceId = null,
                        staffName = staff.name,
                        occurredAtMs = staff.createdAtMs
                    )
                ).getOrThrow()
                dao.markStaffSynced(staff.syncId)
                ultimateDao?.markEntitySyncedInQueue(staff.syncId, now)
                ledgerCount++
            }

            for (aud in dao.getAllAuditLogs().filter { !it.isSynced }) {
                pushLedgerEntryIdempotent(
                    SyncedLedgerEntryDoc(
                        syncId = aud.syncId,
                        entryKind = "AUDIT_LOG",
                        title = "${aud.action}: ${aud.details}",
                        category = aud.entityType,
                        amount = 0.0,
                        quantityDelta = 0.0,
                        referenceId = aud.entityId.ifBlank { null },
                        staffName = aud.staffName,
                        occurredAtMs = aud.occurredAtMs
                    )
                ).getOrThrow()
                dao.markAuditLogSynced(aud.syncId)
                ultimateDao?.markEntitySyncedInQueue(aud.syncId, now)
                ledgerCount++
            }

            if (ultimateDao != null) {
                for (q in ultimateDao.getPendingSyncItems()) {
                    ultimateDao.markEntitySyncedInQueue(q.entitySyncId, now)
                }
            }

            val doneAt = System.currentTimeMillis()
            dao.upsertStoreSettings(settings.copy(lastSyncAtMs = doneAt, updatedAtMs = doneAt))
            Result.success(
                CloudSyncSummary(
                    syncedProducts = productsCount,
                    syncedParties = partiesCount,
                    syncedInvoices = invoicesCount,
                    syncedLedgerEntries = ledgerCount,
                    syncedAtMs = doneAt
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (ultimateDao != null) {
                val failNow = System.currentTimeMillis()
                for (q in ultimateDao.getPendingSyncItems()) {
                    ultimateDao.updateSyncQueueItem(
                        q.copy(
                            status = "FAILED",
                            retryCount = q.retryCount + 1,
                            lastError = (e.localizedMessage ?: "فشل المزامنة").take(240),
                            updatedAtMs = failNow
                        )
                    )
                }
            }
            Result.failure(e)
        }
    }

    suspend fun fetchCloudDatabaseSnapshot(): Result<CloudDatabaseSnapshot> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val userDoc = db.collection("users").document(uid)
        return try {
            val profileSnap = userDoc.get().await()
            val profile = if (profileSnap.exists()) {
                profileSnap.toObject(StoreSyncProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null

            val productsSnap = userDoc.collection("products").get().await()
            val partiesSnap = userDoc.collection("parties").get().await()
            val invoicesSnap = userDoc.collection("invoices").get().await()
            val ledgerSnap = userDoc.collection("ledger_entries").get().await()

            Result.success(
                CloudDatabaseSnapshot(
                    profile = profile,
                    products = productsSnap.toObjects(SyncedProductDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                    parties = partiesSnap.toObjects(SyncedPartyDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                    invoices = invoicesSnap.toObjects(SyncedInvoiceDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                    ledgerEntries = ledgerSnap.toObjects(SyncedLedgerEntryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                    fetchedAtMs = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, userDoc.path, auth)
            Result.failure(e)
        }
    }

    private suspend fun deleteCollectionInBatches(
        colRef: com.google.firebase.firestore.CollectionReference,
        batchSize: Int = 350
    ): Int {
        var totalDeleted = 0
        while (true) {
            val snap = colRef.limit(batchSize.toLong()).get().await()
            if (snap.isEmpty) break
            val batch = db.batch()
            for (doc in snap.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            totalDeleted += snap.size()
            if (snap.size() < batchSize) break
        }
        return totalDeleted
    }

    suspend fun deleteCloudData(
        collections: Set<CloudCollectionKind>,
        wipeProfile: Boolean = false
    ): Result<CloudDeleteResult> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val userDoc = db.collection("users").document(uid)
        return try {
            var productsDeleted = 0
            var partiesDeleted = 0
            var invoicesDeleted = 0
            var ledgerDeleted = 0
            var profileDeleted = false

            if (CloudCollectionKind.PRODUCTS in collections) {
                productsDeleted = deleteCollectionInBatches(userDoc.collection(CloudCollectionKind.PRODUCTS.firestoreCollection))
            }
            if (CloudCollectionKind.PARTIES in collections) {
                partiesDeleted = deleteCollectionInBatches(userDoc.collection(CloudCollectionKind.PARTIES.firestoreCollection))
            }
            if (CloudCollectionKind.INVOICES in collections) {
                invoicesDeleted = deleteCollectionInBatches(userDoc.collection(CloudCollectionKind.INVOICES.firestoreCollection))
            }
            if (CloudCollectionKind.LEDGER_ENTRIES in collections) {
                ledgerDeleted = deleteCollectionInBatches(userDoc.collection(CloudCollectionKind.LEDGER_ENTRIES.firestoreCollection))
            }
            if (wipeProfile) {
                val prof = userDoc.get().await()
                if (prof.exists()) {
                    userDoc.delete().await()
                    profileDeleted = true
                }
            }
            Result.success(
                CloudDeleteResult(
                    deletedProducts = productsDeleted,
                    deletedParties = partiesDeleted,
                    deletedInvoices = invoicesDeleted,
                    deletedLedgerEntries = ledgerDeleted,
                    deletedProfile = profileDeleted
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDoc.path, auth)
            Result.failure(e)
        }
    }
}

enum class CloudCollectionKind(val firestoreCollection: String, val arabicLabel: String) {
    PRODUCTS("products", "الأصناف والمخزون"),
    PARTIES("parties", "الزبائن والموردين"),
    INVOICES("invoices", "الفواتير والمبيعات"),
    LEDGER_ENTRIES("ledger_entries", "القيود والصندوق والموظفين")
}

data class CloudDeleteResult(
    val deletedProducts: Int = 0,
    val deletedParties: Int = 0,
    val deletedInvoices: Int = 0,
    val deletedLedgerEntries: Int = 0,
    val deletedProfile: Boolean = false,
    val deletedAtMs: Long = System.currentTimeMillis()
) {
    val totalDeletedDocs: Int
        get() = deletedProducts + deletedParties + deletedInvoices + deletedLedgerEntries + if (deletedProfile) 1 else 0
}
