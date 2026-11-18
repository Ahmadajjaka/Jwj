package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CashAccountEntity
import com.example.data.local.CashAccountType
import com.example.data.local.CashTxType
import com.example.data.local.PaymentMethod
import com.example.data.local.StaffRole
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashAndExpensesScreen(
    state: MamoMainUiState,
    viewModel: MamoViewModel,
    initialTab: Int = 0,
    openExpenseDialogInitially: Boolean = false,
    onExpenseDialogHandled: () -> Unit = {}
) {
    val mamoColors = LocalMamoColors.current
    val cur = state.settings.currencySymbol
    val cashAccounts by viewModel.cashAccounts.collectAsStateWithLifecycle()
    val closings by viewModel.cashboxClosings.collectAsStateWithLifecycle()
    val canWithdraw = state.activeStaff?.let { it.canWithdrawDeposit || it.role == StaffRole.OWNER } ?: false

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var cashDialogType by remember { mutableStateOf<String?>(null) }
    var showExpenseDialog by remember(openExpenseDialogInitially) { mutableStateOf(openExpenseDialogInitially) }
    var showNewAccountDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }

    val accountBalances = remember(cashAccounts, state.cashTransactions) {
        cashAccounts.associate { acc ->
            acc.id to state.cashTransactions.filter { it.accountId == acc.id }.sumOf { it.amountSigned }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 1 && state.activeStaff?.canManageExpenses == true) {
                FloatingActionButton(
                    onClick = { showExpenseDialog = true },
                    containerColor = mamoColors.gold,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_expense_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة مصروف")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("الصندوق والحسابات") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("المصاريف (${state.expenses.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("رأس المال والسحوبات") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("إغلاق الصندوق اليومي") })
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            GlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("إجمالي السيولة النقدية والبنكية", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            text = ArabicFormatters.formatMoney(state.cashboxBalance, cur),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    StatusPill(
                                        text = if (state.dashboardMetrics.isCashboxReconciled) "مطابق 100%" else "غير مطابق",
                                        color = if (state.dashboardMetrics.isCashboxReconciled) mamoColors.profit else mamoColors.loss,
                                        backgroundColor = if (state.dashboardMetrics.isCashboxReconciled) mamoColors.profitContainer else mamoColors.lossContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                if (cashAccounts.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        cashAccounts.forEach { acc ->
                                            val bal = accountBalances[acc.id] ?: 0.0
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${acc.name} (${ArabicFormatters.cashAccountTypeLabel(acc.accountType)})", style = MaterialTheme.typography.bodySmall)
                                                Text(ArabicFormatters.formatMoney(bal, cur), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { showNewAccountDialog = true }, enabled = canWithdraw, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حساب جديد")
                                    }
                                    OutlinedButton(
                                        onClick = { showTransferDialog = true },
                                        enabled = canWithdraw && cashAccounts.size >= 2,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تحويل بين الحسابات")
                                    }
                                }
                            }
                        }

                        if (state.cashTransactions.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.AccountBalance,
                                    title = "سجل الصندوق فارغ",
                                    subtitle = "ابدأ بإيداع رأس المال الافتتاحي أو تسجيل المبيعات والمصروفات؛ كل حركة موثقة تلقائياً"
                                )
                            }
                        } else {
                            items(state.cashTransactions, key = { it.id }) { tx ->
                                val isPositive = tx.amountSigned >= 0.0
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${ArabicFormatters.cashTxTypeLabel(tx.txType)} • ${tx.accountName}", fontWeight = FontWeight.Bold)
                                            Text(tx.description, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                "${ArabicFormatters.formatDateTime(tx.occurredAtMs, state.settings.dateFormatPattern)} • ${tx.staffName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = (if (isPositive) "+" else "") + ArabicFormatters.formatMoney(tx.amountSigned, cur),
                                            color = if (isPositive) mamoColors.profit else mamoColors.loss,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.expenses.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.MoneyOff,
                                    title = "لا توجد مصاريف مسجلة",
                                    subtitle = "سجل مصاريف العمال، الكهرباء، النقل، الإيجار، والصيانة والضيافة لاحتساب صافي الربح الحقيقي"
                                )
                            }
                        } else {
                            items(state.expenses, key = { it.id }) { exp ->
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(exp.category, fontWeight = FontWeight.Bold)
                                            if (exp.description.isNotBlank()) {
                                                Text(exp.description, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text(
                                                "${ArabicFormatters.formatDateTime(exp.expenseDateMs, state.settings.dateFormatPattern)} • ${ArabicFormatters.paymentMethodLabel(exp.paymentMethod)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = ArabicFormatters.formatMoney(exp.amount, cur),
                                            color = mamoColors.loss,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }

                2 -> {
                    val capitalTxs = remember(state.cashTransactions) {
                        state.cashTransactions.filter {
                            it.txType in listOf(CashTxType.CAPITAL_IN, CashTxType.PERSONAL_DRAWING, CashTxType.OTHER_INCOME, CashTxType.OTHER_OUTFLOW)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            GlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                                Text("إدارة رأس المال والسحوبات الشخصية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (!canWithdraw) {
                                    Text("الصلاحية مقتصرة على المدير والمحاسب فقط", color = mamoColors.warning, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { cashDialogType = CashTxType.CAPITAL_IN },
                                        enabled = canWithdraw,
                                        modifier = Modifier.weight(1f).testTag("capital_in_btn")
                                    ) {
                                        Icon(Icons.Default.Savings, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إيداع رأس مال")
                                    }
                                    OutlinedButton(
                                        onClick = { cashDialogType = CashTxType.PERSONAL_DRAWING },
                                        enabled = canWithdraw,
                                        modifier = Modifier.weight(1f).testTag("personal_drawing_btn")
                                    ) {
                                        Text("سحب شخصي")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { cashDialogType = CashTxType.OTHER_INCOME },
                                        enabled = canWithdraw,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("إيرادات أخرى") }
                                    OutlinedButton(
                                        onClick = { cashDialogType = CashTxType.OTHER_OUTFLOW },
                                        enabled = canWithdraw,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("إخراج أموال") }
                                }
                            }
                        }
                        items(capitalTxs, key = { it.id }) { tx ->
                            val isPositive = tx.amountSigned >= 0.0
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ArabicFormatters.cashTxTypeLabel(tx.txType), fontWeight = FontWeight.Bold)
                                        Text("${tx.description} • ${tx.accountName}", style = MaterialTheme.typography.bodySmall)
                                        Text(ArabicFormatters.formatDateTime(tx.occurredAtMs), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(
                                        text = (if (isPositive) "+" else "") + ArabicFormatters.formatMoney(tx.amountSigned, cur),
                                        color = if (isPositive) mamoColors.profit else mamoColors.loss,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    var selectedAccId by remember { mutableStateOf(cashAccounts.firstOrNull()?.id ?: 1L) }
                    var accExpanded by remember { mutableStateOf(false) }
                    val expectedBal = accountBalances[selectedAccId] ?: state.cashboxBalance
                    var actualText by remember(selectedAccId, expectedBal) { mutableStateOf(expectedBal.toString()) }
                    var closingNotes by remember { mutableStateOf("") }
                    var lockPeriod by remember { mutableStateOf(true) }
                    val actualAmt = ArabicFormatters.parseDoubleOrNull(actualText) ?: expectedBal
                    val variance = actualAmt - expectedBal

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            GlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.LockClock, contentDescription = null, tint = mamoColors.gold)
                                    Text("إغلاق الصندوق اليومي ومطابقة الجرد النقدي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Text("الرصيد الدفتري المتوقع: ${ArabicFormatters.formatMoney(expectedBal, cur)}", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = actualText,
                                    onValueChange = { actualText = it },
                                    label = { Text("الجرد النقدي الفعلي المعدود") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "فارق المطابقة (الزيادة / العجز): ${ArabicFormatters.formatMoney(variance, cur)}",
                                    color = if (kotlin.math.abs(variance) < 0.01) mamoColors.profit else mamoColors.loss,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = closingNotes,
                                    onValueChange = { closingNotes = it },
                                    label = { Text("ملاحظات الإغلاق") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("قفل اليوم ومنع تعديل فواتيره", style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = lockPeriod, onCheckedChange = { lockPeriod = it })
                                }
                                Button(
                                    onClick = {
                                        viewModel.closeCashboxDay(selectedAccId, actualAmt, closingNotes, lockPeriod)
                                        closingNotes = ""
                                    },
                                    enabled = state.activeStaff != null,
                                    modifier = Modifier.fillMaxWidth().testTag("close_cashbox_day_btn")
                                ) { Text("اعتماد إغلاق الصندوق اليومي") }
                            }
                        }
                        items(closings, key = { it.id }) { cls ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${cls.accountName} • ${ArabicFormatters.formatDateTime(cls.closingDateMs)}", fontWeight = FontWeight.Bold)
                                        Text("متوقع: ${ArabicFormatters.formatMoney(cls.expectedBalance, cur)} | فعلي: ${ArabicFormatters.formatMoney(cls.actualCountedBalance, cur)}", style = MaterialTheme.typography.bodySmall)
                                        Text("بواسطة ${cls.closedByStaff} ${if (cls.isLocked) "• مقفل" else ""}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(
                                        text = "فرق: ${ArabicFormatters.formatMoney(cls.varianceAmount, cur)}",
                                        color = if (kotlin.math.abs(cls.varianceAmount) < 0.01) mamoColors.profit else mamoColors.warning,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewAccountDialog) {
        var accName by remember { mutableStateOf("") }
        var accType by remember { mutableStateOf(CashAccountType.BANK) }
        AlertDialog(
            onDismissRequest = { showNewAccountDialog = false },
            title = { Text("إضافة حساب مالي / خزينة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = accName, onValueChange = { accName = it }, label = { Text("اسم الحساب (مثلاً: البنك الإسلامي)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            CashAccountType.CASHBOX to "صندوق",
                            CashAccountType.BANK to "بنك",
                            CashAccountType.SAFE to "خزينة"
                        ).forEach { (t, l) ->
                            FilterChip(selected = accType == t, onClick = { accType = t }, label = { Text(l) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveCashAccount(accName, accType); showNewAccountDialog = false },
                    enabled = accName.isNotBlank()
                ) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showNewAccountDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showTransferDialog && cashAccounts.size >= 2) {
        var fromId by remember { mutableStateOf(cashAccounts.first().id) }
        var toId by remember { mutableStateOf(cashAccounts.last().id) }
        var amtText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("تحويل أموال بين الحسابات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("من حساب:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cashAccounts.forEach { a ->
                            FilterChip(selected = fromId == a.id, onClick = { fromId = a.id }, label = { Text(a.name) })
                        }
                    }
                    Text("إلى حساب:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cashAccounts.forEach { a ->
                            FilterChip(selected = toId == a.id, onClick = { toId = a.id }, label = { Text(a.name) })
                        }
                    }
                    OutlinedTextField(value = amtText, onValueChange = { amtText = it }, label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") })
                }
            },
            confirmButton = {
                val amt = ArabicFormatters.parseDoubleOrNull(amtText) ?: 0.0
                Button(
                    onClick = { viewModel.transferBetweenCashAccounts(fromId, toId, amt, notes); showTransferDialog = false },
                    enabled = amt > 0.0 && fromId != toId
                ) { Text("تحويل الآن") }
            },
            dismissButton = { TextButton(onClick = { showTransferDialog = false }) { Text("إلغاء") } }
        )
    }

    cashDialogType?.let { txType ->
        var amountText by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var selectedAccId by remember { mutableStateOf(cashAccounts.firstOrNull()?.id ?: 1L) }
        var accExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { cashDialogType = null },
            title = { Text(ArabicFormatters.cashTxTypeLabel(txType)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("المبلغ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.testTag("cash_amount_input")
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
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("البيان / الوصف") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = ArabicFormatters.parseDoubleOrNull(amountText) ?: 0.0
                        if (amt > 0.0) {
                            viewModel.recordCashMovement(txType, amt, description, selectedAccId)
                            cashDialogType = null
                        }
                    },
                    enabled = (ArabicFormatters.parseDoubleOrNull(amountText) ?: 0.0) > 0.0,
                    modifier = Modifier.testTag("confirm_cash_btn")
                ) { Text("ترحيل للصندوق") }
            },
            dismissButton = { TextButton(onClick = { cashDialogType = null }) { Text("إلغاء") } }
        )
    }

    if (showExpenseDialog) {
        var category by remember { mutableStateOf(ArabicFormatters.expenseCategories.first()) }
        var catExpanded by remember { mutableStateOf(false) }
        var amountText by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
        var selectedAccId by remember { mutableStateOf(cashAccounts.firstOrNull()?.id ?: 1L) }
        var receiptUri by remember { mutableStateOf("") }
        val receiptLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri -> if (uri != null) receiptUri = uri.toString() }

        AlertDialog(
            onDismissRequest = {
                showExpenseDialog = false
                onExpenseDialogHandled()
            },
            title = { Text("تسجيل مصروف تشغيلي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("تصنيف المصروف") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            ArabicFormatters.expenseCategories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; catExpanded = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("المبلغ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("الوصف") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = paymentMethod == PaymentMethod.CASH, onClick = { paymentMethod = PaymentMethod.CASH }, label = { Text("نقدي") })
                        FilterChip(selected = paymentMethod == PaymentMethod.CREDIT, onClick = { paymentMethod = PaymentMethod.CREDIT }, label = { Text("آجل") })
                    }
                    if (paymentMethod == PaymentMethod.CASH && cashAccounts.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cashAccounts.forEach { a ->
                                FilterChip(selected = selectedAccId == a.id, onClick = { selectedAccId = a.id }, label = { Text(a.name) })
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        receiptLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (receiptUri.isBlank()) "إرفاق صورة وصل (اختياري)" else "تم إرفاق صورة الوصل")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = ArabicFormatters.parseDoubleOrNull(amountText) ?: 0.0
                        if (amt > 0.0) {
                            viewModel.recordExpense(category, amt, description, paymentMethod, System.currentTimeMillis(), receiptUri, selectedAccId)
                            showExpenseDialog = false
                            onExpenseDialogHandled()
                        }
                    },
                    enabled = (ArabicFormatters.parseDoubleOrNull(amountText) ?: 0.0) > 0.0
                ) { Text("حفظ المصروف") }
            },
            dismissButton = {
                TextButton(onClick = { showExpenseDialog = false; onExpenseDialogHandled() }) { Text("إلغاء") }
            }
        )
    }
}
