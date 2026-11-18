package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.local.ProductEntity
import com.example.data.remote.MamoAiTools
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: MamoMainUiState,
    viewModel: MamoViewModel
) {
    val context = LocalContext.current
    val mamoColors = LocalMamoColors.current
    val cur = state.settings.currencySymbol
    val canEdit = state.activeStaff?.canManageInventory == true
    val canApproveStocktake = state.activeStaff?.canApproveStocktake == true

    val productUnits by viewModel.productUnits.collectAsStateWithLifecycle()
    val specialPrices by viewModel.customerSpecialPrices.collectAsStateWithLifecycle()
    val priceHistory by viewModel.priceHistory.collectAsStateWithLifecycle()
    val stockCounts by viewModel.stockCounts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var lowStockOnly by remember { mutableStateOf(false) }
    var staleOnly by remember { mutableStateOf(false) }
    var showStockCountsTab by remember { mutableStateOf(false) }

    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }
    var stocktakeProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var historyProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var advancedProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val staleIds = remember(state.products, state.settings.staleDaysThreshold) {
        MamoAiTools.findStaleProducts(state.products, state.settings.staleDaysThreshold).map { it.id }.toSet()
    }
    val categories = remember(state.products) {
        listOf("الكل") + state.products.map { it.category }.distinct().sorted()
    }

    val filteredProducts = remember(state.products, searchQuery, selectedCategory, lowStockOnly, staleOnly, staleIds) {
        state.products.filter { p ->
            (selectedCategory == "الكل" || p.category == selectedCategory) &&
                (!lowStockOnly || p.quantity <= p.minStockLevel) &&
                (!staleOnly || p.id in staleIds) &&
                (searchQuery.isBlank() ||
                    p.name.contains(searchQuery, ignoreCase = true) ||
                    p.barcode.contains(searchQuery, ignoreCase = true) ||
                    p.sku.contains(searchQuery, ignoreCase = true) ||
                    p.category.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        floatingActionButton = {
            if (canEdit && !showStockCountsTab) {
                FloatingActionButton(
                    onClick = {
                        editingProduct = null
                        showProductDialog = true
                    },
                    containerColor = mamoColors.gold,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_product_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة منتج")
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

            GlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إجمالي قيمة المخزون بالتكلفة", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = ArabicFormatters.formatMoney(state.dashboardMetrics.inventoryValueAtCost, cur),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(
                            text = "${state.products.size} صنف",
                            color = mamoColors.gold,
                            backgroundColor = mamoColors.goldContainer
                        )
                        if (filteredProducts.isNotEmpty()) {
                            OutlinedButton(onClick = { viewModel.exportBarcodeLabelsPdf(context, filteredProducts) }) {
                                Icon(Icons.Default.Print, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ملصقات باركود PDF", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث باسم المنتج أو الباركود أو SKU") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = !showStockCountsTab && !lowStockOnly && !staleOnly && selectedCategory == "الكل",
                        onClick = { showStockCountsTab = false; lowStockOnly = false; staleOnly = false; selectedCategory = "الكل" },
                        label = { Text("الكل") }
                    )
                }
                item {
                    FilterChip(
                        selected = lowStockOnly && !showStockCountsTab,
                        onClick = { showStockCountsTab = false; lowStockOnly = !lowStockOnly; staleOnly = false },
                        label = { Text("النواقص (${state.dashboardMetrics.lowStockCount})") }
                    )
                }
                item {
                    FilterChip(
                        selected = staleOnly && !showStockCountsTab,
                        onClick = { showStockCountsTab = false; staleOnly = !staleOnly; lowStockOnly = false },
                        label = { Text("الراكدة (${staleIds.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = showStockCountsTab,
                        onClick = { showStockCountsTab = !showStockCountsTab },
                        label = { Text("جلسات الجرد (${stockCounts.size})") }
                    )
                }
                items(categories.filter { it != "الكل" }) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat && !showStockCountsTab,
                        onClick = { showStockCountsTab = false; selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (showStockCountsTab) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        StockCountsApprovalList(
                            stockCounts = stockCounts,
                            canApprove = canApproveStocktake,
                            currencySymbol = cur,
                            onApprove = { viewModel.approveStockCount(it) }
                        )
                    }
                }
            } else if (filteredProducts.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.Inventory2,
                    title = "لا توجد أصناف مطابقة",
                    subtitle = "أضف منتجات الخردوات مع الوحدات المتعددة والباركود وسعر المفرق والجملة"
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { prod ->
                        val isLow = prod.quantity <= prod.minStockLevel
                        val isStale = prod.id in staleIds
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prod.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("${prod.category} • ${prod.baseUnit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (prod.barcode.isNotBlank()) {
                                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.height(14.dp))
                                            Text(prod.barcode, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (isStale) {
                                        Text("راكد منذ +${state.settings.staleDaysThreshold} يوم", style = MaterialTheme.typography.labelSmall, color = mamoColors.warning)
                                    }
                                }
                                StatusPill(
                                    text = "${ArabicFormatters.formatNumber(prod.quantity)} ${prod.baseUnit}",
                                    color = if (isLow) mamoColors.loss else mamoColors.profit,
                                    backgroundColor = if (isLow) mamoColors.lossContainer else mamoColors.profitContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("التكلفة: ${ArabicFormatters.formatMoney(prod.averageCost, cur)}", style = MaterialTheme.typography.bodySmall)
                                    Text("مفرق: ${ArabicFormatters.formatMoney(prod.sellingPrice, cur)} | جملة: ${ArabicFormatters.formatMoney(prod.wholesalePrice, cur)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Row {
                                    IconButton(onClick = { advancedProduct = prod }) {
                                        Icon(Icons.Default.Category, contentDescription = "الوحدات والأسعار الخاصة والتالف")
                                    }
                                    IconButton(onClick = { historyProduct = prod }) {
                                        Icon(Icons.Default.History, contentDescription = "سجل حركة الصنف")
                                    }
                                    if (canEdit) {
                                        IconButton(onClick = { stocktakeProduct = prod }) {
                                            Icon(Icons.Default.Tune, contentDescription = "جرد وتعديل الكمية")
                                        }
                                        IconButton(onClick = {
                                            editingProduct = prod
                                            showProductDialog = true
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل الصنف")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProductDialog) {
        UltimateProductEditorDialog(
            existing = editingProduct,
            onDismiss = { showProductDialog = false },
            onSave = { name, cat, barcode, cost, sell, qty, minLvl, sku, imgUri, baseUnit, wholesale, minSell, defSup ->
                viewModel.saveProduct(
                    editingProduct?.id, name, cat, barcode, cost, sell, qty, minLvl,
                    sku, imgUri, baseUnit, wholesale, minSell, defSup
                )
                showProductDialog = false
            }
        )
    }

    advancedProduct?.let { prod ->
        ProductAdvancedSheet(
            product = prod,
            units = productUnits,
            specialPrices = specialPrices,
            priceHistory = priceHistory,
            parties = state.parties,
            currencySymbol = cur,
            canEdit = canEdit,
            viewModel = viewModel,
            onDismiss = { advancedProduct = null }
        )
    }

    stocktakeProduct?.let { prod ->
        var newQtyText by remember(prod) { mutableStateOf(prod.quantity.toString()) }
        var reason by remember { mutableStateOf("") }
        var formalApprovalMode by remember { mutableStateOf(!canApproveStocktake) }
        AlertDialog(
            onDismissRequest = { stocktakeProduct = null },
            title = { Text("جرد وتعديل: ${prod.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("الكمية المسجلة: ${ArabicFormatters.formatNumber(prod.quantity)} ${prod.baseUnit}")
                    OutlinedTextField(
                        value = newQtyText,
                        onValueChange = { newQtyText = it },
                        label = { Text("الكمية الفعلية الجديدة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("السبب / ملاحظة الجرد") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = formalApprovalMode,
                            onClick = { formalApprovalMode = true },
                            label = { Text("رفع للاعتماد الإداري") }
                        )
                        if (canApproveStocktake) {
                            FilterChip(
                                selected = !formalApprovalMode,
                                onClick = { formalApprovalMode = false },
                                label = { Text("تسوية فورية") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = ArabicFormatters.parseDoubleOrNull(newQtyText)
                        if (qty != null && qty >= 0.0 && reason.isNotBlank()) {
                            if (formalApprovalMode) {
                                viewModel.submitStockCount(prod.id, qty, reason)
                            } else {
                                viewModel.adjustStock(prod.id, qty, reason)
                            }
                            stocktakeProduct = null
                        }
                    },
                    enabled = reason.isNotBlank() && (ArabicFormatters.parseDoubleOrNull(newQtyText) ?: -1.0) >= 0.0
                ) { Text(if (formalApprovalMode) "تسجيل الجرد للاعتماد" else "حفظ الجرد الفوري") }
            },
            dismissButton = { TextButton(onClick = { stocktakeProduct = null }) { Text("إلغاء") } }
        )
    }

    historyProduct?.let { prod ->
        val movements = remember(state.stockMovements, prod) {
            state.stockMovements.filter { it.productId == prod.id }
        }
        val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { historyProduct = null }, sheetState = historySheetState) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("سجل حركة الصنف: ${prod.name}", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(10.dp))
                if (movements.isEmpty()) {
                    Text("لا توجد حركات مسجلة لهذا الصنف.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(movements, key = { it.id }) { mv ->
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(ArabicFormatters.movementTypeLabel(mv.movementType), fontWeight = FontWeight.Bold)
                                    val prefix = if (mv.quantityDelta > 0) "+" else ""
                                    Text(
                                        text = "$prefix${ArabicFormatters.formatNumber(mv.quantityDelta)} (الرصيد: ${ArabicFormatters.formatNumber(mv.balanceAfter)})",
                                        color = if (mv.quantityDelta >= 0) mamoColors.profit else mamoColors.loss
                                    )
                                }
                                Text("${mv.reason} • ${ArabicFormatters.formatDateTime(mv.occurredAtMs)}", style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
