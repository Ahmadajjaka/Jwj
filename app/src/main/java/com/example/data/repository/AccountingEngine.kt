package com.example.data.repository

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

object AccountingEngine {

    fun roundMoney(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun roundQty(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).toDouble()
    }

    fun toBaseQuantity(quantity: Double, conversionFactor: Double): Double {
        require(quantity > 0.0) { "الكمية يجب أن تكون أكبر من صفر" }
        require(conversionFactor > 0.0) { "معامل التحويل يجب أن يكون أكبر من صفر" }
        return roundQty(quantity * conversionFactor)
    }

    fun calculateWeightedAverageCost(
        currentQuantity: Double,
        currentAverageCost: Double,
        addedQuantity: Double,
        purchaseUnitCost: Double
    ): Double {
        require(addedQuantity > 0.0) { "الكمية المشتراة يجب أن تكون أكبر من صفر" }
        require(purchaseUnitCost >= 0.0) { "سعر الشراء لا يمكن أن يكون سالباً" }
        val effectiveOldQty = currentQuantity.coerceAtLeast(0.0)
        val totalQty = effectiveOldQty + addedQuantity
        if (totalQty <= 0.0) return roundMoney(purchaseUnitCost)
        val oldVal = BigDecimal.valueOf(effectiveOldQty).multiply(BigDecimal.valueOf(currentAverageCost.coerceAtLeast(0.0)))
        val addVal = BigDecimal.valueOf(addedQuantity).multiply(BigDecimal.valueOf(purchaseUnitCost))
        val avg = oldVal.add(addVal).divide(BigDecimal.valueOf(totalQty), 4, RoundingMode.HALF_UP)
        return avg.toDouble()
    }

    fun calculateLineTotal(
        quantity: Double,
        unitPrice: Double,
        lineDiscount: Double = 0.0
    ): Double {
        require(quantity > 0.0) { "الكمية يجب أن تكون أكبر من صفر" }
        require(unitPrice >= 0.0) { "السعر لا يمكن أن يكون سالباً" }
        require(lineDiscount >= 0.0) { "الخصم لا يمكن أن يكون سالباً" }
        val raw = BigDecimal.valueOf(quantity)
            .multiply(BigDecimal.valueOf(unitPrice))
            .subtract(BigDecimal.valueOf(lineDiscount))
        return roundMoney(raw.toDouble().coerceAtLeast(0.0))
    }

    fun calculateTaxAmount(taxableAmount: Double, taxRatePercent: Double): Double {
        if (taxRatePercent <= 0.0 || taxableAmount <= 0.0) return 0.0
        return roundMoney(taxableAmount * (taxRatePercent / 100.0))
    }

    fun calculateProfitMarginPercent(sellingPrice: Double, costPrice: Double): Double {
        if (sellingPrice <= 0.0) return 0.0
        return roundMoney(((sellingPrice - costPrice) / sellingPrice) * 100.0)
    }

    fun calculateNetProfit(
        salesRevenue: Double,
        costOfGoodsSold: Double,
        operatingExpenses: Double
    ): Double {
        return roundMoney(salesRevenue - costOfGoodsSold - operatingExpenses)
    }

    fun calculateGrossProfit(
        salesRevenue: Double,
        costOfGoodsSold: Double
    ): Double {
        return roundMoney(salesRevenue - costOfGoodsSold)
    }

    fun calculateCashboxBalance(signedAmounts: List<Double>): Double {
        var acc = BigDecimal.ZERO
        for (amt in signedAmounts) {
            if (amt.isFinite()) {
                acc = acc.add(BigDecimal.valueOf(amt))
            }
        }
        return roundMoney(acc.toDouble())
    }

    fun isCashboxReconciled(computedBalance: Double, ledgerSum: Double): Boolean {
        return abs(computedBalance - ledgerSum) < 0.01
    }

    fun isJournalBalanced(debits: List<Double>, credits: List<Double>): Boolean {
        val d = calculateCashboxBalance(debits)
        val c = calculateCashboxBalance(credits)
        return abs(d - c) < 0.01
    }

