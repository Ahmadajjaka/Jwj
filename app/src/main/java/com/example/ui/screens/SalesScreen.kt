package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CashAccountEntity
import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.InvoiceWithItems
import com.example.data.local.MeasureUnit
import com.example.data.local.PartyType
import com.example.data.local.PaymentMethod
import com.example.data.local.PricingTier
import com.example.data.local.ProductUnitEntity
import com.example.data.local.StaffRole
import com.example.data.repository.AccountingEngine
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.ResolvedPriceQuote
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.InvoiceReturnDialog
import com.example.ui.components.OfficialInvoicePaperCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters
import com.example.utils.ExportAndShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    state: MamoMainUiState,
    viewModel: MamoViewModel,
    openCreatorInitially: Boolean = false,
    onCreatorHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val mamoColors = LocalMamoColors.current
    val cur = state.settings.currencySymbol
    val productUnits by viewModel.productUnits.collectAsStateWithLifecycle()
    val cashAccounts by viewModel.cashAccounts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(com.example.data.repository.InvoiceDateFilter()) }
    var showInvoiceEditor by remember(openCreatorInitially) { mutableStateOf(openCreatorInitially) }
    var editingInvoice by remember { mutableStateOf<InvoiceWithItems?>(null) }
    var selectedInvoiceDetail by remember { mutableStateOf<InvoiceWithItems?>(null) }
    var cancelTarget by remember { mutableStateOf<InvoiceWithItems?>(null) }
    var returnTarget by remember { mutableStateOf<InvoiceWithItems?>(null) }

    val allSalesCount = remember(state.invoices) {
        state.invoices.count { it.invoice.invoiceType == InvoiceType.SALE }
    }
    val salesInvoices = remember(state.invoices, searchQuery, dateFilter) {
        state.invoices.filter { inv ->
            inv.invoice.invoiceType == InvoiceType.SALE &&
                com.example.ui.components.matchesInvoiceDateFilter(inv.invoice.invoiceDateMs, dateFilter) &&
                (searchQuery.isBlank() ||
                    inv.invoice.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    inv.invoice.partyName.contains(searchQuery, ignoreCase = true) ||
                    inv.items.any { it.productName.contains(searchQuery, ignoreCase = true) })
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingInvoice = null
                    showInvoiceEditor = true
                },
                containerColor = mamoColors.gold,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("new_sale_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "فاتورة بيع جديدة")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث برقم الفاتورة أو الزبون أو الصنف") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            com.example.ui.components.InvoiceDateFilterBar(
                filter = dateFilter,
                onFilterChanged = { dateFilter = it },
                filteredCount = salesInvoices.size,
                totalCount = allSalesCount,
                dateFormatPattern = state.settings.dateFormatPattern
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (salesInvoices.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "لا توجد فواتير مبيعات بعد",
                    subtitle = "اضغط على زر (+) لإنشاء أول فاتورة بيع مع خصم تلقائي من المخزون وحساب التكلفة المرجحة"
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(salesInvoices, key = { it.invoice.id }) { item ->
                        val inv = item.invoice
                        val isCancelled = inv.status == InvoiceStatus.CANCELLED
                        GlassCard(
                            onClick = { selectedInvoiceDetail = item },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${inv.invoiceNumber} — ${inv.partyName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${ArabicFormatters.formatDateTime(inv.invoiceDateMs, state.settings.dateFormatPattern)} • ${ArabicFormatters.paymentMethodLabel(inv.paymentMethod)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(
                                    text = ArabicFormatters.invoiceStatusLabel(inv.status),
                                    color = if (isCancelled) mamoColors.loss else mamoColors.profit,
                                    backgroundColor = if (isCancelled) mamoColors.lossContainer else mamoColors.profitContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "الإجمالي: ${ArabicFormatters.formatMoney(inv.netTotal, cur)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (inv.remainingAmount > 0.0) {
                                    Text(
                                        text = "متبقي: ${ArabicFormatters.formatMoney(inv.remainingAmount, cur)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = mamoColors.warning
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInvoiceEditor) {
        SaleInvoiceEditorSheet(
            state = state,
            viewModel = viewModel,
            productUnits = productUnits,
            cashAccounts = cashAccounts,
            existing = editingInvoice,
            onDismiss = {
                showInvoiceEditor = false
                editingInvoice = null
                onCreatorHandled()
            },
            onSave = { lines, discount, payMethod, paid, partyId, custName, custPhone, notes, dueMs, saleDateMs, accountId, onError ->
                val targetEdit = editingInvoice
                if (targetEdit == null) {
                    viewModel.postSaleInvoice(
                        lines = lines,
                        invoiceDiscount = discount,
                        paymentMethod = payMethod,
                        paidAmount = paid,
                        partyId = partyId,
                        customerName = custName,
                        customerPhone = custPhone,
                        notes = notes,
                        dueDateMs = dueMs,
                        accountId = accountId,
                        saleDateMs = saleDateMs,
                        onError = onError
                    ) {
                        showInvoiceEditor = false
                        onCreatorHandled()
                    }
                } else {
                    viewModel.editSaleInvoice(
                        invoiceId = targetEdit.invoice.id,
                        lines = lines,
                        invoiceDiscount = discount,
                        paymentMethod = payMethod,
                        paidAmount = paid,
                        customerName = custName,
                        customerPhone = custPhone,
                        notes = notes,
                        dueDateMs = dueMs,
                        saleDateMs = saleDateMs,
                        onError = onError
                    ) {
                        showInvoiceEditor = false
                        editingInvoice = null
                        onCreatorHandled()
                    }
                }
            }
        )
    }

    selectedInvoiceDetail?.let { detail ->
        val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { selectedInvoiceDetail = null }, sheetState = detailSheetState) {
            val inv = detail.invoice
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OfficialInvoicePaperCard(
                    invoiceWithItems = detail,
                    settings = state.settings,
                    modifier = Modifier.testTag("official_sale_invoice_paper")
                )
                if (state.activeStaff?.canViewProfits == true) {
                    val gross = inv.netTotal - inv.totalCost
                    Text(
                        text = "ملخص التكلفة والربح (للمسؤول): التكلفة ${ArabicFormatters.formatMoney(inv.totalCost, cur)} | إجمالي الربح ${ArabicFormatters.formatMoney(gross, cur)}",
                        color = mamoColors.profit,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val pdf = ExportAndShareHelper.generateInvoicePdfFile(context, detail, state.settings)
                            ExportAndShareHelper.shareFile(context, pdf, "application/pdf", "طباعة / حفظ PDF")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            ExportAndShareHelper.shareInvoiceWhatsAppText(context, detail, state.settings)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("واتساب")
                    }
                }

                if (inv.status == InvoiceStatus.POSTED && state.activeStaff != null) {
                    OutlinedButton(
                        onClick = {
                            returnTarget = detail
                            selectedInvoiceDetail = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("sale_return_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.AssignmentReturn, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مرتجع مبيعات (إرجاع صنف)")
                    }
                }

                if (inv.status == InvoiceStatus.POSTED && state.activeStaff?.canCancelInvoices == true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                editingInvoice = detail
                                selectedInvoiceDetail = null
                                showInvoiceEditor = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تعديل")
                        }
                        Button(
                            onClick = {
                                cancelTarget = detail
                                selectedInvoiceDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إلغاء (قيد عكسي)")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    returnTarget?.let { target ->
        InvoiceReturnDialog(
            title = "مرتجع مبيعات",
            invoiceWithItems = target,
            cashAccounts = cashAccounts,
            currencySymbol = cur,
            isSaleReturn = true,
            onDismiss = { returnTarget = null },
            onConfirm = { productId, unitName, conversionFactor, returnedQty, refundCash, reason, accountId ->
                viewModel.postSaleReturn(
                    originalInvoiceId = target.invoice.id,
                    productId = productId,
                    unitName = unitName,
                    conversionFactor = conversionFactor,
                    returnedQty = returnedQty,
                    refundCashAmount = refundCash,
                    reason = reason,
                    accountId = accountId
                )
                returnTarget = null
            }
        )
    }

    cancelTarget?.let { inv ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("إلغاء الفاتورة ${inv.invoice.invoiceNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("لا يتم حذف الفواتير المعتمدة نهائياً. سيتم إنشاء قيد عكسي وإعادة البضاعة للمستودع وتصحيح الصندوق وذمة الزبون.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("سبب الإلغاء") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelInvoice(inv.invoice.id, reason.ifBlank { "إلغاء بناءً على طلب العميل" })
                        cancelTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss)
                ) {
                    Text("تأكيد القيد العكسي")
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) { Text("تراجع") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleInvoiceEditorSheet(
    state: MamoMainUiState,
    viewModel: MamoViewModel,
    productUnits: List<ProductUnitEntity>,
    cashAccounts: List<CashAccountEntity>,
    existing: InvoiceWithItems?,
    onDismiss: () -> Unit,
    onSave: (
        lines: List<InvoiceLineDraft>,
        discount: Double,
        payMethod: String,
        paidAmount: Double,
        partyId: Long?,
        custName: String,
        custPhone: String,
        notes: String,
        dueDateMs: Long?,
        saleDateMs: Long,
        accountId: Long,
        onError: (String) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val cur = state.settings.currencySymbol
    val mamoColors = LocalMamoColors.current
    val customers = remember(state.parties) { state.parties.filter { it.partyType == PartyType.CUSTOMER } }
    val canEditPrice = state.activeStaff?.let { it.canEditPrices || it.role == StaffRole.OWNER } ?: false

    val lines = remember(existing) {
        mutableStateListOf<InvoiceLineDraft>().apply {
            existing?.items?.forEach { item ->
                add(
                    InvoiceLineDraft(
                        productId = item.productId,
                        productName = item.productName,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        lineDiscount = item.discountAmount,
                        unitName = item.unitName,
                        conversionFactor = item.conversionFactor,
                        pricingTier = item.pricingTier
                    )
                )
            }
        }
    }

    var barcodeQuery by remember { mutableStateOf("") }
    var pendingScanUri by remember { mutableStateOf<Uri?>(null) }
    var selectedProductId by remember { mutableStateOf(state.products.firstOrNull()?.id) }
    var productExpanded by remember { mutableStateOf(false) }
    val chosenProduct = state.products.firstOrNull { it.id == selectedProductId } ?: state.products.firstOrNull()

    var selectedUnitName by remember(chosenProduct?.id) {
        mutableStateOf(chosenProduct?.baseUnit ?: MeasureUnit.PIECE)
    }
    var selectedTier by remember { mutableStateOf(PricingTier.RETAIL) }
    var lineQtyText by remember { mutableStateOf("1") }
    var linePriceText by remember {
        mutableStateOf(chosenProduct?.sellingPrice?.toString() ?: "")
    }
    var lineDiscountText by remember { mutableStateOf("0") }
    var resolvedQuote by remember { mutableStateOf<ResolvedPriceQuote?>(null) }

    var invoiceDiscountText by remember { mutableStateOf(existing?.invoice?.discountAmount?.toString() ?: "0") }
    var paymentMethod by remember { mutableStateOf(existing?.invoice?.paymentMethod ?: PaymentMethod.CASH) }
    var paidAmountText by remember { mutableStateOf(existing?.invoice?.paidAmount?.toString() ?: "0") }
    var selectedCustomerId by remember { mutableStateOf(existing?.invoice?.partyId) }
    var customerExpanded by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf(existing?.invoice?.partyName ?: "") }
    var customerPhone by remember { mutableStateOf(existing?.invoice?.partyPhone ?: "") }
    var selectedAccountId by remember { mutableStateOf(existing?.invoice?.accountId ?: cashAccounts.firstOrNull()?.id ?: 1L) }
    var accountExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(existing?.invoice?.notes ?: "") }
    var dueDaysText by remember { mutableStateOf("30") }
    var selectedSaleDateMs by remember(existing?.invoice?.id) {
        mutableStateOf(existing?.invoice?.invoiceDateMs ?: System.currentTimeMillis())
    }

    var localError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val availableUnits = remember(chosenProduct, productUnits) {
        if (chosenProduct == null) emptyList()
        else buildList {
            add(chosenProduct.baseUnit to 1.0)
            productUnits.filter { it.productId == chosenProduct.id }.forEach { u ->
                if (none { it.first == u.unitName }) {
                    add(u.unitName to u.conversionToBase.coerceAtLeast(0.001))
                }
            }
        }
    }
    val selectedFactor = availableUnits.firstOrNull { it.first == selectedUnitName }?.second ?: 1.0

    fun applyScannedBarcode(code: String) {
        val clean = code.trim()
        if (clean.isBlank()) return
        val unitHit = productUnits.firstOrNull { it.barcode.equals(clean, ignoreCase = true) }
        val prodHit = if (unitHit != null) {
            state.products.firstOrNull { it.id == unitHit.productId }
        } else {
            state.products.firstOrNull { it.barcode.equals(clean, ignoreCase = true) || it.sku.equals(clean, ignoreCase = true) }
        }
        if (prodHit != null) {
            selectedProductId = prodHit.id
            selectedUnitName = unitHit?.unitName ?: prodHit.baseUnit
            barcodeQuery = ""
            localError = null
        } else {
            localError = "لم يتم العثور على صنف بالباركود: $clean"
        }
    }

    val barcodeCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingScanUri
        if (ok && uri != null) {
            viewModel.scanBarcodeFromImageUri(context, uri) { code ->
                applyScannedBarcode(code)
            }
        }
    }

    LaunchedEffect(chosenProduct?.id, selectedUnitName, selectedTier, selectedCustomerId, lineQtyText) {
        if (chosenProduct != null) {
            val q = ArabicFormatters.parseDoubleOrNull(lineQtyText) ?: 1.0
            val quote = viewModel.resolveProductPriceForLine(
                productId = chosenProduct.id,
                unitName = selectedUnitName,
                pricingTier = selectedTier,
                partyId = selectedCustomerId,
                requestedQuantity = q
            )
            resolvedQuote = quote
            linePriceText = quote.unitPrice.toString()
        }
    }

    val pendingQty = ArabicFormatters.parseDoubleOrNull(lineQtyText) ?: 0.0
    val pendingPrice = ArabicFormatters.parseDoubleOrNull(linePriceText) ?: chosenProduct?.sellingPrice ?: 0.0
    val pendingDiscount = (ArabicFormatters.parseDoubleOrNull(lineDiscountText) ?: 0.0).coerceAtLeast(0.0)
    val pendingDraft = if (lines.isEmpty() && chosenProduct != null && pendingQty > 0.0 && pendingPrice >= 0.0) {
        InvoiceLineDraft(
            productId = chosenProduct.id,
            productName = chosenProduct.name,
            quantity = pendingQty,
            unitPrice = pendingPrice,
            lineDiscount = pendingDiscount,
            availableStock = chosenProduct.quantity,
            currentCost = chosenProduct.averageCost,
            unitName = selectedUnitName,
            conversionFactor = selectedFactor,
            pricingTier = selectedTier,
            minSellingPrice = chosenProduct.minSellingPrice
        )
    } else null

    val previewLines = if (lines.isNotEmpty()) lines.toList() else listOfNotNull(pendingDraft)
    val subtotal = previewLines.sumOf { AccountingEngine.calculateLineTotal(it.quantity, it.unitPrice, it.lineDiscount) }
    val invDisc = (ArabicFormatters.parseDoubleOrNull(invoiceDiscountText) ?: 0.0).coerceAtLeast(0.0)
    val taxableBase = (subtotal - invDisc).coerceAtLeast(0.0)
    val taxAmount = if (state.settings.taxEnabled && state.settings.taxRatePercent > 0.0) {
        AccountingEngine.calculateTaxAmount(taxableBase, state.settings.taxRatePercent)
    } else 0.0
    val netTotal = AccountingEngine.roundMoney(taxableBase + taxAmount)

    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = editorSheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (existing == null) "فاتورة بيع جديدة (POS)" else "تعديل فاتورة ${existing.invoice.invoiceNumber}",
                style = MaterialTheme.typography.headlineSmall
            )

            if (state.activeStaff == null) {
                Surface(
                    color = mamoColors.lossContainer,
                    border = BorderStroke(1.dp, mamoColors.loss.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "أنت في وضع الزائر (تصفح فقط) — سجّل الدخول بحساب موظف أو مدير لتتمكن من اعتماد الفواتير.",
                        color = mamoColors.loss,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (state.products.isEmpty()) {
                Text(
                    text = "المستودع فارغ حالياً — أضف منتجات أولاً من قسم المخزون أو عبر فاتورة شراء.",
                    color = mamoColors.warning
                )
            } else if (chosenProduct != null) {
                // Barcode Quick Lookup + Camera Scan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = barcodeQuery,
                        onValueChange = { barcodeQuery = it },
                        label = { Text("بحث سريع بالباركود أو SKU") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { applyScannedBarcode(barcodeQuery) }) {
                        Text("بحث")
                    }
                    IconButton(onClick = {
                        val uri = ExportAndShareHelper.createTempCameraImageUri(context, "bc_")
                        pendingScanUri = uri
                        barcodeCameraLauncher.launch(uri)
                    }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "مسح الباركود بالكاميرا")
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = productExpanded,
                    onExpandedChange = { productExpanded = !productExpanded }
                ) {
                    OutlinedTextField(
                        value = "${chosenProduct.name} (متاح: ${ArabicFormatters.formatNumber(chosenProduct.quantity)} ${chosenProduct.baseUnit})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("اختر الصنف من المخزون") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = productExpanded,
                        onDismissRequest = { productExpanded = false }
                    ) {
                        state.products.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text("${prod.name} — متاح: ${ArabicFormatters.formatNumber(prod.quantity)} ${prod.baseUnit} — ${ArabicFormatters.formatMoney(prod.sellingPrice, cur)}") },
                                onClick = {
                                    selectedProductId = prod.id
                                    selectedUnitName = prod.baseUnit
                                    localError = null
                                    productExpanded = false
                                }
                            )
                        }
                    }
                }

                // Unit Selector & Pricing Tier
                if (availableUnits.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableUnits.forEach { (unitName, factor) ->
                            FilterChip(
                                selected = selectedUnitName == unitName,
                                onClick = { selectedUnitName = unitName },
                                label = { Text(if (factor == 1.0) unitName else "$unitName (×${ArabicFormatters.formatNumber(factor)})") }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        PricingTier.RETAIL to "مفرق",
                        PricingTier.WHOLESALE to "جملة",
                        PricingTier.SPECIAL to "سعر خاص"
                    ).forEach { (tier, lbl) ->
                        FilterChip(
                            selected = selectedTier == tier,
                            onClick = { selectedTier = tier },
                            label = { Text(lbl) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lineQtyText,
                        onValueChange = { lineQtyText = it; localError = null },
                        label = { Text("الكمية ($selectedUnitName)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = linePriceText,
                        onValueChange = { if (canEditPrice) { linePriceText = it; localError = null } },
                        enabled = canEditPrice,
                        label = { Text(if (canEditPrice) "سعر الوحدة" else "السعر (مقيد)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lineDiscountText,
                        onValueChange = { if (canEditPrice) { lineDiscountText = it; localError = null } },
                        enabled = canEditPrice,
                        label = { Text("خصم الصنف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                val livePrice = ArabicFormatters.parseDoubleOrNull(linePriceText) ?: 0.0
                val quote = resolvedQuote
                if (quote != null && (livePrice < quote.costForUnit || livePrice < quote.minPriceForUnit)) {
                    Text(
                        text = "تحذير تسعير: السعر (${ArabicFormatters.formatMoney(livePrice, cur)}) أقل من التكلفة أو الحد الأدنى المسموح!",
                        color = mamoColors.loss,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        val qty = ArabicFormatters.parseDoubleOrNull(lineQtyText) ?: 0.0
                        val price = ArabicFormatters.parseDoubleOrNull(linePriceText) ?: chosenProduct.sellingPrice
                        val disc = if (canEditPrice) (ArabicFormatters.parseDoubleOrNull(lineDiscountText) ?: 0.0).coerceAtLeast(0.0) else 0.0
                        when {
                            qty <= 0.0 -> localError = "يرجى إدخال كمية صالحة أكبر من صفر"
                            price < 0.0 -> localError = "سعر الوحدة لا يمكن أن يكون سالباً"
                            else -> {
                                lines.add(
                                    InvoiceLineDraft(
                                        productId = chosenProduct.id,
                                        productName = chosenProduct.name,
                                        quantity = qty,
                                        unitPrice = price,
                                        lineDiscount = disc,
                                        availableStock = chosenProduct.quantity,
                                        currentCost = chosenProduct.averageCost,
                                        unitName = selectedUnitName,
                                        conversionFactor = selectedFactor,
                                        pricingTier = selectedTier,
                                        minSellingPrice = chosenProduct.minSellingPrice
                                    )
                                )
                                lineQtyText = "1"
                                lineDiscountText = "0"
                                localError = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_sale_line_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة الصنف للفاتورة")
                }
            }

            if (lines.isNotEmpty()) {
                HorizontalDivider()
                lines.forEachIndexed { idx, line ->
                    val lt = AccountingEngine.calculateLineTotal(line.quantity, line.unitPrice, line.lineDiscount)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${line.productName} (${ArabicFormatters.formatNumber(line.quantity)} ${line.unitName} × ${ArabicFormatters.formatNumber(line.unitPrice)})",
                            modifier = Modifier.weight(1f)
                        )
                        Text(ArabicFormatters.formatMoney(lt, cur), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { lines.removeAt(idx); localError = null }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف البند")
                        }
                    }
                }
                HorizontalDivider()
            } else if (pendingDraft != null) {
                Text(
                    text = "سيتم اعتماد الصنف المحدد (${pendingDraft.productName} × ${ArabicFormatters.formatNumber(pendingDraft.quantity)} ${pendingDraft.unitName}) تلقائياً عند الضغط على اعتماد الفاتورة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = invoiceDiscountText,
                    onValueChange = { if (canEditPrice) { invoiceDiscountText = it; localError = null } },
                    enabled = canEditPrice,
                    label = { Text("خصم على الفاتورة") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (taxAmount > 0.0) {
                        Text("الضريبة (${ArabicFormatters.formatNumber(state.settings.taxRatePercent)}%): ${ArabicFormatters.formatMoney(taxAmount, cur)}", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = "الصافي: ${ArabicFormatters.formatMoney(netTotal, cur)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PaymentMethod.CASH to "نقدي",
                    PaymentMethod.CREDIT to "آجل",
                    PaymentMethod.PARTIAL to "جزئي"
                ).forEach { (m, label) ->
                    FilterChip(
                        selected = paymentMethod == m,
                        onClick = { paymentMethod = m; localError = null },
                        label = { Text(label) }
                    )
                }
            }

            if (paymentMethod != PaymentMethod.CREDIT && cashAccounts.isNotEmpty()) {
                val acc = cashAccounts.firstOrNull { it.id == selectedAccountId } ?: cashAccounts.first()
                ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = !accountExpanded }) {
                    OutlinedTextField(
                        value = acc.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("حساب التحصيل المالي") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                        cashAccounts.forEach { a ->
                            DropdownMenuItem(text = { Text(a.name) }, onClick = { selectedAccountId = a.id; accountExpanded = false })
                        }
                    }
                }
            }

            if (paymentMethod == PaymentMethod.PARTIAL) {
                OutlinedTextField(
                    value = paidAmountText,
                    onValueChange = { paidAmountText = it; localError = null },
                    label = { Text("المبلغ المدفوع الآن") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (paymentMethod != PaymentMethod.CASH) {
                OutlinedTextField(
                    value = dueDaysText,
                    onValueChange = { dueDaysText = it; localError = null },
                    label = { Text("مدة الاستحقاق (بالأيام)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (customers.isNotEmpty()) {
                val chosenCustomer = customers.firstOrNull { it.id == selectedCustomerId }
                ExposedDropdownMenuBox(
                    expanded = customerExpanded,
                    onExpandedChange = { customerExpanded = !customerExpanded }
                ) {
                    OutlinedTextField(
                        value = chosenCustomer?.name ?: "اختر زبون مسجل (أو أدخل يدوياً)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الزبون") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = customerExpanded,
                        onDismissRequest = { customerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("زبون جديد / نقدي") },
                            onClick = {
                                selectedCustomerId = null
                                customerName = ""
                                customerPhone = ""
                                localError = null
                                customerExpanded = false
                            }
                        )
                        customers.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.name} ${if (c.phone.isNotBlank()) "- ${c.phone}" else ""}") },
                                onClick = {
                                    selectedCustomerId = c.id
                                    customerName = c.name
                                    customerPhone = c.phone
                                    localError = null
                                    customerExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (selectedCustomerId == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it; localError = null },
                        label = {
                            Text(if (paymentMethod == PaymentMethod.CASH) "اسم الزبون (اختياري للنقدي)" else "اسم الزبون (مطلوب للآجل/الجزئي)")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            com.example.ui.components.InvoiceDateTimeSelector(
                selectedMs = selectedSaleDateMs,
                onDateTimeSelected = { selectedSaleDateMs = it },
                label = "تاريخ ووقت فاتورة البيع",
                dateFormatPattern = state.settings.dateFormatPattern,
                modifier = Modifier.testTag("sale_datetime_selector")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات") },
                modifier = Modifier.fillMaxWidth()
            )

            localError?.let { errorMsg ->
                Surface(
                    color = mamoColors.lossContainer,
                    border = BorderStroke(1.dp, mamoColors.loss.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sale_invoice_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = mamoColors.loss)
                        Text(
                            text = errorMsg,
                            color = mamoColors.loss,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val finalLines = if (lines.isNotEmpty()) {
                        lines.toList()
                    } else if (chosenProduct != null) {
                        val qty = ArabicFormatters.parseDoubleOrNull(lineQtyText) ?: 0.0
                        val price = ArabicFormatters.parseDoubleOrNull(linePriceText) ?: chosenProduct.sellingPrice
                        val disc = if (canEditPrice) (ArabicFormatters.parseDoubleOrNull(lineDiscountText) ?: 0.0).coerceAtLeast(0.0) else 0.0
                        if (qty <= 0.0) {
                            localError = "يرجى إدخال كمية صالحة أكبر من صفر"
                            return@Button
                        }
                        if (price < 0.0) {
                            localError = "سعر القطعة لا يمكن أن يكون سالباً"
                            return@Button
                        }
                        listOf(
                            InvoiceLineDraft(
                                productId = chosenProduct.id,
                                productName = chosenProduct.name,
                                quantity = qty,
                                unitPrice = price,
                                lineDiscount = disc,
                                availableStock = chosenProduct.quantity,
                                currentCost = chosenProduct.averageCost,
                                unitName = selectedUnitName,
                                conversionFactor = selectedFactor,
                                pricingTier = selectedTier,
                                minSellingPrice = chosenProduct.minSellingPrice
                            )
                        )
                    } else {
                        localError = "المستودع فارغ — أضف صنفاً من المخزون أولاً قبل اعتماد الفاتورة"
                        return@Button
                    }

                    if (paymentMethod != PaymentMethod.CASH && selectedCustomerId == null && customerName.isBlank()) {
                        localError = "يرجى اختيار الزبون أو إدخال اسمه للفواتير الآجلة أو الجزئية"
                        return@Button
                    }

                    val paid = ArabicFormatters.parseDoubleOrNull(paidAmountText) ?: 0.0
                    val days = (ArabicFormatters.parseLongOrNull(dueDaysText) ?: 30L).coerceAtLeast(1L)
                    val dueMs = if (paymentMethod != PaymentMethod.CASH) {
                        selectedSaleDateMs + days * 86_400_000L
                    } else null

                    localError = null
                    isSubmitting = true
                    onSave(
                        finalLines,
                        invDisc,
                        paymentMethod,
                        paid,
                        selectedCustomerId,
                        customerName,
                        customerPhone,
                        notes,
                        dueMs,
                        selectedSaleDateMs,
                        selectedAccountId
                    ) { msg ->
                        isSubmitting = false
                        localError = msg
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_sale_invoice_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("اعتماد الفاتورة وترحيل الحسابات")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
