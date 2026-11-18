package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingEngineTest {

    @Test
    fun weightedAverageCost_computesExactBlendAcrossPurchases() {
        // Initial: 10 units @ 100.0 = 1,000.0
        // Purchase: 5 units @ 160.0 = 800.0
        // Total: 15 units = 1,800.0 -> 120.0 per unit
        val blended = AccountingEngine.calculateWeightedAverageCost(
            currentQuantity = 10.0,
            currentAverageCost = 100.0,
            addedQuantity = 5.0,
            purchaseUnitCost = 160.0
        )
        assertEquals(120.0, blended, 0.0001)
    }

    @Test
    fun weightedAverageCost_whenZeroInitialStock_equalsPurchaseCost() {
        val blended = AccountingEngine.calculateWeightedAverageCost(
            currentQuantity = 0.0,
            currentAverageCost = 50.0,
            addedQuantity = 8.0,
            purchaseUnitCost = 75.0
        )
        assertEquals(75.0, blended, 0.0001)
    }

    @Test
    fun lineTotal_appliesDiscountAndFloorsAtZero() {
        assertEquals(475.0, AccountingEngine.calculateLineTotal(5.0, 100.0, 25.0), 0.0001)
        assertEquals(0.0, AccountingEngine.calculateLineTotal(2.0, 50.0, 150.0), 0.0001)
    }

    @Test
    fun profitCalculations_computeGrossAndNetAccurately() {
        val revenue = 2500.0
        val cogs = 1750.0
        val expenses = 300.0
        assertEquals(750.0, AccountingEngine.calculateGrossProfit(revenue, cogs), 0.0001)
        assertEquals(450.0, AccountingEngine.calculateNetProfit(revenue, cogs, expenses), 0.0001)
    }

    @Test
    fun cashboxBalanceAndReconciliation_checkSignedLedgerSum() {
        val amounts = listOf(10000.0, -2500.0, 1200.0, -450.0)
        val sum = AccountingEngine.calculateCashboxBalance(amounts)
        assertEquals(8250.0, sum, 0.0001)
        assertTrue(AccountingEngine.isCashboxReconciled(8250.0, sum))
        assertFalse(AccountingEngine.isCashboxReconciled(8250.5, sum))
    }

    @Test
    fun hashPin_isDeterministicAndDistinctAcrossPins() {
        val hash1 = AccountingEngine.hashPin("1234")
        val hash2 = AccountingEngine.hashPin("1234")
        val hash3 = AccountingEngine.hashPin("4321")
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
    }
}
