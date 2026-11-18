package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.PaymentMethod
import com.example.data.local.ProductEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.StoreSettingsEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun authenticatedOwner_canSyncStoreProfileAndProductIdempotently() = runBlocking {
        val uid = signInTestUser("owner_mamo@test.com")
        val repo = FirestoreSyncRepository(firestore, auth)

        val settings = StoreSettingsEntity(
            storeName = "خردوات المامو",
            managerName = "أحمد نوار المامو",
            currencyCode = "SYP"
        )
        val profileRes = withTimeout(5000L) { repo.upsertStoreProfile(settings) }
        assertTrue(profileRes.isSuccess)

        val productSyncId = "prod_${UUID.randomUUID().toString().replace("-", "")}"
        val product = ProductEntity(
            syncId = productSyncId,
            name = "مفتاح إنكليزي 12 إنش",
            category = "عدد يدوية",
            barcode = "123456789",
            averageCost = 25000.0,
            lastPurchaseCost = 25000.0,
            sellingPrice = 35000.0,
            quantity = 12.0,
            minStockLevel = 3.0
        )

        val pushFirst = withTimeout(5000L) { repo.pushProductIdempotent(product) }
        assertTrue(pushFirst.isSuccess)

        // Idempotent second push updates same document without creating duplicates
        val pushSecond = withTimeout(5000L) { repo.pushProductIdempotent(product.copy(quantity = 10.0)) }
        assertTrue(pushSecond.isSuccess)

        val productsSnap = withTimeout(5000L) {
            firestore.collection("users").document(uid).collection("products").get().await()
        }
        assertEquals(1, productsSnap.documents.count { it.id == productSyncId })
    }

    @Test
    fun crossUserRead_isDeniedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_store@test.com")
        val aliceRepo = FirestoreSyncRepository(firestore, auth)
        val invSyncId = "inv_${UUID.randomUUID().toString().replace("-", "")}"
        val inv = InvoiceEntity(
            syncId = invSyncId,
            invoiceNumber = "S-0001",
            invoiceType = InvoiceType.SALE,
            partyName = "زبون نقدي",
            status = InvoiceStatus.POSTED,
            paymentMethod = PaymentMethod.CASH,
            subtotal = 50000.0,
            discountAmount = 0.0,
            netTotal = 50000.0,
            paidAmount = 50000.0,
            remainingAmount = 0.0,
            totalCost = 30000.0,
            staffId = 1L,
            staffName = "أحمد نوار المامو",
            invoiceDateMs = System.currentTimeMillis()
        )
        withTimeout(5000L) { aliceRepo.pushInvoiceIdempotent(inv).getOrThrow() }

        signInTestUser("bob_store@test.com")
        var caught: FirebaseFirestoreException? = null
        try {
            withTimeout(5000L) {
                firestore.collection("users").document(aliceUid).collection("invoices").document(invSyncId).get().await()
            }
        } catch (e: Exception) {
            caught = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
    }

    @Test
    fun unauthenticatedObserve_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreSyncRepository(firestore, auth)
        var caught: Exception? = null
        try {
            withTimeout(3000L) { repo.observeSyncedProducts().first() }
        } catch (e: Exception) {
            caught = e
        }
        assertTrue(caught is IllegalStateException || caught?.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun deleteCloudData_supportsSelectiveAndFullWipe() = runBlocking {
        val uid = signInTestUser("delete_owner@test.com")
        val repo = FirestoreSyncRepository(firestore, auth)

        withTimeout(5000L) {
            repo.upsertStoreProfile(StoreSettingsEntity(storeName = "خردوات المامو", managerName = "أحمد نوار المامو")).getOrThrow()
            repo.pushProductIdempotent(
                ProductEntity(
                    syncId = "prod_del_1",
                    name = "مفك براغي",
                    category = "عدد",
                    averageCost = 10.0,
                    lastPurchaseCost = 10.0,
                    sellingPrice = 18.0,
                    quantity = 5.0,
                    minStockLevel = 1.0
                )
            ).getOrThrow()
            repo.pushInvoiceIdempotent(
                InvoiceEntity(
                    syncId = "inv_del_1",
                    invoiceNumber = "S-9001",
                    invoiceType = InvoiceType.SALE,
                    partyName = "زبون",
                    status = InvoiceStatus.POSTED,
                    paymentMethod = PaymentMethod.CASH,
                    subtotal = 18.0,
                    discountAmount = 0.0,
                    netTotal = 18.0,
                    paidAmount = 18.0,
                    remainingAmount = 0.0,
                    totalCost = 10.0,
                    staffId = 1L,
                    staffName = "أحمد",
                    invoiceDateMs = System.currentTimeMillis()
                )
            ).getOrThrow()
        }

        // Selective delete: delete PRODUCTS only, keep INVOICES and profile
        val selectiveRes = withTimeout(5000L) {
            repo.deleteCloudData(setOf(CloudCollectionKind.PRODUCTS), wipeProfile = false).getOrThrow()
        }
        assertEquals(1, selectiveRes.deletedProducts)
        assertEquals(0, selectiveRes.deletedInvoices)
        assertEquals(false, selectiveRes.deletedProfile)

        val snapAfterSelective = withTimeout(5000L) { repo.fetchCloudDatabaseSnapshot().getOrThrow() }
        assertTrue(snapAfterSelective.products.isEmpty())
        assertEquals(1, snapAfterSelective.invoices.size)
        assertTrue(snapAfterSelective.profile != null)

        // Full cloud wipe: delete remaining collections + profile
        val fullRes = withTimeout(5000L) {
            repo.deleteCloudData(CloudCollectionKind.entries.toSet(), wipeProfile = true).getOrThrow()
        }
        assertEquals(1, fullRes.deletedInvoices)
        assertEquals(true, fullRes.deletedProfile)

        val profDoc = withTimeout(5000L) { firestore.collection("users").document(uid).get().await() }
        assertTrue(!profDoc.exists())
    }
}
