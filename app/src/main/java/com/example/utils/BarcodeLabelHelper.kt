package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.example.data.local.ProductEntity
import com.example.data.local.StoreSettingsEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.io.FileOutputStream

object BarcodeLabelHelper {

    fun generateCode128Bitmap(content: String, widthPx: Int = 360, heightPx: Int = 90): Bitmap? {
        val clean = content.trim().ifBlank { return null }
        return try {
            val matrix = MultiFormatWriter().encode(clean, BarcodeFormat.CODE_128, widthPx, heightPx)
            val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            for (x in 0 until widthPx) {
                for (y in 0 until heightPx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun generateLabelsPdfFile(
        context: Context,
        products: List<ProductEntity>,
        settings: StoreSettingsEntity
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(dir, "barcode_labels_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val cols = 3
        val rowsPerPage = 6
        val perPage = cols * rowsPerPage
        val marginX = 24f
        val marginY = 26f
        val cellW = (pageWidth - marginX * 2) / cols
        val cellH = (pageHeight - marginY * 2) / rowsPerPage

        val borderPaint = Paint().apply {
            color = Color.rgb(14, 32, 60)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val storePaint = TextPaint().apply {
            color = Color.rgb(197, 155, 61)
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val titlePaint = TextPaint().apply {
            color = Color.rgb(14, 32, 60)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val pricePaint = TextPaint().apply {
            color = Color.rgb(11, 25, 44)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val codePaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val items = products.filter { it.barcode.isNotBlank() }.ifEmpty { products }
        val pagesCount = ((items.size + perPage - 1) / perPage).coerceAtLeast(1)

        for (pIndex in 0 until pagesCount) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pIndex + 1).create())
            val canvas: Canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val slice = items.drop(pIndex * perPage).take(perPage)
            slice.forEachIndexed { idx, prod ->
                val col = idx % cols
                val row = idx / cols
                val left = marginX + col * cellW + 4f
                val top = marginY + row * cellH + 4f
                val right = left + cellW - 8f
                val bottom = top + cellH - 8f
                val cx = (left + right) / 2f

                canvas.drawRect(left, top, right, bottom, borderPaint)
                canvas.drawText(settings.storeName.take(22), cx, top + 14f, storePaint)
                canvas.drawText(prod.name.take(24), cx, top + 29f, titlePaint)
                canvas.drawText(
                    ArabicFormatters.formatMoney(prod.sellingPrice, settings.currencySymbol),
                    cx,
                    top + 45f,
                    pricePaint
                )

                val code = prod.barcode.ifBlank { "MAMO-${prod.id}" }
                val barcodeBmp = generateCode128Bitmap(code, 220, 54)
                if (barcodeBmp != null) {
                    val dst = android.graphics.Rect(
                        (left + 12f).toInt(),
                        (top + 52f).toInt(),
                        (right - 12f).toInt(),
                        (bottom - 18f).toInt()
                    )
                    canvas.drawBitmap(barcodeBmp, null, dst, null)
                }
                canvas.drawText(code, cx, bottom - 6f, codePaint)
            }
            doc.finishPage(page)
        }

        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }
}