    fun generateEan13Barcode(seedNumber: Long): String {
        val base12 = "629" + String.format("%09d", abs(seedNumber) % 1_000_000_000L)
        var sum = 0
        for (i in 0 until 12) {
            val digit = base12[i].digitToInt()
            sum += if (i % 2 == 0) digit else digit * 3
        }
        val check = (10 - (sum % 10)) % 10
        return "$base12$check"
    }

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("mamo_salt_2026:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun encryptBackupText(plainJson: String, passphrase: String): String {
        require(passphrase.length >= 4) { "كلمة مرور التشفير يجب أن تكون 4 أحرف أو أرقام على الأقل" }
        val rng = SecureRandom()
        val salt = ByteArray(16).apply { rng.nextBytes(this) }
        val iv = ByteArray(12).apply { rng.nextBytes(this) }
        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val checksum = sha256Hex(plainJson)

        return org.json.JSONObject().apply {
            put("format", "MAMO_ENCRYPTED_BACKUP_V1")
            put("algorithm", "AES-256-GCM")
            put("checksumSha256", checksum)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }.toString(2)
    }

    fun decryptBackupText(encryptedEnvelopeJson: String, passphrase: String): String {
        require(passphrase.isNotBlank()) { "يرجى إدخال كلمة مرور فك تشفير النسخة الاحتياطية" }
        val root = org.json.JSONObject(encryptedEnvelopeJson)
        require(root.optString("format") == "MAMO_ENCRYPTED_BACKUP_V1") {
            "صيغة التشفير غير صالحة"
        }
        val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
        val cipherBytes = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)
        val expectedChecksum = root.optString("checksumSha256", "")

        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plain = try {
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalArgumentException("كلمة مرور فك التشفير غير صحيحة أو الملف تالف")
        }
        if (expectedChecksum.isNotBlank() && sha256Hex(plain) != expectedChecksum) {
            throw IllegalStateException("فشل التحقق من سلامة الملف (Checksum Mismatch)")
        }
        return plain
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65_536, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun compressGzip(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(data)
        }
        return baos.toByteArray()
    }

    fun decompressGzip(compressed: ByteArray): ByteArray {
        ByteArrayInputStream(compressed).use { bais ->
            GZIPInputStream(bais).use { gzip ->
                return gzip.readBytes()
            }
        }
    }

    fun isGzipBytes(data: ByteArray): Boolean {
        return data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()
    }

    fun encryptCompressedBackup(plainJson: String, passphrase: String): String {
        require(passphrase.length >= 4) { "كلمة مرور التشفير يجب أن تكون 4 أحرف أو أرقام على الأقل" }
        val rawBytes = plainJson.toByteArray(Charsets.UTF_8)
        val gzippedBytes = compressGzip(rawBytes)
        val rng = SecureRandom()
        val salt = ByteArray(16).apply { rng.nextBytes(this) }
        val iv = ByteArray(12).apply { rng.nextBytes(this) }
        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(gzippedBytes)
        val checksum = sha256Hex(plainJson)

        return org.json.JSONObject().apply {
            put("format", "MAMO_GZIP_ENCRYPTED_BACKUP_V1")
            put("algorithm", "GZIP+AES-256-GCM")
            put("uncompressedBytes", rawBytes.size)
            put("compressedBytes", gzippedBytes.size)
            put("checksumSha256", checksum)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }.toString(2)
    }

    fun decryptCompressedBackup(envelopeJson: String, passphrase: String): String {
        require(passphrase.isNotBlank()) { "يرجى إدخال كلمة مرور فك تشفير النسخة الاحتياطية" }
        val root = org.json.JSONObject(envelopeJson)
        require(root.optString("format") == "MAMO_GZIP_ENCRYPTED_BACKUP_V1") {
            "صيغة التشفير المضغوطة غير صالحة"
        }
        val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
        val cipherBytes = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)
        val expectedChecksum = root.optString("checksumSha256", "")

        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plain = try {
            val gzipped = cipher.doFinal(cipherBytes)
            String(decompressGzip(gzipped), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalArgumentException("كلمة مرور فك التشفير غير صحيحة أو الملف المضغوط تالف")
        }
        if (expectedChecksum.isNotBlank() && sha256Hex(plain) != expectedChecksum) {
            throw IllegalStateException("فشل التحقق من سلامة الملف (Checksum Mismatch)")
        }
        return plain
    }
}
