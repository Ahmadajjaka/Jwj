package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.local.InvoiceWithItems
import com.example.data.local.StoreSettingsEntity
import java.io.File
import java.io.FileOutputStream

object ExportAndShareHelper {

    fun generateInvoicePdfFile(
        context: Context,
        invoiceWithItems: InvoiceWithItems,
        settings: StoreSettingsEntity
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val inv = invoiceWithItems.invoice
        val outFile = File(dir, "invoice_${inv.invoiceNumber}.pdf")
        val cur = settings.currencySymbol
        val isSale = inv.invoiceType == com.example.data.local.InvoiceType.SALE
        val isCancelled = inv.status == com.example.data.local.InvoiceStatus.CANCELLED
        val isPaidFull = !isCancelled && inv.remainingAmount <= 0.01 && inv.paidAmount > 0.0
        val isUnpaid = !isCancelled && inv.paidAmount <= 0.01
        val isPartial = !isCancelled && inv.paidAmount > 0.01 && inv.remainingAmount > 0.01

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val cream = Color.rgb(251, 247, 236)
        val navy = Color.rgb(14, 32, 60)
        val navyMid = Color.rgb(26, 53, 94)
        val gold = Color.rgb(197, 155, 61)
        val goldBright = Color.rgb(231, 202, 120)
        val muted = Color.rgb(107, 100, 77)
        val borderGray = Color.rgb(140, 126, 94)
        val rowAlt = Color.rgb(229, 228, 224)
        val rowLight = Color.rgb(255, 253, 248)
        val sigBlue = Color.rgb(24, 61, 122)

        // Cream Paper Background
        canvas.drawRect(0f, 0f, 595f, 842f, Paint().apply { color = cream; style = Paint.Style.FILL })

        // Ornate Outer Navy & Inner Gold Double Border
        canvas.drawRect(14f, 14f, 581f, 828f, Paint().apply {
            color = navy; style = Paint.Style.STROKE; strokeWidth = 3.2f
        })
        canvas.drawRect(22f, 22f, 573f, 820f, Paint().apply {
            color = gold; style = Paint.Style.STROKE; strokeWidth = 1.4f
        })
        drawPdfCornerOrnament(canvas, 22f, 22f, 1f, 1f, navy, gold)
        drawPdfCornerOrnament(canvas, 573f, 22f, -1f, 1f, navy, gold)
        drawPdfCornerOrnament(canvas, 22f, 820f, 1f, -1f, navy, gold)
        drawPdfCornerOrnament(canvas, 573f, 820f, -1f, -1f, navy, gold)

        // Left Brand Block (Optional Custom Logo or Vector M-Tools Emblem)
        var drewCustomLogo = false
        if (settings.logoUri.isNotBlank()) {
            try {
                context.contentResolver.openInputStream(Uri.parse(settings.logoUri))?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        canvas.drawBitmap(bmp, null, Rect(66, 40, 152, 126), null)
                        drewCustomLogo = true
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (!drewCustomLogo) {
            drawPdfMamoEmblem(canvas, 110f, 82f, 40f, navy, navyMid, gold)
        }

        val brandTitlePaint = TextPaint().apply {
            color = navy; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val brandSubPaint = TextPaint().apply {
            color = muted; textSize = 11.5f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        canvas.drawText(settings.storeName, 112f, 146f, brandTitlePaint)
        canvas.drawText("إدارة ${settings.managerName}", 112f, 164f, brandSubPaint)

        // Right Header Title + Metadata Dotted Rows
        val docTitlePaint = TextPaint().apply {
            color = navy; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val titleText = if (isSale) "فاتورة بيع" else "فاتورة شراء"
        drawRtlText(canvas, titleText, 555f, 38f, 320, docTitlePaint)
        if (isCancelled) {
            val cancelPaint = TextPaint().apply {
                color = Color.rgb(198, 40, 40); textSize = 11f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
            }
            drawRtlText(canvas, "[ملغاة بقيد عكسي]", 380f, 46f, 150, cancelPaint)
        }

        val labelPaint = TextPaint().apply { color = navy; textSize = 11.5f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val valPaint = TextPaint().apply { color = navyMid; textSize = 11.5f; isAntiAlias = true }
        val dotLinePaint = Paint().apply {
            color = borderGray; strokeWidth = 0.8f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
        }

        val fields = listOf(
            "فاتورة رقم:" to inv.invoiceNumber,
            "تاريخ:" to ArabicFormatters.formatDate(inv.invoiceDateMs, settings.dateFormatPattern),
            (if (isSale) "اسم العميل:" else "اسم المورد:") to inv.partyName,
            "رقم الهاتف:" to inv.partyPhone.ifBlank { "—" },
            "العنوان / ملاحظات:" to inv.notes.ifBlank { "—" },
            "طريقة الدفع:" to ArabicFormatters.paymentMethodLabel(inv.paymentMethod)
        )
        var fy = 82f
        fields.forEach { (lbl, v) ->
            drawRtlText(canvas, lbl, 555f, fy, 115, labelPaint)
            drawRtlText(canvas, v, 435f, fy, 225, valPaint)
            canvas.drawLine(210f, fy + 16f, 442f, fy + 16f, dotLinePaint)
            fy += 22f
        }

        // Invoice Payment Status Checkboxes
        val statusY = fy + 6f
        drawRtlText(canvas, "حالة الفاتورة:", 555f, statusY, 95, labelPaint)
        drawPdfStatusCheckbox(canvas, 428f, statusY + 2f, "مدفوعة", isPaidFull, navy, goldBright, labelPaint)
        drawPdfStatusCheckbox(canvas, 336f, statusY + 2f, "غير مدفوعة", isUnpaid, navy, goldBright, labelPaint)
        drawPdfStatusCheckbox(canvas, 224f, statusY + 2f, "مدفوعة جزئياً", isPartial, navy, goldBright, labelPaint)

        // 6-Column RTL Zebra Table (Min 8 rows)
        var ty = statusY + 28f
        val leftX = 36f
        val rightX = 559f
        // Columns from left to right visually (in RTL, rightmost column is #1 Serial):
        // Col edges: [36, 126 (Total), 210 (UnitPrice), 270 (Unit), 328 (Qty), 515 (Name), 559 (Serial)]
        val colEdges = floatArrayOf(36f, 126f, 210f, 270f, 328f, 515f, 559f)
        val headers = listOf("الإجمالي", "سعر الوحدة", "الوحدة", "الكمية", "اسم المادة أو الصنف", "م")

        val tableHeaderBg = Paint().apply { color = navy; style = Paint.Style.FILL }
        canvas.drawRect(leftX, ty, rightX, ty + 28f, tableHeaderBg)
        val tableHeaderText = TextPaint().apply {
            color = goldBright; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val goldDivider = Paint().apply { color = gold; strokeWidth = 0.9f }
        for (c in headers.indices) {
            val cx = (colEdges[c] + colEdges[c + 1]) / 2f
            canvas.drawText(headers[c], cx, ty + 18f, tableHeaderText)
            if (c > 0) canvas.drawLine(colEdges[c], ty, colEdges[c], ty + 28f, goldDivider)
        }
        ty += 28f

        val rowCount = maxOf(8, minOf(12, invoiceWithItems.items.size))
        val rowHeight = 26f
        val cellCenterPaint = TextPaint().apply { color = navy; textSize = 10f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        val cellBoldPaint = TextPaint().apply { color = navy; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val gridPaint = Paint().apply { color = borderGray; strokeWidth = 0.8f; style = Paint.Style.STROKE }

        for (r in 0 until rowCount) {
            val item = invoiceWithItems.items.getOrNull(r)
            val bg = if (r % 2 == 0) rowLight else rowAlt
            canvas.drawRect(leftX, ty, rightX, ty + rowHeight, Paint().apply { color = bg; style = Paint.Style.FILL })
            canvas.drawRect(leftX, ty, rightX, ty + rowHeight, gridPaint)
            for (c in 1 until colEdges.size - 1) {
                canvas.drawLine(colEdges[c], ty, colEdges[c], ty + rowHeight, gridPaint)
            }
            if (item != null) {
                canvas.drawText((r + 1).toString(), (colEdges[5] + colEdges[6]) / 2f, ty + 17f, cellCenterPaint)
                drawRtlText(canvas, item.productName, colEdges[5] - 6f, ty + 6f, (colEdges[5] - colEdges[4] - 12f).toInt(), cellBoldPaint)
                canvas.drawText(ArabicFormatters.formatNumber(item.quantity), (colEdges[3] + colEdges[4]) / 2f, ty + 17f, cellCenterPaint)
                canvas.drawText(item.unitName.ifBlank { "قطعة" }, (colEdges[2] + colEdges[3]) / 2f, ty + 17f, cellCenterPaint)
                canvas.drawText(ArabicFormatters.formatNumber(item.unitPrice), (colEdges[1] + colEdges[2]) / 2f, ty + 17f, cellCenterPaint)
                canvas.drawText(ArabicFormatters.formatNumber(item.lineTotal), (colEdges[0] + colEdges[1]) / 2f, ty + 17f, TextPaint(cellBoldPaint).apply { textAlign = Paint.Align.CENTER })
            }
            ty += rowHeight
        }

        // Bottom Totals (Right) + Stamp & Manager Signature (Left)
        val bottomY = maxOf(ty + 18f, 560f)

        // Totals on Right
        val totalRows = buildList {
            add("المجموع الفرعي" to ArabicFormatters.formatMoney(inv.subtotal, cur))
            add("الخصم" to ArabicFormatters.formatMoney(inv.discountAmount, cur))
            if (inv.taxAmount > 0.0) {
                add("الضريبة (${ArabicFormatters.formatNumber(inv.taxRate)}%)" to ArabicFormatters.formatMoney(inv.taxAmount, cur))
            }
            add("المبلغ المدفوع" to ArabicFormatters.formatMoney(inv.paidAmount, cur))
            add("المبلغ المتبقي" to ArabicFormatters.formatMoney(inv.remainingAmount, cur))
        }
        var sumY = bottomY
        totalRows.forEach { (lbl, v) ->
            drawRtlText(canvas, lbl, 555f, sumY, 120, labelPaint)
            drawRtlText(canvas, v, 425f, sumY, 140, valPaint)
            canvas.drawLine(285f, sumY + 17f, 555f, sumY + 17f, Paint().apply { color = borderGray; strokeWidth = 0.8f })
            sumY += 22f
        }

        // Two-Tone Navy + Brushed Gold Final Total Pill
        val pillTop = sumY + 6f
        val pillBottom = pillTop + 50f
        val navyPillPaint = Paint().apply { color = navy; style = Paint.Style.FILL }
        val goldGradPaint = Paint().apply {
            shader = android.graphics.LinearGradient(
                285f, pillTop, 435f, pillTop,
                intArrayOf(goldBright, gold, goldBright),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(285f, pillTop, 435f, pillBottom, 10f, 10f, goldGradPaint)
        canvas.drawRoundRect(425f, pillTop, 557f, pillBottom, 10f, 10f, navyPillPaint)
        val finalGoldText = TextPaint().apply {
            color = goldBright; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val finalSubGoldText = TextPaint().apply {
            color = goldBright; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val finalAmountPaint = TextPaint().apply {
            color = navy; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        canvas.drawText("الإجمالي", 491f, pillTop + 22f, finalGoldText)
        canvas.drawText("الإجمالي النهائي", 491f, pillTop + 40f, finalSubGoldText)
        canvas.drawText(ArabicFormatters.formatMoney(inv.netTotal, cur), 355f, pillTop + 31f, finalAmountPaint)

        // Official Store Circular Stamp + Manager Signature Box on Left
        if (settings.printShowStamp) {
            drawPdfOfficialStamp(canvas, 146f, bottomY + 38f, 44f, settings.storeName, settings.managerName, navyMid)
        }
        if (settings.printShowSignature) {
            val sigBoxTop = bottomY + 94f
            canvas.drawRoundRect(56f, sigBoxTop, 236f, sigBoxTop + 58f, 10f, 10f, Paint().apply { color = rowLight; style = Paint.Style.FILL })
            canvas.drawRoundRect(56f, sigBoxTop, 236f, sigBoxTop + 58f, 10f, 10f, Paint().apply { color = navyMid; style = Paint.Style.STROKE; strokeWidth = 1.4f })
            canvas.drawText("توقيع المدير المسؤول", 146f, sigBoxTop + 20f, TextPaint(labelPaint).apply { textAlign = Paint.Align.CENTER })
            canvas.drawText(
                settings.managerName,
                146f,
                sigBoxTop + 44f,
                TextPaint().apply {
                    color = sigBlue; textSize = 14f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                    textAlign = Paint.Align.CENTER; isAntiAlias = true
                }
            )
        }

        // Ornamental Footer Divider & Footer Copy
        val footerDivY = 766f
        canvas.drawLine(36f, footerDivY, 275f, footerDivY, goldDivider)
        canvas.drawLine(320f, footerDivY, 559f, footerDivY, goldDivider)
        canvas.drawCircle(297.5f, footerDivY, 4f, Paint().apply { color = navy })
        canvas.drawCircle(284f, footerDivY, 2.5f, Paint().apply { color = gold })
        canvas.drawCircle(311f, footerDivY, 2.5f, Paint().apply { color = gold })

        drawRtlText(canvas, "شكراً لثقتكم ب${settings.storeName}", 555f, 782f, 250, labelPaint)
        canvas.drawText(
            "إدارة المتجر: ${settings.managerName}",
            42f,
            795f,
            TextPaint(labelPaint).apply { textAlign = Paint.Align.LEFT }
        )

        doc.finishPage(page)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    private fun drawPdfCornerOrnament(canvas: Canvas, x: Float, y: Float, sx: Float, sy: Float, navy: Int, gold: Int) {
        val path = android.graphics.Path().apply {
            moveTo(x + sx * 6f, y + sy * 28f)
            quadTo(x + sx * 8f, y + sy * 8f, x + sx * 28f, y + sy * 6f)
        }
        canvas.drawPath(path, Paint().apply { color = gold; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true })
        canvas.drawCircle(x + sx * 12f, y + sy * 12f, 3.5f, Paint().apply { color = navy; isAntiAlias = true })
    }

    private fun drawPdfMamoEmblem(canvas: Canvas, cx: Float, cy: Float, r: Float, navy: Int, navyMid: Int, gold: Int) {
        val mPaint = Paint().apply {
            color = navy; style = Paint.Style.STROKE; strokeWidth = r * 0.22f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
        }
        val mPath = android.graphics.Path().apply {
            moveTo(cx - r * 0.68f, cy + r * 0.68f)
            lineTo(cx - r * 0.44f, cy - r * 0.52f)
            lineTo(cx, cy + r * 0.24f)
            lineTo(cx + r * 0.44f, cy - r * 0.52f)
            lineTo(cx + r * 0.68f, cy + r * 0.68f)
        }
        canvas.drawPath(mPath, mPaint)
        canvas.drawLine(cx + r * 0.08f, cy + r * 0.16f, cx + r * 0.56f, cy - r * 0.56f, Paint().apply {
            color = gold; strokeWidth = r * 0.16f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        })
        canvas.drawLine(cx + r * 0.36f, cy - r * 0.72f, cx + r * 0.76f, cy - r * 0.40f, Paint().apply {
            color = gold; strokeWidth = r * 0.24f; isAntiAlias = true
        })
        canvas.drawLine(cx, cy - r * 0.76f, cx, cy + r * 0.08f, Paint().apply {
            color = navyMid; strokeWidth = r * 0.16f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        })
    }

    private fun drawPdfStatusCheckbox(
        canvas: Canvas,
        rightBoxX: Float,
        topY: Float,
        label: String,
        checked: Boolean,
        navy: Int,
        goldBright: Int,
        labelPaint: TextPaint
    ) {
        val boxSize = 12f
        if (checked) {
            canvas.drawRect(rightBoxX - boxSize, topY, rightBoxX, topY + boxSize, Paint().apply { color = navy; style = Paint.Style.FILL })
            canvas.drawText("✓", rightBoxX - boxSize / 2f, topY + 10f, TextPaint().apply {
                color = goldBright; textSize = 9.5f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
            })
        }
        canvas.drawRect(rightBoxX - boxSize, topY, rightBoxX, topY + boxSize, Paint().apply { color = navy; style = Paint.Style.STROKE; strokeWidth = 1.2f })
        drawRtlText(canvas, label, rightBoxX - boxSize - 5f, topY - 1f, 82, labelPaint)
    }

    private fun drawPdfOfficialStamp(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        storeName: String,
        managerName: String,
        navyMid: Int
    ) {
        canvas.drawCircle(cx, cy, r, Paint().apply { color = navyMid; style = Paint.Style.STROKE; strokeWidth = 2.4f; isAntiAlias = true })
        canvas.drawCircle(cx, cy, r - 6f, Paint().apply { color = navyMid; style = Paint.Style.STROKE; strokeWidth = 1.1f; isAntiAlias = true })
        canvas.drawCircle(cx, cy, r * 0.48f, Paint().apply { color = navyMid; style = Paint.Style.STROKE; strokeWidth = 1.2f; isAntiAlias = true })
        val mPath = android.graphics.Path().apply {
            moveTo(cx - r * 0.24f, cy + r * 0.16f)
            lineTo(cx - r * 0.15f, cy - r * 0.16f)
            lineTo(cx, cy + r * 0.06f)
            lineTo(cx + r * 0.15f, cy - r * 0.16f)
            lineTo(cx + r * 0.24f, cy + r * 0.16f)
        }
        canvas.drawPath(mPath, Paint().apply {
            color = navyMid; style = Paint.Style.STROKE; strokeWidth = 2.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
        })
        val stampPaint = Paint().apply {
            color = navyMid; textSize = 8.5f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        canvas.drawText(storeName.take(18), cx, cy - r * 0.60f, stampPaint)
        canvas.drawText(managerName.take(20), cx, cy + r * 0.74f, stampPaint)
    }

    fun generateReportPdfFile(
        context: Context,
        reportTitle: String,
        periodLabel: String,
        rows: List<Pair<String, String>>,
        settings: StoreSettingsEntity
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = reportTitle.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
        val outFile = File(dir, "report_${safeName}_${System.currentTimeMillis()}.pdf")

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val navy = Color.rgb(11, 25, 44)
        val gold = Color.rgb(200, 155, 60)
        val headerPaint = Paint().apply { color = navy; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, 595f, 96f, headerPaint)
        canvas.drawRect(0f, 92f, 595f, 96f, Paint().apply { color = gold })

        val titlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val subtitlePaint = TextPaint().apply {
            color = Color.rgb(226, 232, 240)
            textSize = 11.5f
            isAntiAlias = true
        }
        drawRtlText(canvas, "${settings.storeName} — $reportTitle", 560f, 22f, 520, titlePaint)
        drawRtlText(canvas, "الفترة: $periodLabel  |  الإدارة: ${settings.managerName}", 560f, 56f, 520, subtitlePaint)

        val rowLabelPaint = TextPaint().apply { color = navy; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val rowValPaint = TextPaint().apply { color = Color.rgb(30, 41, 59); textSize = 12f; isAntiAlias = true }
        val divider = Paint().apply { color = Color.rgb(220, 227, 237); strokeWidth = 1f }

        var y = 120f
        rows.forEach { (label, value) ->
            if (y > 785f) return@forEach
            drawRtlText(canvas, label, 560f, y, 320, rowLabelPaint)
            drawRtlText(canvas, value, 230f, y, 195, rowValPaint)
            y += 28f
            canvas.drawLine(32f, y - 6f, 563f, y - 6f, divider)
        }

        doc.finishPage(page)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun generateCsvFile(
        context: Context,
        fileNamePrefix: String,
        headers: List<String>,
        rows: List<List<String>>
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(dir, "${fileNamePrefix}_${System.currentTimeMillis()}.csv")
        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel reads Arabic columns cleanly
        sb.append('\uFEFF')
        sb.append(headers.joinToString(",") { escapeCsv(it) }).append('\n')
        rows.forEach { row ->
            sb.append(row.joinToString(",") { escapeCsv(it) }).append('\n')
        }
        outFile.writeText(sb.toString(), Charsets.UTF_8)
        return outFile
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String, targetPackage: String? = null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
        try {
            if (!targetPackage.isNullOrBlank()) {
                context.startActivity(intent)
            } else {
                context.startActivity(Intent.createChooser(intent, chooserTitle))
            }
        } catch (_: Exception) {
            // Fallback to generic chooser if WhatsApp is not installed on emulator
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, chooserTitle))
        }
    }

    fun shareInvoiceWhatsAppText(
        context: Context,
        invoiceWithItems: InvoiceWithItems,
        settings: StoreSettingsEntity
    ) {
        val inv = invoiceWithItems.invoice
        val text = buildString {
            appendLine("*${settings.storeName}* (إدارة: ${settings.managerName})")
            appendLine("${ArabicFormatters.invoiceTypeLabel(inv.invoiceType)} رقم: *${inv.invoiceNumber}*")
            appendLine("الطرف: ${inv.partyName}")
            appendLine("التاريخ: ${ArabicFormatters.formatDateTime(inv.invoiceDateMs, settings.dateFormatPattern)}")
            appendLine("------------------")
            invoiceWithItems.items.forEach { item ->
                appendLine("• ${item.productName} × ${ArabicFormatters.formatNumber(item.quantity)} ${item.unitName} = ${ArabicFormatters.formatMoney(item.lineTotal, settings.currencySymbol)}")
            }
            appendLine("------------------")
            appendLine("الصافي: *${ArabicFormatters.formatMoney(inv.netTotal, settings.currencySymbol)}*")
            appendLine("المدفوع: ${ArabicFormatters.formatMoney(inv.paidAmount, settings.currencySymbol)} | المتبقي: ${ArabicFormatters.formatMoney(inv.remainingAmount, settings.currencySymbol)}")
        }
        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(waIntent)
        } catch (_: Exception) {
            val generic = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(generic, "مشاركة الفاتورة عبر واتساب"))
        }
    }

    private fun escapeCsv(cell: String): String {
        val cleaned = cell.replace("\"", "\"\"")
        return "\"$cleaned\""
    }

    private fun drawRtlText(
        canvas: Canvas,
        text: String,
        rightX: Float,
        topY: Float,
        width: Int,
        paint: TextPaint
    ) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .build()
        canvas.save()
        canvas.translate(rightX - width, topY)
        layout.draw(canvas)
        canvas.restore()
    }

    fun createTempCameraImageUri(context: Context, prefix: String = "scan_"): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${prefix}${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
