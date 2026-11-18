package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.InvoiceWithItems
import com.example.data.local.StoreSettingsEntity
import com.example.utils.ArabicFormatters
import kotlin.math.min

private val PaperCream = Color(0xFFFBF7EC)
private val PaperNavy = Color(0xFF0E203C)
private val PaperNavyMid = Color(0xFF1A355E)
private val PaperGold = Color(0xFFC59B3D)
private val PaperGoldBright = Color(0xFFE7CA78)
private val PaperMuted = Color(0xFF6B644D)
private val PaperBorderGray = Color(0xFF8C7E5E)
private val PaperRowAlt = Color(0xFFE5E4E0)
private val PaperRowLight = Color(0xFFFFFDF8)
private val PaperSignatureBlue = Color(0xFF183D7A)
private val PaperCancelledRed = Color(0xFFC62828)

@Composable
fun OfficialInvoicePaperCard(
    invoiceWithItems: InvoiceWithItems,
    settings: StoreSettingsEntity,
    modifier: Modifier = Modifier
) {
    val inv = invoiceWithItems.invoice
    val cur = settings.currencySymbol
    val isSale = inv.invoiceType == InvoiceType.SALE
    val isCancelled = inv.status == InvoiceStatus.CANCELLED
    val isPaidFull = !isCancelled && inv.remainingAmount <= 0.01 && inv.paidAmount > 0.0
    val isUnpaid = !isCancelled && inv.paidAmount <= 0.01
    val isPartial = !isCancelled && inv.paidAmount > 0.01 && inv.remainingAmount > 0.01

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(PaperCream, RoundedCornerShape(6.dp))
            .border(2.5.dp, PaperNavy, RoundedCornerShape(6.dp))
            .padding(5.dp)
            .border(1.dp, PaperGold, RoundedCornerShape(4.dp))
    ) {
        // Corner navy & gold filigree ornaments
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCornerOrnament(0f, 0f, flipX = false, flipY = false)
            drawCornerOrnament(size.width, 0f, flipX = true, flipY = false)
            drawCornerOrnament(0f, size.height, flipX = false, flipY = true)
            drawCornerOrnament(size.width, size.height, flipX = true, flipY = true)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Header: Right = Invoice Title & Metadata, Left = Store Emblem & Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1.45f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isSale) "فاتورة بيع" else "فاتورة شراء",
                            color = PaperNavy,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (isCancelled) {
                            Surface(
                                color = PaperCancelledRed.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, PaperCancelledRed),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ملغاة (قيد عكسي)",
                                    color = PaperCancelledRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    InvoiceDottedField("فاتورة رقم:", inv.invoiceNumber)
                    InvoiceDottedField("تاريخ:", ArabicFormatters.formatDate(inv.invoiceDateMs, settings.dateFormatPattern))
                    InvoiceDottedField(
                        if (isSale) "اسم العميل:" else "اسم المورد:",
                        inv.partyName
                    )
                    InvoiceDottedField("رقم الهاتف:", inv.partyPhone.ifBlank { "—" })
                    InvoiceDottedField("العنوان / ملاحظات:", inv.notes.ifBlank { "—" })
                    InvoiceDottedField("طريقة الدفع:", ArabicFormatters.paymentMethodLabel(inv.paymentMethod))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MamoMonogramEmblem(modifier = Modifier.size(68.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = settings.storeName,
                        color = PaperNavy,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "إدارة ${settings.managerName}",
                        color = PaperMuted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Status Checkbox Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("حالة الفاتورة:", color = PaperNavy, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                InvoiceStatusCheckbox("مدفوعة", isPaidFull)
                InvoiceStatusCheckbox("غير مدفوعة", isUnpaid)
                InvoiceStatusCheckbox("مدفوعة جزئياً", isPartial)
            }

            // 6-Column Zebra Items Table (padded to min 8 rows like official printed stationery)
            val totalRows = maxOf(8, invoiceWithItems.items.size)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaperBorderGray)
            ) {
                // Navy Header Bar with Gold Text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(PaperNavy)
                ) {
                    TableHeaderCell("م", 0.55f)
                    TableHeaderCell("اسم المادة أو الصنف", 2.2f)
                    TableHeaderCell("الكمية", 0.8f)
                    TableHeaderCell("الوحدة", 0.75f)
                    TableHeaderCell("سعر الوحدة", 1.0f)
                    TableHeaderCell("الإجمالي", 1.05f, showDivider = false)
                }

                for (idx in 0 until totalRows) {
                    val item = invoiceWithItems.items.getOrNull(idx)
                    val rowBg = if (idx % 2 == 0) PaperRowLight else PaperRowAlt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(rowBg)
                            .border(0.5.dp, PaperBorderGray)
                    ) {
                        TableBodyCell(if (item != null) (idx + 1).toString() else "", 0.55f)
                        TableBodyCell(item?.productName ?: "", 2.2f, alignStart = true)
                        TableBodyCell(item?.let { ArabicFormatters.formatNumber(it.quantity) } ?: "", 0.8f)
                        TableBodyCell(item?.unitName ?: "", 0.75f)
                        TableBodyCell(item?.let { ArabicFormatters.formatNumber(it.unitPrice) } ?: "", 1.0f)
                        TableBodyCell(
                            item?.let { ArabicFormatters.formatNumber(it.lineTotal) } ?: "",
                            1.05f,
                            isBold = true,
                            showDivider = false
                        )
                    }
                }
            }

            // Bottom Totals + Official Store Stamp & Manager Signature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Right: Financial Summary + Navy/Gold Final Total Pill
                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InvoiceTotalLine("المجموع الفرعي", ArabicFormatters.formatMoney(inv.subtotal, cur))
                    InvoiceTotalLine("الخصم", ArabicFormatters.formatMoney(inv.discountAmount, cur))
                    if (inv.taxAmount > 0.0) {
                        InvoiceTotalLine("الضريبة (${ArabicFormatters.formatNumber(inv.taxRate)}%)", ArabicFormatters.formatMoney(inv.taxAmount, cur))
                    }
                    InvoiceTotalLine("المبلغ المدفوع", ArabicFormatters.formatMoney(inv.paidAmount, cur))
                    InvoiceTotalLine("المبلغ المتبقي", ArabicFormatters.formatMoney(inv.remainingAmount, cur))

                    Spacer(modifier = Modifier.height(2.dp))

                    // Two-tone Navy + Brushed Gold Final Total Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, PaperGold, RoundedCornerShape(8.dp))
                            .height(IntrinsicSize.Min)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.95f)
                                .background(PaperNavy)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("الإجمالي", color = PaperGoldBright, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                            Text("الإجمالي النهائي", color = PaperGoldBright, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(PaperGoldBright, PaperGold, PaperGoldBright)
                                    )
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ArabicFormatters.formatMoney(inv.netTotal, cur),
                                color = PaperNavy,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Left: Official Circular Store Stamp + Manager Signature Box
                Column(
                    modifier = Modifier.weight(0.95f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (settings.printShowStamp) {
                        OfficialStoreStamp(
                            storeName = settings.storeName,
                            managerName = settings.managerName,
                            modifier = Modifier.size(82.dp)
                        )
                    }

                    if (settings.printShowSignature) {
                        Surface(
                            color = PaperRowLight,
                            border = BorderStroke(1.2.dp, PaperNavyMid),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "توقيع المدير المسؤول",
                                    color = PaperNavy,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = settings.managerName,
                                    color = PaperSignatureBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }

            // Ornamental Gold Divider + Footer
            Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                val midY = size.height / 2f
                val midX = size.width / 2f
                drawLine(PaperGold, Offset(8f, midY), Offset(midX - 18f, midY), strokeWidth = 1.5f)
                drawLine(PaperGold, Offset(midX + 18f, midY), Offset(size.width - 8f, midY), strokeWidth = 1.5f)
                drawCircle(PaperNavy, radius = 3.5f, center = Offset(midX, midY))
                drawCircle(PaperGold, radius = 2.2f, center = Offset(midX - 10f, midY))
                drawCircle(PaperGold, radius = 2.2f, center = Offset(midX + 10f, midY))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "شكراً لثقتكم ب${settings.storeName}",
                    color = PaperNavy,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "إدارة المتجر: ${settings.managerName}",
                    color = PaperNavy,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun InvoiceDottedField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = label,
            color = PaperNavy,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                color = PaperNavyMid,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                val dash = 4f
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = PaperBorderGray,
                        start = Offset(x, 1f),
                        end = Offset(min(x + dash, size.width), 1f),
                        strokeWidth = 1f
                    )
                    x += dash * 2f
                }
            }
        }
    }
}

