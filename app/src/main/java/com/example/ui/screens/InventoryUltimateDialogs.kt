package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CustomerSpecialPriceEntity
import com.example.data.local.MeasureUnit
import com.example.data.local.PartyEntity
import com.example.data.local.PartyType
import com.example.data.local.ProductEntity
import com.example.data.local.ProductPriceHistoryEntity
import com.example.data.local.ProductUnitEntity
import com.example.data.local.StockCountEntity
import com.example.data.repository.AccountingEngine
import com.example.ui.MamoViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters
import com.example.utils.BarcodeLabelHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltimateProductEditorDialog(
    existing: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        cat: String,
        barcode: String,
        cost: Double,
        sell: Double,
        qty: Double,
        minLvl: Double,
        sku: String,
        imageUri: String,
        baseUnit: String,
        wholesalePrice: Double,
        minSellingPrice: Double,
        defaultSupplierName: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "عدد يدوية") }
    var sku by remember { mutableStateOf(existing?.sku ?: "") }
    var barcode by remember { mutableStateOf(existing?.barcode ?: "") }
    var imageUri by remember { mutableStateOf(existing?.imageUri ?: "") }
    var baseUnit by remember { mutableStateOf(existing?.baseUnit ?: MeasureUnit.PIECE) }
    var unitExpanded by remember { mutableStateOf(false) }

    var costText by remember { mutableStateOf(existing?.averageCost?.toString() ?: "") }
    var sellText by remember { mutableStateOf(existing?.sellingPrice?.toString() ?: "") }
    var wholesaleText by remember { mutableStateOf(existing?.wholesalePrice?.toString() ?: "") }
    var minSellText by remember { mutableStateOf(existing?.minSellingPrice?.toString() ?: "") }
    var qtyText by remember { mutableStateOf(existing?.quantity?.toString() ?: "0") }
    var minText by remember { mutableStateOf(existing?.minStockLevel?.toString() ?: "2") }
    var supplierText by remember { mutableStateOf(existing?.defaultSupplierName ?: "") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    val barcodePreview = remember(barcode) {
        if (barcode.isNotBlank()) BarcodeLabelHelper.generateCode128Bitmap(barcode, 420, 90) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "إضافة صنف جديد" else "تعديل الصنف") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الصنف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("التصنيف") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("رمز SKU") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = !unitExpanded }) {
                    OutlinedTextField(
                        value = baseUnit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("وحدة القياس الأساسية") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        MeasureUnit.ALL_UNITS.forEach { u ->
                            DropdownMenuItem(text = { Text(u) }, onClick = { baseUnit = u; unitExpanded = false })
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("الباركود") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        barcode = AccountingEngine.generateEan13Barcode(System.currentTimeMillis() % 999999999L)
                    }) {
                        Icon(Icons.Default.QrCode2, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("توليد")
                    }
                }

                barcodePreview?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "معاينة الباركود",
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("سعر الشراء") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("product_cost_input")
                    )
                    OutlinedTextField(
                        value = sellText,
                        onValueChange = { sellText = it },
                        label = { Text("سعر المفرق") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("product_sell_input")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wholesaleText,
                        onValueChange = { wholesaleText = it },
                        label = { Text("سعر الجملة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minSellText,
                        onValueChange = { minSellText = it },
                        label = { Text("أدنى سعر بيع") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existing == null) {
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it },
                            label = { Text("الكمية ($baseUnit)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("product_qty_input")
                        )
                    }
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("حد التنبيه") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = supplierText,
                    onValueChange = { supplierText = it },
                    label = { Text("المورد الافتراضي (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (imageUri.isNotBlank()) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "صورة الصنف",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }
                    OutlinedButton(onClick = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (imageUri.isBlank()) "صورة الصنف" else "تغيير الصورة")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cost = ArabicFormatters.parseDoubleOrNull(costText) ?: 0.0
                    val sell = ArabicFormatters.parseDoubleOrNull(sellText) ?: 0.0
                    val wholesale = ArabicFormatters.parseDoubleOrNull(wholesaleText) ?: sell
                    val minSell = ArabicFormatters.parseDoubleOrNull(minSellText) ?: cost
                    val qty = ArabicFormatters.parseDoubleOrNull(qtyText) ?: 0.0
                    val min = ArabicFormatters.parseDoubleOrNull(minText) ?: 0.0
                    if (name.isNotBlank()) {
                        onSave(name, category, barcode, cost, sell, qty, min, sku, imageUri, baseUnit, wholesale, minSell, supplierText)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_product_btn")
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductAdvancedSheet(
    product: ProductEntity,
    units: List<ProductUnitEntity>,
    specialPrices: List<CustomerSpecialPriceEntity>,
    priceHistory: List<ProductPriceHistoryEntity>,
    parties: List<PartyEntity>,
    currencySymbol: String,
    canEdit: Boolean,
    viewModel: MamoViewModel,
    onDismiss: () -> Unit
) {
    val mamoColors = LocalMamoColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    val prodUnits = remember(units, product.id) { units.filter { it.productId == product.id } }
    val prodSpecials = remember(specialPrices, product.id) { specialPrices.filter { it.productId == product.id } }
    val prodHistory = remember(priceHistory, product.id) { priceHistory.filter { it.productId == product.id } }
    val customers = remember(parties) { parties.filter { it.partyType == PartyType.CUSTOMER } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("${product.name} — إدارة متقدمة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("الوحدات") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("أسعار خاصة") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("سجل السعر") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("تالف") })
            }
            Spacer(modifier = Modifier.height(10.dp))

            when (tab) {
                0 -> {
                    var unitName by remember { mutableStateOf(MeasureUnit.CARTON) }
                    var factorText by remember { mutableStateOf("12") }
                    var sellText by remember { mutableStateOf((product.sellingPrice * 12).toString()) }
                    var wholesaleText by remember { mutableStateOf((product.wholesalePrice * 12).toString()) }
                    var uBarcode by remember { mutableStateOf("") }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الوحدة الأساسية: ${product.baseUnit} (= 1)", style = MaterialTheme.typography.labelLarge)
                        prodUnits.forEach { u ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${u.unitName} = ${ArabicFormatters.formatNumber(u.conversionToBase)} ${product.baseUnit}", fontWeight = FontWeight.Bold)
                                    Text("مفرق: ${ArabicFormatters.formatMoney(u.sellingPrice, currencySymbol)} | جملة: ${ArabicFormatters.formatMoney(u.wholesalePrice, currencySymbol)}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (canEdit) {
                                    IconButton(onClick = { viewModel.deleteProductUnit(u.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف الوحدة")
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                        if (canEdit) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = unitName, onValueChange = { unitName = it }, label = { Text("اسم الوحدة") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = factorText, onValueChange = { factorText = it }, label = { Text("كم ${product.baseUnit}؟") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = sellText, onValueChange = { sellText = it }, label = { Text("سعر المفرق") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = wholesaleText, onValueChange = { wholesaleText = it }, label = { Text("سعر الجملة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            }
                            OutlinedTextField(value = uBarcode, onValueChange = { uBarcode = it }, label = { Text("باركود الوحدة (اختياري)") }, modifier = Modifier.fillMaxWidth())
                            Button(
                                onClick = {
                                    val factor = ArabicFormatters.parseDoubleOrNull(factorText) ?: 0.0
                                    val sp = ArabicFormatters.parseDoubleOrNull(sellText) ?: 0.0
                                    val wp = ArabicFormatters.parseDoubleOrNull(wholesaleText) ?: sp
                                    if (unitName.isNotBlank() && factor > 0.0) {
                                        viewModel.saveProductUnit(null, product.id, unitName, factor, sp, wp, uBarcode)
                                        uBarcode = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إضافة / تحديث وحدة قياس")
                            }
                        }
                    }
                }
                1 -> {
                    var selectedPartyId by remember { mutableStateOf(customers.firstOrNull()?.id) }
                    var custExpanded by remember { mutableStateOf(false) }
                    var spText by remember { mutableStateOf(product.wholesalePrice.toString()) }
                    var minQtyText by remember { mutableStateOf("1") }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        prodSpecials.forEach { sp ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${sp.partyName} (من كمية ${ArabicFormatters.formatNumber(sp.minQty)})")
                                Text(ArabicFormatters.formatMoney(sp.specialPrice, currencySymbol), fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                        }
                        if (canEdit && customers.isNotEmpty()) {
                            val party = customers.firstOrNull { it.id == selectedPartyId } ?: customers.first()
                            ExposedDropdownMenuBox(expanded = custExpanded, onExpandedChange = { custExpanded = !custExpanded }) {
                                OutlinedTextField(
                                    value = party.name,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("الزبون") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = custExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = custExpanded, onDismissRequest = { custExpanded = false }) {
                                    customers.forEach { c ->
                                        DropdownMenuItem(text = { Text(c.name) }, onClick = { selectedPartyId = c.id; custExpanded = false })
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = spText, onValueChange = { spText = it }, label = { Text("السعر الخاص") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = minQtyText, onValueChange = { minQtyText = it }, label = { Text("أقل كمية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            }
                            Button(
                                onClick = {
                                    val pId = selectedPartyId ?: party.id
                                    val sp = ArabicFormatters.parseDoubleOrNull(spText) ?: 0.0
                                    val mq = (ArabicFormatters.parseDoubleOrNull(minQtyText) ?: 1.0).coerceAtLeast(1.0)
                                    if (sp >= 0.0) viewModel.saveCustomerSpecialPrice(pId, product.id, sp, mq)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("حفظ السعر الخاص للزبون") }
                        } else if (customers.isEmpty()) {
                            Text("أضف زبائن أولاً لتعيين أسعار خاصة لهم.")
                        }
                    }
                }
                2 -> {
                    if (prodHistory.isEmpty()) {
                        Text("لا توجد تغيرات أسعار مسجلة بعد لهذا الصنف.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(240.dp)) {
                            items(prodHistory, key = { it.id }) { ph ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(ph.changeType, fontWeight = FontWeight.Bold)
                                        Text("${ArabicFormatters.formatDateTime(ph.changedAtMs)} • ${ph.staffName}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text("${ArabicFormatters.formatNumber(ph.oldPrice)} ← ${ArabicFormatters.formatNumber(ph.newPrice)}", fontWeight = FontWeight.SemiBold)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                else -> {
                    var dmgQtyText by remember { mutableStateOf("") }
                    var dmgReason by remember { mutableStateOf("بضاعة تالفة / مكسورة") }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الرصيد المتاح: ${ArabicFormatters.formatNumber(product.quantity)} ${product.baseUnit}")
                        OutlinedTextField(
                            value = dmgQtyText,
                            onValueChange = { dmgQtyText = it },
                            label = { Text("الكمية التالفة (${product.baseUnit})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = dmgReason,
                            onValueChange = { dmgReason = it },
                            label = { Text("سبب التلف / الشطب") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val q = ArabicFormatters.parseDoubleOrNull(dmgQtyText) ?: 0.0
                                if (q > 0.0 && dmgReason.isNotBlank()) {
                                    viewModel.writeOffDamagedStock(product.id, q, dmgReason)
                                    onDismiss()
                                }
                            },
                            enabled = canEdit && (ArabicFormatters.parseDoubleOrNull(dmgQtyText) ?: 0.0) > 0.0,
                            colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BrokenImage, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("شطب البضاعة التالفة محاسبياً")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun StockCountsApprovalList(
    stockCounts: List<StockCountEntity>,
    canApprove: Boolean,
    currencySymbol: String,
    onApprove: (Long) -> Unit
) {
    val mamoColors = LocalMamoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stockCounts.isEmpty()) {
            Text("لا توجد جلسات جرد فعلي مسجلة بعد.", style = MaterialTheme.typography.bodyMedium)
        } else {
            stockCounts.forEach { sc ->
                val isApproved = sc.status == "APPROVED"
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sc.productName, fontWeight = FontWeight.Bold)
                            Text("المسجل: ${ArabicFormatters.formatNumber(sc.systemQty)} | الفعلي: ${ArabicFormatters.formatNumber(sc.countedQty)} | الفرق: ${ArabicFormatters.formatNumber(sc.varianceQty)}", style = MaterialTheme.typography.bodySmall)
                            Text("قيمة الفرق: ${ArabicFormatters.formatMoney(sc.varianceValue, currencySymbol)} • بواسطة ${sc.countedBy}", style = MaterialTheme.typography.labelSmall)
                        }
                        StatusPill(
                            text = if (isApproved) "معتمد" else "بانتظار الاعتماد",
                            color = if (isApproved) mamoColors.profit else mamoColors.warning,
                            backgroundColor = if (isApproved) mamoColors.profitContainer else mamoColors.goldContainer
                        )
                    }
                    if (!isApproved && canApprove) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { onApprove(sc.id) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اعتماد الجرد وتسوية المخزون")
                        }
                    }
                }
            }
        }
    }
}
