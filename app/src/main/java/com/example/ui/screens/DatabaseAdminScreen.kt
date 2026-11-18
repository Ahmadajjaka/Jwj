package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ApprovalStatus
import com.example.data.local.StaffRole
import com.example.data.local.StaffUserEntity
import com.example.data.remote.CloudCollectionKind
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters
import java.util.Locale

private enum class DbTableKind(val label: String) {
    PRODUCTS("المنتجات"),
    INVOICES("الفواتير"),
    PARTIES("الأطراف"),
    CASH("الصندوق"),
    EXPENSES("المصاريف"),
    MOVEMENTS("حركات المخزون"),
    PAYMENTS("دفعات الديون"),
    STAFF("الموظفون"),
    AUDIT("سجل التدقيق")
}

private enum class SyncFilterKind(val label: String) {
    ALL("الكل"),
    UNSYNCED("غير متزامن"),
    SYNCED("متزامن")
}

private data class DbRowItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val meta: String,
    val syncId: String,
    val isSynced: Boolean,
    val detailLines: List<Pair<String, String>>,
    val productId: Long? = null,
    val isArchived: Boolean = false,
    val staffEntity: StaffUserEntity? = null
)

@Composable
fun DatabaseAdminScreen(
    state: MamoMainUiState,
    viewModel: MamoViewModel,
    googleEmail: String?
) {
    val context = LocalContext.current
    val mamoColors = LocalMamoColors.current
    val isEmailAdmin = !googleEmail.isNullOrBlank() && state.activeStaff?.role == StaffRole.OWNER

    if (!isEmailAdmin) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = mamoColors.loss, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text("الوصول مقيد للأدمن فقط", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "لوحة التحكم بقاعدة البيانات متاحة حصرياً لمدير النظام المسجل دخوله بالبريد الإلكتروني.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val allProducts by viewModel.allProductsForAdmin.collectAsStateWithLifecycle()
    val cloudSnapshot by viewModel.cloudSnapshot.collectAsStateWithLifecycle()
    val integrityReport by viewModel.integrityReport.collectAsStateWithLifecycle()
    val dbStorageInfo by viewModel.dbStorageInfo.collectAsStateWithLifecycle()
    val compressionReport by viewModel.compressionReport.collectAsStateWithLifecycle()
    val cloudDeleteResult by viewModel.cloudDeleteResult.collectAsStateWithLifecycle()
    val deviceBackupStatus by viewModel.deviceBackupStatus.collectAsStateWithLifecycle()
    val isDbBusy by viewModel.isDbBusy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshDatabaseStorageInfo(context)
        viewModel.refreshDeviceBackupStatus(context)
    }

    var selectedTable by remember { mutableStateOf(DbTableKind.PRODUCTS) }
    var syncFilter by remember { mutableStateOf(SyncFilterKind.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var inspectedRow by remember { mutableStateOf<DbRowItem?>(null) }
    var managedStaff by remember { mutableStateOf<StaffUserEntity?>(null) }
    var approvalTarget by remember { mutableStateOf<StaffUserEntity?>(null) }
    var rejectTarget by remember { mutableStateOf<StaffUserEntity?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    var selectedCloudCollections by remember { mutableStateOf(CloudCollectionKind.entries.toSet()) }
    var wipeCloudProfile by remember { mutableStateOf(false) }
    var showCloudDeleteDialog by remember { mutableStateOf(false) }
    var pendingCloudCollections by remember { mutableStateOf<Set<CloudCollectionKind>>(emptySet()) }
    var pendingWipeProfile by remember { mutableStateOf(false) }

    var selectedRetentionDays by remember { mutableIntStateOf(60) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportJsonToUri(context, uri, compressGzip = false)
    }

    val exportGzipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.exportJsonToUri(context, uri, compressGzip = true)
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importJsonFromUri(context, uri)
    }

    val cur = state.settings.currencySymbol
    val dateFmt = state.settings.dateFormatPattern

    val unsyncedCount = remember(allProducts, state) {
        allProducts.count { !it.isSynced } +
            state.parties.count { !it.isSynced } +
            state.invoices.count { !it.invoice.isSynced } +
            state.cashTransactions.count { !it.isSynced } +
            state.expenses.count { !it.isSynced } +
            state.debtPayments.count { !it.isSynced } +
            state.stockMovements.count { !it.isSynced } +
            state.staffUsers.count { !it.isSynced } +
            state.auditLogs.count { !it.isSynced }
    }

    val totalLocalCount = allProducts.size + state.parties.size + state.invoices.size +
        state.cashTransactions.size + state.expenses.size + state.debtPayments.size +
        state.stockMovements.size + state.staffUsers.size + state.auditLogs.size

    val rawRows = remember(selectedTable, allProducts, state, cur, dateFmt) {
        when (selectedTable) {
            DbTableKind.PRODUCTS -> allProducts.map { p ->
                DbRowItem(
                    key = "prod_${p.id}",
                    title = if (p.isArchived) "${p.name} [مؤرشف]" else p.name,
                    subtitle = "${p.category} | الكمية: ${ArabicFormatters.formatNumber(p.quantity)} | التكلفة: ${ArabicFormatters.formatMoney(p.averageCost, cur)}",
                    meta = "بيع: ${ArabicFormatters.formatMoney(p.sellingPrice, cur)}",
                    syncId = p.syncId,
                    isSynced = p.isSynced,
                    detailLines = listOf(
                        "المعرف المحلي ID" to p.id.toString(),
                        "معرف المزامنة syncId" to p.syncId,
                        "الاسم" to p.name,
                        "التصنيف" to p.category,
                        "الباركود" to p.barcode.ifBlank { "-" },
                        "الكمية" to ArabicFormatters.formatNumber(p.quantity),
                        "الحد الأدنى" to ArabicFormatters.formatNumber(p.minStockLevel),
                        "متوسط التكلفة" to ArabicFormatters.formatMoney(p.averageCost, cur),
                        "سعر البيع" to ArabicFormatters.formatMoney(p.sellingPrice, cur),
                        "مؤرشف" to if (p.isArchived) "نعم" else "لا"
                    ),
                    productId = p.id,
                    isArchived = p.isArchived
                )
            }
            DbTableKind.INVOICES -> state.invoices.map { wi ->
                val inv = wi.invoice
                DbRowItem(
                    key = "inv_${inv.id}",
                    title = "${inv.invoiceNumber} (${if (inv.invoiceType == "SALE") "بيع" else "شراء"}) — ${inv.partyName}",
                    subtitle = "الصافي: ${ArabicFormatters.formatMoney(inv.netTotal, cur)} | المدفوع: ${ArabicFormatters.formatMoney(inv.paidAmount, cur)} | البنود: ${wi.items.size}",
                    meta = "${ArabicFormatters.invoiceStatusLabel(inv.status)} • ${ArabicFormatters.formatDate(inv.invoiceDateMs, dateFmt)}",
                    syncId = inv.syncId,
                    isSynced = inv.isSynced,
                    detailLines = listOf(
                        "المعرف المحلي ID" to inv.id.toString(),
                        "معرف المزامنة" to inv.syncId,
                        "رقم الفاتورة" to inv.invoiceNumber,
                        "الحالة" to ArabicFormatters.invoiceStatusLabel(inv.status),
                        "الطرف" to inv.partyName,
                        "الصافي" to ArabicFormatters.formatMoney(inv.netTotal, cur),
                        "المدفوع" to ArabicFormatters.formatMoney(inv.paidAmount, cur),
                        "المتبقي" to ArabicFormatters.formatMoney(inv.remainingAmount, cur),
                        "التكلفة" to ArabicFormatters.formatMoney(inv.totalCost, cur),
                        "الموظف" to inv.staffName
                    )
                )
            }
            DbTableKind.PARTIES -> state.parties.map { pt ->
                DbRowItem(
                    key = "party_${pt.id}",
                    title = "${pt.name} (${if (pt.partyType == "CUSTOMER") "زبون" else "مورد"})",
                    subtitle = "إجمالي: ${ArabicFormatters.formatMoney(pt.totalAmount, cur)} | المتبقي: ${ArabicFormatters.formatMoney(pt.balanceDue, cur)}",
                    meta = pt.phone.ifBlank { "بدون هاتف" },
                    syncId = pt.syncId,
                    isSynced = pt.isSynced,
                    detailLines = listOf(
                        "المعرف المحلي ID" to pt.id.toString(),
                        "معرف المزامنة" to pt.syncId,
                        "النوع" to pt.partyType,
                        "الهاتف" to pt.phone.ifBlank { "-" },
                        "الإجمالي" to ArabicFormatters.formatMoney(pt.totalAmount, cur),
                        "المدفوع" to ArabicFormatters.formatMoney(pt.paidAmount, cur),
                        "الرصيد المتبقي" to ArabicFormatters.formatMoney(pt.balanceDue, cur)
                    )
                )
            }
            DbTableKind.CASH -> state.cashTransactions.map { c ->
                DbRowItem(
                    key = "cash_${c.id}",
                    title = c.description,
                    subtitle = "${ArabicFormatters.cashTxTypeLabel(c.txType)} | ${ArabicFormatters.formatMoney(c.amountSigned, cur)}",
                    meta = "${c.staffName} • ${ArabicFormatters.formatDateTime(c.occurredAtMs, dateFmt)}",
                    syncId = c.syncId,
                    isSynced = c.isSynced,
                    detailLines = listOf(
                        "المعرف المحلي ID" to c.id.toString(),
                        "معرف المزامنة" to c.syncId,
                        "النوع" to ArabicFormatters.cashTxTypeLabel(c.txType),
                        "المبلغ" to ArabicFormatters.formatMoney(c.amountSigned, cur),
                        "المرجع" to c.referenceId.ifBlank { "-" },
                        "الموظف" to c.staffName
                    )
                )
            }
            DbTableKind.EXPENSES -> state.expenses.map { e ->
                DbRowItem(
                    key = "exp_${e.id}",
                    title = "${e.category} — ${e.description.ifBlank { "بدون وصف" }}",
                    subtitle = "المبلغ: ${ArabicFormatters.formatMoney(e.amount, cur)}",
                    meta = "${e.staffName} • ${ArabicFormatters.formatDate(e.expenseDateMs, dateFmt)}",
                    syncId = e.syncId,
                    isSynced = e.isSynced,
                    detailLines = listOf(
                        "المعرف المحلي ID" to e.id.toString(),
                        "معرف المزامنة" to e.syncId,
                        "التصنيف" to e.category,
                        "المبلغ" to ArabicFormatters.formatMoney(e.amount, cur),
                        "طريقة الدفع" to ArabicFormatters.paymentMethodLabel(e.paymentMethod),
                        "الموظف" to e.staffName
                    )
                )
            }
            DbTableKind.MOVEMENTS -> state.stockMovements.map { m ->
                DbRowItem(
                    key = "mov_${m.id}",
                    title = "${m.productName} (${m.movementType})",
                    subtitle = "التغير: ${ArabicFormatters.formatNumber(m.quantityDelta)} | الرصيد بعد: ${ArabicFormatters.formatNumber(m.balanceAfter)}",
                    meta = m.reason,
                    syncId = m.syncId,
                    isSynced = m.isSynced,
                    detailLines = listOf(
                        "المعرف المحلي ID" to m.id.toString(),
                        "معرف المزامنة" to m.syncId,
                        "الصنف" to m.productName,
                        "الحركة" to m.movementType,
                        "الكمية" to ArabicFormatters.formatNumber(m.quantityDelta),
                        "التكلفة" to ArabicFormatters.formatMoney(m.unitCost, cur),
                        "الرصيد بعد" to ArabicFormatters.formatNumber(m.balanceAfter),
                        "السبب" to m.reason
                    )
                )
            }
            DbTableKind.PAYMENTS -> state.debtPayments.map { d ->
                DbRowItem(
                    key = "pay_${d.id}",
                    title = "${d.partyName} — ${ArabicFormatters.formatMoney(d.amount, cur)}",
                    subtitle = d.notes.ifBlank { "دفعة سداد/تحصيل" },
                    meta = "${d.staffName} • ${ArabicFormatters.formatDate(d.paymentDateMs, dateFmt)}",
                    syncId = d.syncId,
                    isSynced = d.isSynced,
                    detailLines = listOf(
                        "المعرف المحلي ID" to d.id.toString(),
                        "معرف المزامنة" to d.syncId,
                        "الطرف" to d.partyName,
                        "المبلغ" to ArabicFormatters.formatMoney(d.amount, cur),
                        "الملاحظات" to d.notes.ifBlank { "-" }
                    )
                )
            }
            DbTableKind.STAFF -> state.staffUsers.map { u ->
                val approvalLabel = when (u.approvalStatus) {
                    ApprovalStatus.PENDING -> "بانتظار الموافقة"
                    ApprovalStatus.REJECTED -> "مرفوض"
                    else -> "معتمد"
                }
                DbRowItem(
                    key = "staff_${u.id}",
                    title = "${u.name} (${ArabicFormatters.staffRoleLabel(u.role)})",
                    subtitle = "$approvalLabel • ${if (u.isActive) "نشط" else "معطل"}${if (u.loginIdentifier.isNotBlank()) " | ${u.loginIdentifier}" else ""}",
                    meta = "أرباح: ${if (u.canViewProfits) "نعم" else "لا"} • إلغاء: ${if (u.canCancelInvoices) "نعم" else "لا"} • أسعار: ${if (u.canEditPrices) "نعم" else "لا"}",
                    syncId = u.syncId,
                    isSynced = u.isSynced,
                    detailLines = listOf(
                        "المعرف المحلي ID" to u.id.toString(),
                        "معرف المزامنة" to u.syncId,
                        "معرف الدخول" to u.loginIdentifier.ifBlank { "-" },
                        "الدور المعين" to ArabicFormatters.staffRoleLabel(u.role),
                        "الدور المطلوب" to ArabicFormatters.staffRoleLabel(u.requestedRole),
                        "حالة الاعتماد" to approvalLabel,
                        "معتمد بواسطة" to u.approvedBy.ifBlank { "-" },
                        "الحالة" to if (u.isActive) "نشط" else "معطل"
                    ),
                    staffEntity = u
                )
            }
            DbTableKind.AUDIT -> state.auditLogs.map { a ->
                DbRowItem(
                    key = "aud_${a.id}",
                    title = a.action,
                    subtitle = a.details,
                    meta = "${a.staffName} • ${ArabicFormatters.formatDateTime(a.occurredAtMs, dateFmt)}",
                    syncId = a.syncId,
                    isSynced = a.isSynced,
                    detailLines = listOf(
                        "المعرف المحلي ID" to a.id.toString(),
                        "معرف المزامنة" to a.syncId,
                        "العملية" to a.action,
                        "الكيان" to "${a.entityType} (${a.entityId})",
                        "التفاصيل" to a.details,
                        "الموظف" to a.staffName
                    )
                )
            }
        }
    }

    val filteredRows = remember(rawRows, syncFilter, searchQuery) {
        rawRows.filter { row ->
            val syncMatch = when (syncFilter) {
                SyncFilterKind.ALL -> true
                SyncFilterKind.UNSYNCED -> !row.isSynced
                SyncFilterKind.SYNCED -> row.isSynced
            }
            val q = searchQuery.trim()
            val queryMatch = q.isEmpty() ||
                row.title.contains(q, ignoreCase = true) ||
                row.subtitle.contains(q, ignoreCase = true) ||
                row.syncId.contains(q, ignoreCase = true)
            syncMatch && queryMatch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("database_admin_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Admin Identity Card
        item {
            GlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = mamoColors.gold, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("لوحة تحكم الأدمن بقاعدة البيانات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("البريد الموثق: $googleEmail", style = MaterialTheme.typography.bodySmall, color = mamoColors.profit)
                    }
                    Surface(
                        color = mamoColors.profitContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = mamoColors.profit, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("أدمن", style = MaterialTheme.typography.labelSmall, color = mamoColors.profit)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("معرف المستخدم السحابي: ${viewModel.adminUserId.take(16)}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي السجلات المحلية: $totalLocalCount", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (unsyncedCount == 0) "متزامن بالكامل" else "بانتظار المزامنة: $unsyncedCount",
                        color = if (unsyncedCount == 0) mamoColors.profit else mamoColors.warning,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isDbBusy || state.isSyncing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Pending User Registration Approval Queue
        val pendingUsers = state.staffUsers.filter { it.approvalStatus == ApprovalStatus.PENDING }
        item {
            GlassCard(highlightGold = pendingUsers.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("admin_pending_approval_card")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.HowToReg,
                        contentDescription = null,
                        tint = if (pendingUsers.isNotEmpty()) mamoColors.gold else mamoColors.profit
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "قائمة انتظار موافقة المستخدمين (${pendingUsers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (pendingUsers.isEmpty()) "لا توجد طلبات انضمام معلقة حالياً." else "راجع طلبات التسجيل الجديدة وحدد الدور الوظيفي والصلاحيات قبل تفعيل الدخول.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (pendingUsers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    pendingUsers.forEachIndexed { idx, pendingUser ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(pendingUser.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Surface(shape = RoundedCornerShape(8.dp), color = mamoColors.warningContainer) {
                                        Text(
                                            "بانتظار الموافقة",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = mamoColors.warning,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (pendingUser.loginIdentifier.isNotBlank()) {
                                    Text("معرف الدخول: ${pendingUser.loginIdentifier}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "الدور المطلوب: ${ArabicFormatters.staffRoleLabel(pendingUser.requestedRole)} • تاريخ الطلب: ${ArabicFormatters.formatDateTime(pendingUser.requestedAtMs, dateFmt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { approvalTarget = pendingUser },
                                        enabled = !isDbBusy,
                                        modifier = Modifier.weight(1f).testTag("approve_staff_btn_${pendingUser.id}")
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("قبول وتحديد الصلاحيات", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { rejectTarget = pendingUser },
                                        enabled = !isDbBusy,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = mamoColors.loss),
                                        modifier = Modifier.testTag("reject_staff_btn_${pendingUser.id}")
                                    ) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("رفض")
                                    }
                                }
                            }
                        }
                        if (idx < pendingUsers.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Cloud & Integrity Operations Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("أدوات الفحص والمزامنة السحابية (Room ↔ Firestore)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.runDatabaseIntegrityCheck() },
                        enabled = !isDbBusy,
                        modifier = Modifier.weight(1f).testTag("db_integrity_check_btn")
                    ) {
                        Text("فحص التطابق", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { viewModel.refreshCloudDatabaseSnapshot() },
                        enabled = !isDbBusy,
                        modifier = Modifier.weight(1f).testTag("db_cloud_stats_btn")
                    ) {
                        Text("فحص السحابة", maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.forceFullCloudResync() },
                        enabled = !isDbBusy && !state.isSyncing,
                        modifier = Modifier.weight(1f).testTag("db_force_resync_btn")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مزامنة شاملة", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { viewModel.restoreMissingFromCloud() },
                        enabled = !isDbBusy,
                        modifier = Modifier.weight(1f).testTag("db_pull_cloud_btn")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استيراد من السحابة", maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportJsonLauncher.launch("mamo_admin_backup_${System.currentTimeMillis()}.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصدير JSON", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { importJsonLauncher.launch(arrayOf("application/json", "application/gzip", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استيراد (.gz/.json)", maxLines = 1)
                    }
                }

                integrityReport?.let { rep ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("نتيجة فحص السلامة (${ArabicFormatters.formatDateTime(rep.checkedAtMs, dateFmt)})", fontWeight = FontWeight.Bold)
                            Text("تطابق الصندوق: ${if (rep.isCashboxReconciled) "متطابق تماماً" else "يوجد فرق"} (${ArabicFormatters.formatMoney(rep.cashboxQueryBalance, cur)})")
                            Text("توازن الميزانية: ${if (rep.isEquityBalanced) "متوازنة" else "يوجد فرق"} (الأصول: ${ArabicFormatters.formatMoney(rep.assetsNetWorth, cur)} | حقوق الملكية: ${ArabicFormatters.formatMoney(rep.equityCheck, cur)})")
                            Text("مخزون سالب: ${rep.negativeStockProductsCount} | أصناف مؤرشفة: ${rep.archivedProductsCount} | غير متزامن: ${rep.unsyncedTotalCount}")
                        }
                    }
                }

                cloudSnapshot?.let { snap ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("سجلات Firestore السحابية (${ArabicFormatters.formatDateTime(snap.fetchedAtMs, dateFmt)})", fontWeight = FontWeight.Bold)
                            Text("المنتجات: ${snap.products.size} (محلي: ${allProducts.size}) | الأطراف: ${snap.parties.size} (محلي: ${state.parties.size})")
                            Text("الفواتير: ${snap.invoices.size} (محلي: ${state.invoices.size}) | قيود الدفاتر والتدقيق: ${snap.ledgerEntries.size}")
                        }
                    }
                }
            }
        }

        // Table Browser Controls
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("مستعرض جداول قاعدة البيانات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DbTableKind.entries) { kind ->
                        FilterChip(
                            selected = selectedTable == kind,
                            onClick = { selectedTable = kind },
                            label = { Text(kind.label) },
                            modifier = Modifier.testTag("db_tab_${kind.name.lowercase()}")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("بحث في ${selectedTable.label} أو syncId") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SyncFilterKind.entries.forEach { sf ->
                        FilterChip(
                            selected = syncFilter == sf,
                            onClick = { syncFilter = sf },
                            label = { Text(sf.label) }
                        )
                    }
                }
            }
        }

        // Records List
        if (filteredRows.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "لا توجد سجلات مطابقة في جدول ${selectedTable.label}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredRows.take(80), key = { it.key }) { row ->
                GlassCard(onClick = { inspectedRow = row }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.title, fontWeight = FontWeight.Bold)
                            Text(row.subtitle, style = MaterialTheme.typography.bodySmall)
                            Text(row.meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (row.isSynced) mamoColors.profitContainer else mamoColors.warningContainer
                        ) {
                            Text(
                                text = if (row.isSynced) "متزامن" else "معلق",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (row.isSynced) mamoColors.profit else mamoColors.warning,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (row.productId != null || row.staffEntity != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.productId?.let { pid ->
                                OutlinedButton(
                                    onClick = { viewModel.toggleProductArchived(pid, !row.isArchived) }
                                ) {
                                    Icon(
                                        imageVector = if (row.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (row.isArchived) "إلغاء الأرشفة" else "أرشفة الصنف")
                                }
                            }
                            row.staffEntity?.let { st ->
                                OutlinedButton(onClick = { managedStaff = st }) {
                                    Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إدارة الصلاحية / PIN")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Firebase Cloud Data Deletion Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("cloud_delete_card")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = mamoColors.loss)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("حذف البيانات المخزنة في Firebase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "احذف أقساماً محددة أو امسح بيانات السحابة بالكامل مع تأكيد PIN للأدمن (دون مسح القاعدة المحلية).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("الأقسام المستهدفة:", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = {
                        selectedCloudCollections = if (selectedCloudCollections.size == CloudCollectionKind.entries.size) {
                            emptySet()
                        } else {
                            CloudCollectionKind.entries.toSet()
                        }
                    }) {
                        Text(if (selectedCloudCollections.size == CloudCollectionKind.entries.size) "إلغاء تحديد الكل" else "تحديد الكل")
                    }
                }
                CloudCollectionKind.entries.forEach { kind ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = kind in selectedCloudCollections,
                            onCheckedChange = { checked ->
                                selectedCloudCollections = if (checked) {
                                    selectedCloudCollections + kind
                                } else {
                                    selectedCloudCollections - kind
                                }
                            }
                        )
                        Text(kind.arabicLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("حذف ملف المتجر السحابي بالكامل (users/{uid})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(checked = wipeCloudProfile, onCheckedChange = { wipeCloudProfile = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pendingCloudCollections = selectedCloudCollections
                            pendingWipeProfile = wipeCloudProfile
                            showCloudDeleteDialog = true
                        },
                        enabled = !isDbBusy && (selectedCloudCollections.isNotEmpty() || wipeCloudProfile),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = mamoColors.loss),
                        modifier = Modifier.weight(1f).testTag("cloud_delete_selected_btn")
                    ) {
                        Text("حذف المحدد", maxLines = 1)
                    }
                    Button(
                        onClick = {
                            pendingCloudCollections = CloudCollectionKind.entries.toSet()
                            pendingWipeProfile = true
                            showCloudDeleteDialog = true
                        },
                        enabled = !isDbBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss),
                        modifier = Modifier.weight(1f).testTag("cloud_delete_all_btn")
                    ) {
                        Text("حذف كل السحابة", maxLines = 1)
                    }
                }
                cloudDeleteResult?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("آخر عملية حذف سحابي (${ArabicFormatters.formatDateTime(res.deletedAtMs, dateFmt)})", fontWeight = FontWeight.Bold)
                            Text("الإجمالي المحذوف: ${res.totalDeletedDocs} مستند | أصناف: ${res.deletedProducts} | أطراف: ${res.deletedParties}")
                            Text("فواتير: ${res.deletedInvoices} | قيود وموظفين: ${res.deletedLedgerEntries} | ملف المتجر: ${if (res.deletedProfile) "تم حذفه" else "محتفظ به"}")
                        }
                    }
                }
            }
        }

        // Database Compression (VACUUM), Log Pruning & GZIP Backup Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("db_compression_card")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = mamoColors.gold)
                        Column {
                            Text("ضغط قاعدة البيانات (VACUUM) وتوفير المساحة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("تنظيف السجلات القديمة، تفريغ WAL، وضغط ملف SQLite مع نسخ احتياطي GZIP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.refreshDatabaseStorageInfo(context) },
                        enabled = !isDbBusy
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("قياس")
                    }
                }
                dbStorageInfo?.let { info ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("الحجم الإجمالي الفعلي: ${formatByteSize(info.totalBytes)}", fontWeight = FontWeight.Bold)
                            Text(
                                "القاعدة (.db): ${formatByteSize(info.dbFileBytes)} | WAL: ${formatByteSize(info.walFileBytes)} | SHM: ${formatByteSize(info.shmFileBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "التدقيق: ${info.auditLogsCount} | المزامنة: ${info.syncQueueCount} | تغير الأسعار: ${info.priceHistoryCount} | الذكاء: ${info.aiLogsCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("الاحتفاظ بسجلات التدقيق والذكاء لآخر:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90).forEach { days ->
                        FilterChip(
                            selected = selectedRetentionDays == days,
                            onClick = { selectedRetentionDays = days },
                            label = { Text("$days يوم") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.compactAndPruneDatabase(context, selectedRetentionDays) },
                        enabled = !isDbBusy,
                        modifier = Modifier.weight(1f).testTag("db_vacuum_btn")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تنظيف وضغط VACUUM", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { exportGzipLauncher.launch("mamo_compressed_${System.currentTimeMillis()}.mamo.gz") },
                        enabled = !isDbBusy,
                        modifier = Modifier.weight(1f).testTag("db_export_gzip_btn")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخة مضغوطة GZIP", maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "الحفظ التلقائي بذاكرة الجهاز: ${if (deviceBackupStatus.internalSnapshotExists) formatByteSize(deviceBackupStatus.internalSnapshotBytes) else "جاهز"}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(deviceBackupStatus.locationLabel, style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.backupNowToDeviceStorage(context) },
                            enabled = !isDbBusy,
                            modifier = Modifier.fillMaxWidth().testTag("db_save_device_snapshot_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حفظ فوري في ذاكرة الجهاز (Documents)")
                        }
                    }
                }
                compressionReport?.let { rep ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = mamoColors.profitContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("نتيجة الضغط الأخيرة (${ArabicFormatters.formatDateTime(rep.completedAtMs, dateFmt)})", fontWeight = FontWeight.Bold, color = mamoColors.profit)
                            Text("قبل الضغط: ${formatByteSize(rep.sizeBeforeBytes)} → بعد الضغط: ${formatByteSize(rep.sizeAfterBytes)}")
                            Text("المساحة الموفرة: ${formatByteSize(rep.savedBytes)} (${rep.savedPercent}%) | سجلات منظفة: ${rep.totalPrunedRecords}")
                        }
                    }
                }
            }
        }

        // Danger Zone Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("منطقة الخطر — إعادة ضبط قاعدة البيانات", style = MaterialTheme.typography.titleMedium, color = mamoColors.loss, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "يمكن للأدمن تصفير كافة الفواتير والمنتجات والأطراف وحركات الصندوق للبدء من الصفر مع الاحتفاظ بإعدادات المتجر وحسابات الموظفين.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss),
                    modifier = Modifier.fillMaxWidth().testTag("db_reset_open_btn")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصفير قاعدة البيانات التشغيلية")
                }
            }
        }
    }

    inspectedRow?.let { row ->
        AlertDialog(
            onDismissRequest = { inspectedRow = null },
            title = { Text("تفاصيل السجل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.detailLines.forEach { (k, v) ->
                        Text("$k: $v", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("حالة المزامنة: ${if (row.isSynced) "متزامن سحابياً" else "بانتظار المزامنة"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { inspectedRow = null }) { Text("إغلاق") } }
        )
    }

    managedStaff?.let { target ->
        var isActive by remember(target.id) { mutableStateOf(target.isActive) }
        var newPin by remember(target.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { managedStaff = null },
            title = { Text("إدارة حساب: ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الحساب نشط")
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("رمز PIN جديد (اختياري)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminUpdateStaffStatusAndPin(
                            staffId = target.id,
                            newPin = newPin.ifBlank { null },
                            isActive = isActive
                        )
                        managedStaff = null
                    },
                    enabled = newPin.isBlank() || newPin.length >= 4
                ) { Text("حفظ التعديل") }
            },
            dismissButton = { TextButton(onClick = { managedStaff = null }) { Text("إلغاء") } }
        )
    }

    if (showResetDialog) {
        var adminPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("تأكيد تصفير قاعدة البيانات", color = mamoColors.loss) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("سيتم حذف جميع الفواتير والمنتجات والزبائن والموردين وحركات الصندوق والمصاريف نهائياً من القاعدة المحلية.")
                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) adminPin = it },
                        label = { Text("أدخل رمز PIN للأدمن للتأكيد") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.testTag("db_reset_pin_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetTransactionalDatabase(adminPin) {
                            showResetDialog = false
                        }
                    },
                    enabled = adminPin.length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss),
                    modifier = Modifier.testTag("db_reset_confirm_btn")
                ) { Text("تأكيد التصفير") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("إلغاء") } }
        )
    }

    approvalTarget?.let { target ->
        var assignedRole by remember(target.id) { mutableStateOf(target.requestedRole.ifBlank { StaffRole.CASHIER }) }
        var canProfits by remember(target.id) { mutableStateOf(assignedRole == StaffRole.OWNER || assignedRole == StaffRole.ACCOUNTANT) }
        var canCancel by remember(target.id) { mutableStateOf(assignedRole == StaffRole.OWNER || assignedRole == StaffRole.ACCOUNTANT) }
        var canInv by remember(target.id) { mutableStateOf(assignedRole == StaffRole.OWNER || assignedRole == StaffRole.ACCOUNTANT || assignedRole == StaffRole.WAREHOUSE_KEEPER) }
        var canExp by remember(target.id) { mutableStateOf(assignedRole == StaffRole.OWNER || assignedRole == StaffRole.ACCOUNTANT) }
        var canSettings by remember(target.id) { mutableStateOf(assignedRole == StaffRole.OWNER) }
        var canEditPrices by remember(target.id) { mutableStateOf(assignedRole == StaffRole.OWNER || assignedRole == StaffRole.ACCOUNTANT) }
        var canWithdrawDeposit by remember(target.id) { mutableStateOf(assignedRole == StaffRole.OWNER || assignedRole == StaffRole.ACCOUNTANT) }
        var canApproveStocktake by remember(target.id) { mutableStateOf(assignedRole == StaffRole.OWNER) }

        AlertDialog(
            onDismissRequest = { approvalTarget = null },
            title = { Text("قبول وتحديد عمل: ${target.name}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (target.loginIdentifier.isNotBlank()) {
                        Text("معرف الدخول: ${target.loginIdentifier}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("تحديد طبيعة العمل (الدور الوظيفي):", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            listOf(
                                StaffRole.OWNER to "مدير عام",
                                StaffRole.ACCOUNTANT to "محاسب",
                                StaffRole.CASHIER to "بائع / كاشير",
                                StaffRole.WAREHOUSE_KEEPER to "أمين مخزن",
                                StaffRole.AUDITOR to "مراقب فقط"
                            )
                        ) { (r, label) ->
                            FilterChip(
                                selected = assignedRole == r,
                                onClick = {
                                    assignedRole = r
                                    when (r) {
                                        StaffRole.OWNER -> {
                                            canProfits = true; canCancel = true; canInv = true; canExp = true
                                            canSettings = true; canEditPrices = true; canWithdrawDeposit = true; canApproveStocktake = true
                                        }
                                        StaffRole.ACCOUNTANT -> {
                                            canProfits = true; canCancel = true; canInv = true; canExp = true
                                            canSettings = false; canEditPrices = true; canWithdrawDeposit = true; canApproveStocktake = false
                                        }
                                        StaffRole.WAREHOUSE_KEEPER -> {
                                            canProfits = false; canCancel = false; canInv = true; canExp = false
                                            canSettings = false; canEditPrices = false; canWithdrawDeposit = false; canApproveStocktake = false
                                        }
                                        else -> {
                                            canProfits = false; canCancel = false; canInv = false; canExp = false
                                            canSettings = false; canEditPrices = false; canWithdrawDeposit = false; canApproveStocktake = false
                                        }
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    HorizontalDivider()
                    AdminPermCheckRow("عرض الأرباح ورأس المال", canProfits) { canProfits = it }
                    AdminPermCheckRow("إلغاء وتعديل الفواتير", canCancel) { canCancel = it }
                    AdminPermCheckRow("إدارة المخزون والمشتريات", canInv) { canInv = it }
                    AdminPermCheckRow("تسجيل المصاريف", canExp) { canExp = it }
                    AdminPermCheckRow("إدارة إعدادات المتجر والنسخ", canSettings) { canSettings = it }
                    AdminPermCheckRow("تعديل أسعار البيع ومنح خصومات", canEditPrices) { canEditPrices = it }
                    AdminPermCheckRow("الإيداع والسحب وإغلاق الصندوق", canWithdrawDeposit) { canWithdrawDeposit = it }
                    AdminPermCheckRow("اعتماد تسويات الجرد المخزني", canApproveStocktake) { canApproveStocktake = it }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approvePendingStaff(
                            staffId = target.id,
                            assignedRole = assignedRole,
                            canViewProfits = canProfits,
                            canCancelInvoices = canCancel,
                            canManageInventory = canInv,
                            canManageExpenses = canExp,
                            canManageSettings = canSettings,
                            canEditPrices = canEditPrices,
                            canWithdrawDeposit = canWithdrawDeposit,
                            canApproveStocktake = canApproveStocktake
                        ) {
                            approvalTarget = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_approve_staff_btn")
                ) { Text("اعتماد وتفعيل الحساب") }
            },
            dismissButton = { TextButton(onClick = { approvalTarget = null }) { Text("إلغاء") } }
        )
    }

    rejectTarget?.let { target ->
        var rejectReason by remember(target.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("رفض طلب التسجيل: ${target.name}", color = mamoColors.loss) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("لن يتمكن هذا المستخدم من تسجيل الدخول بعد الرفض.")
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("سبب الرفض (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rejectPendingStaff(target.id, rejectReason) {
                            rejectTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss)
                ) { Text("تأكيد الرفض") }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null }) { Text("إلغاء") } }
        )
    }

    if (showCloudDeleteDialog) {
        var adminPin by remember { mutableStateOf("") }
        val scopeSummary = buildString {
            if (pendingCloudCollections.isNotEmpty()) {
                append(pendingCloudCollections.joinToString("، ") { it.arabicLabel })
            }
            if (pendingWipeProfile) {
                if (isNotEmpty()) append(" + ")
                append("ملف المتجر السحابي")
            }
        }
        AlertDialog(
            onDismissRequest = { showCloudDeleteDialog = false },
            title = { Text("تأكيد حذف بيانات Firebase", color = mamoColors.loss) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم حذف البيانات السحابية للأقسام التالية نهائياً من Firestore:")
                    Text(scopeSummary, fontWeight = FontWeight.Bold, color = mamoColors.loss)
                    Text("تبقى القاعدة المحلية على جهازك محفوظة كما هي مع إعادة ضبط حالة المزامنة للأقسام المحذوفة.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) adminPin = it },
                        label = { Text("أدخل رمز PIN للأدمن للتأكيد") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("cloud_delete_pin_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCloudData(adminPin, pendingCloudCollections, pendingWipeProfile) {
                            showCloudDeleteDialog = false
                        }
                    },
                    enabled = adminPin.length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = mamoColors.loss),
                    modifier = Modifier.testTag("cloud_delete_confirm_btn")
                ) { Text("تأكيد الحذف السحابي") }
            },
            dismissButton = { TextButton(onClick = { showCloudDeleteDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun AdminPermCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes بايت"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${String.format(Locale.US, "%.1f", kb)} كيلوبايت"
    val mb = kb / 1024.0
    return "${String.format(Locale.US, "%.2f", mb)} ميجابايت"
}