@Composable
private fun InvoiceStatusCheckbox(label: String, checked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .border(1.2.dp, PaperNavy, RoundedCornerShape(2.dp))
                .background(if (checked) PaperNavy else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text("✓", color = PaperGoldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, color = PaperNavy, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableHeaderCell(
    text: String,
    weight: Float,
    showDivider: Boolean = true
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(vertical = 5.dp, horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = PaperGoldBright,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
    if (showDivider) {
        Box(modifier = Modifier.width(0.75.dp).fillMaxHeight().background(PaperGold.copy(alpha = 0.65f)))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableBodyCell(
    text: String,
    weight: Float,
    alignStart: Boolean = false,
    isBold: Boolean = false,
    showDivider: Boolean = true
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(22.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = text,
            color = PaperNavy,
            fontSize = 9.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center
        )
    }
    if (showDivider) {
        Box(modifier = Modifier.width(0.75.dp).height(22.dp).background(PaperBorderGray))
    }
}

@Composable
private fun InvoiceTotalLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PaperNavy, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
            Text(value, color = PaperNavyMid, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Box(modifier = Modifier.fillMaxWidth().height(0.8.dp).background(PaperBorderGray))
        }
    }
}

@Composable
private fun MamoMonogramEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Stylized M + Wrench + Hammer + Central Screw silhouette
        val mPath = Path().apply {
            moveTo(w * 0.16f, h * 0.84f)
            lineTo(w * 0.28f, h * 0.24f)
            lineTo(w * 0.50f, h * 0.62f)
            lineTo(w * 0.72f, h * 0.24f)
            lineTo(w * 0.84f, h * 0.84f)
        }
        drawPath(mPath, color = PaperNavy, style = stroke)
        // Gold hammer handle & head accent on right leg
        drawLine(
            color = PaperGold,
            start = Offset(w * 0.54f, h * 0.58f),
            end = Offset(w * 0.78f, h * 0.22f),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = PaperGold,
            start = Offset(w * 0.68f, h * 0.14f),
            end = Offset(w * 0.88f, h * 0.30f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Butt
        )
        // Central vertical screw shaft
        drawLine(
            color = PaperNavyMid,
            start = Offset(w * 0.50f, h * 0.12f),
            end = Offset(w * 0.50f, h * 0.54f),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = PaperGold,
            start = Offset(w * 0.42f, h * 0.12f),
            end = Offset(w * 0.58f, h * 0.12f),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun OfficialStoreStamp(
    storeName: String,
    managerName: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val r = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(PaperNavyMid, radius = r - 2f, center = center, style = Stroke(width = 2.4f))
        drawCircle(PaperNavyMid, radius = r - 7f, center = center, style = Stroke(width = 1.1f))
        drawCircle(PaperNavyMid, radius = r * 0.48f, center = center, style = Stroke(width = 1.2f))

        // Inner small M
        val mPath = Path().apply {
            moveTo(center.x - r * 0.25f, center.y + r * 0.18f)
            lineTo(center.x - r * 0.16f, center.y - r * 0.16f)
            lineTo(center.x, center.y + r * 0.06f)
            lineTo(center.x + r * 0.16f, center.y - r * 0.16f)
            lineTo(center.x + r * 0.25f, center.y + r * 0.18f)
        }
        drawPath(mPath, color = PaperNavyMid, style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val textPaint = Paint().apply {
            color = android.graphics.Color.rgb(26, 53, 94)
            textSize = r * 0.22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(storeName.take(16), center.x, center.y - r * 0.58f, textPaint)
        drawContext.canvas.nativeCanvas.drawText(managerName.take(18), center.x, center.y + r * 0.72f, textPaint)
    }
}

private fun DrawScope.drawCornerOrnament(x: Float, y: Float, flipX: Boolean, flipY: Boolean) {
    val sx = if (flipX) -1f else 1f
    val sy = if (flipY) -1f else 1f
    val path = Path().apply {
        moveTo(x + sx * 8f, y + sy * 28f)
        quadraticTo(x + sx * 10f, y + sy * 10f, x + sx * 28f, y + sy * 8f)
    }
    drawPath(path, color = PaperGold, style = Stroke(width = 2f, cap = StrokeCap.Round))
    drawCircle(PaperNavy, radius = 3.2f, center = Offset(x + sx * 13f, y + sy * 13f))
}
