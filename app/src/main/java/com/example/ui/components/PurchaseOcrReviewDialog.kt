package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CashAccountEntity
import com.example.data.local.MeasureUnit
import com.example.data.local.PartyEntity
import com.example.data.local.PartyType
import com.example.data.local.PaymentMethod
import com.example.data.local.ProductEntity
import com.example.data.repository.AccountingEngine
import com.example.data.repository.InvoiceLineDraft
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters
import com.example.utils.ParsedPurchaseOcrDraft

private data class EditableOcrRow(
    val rawName: String,
    val matchedProductId: Long?,
    val qtyText: String,
    val priceText: String,
    val unitName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOcrReviewSheet(
    draft: ParsedPurchaseOcrDraft,
    products: List<ProductEntity>,
    parties: List<PartyEntity>,
    cashAccounts: List<CashAccountEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirmPost: (
        lines: List<InvoiceLineDraft>,
        discount: Double,
        extraCosts: Double,
        payMethod: String,
        paidAmount: Double,
        partyId: Long?,
        supplierName: String,
        supplierPhone: String,
        notes: String,
        dueDateMs: Long?,
        purchaseDateMs: Long,
        accountId: Long,
        sourceOcrHash: String,
        onError: (String) -> Unit
    ) -> Unit
) {
    val mamoColors = LocalMamoColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val suppliers = remember(parties) { parties.filter { it.partyType == PartyType.SUPPLIER } }

    val editableRows = remember(draft) {
        mutableStateListOf<EditableOcrRow>().apply {
            draft.lines.forEach { line ->
                add(
                    EditableOcrRow(
                        rawName = line.rawName,
                        matchedProductId = line.matchedProductId ?: products.firstOrNull()?.id,
                        qtyText = line.quantity.toString(),
                        priceText = line.unitPrice.toString(),
                        unitName = line.unitName
                    )
                )
            }
        }
    }

    var supplierName by remember(draft) { mutableStateOf(draft.supplierName) }
    var supplierPhone by remember(draft) { mutableStateOf(draft.supplierPhone) }
    var selectedSupplierId by remember {
        mutableStateOf(
            suppliers.firstOrNull {
                draft.supplierName.isNotBlank() && it.name.contains(draft.supplierName, ignoreCase = true)
            }?.id
        )
    }
    var discountText by remember(draft) { mutableStateOf(draft.invoiceDiscount.toString()) }
    var extraCostsText by remember(draft) { mutableStateOf(draft.extraCosts.toString()) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var paidAmountText by remember { mutableStateOf("0") }
    var selectedAccountId by remember { mutableStateOf(cashAccounts.firstOrNull()?.id ?: 1L) }
    var notes by remember(draft) {
        mutableStateOf(
            if (draft.invoiceNumber.isNotBlank()) "مسح ضوئي لفاتورة مورد #${draft.invoiceNumber}"
            else "مسح ضوئي لفاتورة مورد"
        )
    }
    var selectedPurchaseDateMs by remember(draft) {
        mutableStateOf(parseOcrInvoiceDateOrNow(draft.rawText))
    }
    var localError by remember { mutableStateOf<String?>(null) }

    val totalLinesAmount = editableRows.sumOf { row ->
        val q = ArabicFormatters.parseDoubleOrNull(row.qtyText) ?: 0.0
        val p = ArabicFormatters.parseDoubleOrNull(row.priceText) ?: 0.0
        q * p
    }
    val discount = (ArabicFormatters.parseDoubleOrNull(discountText) ?: 0.0).coerceAtLeast(0.0)
    val extraCosts = (ArabicFormatters.parseDoubleOrNull(extraCostsText) ?: 0.0).coerceAtLeast(0.0)
    val netTotal = (totalLinesAmount - discount + extraCosts).coerceAtLeast(0.0)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = mamoColors.gold)
                Text(
                    text = "مراجعة الفاتورة المستخرجة (${if (draft.usedAiParser) "Groq AI OCR" else "تحليل محلي"})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (draft.warnings.isNotEmpty()) {
                Surface(
                    color = mamoColors.goldContainer,
                    border = BorderStroke(1.dp, mamoColors.warning),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        draft.warnings.forEach { warn ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = mamoColors.warning)
                                Text(warn, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (draft.detectedBarcodes.isNotEmpty()) {
                Text(
                    text = "الباركود المكتشف: ${draft.detectedBarcodes.joinToString(" • ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = supplierName,
                    onValueChange = { supplierName = it; selectedSupplierId = null },
                    label = { Text("اسم المورد") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = supplierPhone,
                    onValueChange = { supplierPhone = it },
                    label = { Text("الهاتف") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()
            Text("البنود المستخرجة (${editableRows.size}) — تحقق واربُط كل بند بصنف المخزون:", style = MaterialTheme.typography.labelLarge)

            if (editableRows.isEmpty()) {
                Text("لم يتم العثور على بنود رقمية واضحة. راجع النص المستخرج أو أدخل البنود يدوياً.")
            } else {
                editableRows.forEachIndexed { idx, row ->
                    var prodExpanded by remember { mutableStateOf(false) }
                    val matchedProd = products.firstOrNull { it.id == row.matchedProductId }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("النص المقروء: ${row.rawName}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { editableRows.removeAt(idx) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف السطر")
                                }
                            }
                            ExposedDropdownMenuBox(
                                expanded = prodExpanded,
                                onExpandedChange = { prodExpanded = !prodExpanded }
                            ) {
                                OutlinedTextField(
                                    value = matchedProd?.name ?: "اختر صنفاً من المخزون",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("ربط بصنف المخزون") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = prodExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = prodExpanded, onDismissRequest = { prodExpanded = false }) {
                                    products.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text("${p.name} (${p.baseUnit})") },
                                            onClick = {
                                                editableRows[idx] = row.copy(matchedProductId = p.id, unitName = p.baseUnit)
                                                prodExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = row.qtyText,
                                    onValueChange = { editableRows[idx] = row.copy(qtyText = it) },
                                    label = { Text("الكمية") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = row.priceText,
                                    onValueChange = { editableRows[idx] = row.copy(priceText = it) },
                                    label = { Text("السعر") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = row.unitName,
                                    onValueChange = { editableRows[idx] = row.copy(unitName = it) },
                                    label = { Text("الوحدة") },
                                    modifier = Modifier.weight(0.9f)
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text("الخصم") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = extraCostsText,
                    onValueChange = { extraCostsText = it },
                    label = { Text("مصاريف الشحن/النقل") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaymentMethod.CASH to "نقدي", PaymentMethod.CREDIT to "آجل", PaymentMethod.PARTIAL to "جزئي").forEach { (m, lbl) ->
                    FilterChip(selected = paymentMethod == m, onClick = { paymentMethod = m }, label = { Text(lbl) })
                }
            }

            if (paymentMethod == PaymentMethod.PARTIAL) {
                OutlinedTextField(
                    value = paidAmountText,
                    onValueChange = { paidAmountText = it },
                    label = { Text("المدفوع للمورد") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            InvoiceDateTimeSelector(
                selectedMs = selectedPurchaseDateMs,
                onDateTimeSelected = { selectedPurchaseDateMs = it },
                label = "تاريخ ووقت فاتورة الشراء",
                modifier = Modifier.testTag("ocr_purchase_datetime_selector")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "الصافي المقدر للاعتماد: ${ArabicFormatters.formatMoney(netTotal, currencySymbol)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            localError?.let {
                Text(it, color = mamoColors.loss, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("إلغاء")
                }
                Button(
                    onClick = {
                        val finalLines = editableRows.mapNotNull { row ->
                            val prod = products.firstOrNull { it.id == row.matchedProductId } ?: return@mapNotNull null
                            val q = ArabicFormatters.parseDoubleOrNull(row.qtyText) ?: 0.0
                            val p = ArabicFormatters.parseDoubleOrNull(row.priceText) ?: 0.0
                            if (q <= 0.0) return@mapNotNull null
                            InvoiceLineDraft(
                                productId = prod.id,
                                productName = prod.name,
                                quantity = q,
                                unitPrice = p,
                                unitName = row.unitName.ifBlank { prod.baseUnit },
                                conversionFactor = 1.0
                            )
                        }
                        if (finalLines.isEmpty()) {
                            localError = "يرجى ربط بند واحد على الأقل بصنف موجود في المخزون وبكمية أكبر من صفر"
                            return@Button
                        }
                        if (paymentMethod != PaymentMethod.CASH && selectedSupplierId == null && supplierName.isBlank()) {
                            localError = "اسم المورد مطلوب للفواتير الآجلة أو الجزئية"
                            return@Button
                        }
                        val paid = ArabicFormatters.parseDoubleOrNull(paidAmountText) ?: 0.0
                        val dueMs = if (paymentMethod != PaymentMethod.CASH) selectedPurchaseDateMs + 30L * 86_400_000L else null
                        onConfirmPost(
                            finalLines,
                            discount,
                            extraCosts,
                            paymentMethod,
                            paid,
                            selectedSupplierId,
                            supplierName,
                            supplierPhone,
                            notes,
                            dueMs,
                            selectedPurchaseDateMs,
                            selectedAccountId,
                            draft.sourceOcrHash
                        ) { err -> localError = err }
                    },
                    modifier = Modifier.weight(2f).testTag("ocr_confirm_post_btn")
                ) {
                    Text("مراجعة واعتماد فاتورة الشراء")
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}
