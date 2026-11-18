package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
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
import com.example.data.local.ProductUnitEntity
import com.example.data.repository.AccountingEngine
import com.example.data.repository.InvoiceLineDraft
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.InvoiceReturnDialog
import com.example.ui.components.OfficialInvoicePaperCard
import com.example.ui.components.PurchaseOcrReviewSheet
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters
import com.example.utils.ExportAndShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
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
    val ocrDraft by viewModel.ocrDraft.collectAsStateWithLifecycle()
    val isOcrParsing by viewModel.isOcrParsing.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(com.example.data.repository.InvoiceDateFilter()) }
    var showCreateSheet by remember(openCreatorInitially) { mutableStateOf(openCreatorInitially) }
    var selectedDetail by remember { mutableStateOf<InvoiceWithItems?>(null) }
    var cancelTarget by remember { mutableStateOf<InvoiceWithItems?>(null) }
    var returnTarget by remember { mutableStateOf<InvoiceWithItems?>(null) }
    var pendingOcrPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val ocrCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingOcrPhotoUri
        if (ok && uri != null) viewModel.parsePurchaseOcrUri(context, uri)
    }
    val ocrGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.parsePurchaseOcrUri(context, uri)
    }
    val ocrPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.parsePurchaseOcrUri(context, uri)
    }

    val allPurchasesCount = remember(state.invoices) {
        state.invoices.count { it.invoice.invoiceType == InvoiceType.PURCHASE }
    }
    val purchaseInvoices = remember(state.invoices, searchQuery, dateFilter) {
        state.invoices.filter { inv ->
            inv.invoice.invoiceType == InvoiceType.PURCHASE &&
                com.example.ui.components.matchesInvoiceDateFilter(inv.invoice.invoiceDateMs, dateFilter) &&
                (searchQuery.isBlank() ||
                    inv.invoice.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    inv.invoice.partyName.contains(searchQuery, ignoreCase = true) ||
                    inv.items.any { it.productName.contains(searchQuery, ignoreCase = true) })
        }
    }

    Scaffold(
        floatingActionButton = {
            if (state.activeStaff?.canManageInventory == true) {
                FloatingActionButton(
                    onClick = { showCreateSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("new_purchase_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "فاتورة شراء جديدة")
                }
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
                label = { Text("بحث برقم الفاتورة أو المورد أو الصنف") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            com.example.ui.components.InvoiceDateFilterBar(
                filter = dateFilter,
                onFilterChanged = { dateFilter = it },
                filteredCount = purchaseInvoices.size,
                totalCount = allPurchasesCount,
                dateFormatPattern = state.settings.dateFormatPattern
            )
            if (state.activeStaff?.canManageInventory == true) {
                Spacer(modifier = Modifier.height(8.dp))
                GlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مسح فاتورة مورد بالذكاء الاصطناعي (OCR)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("استخرج البنود والكميات للمراجعة قبل الترحيل", style = MaterialTheme.typography.labelSmall)
                        }
                        if (isOcrParsing) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val uri = ExportAndShareHelper.createTempCameraImageUri(context, "ocr_")
                                pendingOcrPhotoUri = uri
                                ocrCameraLauncher.launch(uri)
                            },
                            enabled = !isOcrParsing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تصوير", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                ocrGalleryLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isOcrParsing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("صورة", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { ocrPdfLauncher.launch(arrayOf("application/pdf")) },
                            enabled = !isOcrParsing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", maxLines = 1)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (purchaseInvoices.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.AddShoppingCart,
                    title = "لا توجد فواتير مشتريات مسجلة",
                    subtitle = "سجّل فواتير الشراء لتحديث المخزون آلياً وإعادة احتساب المتوسط المرجح للتكلفة وديون الموردين"
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(purchaseInvoices, key = { it.invoice.id }) { item ->
                        val inv = item.invoice
                        val isCancelled = inv.status == InvoiceStatus.CANCELLED
                        GlassCard(
                            onClick = { selectedDetail = item },
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
                                        text = "${ArabicFormatters.formatDate(inv.invoiceDateMs, state.settings.dateFormatPattern)} • ${ArabicFormatters.paymentMethodLabel(inv.paymentMethod)}",
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
                                Text("الإجمالي: ${ArabicFormatters.formatMoney(inv.netTotal, cur)}", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "مدفوع: ${ArabicFormatters.formatMoney(inv.paidAmount, cur)} | متبقي: ${ArabicFormatters.formatMoney(inv.remainingAmount, cur)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (inv.remainingAmount > 0) mamoColors.warning else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        PurchaseCreatorSheet(
            state = state,
            productUnits = productUnits,
            cashAccounts = cashAccounts,
            onDismiss = {
                showCreateSheet = false
                onCreatorHandled()
            },
            onSave = { lines, disc, extraCosts, method, paid, partyId, supName, supPhone, notes, dueMs, purchaseDateMs, accountId, onError ->
                viewModel.postPurchaseInvoice(
                    lines = lines,
                    invoiceDiscount = disc,
                    paymentMethod = method,
                    paidAmount = paid,
                    partyId = partyId,
                    supplierName = supName,
                    supplierPhone = supPhone,
                    notes = notes,
                    dueDateMs = dueMs,
                    purchaseDateMs = purchaseDateMs,
                    extraCosts = extraCosts,
                    accountId = accountId,
                    onError = onError
                ) {
                    showCreateSheet = false
                    onCreatorHandled()
                }
            }
        )
    }

    ocrDraft?.let { draft ->
        PurchaseOcrReviewSheet(
            draft = draft,
            products = state.products,
            parties = state.parties,
            cashAccounts = cashAccounts,
            currencySymbol = cur,
            onDismiss = { viewModel.clearOcrDraft() },
            onConfirmPost = { lines, disc, extraCosts, method, paid, partyId, supName, supPhone, notes, dueMs, purchaseDateMs, accountId, ocrHash, onError ->
                viewModel.postPurchaseInvoice(
                    lines = lines,
                    invoiceDiscount = disc,
                    paymentMethod = method,
                    paidAmount = paid,
                    partyId = partyId,
                    supplierName = supName,
                    supplierPhone = supPhone,
                    notes = notes,
                    dueDateMs = dueMs,
                    purchaseDateMs = purchaseDateMs,
                    extraCosts = extraCosts,
                    accountId = accountId,
                    sourceOcrHash = ocrHash,
                    onError = onError
                ) {
                    viewModel.clearOcrDraft()
                }
            }
        )
    }

    selectedDetail?.let { detail ->
        val inv = detail.invoice
        val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { selectedDetail = null }, sheetState = detailSheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OfficialInvoicePaperCard(
                    invoiceWithItems = detail,
                    settings = state.settings,
                    modifier = Modifier.testTag("official_purchase_invoice_paper")
                )
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
                if (inv.status == InvoiceStatus.POSTED && state.activeStaff?.canManageInventory == true) {
                    OutlinedButton(
                        onClick = {
                            returnTarget = detail
                            selectedDetail = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("purchase_return_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.AssignmentReturn, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مرتجع مشتريات للمورد")
                    }
                }
                if (inv.status == InvoiceStatus.POSTED && state.activeStaff?.canCancelInvoices == true) {
                    Button(
                        onClick = {
                            cancelTarget = detail
                            selectedDetail = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إلغاء فاتورة الشراء (قيد عكسي)")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    returnTarget?.let { target ->
        InvoiceReturnDialog(
            title = "مرتجع مشتريات",
            invoiceWithItems = target,
            cashAccounts = cashAccounts,
            currencySymbol = cur,
            isSaleReturn = false,
            onDismiss = { returnTarget = null },
            onConfirm = { productId, unitName, conversionFactor, returnedQty, refundCash, reason, accountId ->
                viewModel.postPurchaseReturn(
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

    cancelTarget?.let { target ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("إلغاء فاتورة شراء ${target.invoice.invoiceNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم عكس الكميات من المستودع وإعادة المبلغ المدفوع للصندوق وتعديل ذمة المورد.")
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("سبب الإلغاء") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelInvoice(target.invoice.id, reason.ifBlank { "إلغاء فاتورة شراء" })
                        cancelTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss)
                ) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) { Text("تراجع") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseCreatorSheet(
    state: MamoMainUiState,
    productUnits: List<ProductUnitEntity>,
    cashAccounts: List<CashAccountEntity>,
    onDismiss: () -> Unit,
    onSave: (
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
        onError: (String) -> Unit
    ) -> Unit
) {
    val cur = state.settings.currencySymbol
    val mamoColors = LocalMamoColors.current
    val suppliers = remember(state.parties) { state.parties.filter { it.partyType == PartyType.SUPPLIER } }
    val lines = remember { mutableStateListOf<InvoiceLineDraft>() }

    var selectedProductId by remember { mutableStateOf(state.products.firstOrNull()?.id) }
    var productExpanded by remember { mutableStateOf(false) }
    val prod = state.products.firstOrNull { it.id == selectedProductId } ?: state.products.firstOrNull()
    var selectedUnitName by remember(prod?.id) { mutableStateOf(prod?.baseUnit ?: MeasureUnit.PIECE) }
    var qtyText by remember { mutableStateOf("1") }
    var costText by remember {
        mutableStateOf(prod?.lastPurchaseCost?.toString() ?: "0")
    }

    var discountText by remember { mutableStateOf("0") }
    var extraCostsText by remember { mutableStateOf("0") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var paidAmountText by remember { mutableStateOf("0") }
    var selectedSupplierId by remember { mutableStateOf<Long?>(null) }
    var supplierExpanded by remember { mutableStateOf(false) }
    var supplierName by remember { mutableStateOf("") }
    var supplierPhone by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(cashAccounts.firstOrNull()?.id ?: 1L) }
    var accountExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var selectedPurchaseDateMs by remember { mutableStateOf(System.currentTimeMillis()) }

    var localError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val availableUnits = remember(prod, productUnits) {
        if (prod == null) emptyList()
        else buildList {
            add(prod.baseUnit to 1.0)
            productUnits.filter { it.productId == prod.id }.forEach { u ->
                if (none { it.first == u.unitName }) {
                    add(u.unitName to u.conversionToBase.coerceAtLeast(0.001))
                }
            }
        }
    }
    val selectedFactor = availableUnits.firstOrNull { it.first == selectedUnitName }?.second ?: 1.0

    val previewQty = ArabicFormatters.parseDoubleOrNull(qtyText) ?: 0.0
    val previewCost = ArabicFormatters.parseDoubleOrNull(costText) ?: (prod?.lastPurchaseCost ?: 0.0)
    val pendingDraft = if (lines.isEmpty() && prod != null && previewQty > 0.0 && previewCost >= 0.0) {
        InvoiceLineDraft(
            productId = prod.id,
            productName = prod.name,
            quantity = previewQty,
            unitPrice = previewCost,
            unitName = selectedUnitName,
            conversionFactor = selectedFactor
        )
    } else null

    val previewLines = if (lines.isNotEmpty()) lines.toList() else listOfNotNull(pendingDraft)
    val subtotal = previewLines.sumOf { AccountingEngine.calculateLineTotal(it.quantity, it.unitPrice, 0.0) }
    val disc = (ArabicFormatters.parseDoubleOrNull(discountText) ?: 0.0).coerceAtLeast(0.0)
    val extraCosts = (ArabicFormatters.parseDoubleOrNull(extraCostsText) ?: 0.0).coerceAtLeast(0.0)
    val netTotal = (subtotal - disc + extraCosts).coerceAtLeast(0.0)

    val creatorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = creatorSheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("فاتورة شراء بضاعة", style = MaterialTheme.typography.headlineSmall)

            if (state.products.isEmpty()) {
                Text(
                    text = "يرجى إضافة الصنف أولاً في قسم المخزون قبل تسجيل فاتورة الشراء.",
                    color = mamoColors.warning
                )
            } else if (prod != null) {
                ExposedDropdownMenuBox(expanded = productExpanded, onExpandedChange = { productExpanded = !productExpanded }) {
                    OutlinedTextField(
                        value = "${prod.name} (الحالي: ${ArabicFormatters.formatNumber(prod.quantity)} ${prod.baseUnit})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("اختر الصنف") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = productExpanded, onDismissRequest = { productExpanded = false }) {
                        state.products.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.name} (تكلفة حالية: ${ArabicFormatters.formatMoney(p.averageCost, cur)})") },
                                onClick = {
                                    selectedProductId = p.id
                                    selectedUnitName = p.baseUnit
                                    costText = p.lastPurchaseCost.toString()
                                    localError = null
                                    productExpanded = false
                                }
                            )
                        }
                    }
                }

                if (availableUnits.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableUnits.forEach { (uName, factor) ->
                            FilterChip(
                                selected = selectedUnitName == uName,
                                onClick = { selectedUnitName = uName },
                                label = { Text(if (factor == 1.0) uName else "$uName (×${ArabicFormatters.formatNumber(factor)})") }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it; localError = null },
                        label = { Text("الكمية ($selectedUnitName)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it; localError = null },
                        label = { Text("سعر شراء $selectedUnitName") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (previewQty > 0.0) {
                    val baseQty = previewQty * selectedFactor
                    val unitBaseCost = if (selectedFactor > 0.0) previewCost / selectedFactor else previewCost
                    val projectedAvg = AccountingEngine.calculateWeightedAverageCost(prod.quantity, prod.averageCost, baseQty, unitBaseCost)
                    Text(
                        text = "المتوسط المرجح للوحدة الأساسية (${prod.baseUnit}) بعد الشراء: ${ArabicFormatters.formatMoney(projectedAvg, cur)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = mamoColors.profit
                    )
                }

                OutlinedButton(
                    onClick = {
                        when {
                            previewQty <= 0.0 -> localError = "يرجى إدخال كمية صالحة أكبر من صفر"
                            previewCost < 0.0 -> localError = "سعر الشراء لا يمكن أن يكون سالباً"
                            else -> {
                                lines.add(
                                    InvoiceLineDraft(
                                        productId = prod.id,
                                        productName = prod.name,
                                        quantity = previewQty,
                                        unitPrice = previewCost,
                                        unitName = selectedUnitName,
                                        conversionFactor = selectedFactor
                                    )
                                )
                                qtyText = "1"
                                localError = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_purchase_line_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة الصنف")
                }
            }

            if (lines.isNotEmpty()) {
                HorizontalDivider()
                lines.forEachIndexed { idx, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${line.productName} (${ArabicFormatters.formatNumber(line.quantity)} ${line.unitName} × ${ArabicFormatters.formatNumber(line.unitPrice)})", modifier = Modifier.weight(1f))
                        Text(ArabicFormatters.formatMoney(line.quantity * line.unitPrice, cur), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { lines.removeAt(idx); localError = null }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف")
                        }
                    }
                }
                HorizontalDivider()
            } else if (pendingDraft != null) {
                Text(
                    text = "سيتم اعتماد الصنف المحدد (${pendingDraft.productName} × ${ArabicFormatters.formatNumber(pendingDraft.quantity)} ${pendingDraft.unitName}) تلقائياً عند الضغط على اعتماد الشراء.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it; localError = null },
                    label = { Text("خصم المورد") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = extraCostsText,
                    onValueChange = { extraCostsText = it; localError = null },
                    label = { Text("نقل / شحن إضافي") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "الإجمالي الصافي (شامل الشحن): ${ArabicFormatters.formatMoney(netTotal, cur)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

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
                        label = { Text("حساب السداد المالي") },
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
                    label = { Text("المبلغ المدفوع للمورد") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (suppliers.isNotEmpty()) {
                val sup = suppliers.firstOrNull { it.id == selectedSupplierId }
                ExposedDropdownMenuBox(expanded = supplierExpanded, onExpandedChange = { supplierExpanded = !supplierExpanded }) {
                    OutlinedTextField(
                        value = sup?.name ?: "اختر مورد مسجل (أو أدخل يدوياً)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("المورد") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                        DropdownMenuItem(text = { Text("مورد جديد / نقدي") }, onClick = {
                            selectedSupplierId = null
                            supplierName = ""
                            supplierPhone = ""
                            localError = null
                            supplierExpanded = false
                        })
                        suppliers.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = {
                                selectedSupplierId = s.id
                                supplierName = s.name
                                supplierPhone = s.phone
                                localError = null
                                supplierExpanded = false
                            })
                        }
                    }
                }
            }

            if (selectedSupplierId == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = supplierName,
                        onValueChange = { supplierName = it; localError = null },
                        label = { Text(if (paymentMethod == PaymentMethod.CASH) "اسم المورد (اختياري للنقدي)" else "اسم المورد (مطلوب للآجل/الجزئي)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = supplierPhone,
                        onValueChange = { supplierPhone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            com.example.ui.components.InvoiceDateTimeSelector(
                selectedMs = selectedPurchaseDateMs,
                onDateTimeSelected = { selectedPurchaseDateMs = it },
                label = "تاريخ ووقت فاتورة الشراء",
                dateFormatPattern = state.settings.dateFormatPattern,
                modifier = Modifier.testTag("purchase_datetime_selector")
            )

            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())

            localError?.let { errorMsg ->
                Surface(
                    color = mamoColors.lossContainer,
                    border = BorderStroke(1.dp, mamoColors.loss.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("purchase_invoice_error_banner")
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
                    } else if (prod != null) {
                        if (previewQty <= 0.0) {
                            localError = "يرجى إدخال كمية صالحة أكبر من صفر"
                            return@Button
                        }
                        if (previewCost < 0.0) {
                            localError = "سعر الشراء لا يمكن أن يكون سالباً"
                            return@Button
                        }
                        listOf(
                            InvoiceLineDraft(
                                productId = prod.id,
                                productName = prod.name,
                                quantity = previewQty,
                                unitPrice = previewCost,
                                unitName = selectedUnitName,
                                conversionFactor = selectedFactor
                            )
                        )
                    } else {
                        localError = "يرجى إضافة الصنف في قسم المخزون أولاً"
                        return@Button
                    }

                    if (paymentMethod != PaymentMethod.CASH && selectedSupplierId == null && supplierName.isBlank()) {
                        localError = "يرجى تحديد اسم المورد للمشتريات الآجلة أو الجزئية"
                        return@Button
                    }

                    val paid = ArabicFormatters.parseDoubleOrNull(paidAmountText) ?: 0.0
                    val dueMs = if (paymentMethod != PaymentMethod.CASH) selectedPurchaseDateMs + 30L * 86_400_000L else null
                    localError = null
                    isSubmitting = true
                    onSave(finalLines, disc, extraCosts, paymentMethod, paid, selectedSupplierId, supplierName, supplierPhone, notes, dueMs, selectedPurchaseDateMs, selectedAccountId) { msg ->
                        isSubmitting = false
                        localError = msg
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_purchase_invoice_btn"),
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
                Text("اعتماد الشراء وتحديث المخزون")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
