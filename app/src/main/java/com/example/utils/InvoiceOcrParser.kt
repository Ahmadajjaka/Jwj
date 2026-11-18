package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.example.data.local.MeasureUnit
import com.example.data.local.ProductEntity
import com.example.data.remote.GroqAiResult
import com.example.data.remote.GroqAiService
import com.example.data.repository.AccountingEngine
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

data class OcrParsedLineDraft(
    val rawName: String,
    val matchedProductId: Long? = null,
    val matchedProductName: String? = null,
    val quantity: Double = 1.0,
    val unitName: String = MeasureUnit.PIECE,
    val unitPrice: Double = 0.0,
    val barcode: String = "",
    val needsReview: Boolean = true
)

data class ParsedPurchaseOcrDraft(
    val sourceOcrHash: String,
    val rawText: String,
    val supplierName: String = "",
    val supplierPhone: String = "",
    val invoiceNumber: String = "",
    val extraCosts: Double = 0.0,
    val invoiceDiscount: Double = 0.0,
    val printedTotal: Double? = null,
    val lines: List<OcrParsedLineDraft> = emptyList(),
    val detectedBarcodes: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val usedAiParser: Boolean = false
)

object InvoiceOcrParser {

    suspend fun scanBarcodesFromBitmap(bitmap: Bitmap): List<String> = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()
            val results = Tasks.await(scanner.process(image))
            results.mapNotNull { it.rawValue?.trim()?.takeIf { v -> v.isNotBlank() } }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun loadBitmapFromUri(context: Context, uri: Uri): Pair<Bitmap?, Boolean> = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext (null to false)
            pfd.use { fd ->
                val renderer = PdfRenderer(fd)
                renderer.use { r ->
                    if (r.pageCount == 0) return@withContext (null to false)
                    val page = r.openPage(0)
                    page.use { p ->
                        val bmp = Bitmap.createBitmap(
                            (p.width * 2).coerceAtMost(1800),
                            (p.height * 2).coerceAtMost(2400),
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return@withContext (bmp to false)
                    }
                }
            }
        } else {
            val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }
            val maxDim = maxOf(optsBounds.outWidth, optsBounds.outHeight)
            val sample = if (maxDim > 1800) (maxDim / 1200).coerceAtLeast(1) else 1
            val optsDecode = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, optsDecode)
            }
            val blurry = bmp != null && isLowContrastOrBlurry(bmp)
            bmp to blurry
        }
    }

    suspend fun parsePurchaseInvoiceUri(
        context: Context,
        uri: Uri,
        existingProducts: List<ProductEntity>,
        groqService: GroqAiService
    ): ParsedPurchaseOcrDraft = withContext(Dispatchers.IO) {
        val (bitmap, isBlurry) = loadBitmapFromUri(context, uri)
        if (bitmap == null) {
            return@withContext ParsedPurchaseOcrDraft(
                sourceOcrHash = "",
                rawText = "",
                warnings = listOf("تعذر قراءة الصورة أو ملف PDF المحدد.")
            )
        }

        val warnings = mutableListOf<String>()
        if (isBlurry) {
            warnings.add("تحذير جودة الصورة: الإضاءة أو الحدة منخفضة، يرجى مراجعة الأرقام بدقة قبل الاعتماد.")
        }

        val barcodes = scanBarcodesFromBitmap(bitmap)
        val rawText = try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionResult = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            visionResult.text.trim()
        } catch (_: Exception) {
            ""
        }

        val combinedSeed = "$rawText|${barcodes.joinToString(",")}|${uri}"
        val ocrHash = AccountingEngine.sha256Hex(combinedSeed)

        if (rawText.isBlank() && barcodes.isEmpty()) {
            warnings.add("لم يتم اكتشاف نص واضح في الفاتورة. تأكد من وضوح المستند أو أضف البنود يدوياً.")
            return@withContext ParsedPurchaseOcrDraft(
                sourceOcrHash = ocrHash,
                rawText = "",
                detectedBarcodes = barcodes,
                warnings = warnings
            )
        }

        var usedAi = false
        var draft = if (groqService.hasConfiguredKey() && rawText.isNotBlank()) {
            val aiDraft = tryExtractWithGroq(rawText, barcodes, existingProducts, groqService)
            if (aiDraft != null) {
                usedAi = true
                aiDraft.copy(sourceOcrHash = ocrHash, rawText = rawText, detectedBarcodes = barcodes)
            } else null
        } else null

        if (draft == null) {
            draft = parseWithOfflineHeuristic(rawText, barcodes, existingProducts, ocrHash)
        }

        val linesSum = draft.lines.sumOf { it.quantity * it.unitPrice }
        val printed = draft.printedTotal
        if (printed != null && printed > 0.0 && abs((linesSum - draft.invoiceDiscount + draft.extraCosts) - printed) > 1.0) {
            warnings.add(
                "تنبيه فرق حسابي: مجموع البنود (${ArabicFormatters.formatNumber(linesSum)}) يختلف عن الإجمالي المطبوع (${ArabicFormatters.formatNumber(printed)})."
            )
        }
        if (draft.lines.any { it.matchedProductId == null }) {
            warnings.add("توجد بنود غير مطابقة تلقائياً في المخزون — يرجى ربطها أو إضافتها أثناء المراجعة.")
        }

        draft.copy(
            warnings = (warnings + draft.warnings).distinct(),
            usedAiParser = usedAi
        )
    }

    private suspend fun tryExtractWithGroq(
        rawText: String,
        barcodes: List<String>,
        existingProducts: List<ProductEntity>,
        groqService: GroqAiService
    ): ParsedPurchaseOcrDraft? {
        val catalogHint = existingProducts.take(40).joinToString(" | ") {
            "${it.id}:${it.name}(${it.barcode})"
        }
        val systemPrompt = """
            You are an invoice OCR parser for a hardware store.
            Return ONLY valid JSON without markdown fences:
            {
              "supplierName": "",
              "supplierPhone": "",
              "invoiceNumber": "",
              "extraCosts": 0.0,
              "invoiceDiscount": 0.0,
              "printedTotal": 0.0,
              "items": [
                {"name": "", "quantity": 1.0, "unit": "قطعة", "unitPrice": 0.0, "barcode": ""}
              ]
            }
        """.trimIndent()
        val userPrompt = "OCR Text:\n$rawText\nBarcodes: ${barcodes.joinToString(", ")}\nCatalog: $catalogHint"
        val res = groqService.sendStructuredCompletion(
            modelId = GroqAiService.FAST_MODEL_ID,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )
        if (res !is GroqAiResult.Success) return null

        return runCatching {
            val cleaned = res.reply.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val obj = JSONObject(cleaned)
            val arr = obj.optJSONArray("items")
            val parsedLines = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val itemObj = arr.getJSONObject(i)
                        val name = itemObj.optString("name", "").trim()
                        if (name.isBlank()) continue
                        val bc = itemObj.optString("barcode", "").trim()
                        val matched = matchCatalogProduct(name, bc, existingProducts)
                        add(
                            OcrParsedLineDraft(
                                rawName = name,
                                matchedProductId = matched?.id,
                                matchedProductName = matched?.name,
                                quantity = itemObj.optDouble("quantity", 1.0).coerceAtLeast(0.01),
                                unitName = itemObj.optString("unit", matched?.baseUnit ?: MeasureUnit.PIECE).ifBlank { MeasureUnit.PIECE },
                                unitPrice = itemObj.optDouble("unitPrice", matched?.lastPurchaseCost ?: 0.0).coerceAtLeast(0.0),
                                barcode = bc,
                                needsReview = matched == null
                            )
                        )
                    }
                }
            }
            val printedTotal = obj.optDouble("printedTotal", 0.0).takeIf { it > 0.0 }
            ParsedPurchaseOcrDraft(
                sourceOcrHash = "",
                rawText = rawText,
                supplierName = obj.optString("supplierName", "").trim(),
                supplierPhone = obj.optString("supplierPhone", "").trim(),
                invoiceNumber = obj.optString("invoiceNumber", "").trim(),
                extraCosts = obj.optDouble("extraCosts", 0.0).coerceAtLeast(0.0),
                invoiceDiscount = obj.optDouble("invoiceDiscount", 0.0).coerceAtLeast(0.0),
                printedTotal = printedTotal,
                lines = parsedLines
            )
        }.getOrNull()
    }

    private fun parseWithOfflineHeuristic(
        rawText: String,
        barcodes: List<String>,
        existingProducts: List<ProductEntity>,
        ocrHash: String
    ): ParsedPurchaseOcrDraft {
        val textLines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val linesOut = mutableListOf<OcrParsedLineDraft>()
        val numRegex = Regex("""(\d+(?:[.,]\d+)?)""")

        for (line in textLines) {
            val normalized = ArabicFormatters.normalizeDigits(line)
            val nums = numRegex.findAll(normalized).mapNotNull { it.value.toDoubleOrNull() }.toList()
            if (nums.size >= 2) {
                val cleanedName = normalized.replace(numRegex, "").replace(Regex("""[^\p{L}\p{N}\s-]"""), " ").trim()
                if (cleanedName.length >= 2 && !cleanedName.contains("total", ignoreCase = true) && !cleanedName.contains("المجموع")) {
                    val qty = nums.first().coerceAtLeast(1.0)
                    val price = nums[1].coerceAtLeast(0.0)
                    val matched = matchCatalogProduct(cleanedName, "", existingProducts)
                    linesOut.add(
                        OcrParsedLineDraft(
                            rawName = cleanedName,
                            matchedProductId = matched?.id,
                            matchedProductName = matched?.name,
                            quantity = qty,
                            unitName = matched?.baseUnit ?: MeasureUnit.PIECE,
                            unitPrice = price,
                            needsReview = matched == null
                        )
                    )
                }
            }
        }

        // Also map detected barcodes directly if found in catalog
        for (bc in barcodes) {
            val matched = existingProducts.firstOrNull { it.barcode.equals(bc, ignoreCase = true) }
            if (matched != null && linesOut.none { it.matchedProductId == matched.id }) {
                linesOut.add(
                    OcrParsedLineDraft(
                        rawName = matched.name,
                        matchedProductId = matched.id,
                        matchedProductName = matched.name,
                        quantity = 1.0,
                        unitName = matched.baseUnit,
                        unitPrice = matched.lastPurchaseCost,
                        barcode = bc,
                        needsReview = true
                    )
                )
            }
        }

        val supplierGuess = textLines.firstOrNull { it.length in 3..40 && !it.any(Char::isDigit) }.orEmpty()
        return ParsedPurchaseOcrDraft(
            sourceOcrHash = ocrHash,
            rawText = rawText,
            supplierName = supplierGuess,
            lines = linesOut,
            detectedBarcodes = barcodes
        )
    }

    private fun matchCatalogProduct(name: String, barcode: String, products: List<ProductEntity>): ProductEntity? {
        if (barcode.isNotBlank()) {
            products.firstOrNull { it.barcode.equals(barcode, ignoreCase = true) }?.let { return it }
        }
        val clean = name.trim().lowercase()
        if (clean.isBlank()) return null
        return products.firstOrNull { p ->
            val pName = p.name.lowercase()
            pName == clean || pName.contains(clean) || clean.contains(pName)
        }
    }

    private fun isLowContrastOrBlurry(bmp: Bitmap): Boolean {
        val stepX = (bmp.width / 32).coerceAtLeast(1)
        val stepY = (bmp.height / 32).coerceAtLeast(1)
        var sumDiff = 0L
        var count = 0
        var prevLum = -1
        for (y in 0 until bmp.height step stepY) {
            for (x in 0 until bmp.width step stepX) {
                val c = bmp.getPixel(x, y)
                val lum = (Color.red(c) * 3 + Color.green(c) * 6 + Color.blue(c)) / 10
                if (prevLum >= 0) {
                    sumDiff += abs(lum - prevLum)
                    count++
                }
                prevLum = lum
            }
        }
        if (count == 0) return false
        return (sumDiff.toDouble() / count) < 6.5
    }
}
