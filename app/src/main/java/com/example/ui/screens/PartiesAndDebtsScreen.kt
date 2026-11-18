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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.InvoiceStatus
import com.example.data.local.PartyEntity
import com.example.data.local.PartyType
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters

import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartiesAndDebtsScreen(
    state: MamoMainUiState,
    viewModel: MamoViewModel,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val mamoColors = LocalMamoColors.current
    val cur = state.settings.currencySymbol
    val cashAccounts by viewModel.cashAccounts.collectAsStateWithLifecycle()
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }

    var showPartyDialog by remember { mutableStateOf(false) }
    var newPartyType by remember { mutableStateOf(PartyType.CUSTOMER) }
    var statementParty by remember { mutableStateOf<PartyEntity?>(null) }
    var paymentTarget by remember { mutableStateOf<PartyEntity?>(null) }
    var writeOffTarget by remember { mutableStateOf<PartyEntity?>(null) }

    val customers = remember(state.parties, searchQuery) {
        state.parties.filter {
            it.partyType == PartyType.CUSTOMER &&
                (searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.phone.contains(searchQuery, true))
        }
    }
    val suppliers = remember(state.parties, searchQuery) {
        state.parties.filter {
            it.partyType == PartyType.SUPPLIER &&
                (searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.phone.contains(searchQuery, true))
        }
    }
    val debtParties = remember(state.parties, searchQuery) {
        state.parties.filter {
            it.balanceDue > 0.0 &&
                (searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.phone.contains(searchQuery, true))
        }
    }
    val overdueInvoices = remember(state.invoices) {
        val now = System.currentTimeMillis()
        state.invoices.filter {
            it.invoice.status == InvoiceStatus.POSTED &&
                it.invoice.remainingAmount > 0.0 &&
                it.invoice.dueDateMs != null &&
                it.invoice.dueDateMs < now
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab != 2) {
                FloatingActionButton(
                    onClick = {
                        newPartyType = if (selectedTab == 0) PartyType.CUSTOMER else PartyType.SUPPLIER
                        showPartyDialog = true
                    },
                    containerColor = mamoColors.gold,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_party_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة زبون أو مورد")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("الزبائن (${customers.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("الموردون (${suppliers.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("الديون والأقساط") })
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("بحث بالاسم أو رقم الهاتف") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> PartyListContent(
                        list = customers,
                        emptyTitle = "لا يوجد زبائن مسجلون بعد",
                        emptySubtitle = "أضف زبائن المتجر لمتابعة مشترياتهم والديون وكشف الحساب التفصيلي",
                        cur = cur,
                        onOpenStatement = { statementParty = it },
                        onPayDebt = { paymentTarget = it }
                    )
                    1 -> PartyListContent(
                        list = suppliers,
                        emptyTitle = "لا يوجد موردون مسجلون بعد",
                        emptySubtitle = "أضف الموردين لمتابعة فواتير التوريد والذمم الدائنة وكشف الحساب",
                        cur = cur,
                        onOpenStatement = { statementParty = it },
                        onPayDebt = { paymentTarget = it }
                    )
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GlassCard(modifier = Modifier.weight(1f)) {
                                        Text("ديون الزبائن (لنا)", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = ArabicFormatters.formatMoney(state.dashboardMetrics.receivablesFromCustomers, cur),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = mamoColors.profit,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    GlassCard(modifier = Modifier.weight(1f)) {
                                        Text("ديون الموردين (علينا)", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = ArabicFormatters.formatMoney(state.dashboardMetrics.payablesToSuppliers, cur),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = mamoColors.warning,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if (overdueInvoices.isNotEmpty()) {
                                item {
                                    GlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = mamoColors.loss)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "تنبيه: ${overdueInvoices.size} فواتير مستحقة ومتأخرة السداد",
                                                color = mamoColors.loss,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        overdueInvoices.forEach { inv ->
                                            Text(
                                                text = "• ${inv.invoice.invoiceNumber} (${inv.invoice.partyName}) — متبقي ${ArabicFormatters.formatMoney(inv.invoice.remainingAmount, cur)}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                            if (debtParties.isEmpty()) {
                                item {
                                    EmptyStateCard(
                                        icon = Icons.Default.AccountBalanceWallet,
                                        title = "لا توجد ديون قائمة حالياً",
                                        subtitle = "تظهر هنا ذمم الزبائن والموردين عند تسجيل فواتير آجلة أو جزئية"
                                    )
                                }
                            } else {
                                items(debtParties, key = { it.id }) { party ->
                                    PartyItemCard(
                                        party = party,
                                        cur = cur,
                                        onOpenStatement = { statementParty = party },
                                        onPayDebt = { paymentTarget = party }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPartyDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var openingText by remember { mutableStateOf("0") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPartyDialog = false },
            title = { Text(if (newPartyType == PartyType.CUSTOMER) "إضافة زبون جديد" else "إضافة مورد جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم الكامل") }, singleLine = true)
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان (اختياري)") }, singleLine = true)
                    OutlinedTextField(
                        value = openingText,
                        onValueChange = { openingText = it },
                        label = { Text("الرصيد الافتتاحي") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val opening = (ArabicFormatters.parseDoubleOrNull(openingText) ?: 0.0).coerceAtLeast(0.0)
                        viewModel.saveParty(null, newPartyType, name, phone, address, opening, notes)
                        showPartyDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showPartyDialog = false }) { Text("إلغاء") } }
        )
    }

    paymentTarget?.let { party ->
        var amountText by remember(party) { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var selectedAccId by remember { mutableStateOf(cashAccounts.firstOrNull()?.id ?: 1L) }
        var accExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { paymentTarget = null },
            title = {
                Text(
                    if (party.partyType == PartyType.CUSTOMER) "تحصيل دفعة من ${party.name}"
                    else "سداد دفعة للمورد ${party.name}"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الرصيد المتبقي الحالي: ${ArabicFormatters.formatMoney(party.balanceDue, cur)}")
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("مبلغ الدفعة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    if (cashAccounts.isNotEmpty()) {
                        val acc = cashAccounts.firstOrNull { it.id == selectedAccId } ?: cashAccounts.first()
                        ExposedDropdownMenuBox(expanded = accExpanded, onExpandedChange = { accExpanded = !accExpanded }) {
                            OutlinedTextField(
                                value = acc.name,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("الحساب المالي") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                                cashAccounts.forEach { a ->
                                    DropdownMenuItem(text = { Text(a.name) }, onClick = { selectedAccId = a.id; accExpanded = false })
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = ArabicFormatters.parseDoubleOrNull(amountText) ?: 0.0
                        if (amt > 0.0) {
                            viewModel.recordDebtPayment(party.id, amt, notes, selectedAccId)
                            paymentTarget = null
                        }
                    },
                    enabled = (ArabicFormatters.parseDoubleOrNull(amountText) ?: 0.0) > 0.0
                ) { Text("اعتماد الدفعة") }
            },
            dismissButton = { TextButton(onClick = { paymentTarget = null }) { Text("إلغاء") } }
        )
    }

    writeOffTarget?.let { party ->
        var amtText by remember(party) { mutableStateOf(party.balanceDue.toString()) }
        var reason by remember { mutableStateOf("دين معدوم متعذر التحصيل") }
        AlertDialog(
            onDismissRequest = { writeOffTarget = null },
            title = { Text("شطب دين معدوم — ${party.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم شطب الرصيد من حساب الزبون وإثباته في حساب الديون المعدومة.")
                    OutlinedTextField(
                        value = amtText,
                        onValueChange = { amtText = it },
                        label = { Text("المبلغ المشطوب (الحد: ${ArabicFormatters.formatNumber(party.balanceDue)})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("السبب") })
                }
            },
            confirmButton = {
                val amt = ArabicFormatters.parseDoubleOrNull(amtText) ?: 0.0
                Button(
                    onClick = {
                        viewModel.writeOffBadDebt(party.id, amt, reason)
                        writeOffTarget = null
                    },
                    enabled = amt > 0.0 && amt <= party.balanceDue + 0.01 && reason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss)
                ) { Text("تأكيد الشطب") }
            },
            dismissButton = { TextButton(onClick = { writeOffTarget = null }) { Text("إلغاء") } }
        )
    }

    statementParty?.let { party ->
        val statement = remember(party, state.invoices, state.debtPayments) {
            viewModel.buildPartyStatement(party)
        }
        val statementSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { statementParty = null }, sheetState = statementSheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("كشف حساب تفصيلي: ${party.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        val rows = listOf(
                            "الطرف" to party.name,
                            "الهاتف" to party.phone.ifBlank { "—" },
                            "إجمالي التعاملات" to ArabicFormatters.formatMoney(party.totalAmount, cur),
                            "إجمالي المدفوع" to ArabicFormatters.formatMoney(party.paidAmount, cur),
                            "الرصيد المتبقي" to ArabicFormatters.formatMoney(party.balanceDue, cur)
                        ) + statement.invoices.map {
                            "${it.invoice.invoiceNumber} (${ArabicFormatters.formatDate(it.invoice.invoiceDateMs)})" to ArabicFormatters.formatMoney(it.invoice.netTotal, cur)
                        } + statement.payments.map {
                            "دفعة (${ArabicFormatters.formatDate(it.paymentDateMs)})" to ArabicFormatters.formatMoney(it.amount, cur)
                        }
                        viewModel.exportPdfReport(context, "كشف حساب: ${party.name}", "شامل", rows)
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF")
                    }
                }
                Text(
                    text = "الإجمالي: ${ArabicFormatters.formatMoney(party.totalAmount, cur)} | المدفوع: ${ArabicFormatters.formatMoney(party.paidAmount, cur)} | المتبقي: ${ArabicFormatters.formatMoney(party.balanceDue, cur)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (party.partyType == PartyType.CUSTOMER && party.balanceDue > 0.0 && state.activeStaff?.canCancelInvoices == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = {
                        writeOffTarget = party
                        statementParty = null
                    }) {
                        Text("شطب كدين معدوم", color = mamoColors.loss)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    items(statement.invoices) { inv ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${inv.invoice.invoiceNumber} (${ArabicFormatters.formatDate(inv.invoice.invoiceDateMs)})")
                            Text(ArabicFormatters.formatMoney(inv.invoice.netTotal, cur), fontWeight = FontWeight.Bold)
                        }
                    }
                    items(statement.payments) { pay ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("دفعة دين (${ArabicFormatters.formatDate(pay.paymentDateMs)})")
                            Text(ArabicFormatters.formatMoney(pay.amount, cur), color = mamoColors.profit, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PartyListContent(
    list: List<PartyEntity>,
    emptyTitle: String,
    emptySubtitle: String,
    cur: String,
    onOpenStatement: (PartyEntity) -> Unit,
    onPayDebt: (PartyEntity) -> Unit
) {
    if (list.isEmpty()) {
        EmptyStateCard(icon = Icons.Default.People, title = emptyTitle, subtitle = emptySubtitle)
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(list, key = { it.id }) { party ->
                PartyItemCard(party = party, cur = cur, onOpenStatement = { onOpenStatement(party) }, onPayDebt = { onPayDebt(party) })
            }
        }
    }
}

@Composable
private fun PartyItemCard(
    party: PartyEntity,
    cur: String,
    onOpenStatement: () -> Unit,
    onPayDebt: () -> Unit
) {
    val mamoColors = LocalMamoColors.current
    val isCustomer = party.partyType == PartyType.CUSTOMER
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(party.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (party.phone.isNotBlank()) {
                    Text(party.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusPill(
                text = if (isCustomer) "زبون" else "مورد",
                color = if (isCustomer) MaterialTheme.colorScheme.primary else mamoColors.gold,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "التعاملات: ${ArabicFormatters.formatMoney(party.totalAmount, cur)} | المدفوع: ${ArabicFormatters.formatMoney(party.paidAmount, cur)}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "المتبقي: ${ArabicFormatters.formatMoney(party.balanceDue, cur)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (party.balanceDue > 0) mamoColors.loss else mamoColors.profit
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenStatement, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Receipt, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("كشف حساب")
            }
            if (party.balanceDue > 0.0) {
                Button(onClick = onPayDebt, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isCustomer) "تحصيل دفعة" else "سداد دفعة")
                }
            }
        }
    }
}
