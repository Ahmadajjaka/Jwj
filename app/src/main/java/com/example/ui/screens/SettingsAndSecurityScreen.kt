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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.CostingMethod
import com.example.data.local.StaffRole
import com.example.data.local.StaffUserEntity
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters
import java.util.Calendar
import kotlin.math.abs

@Composable
fun SettingsAndSecurityScreen(
    state: MamoMainUiState,
    viewModel: MamoViewModel,
    googleEmail: String?,
    onOpenDatabaseAdmin: (() -> Unit)? = null,
    onLockStaff: () -> Unit,
    onSignOutGoogle: () -> Unit
) {
    val context = LocalContext.current
    val mamoColors = LocalMamoColors.current
    val canManage = state.activeStaff?.canManageSettings == true
    val isEmailAdmin = !googleEmail.isNullOrBlank() && state.activeStaff?.role == StaffRole.OWNER
    val canViewFinancialLedgers = state.activeStaff?.role == StaffRole.OWNER || state.activeStaff?.role == StaffRole.ACCOUNTANT

    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val syncQueue by viewModel.syncQueue.collectAsStateWithLifecycle()
    val journalEntries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val accountingPeriods by viewModel.accountingPeriods.collectAsStateWithLifecycle()
    val deviceBackupStatus by viewModel.deviceBackupStatus.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshDeviceBackupStatus(context)
    }

    var storeName by remember(state.settings) { mutableStateOf(state.settings.storeName) }
    var managerName by remember(state.settings) { mutableStateOf(state.settings.managerName) }
    var storePhone by remember(state.settings) { mutableStateOf(state.settings.storePhone) }
    var storeAddress by remember(state.settings) { mutableStateOf(state.settings.storeAddress) }
    var currencyCode by remember(state.settings) { mutableStateOf(state.settings.currencyCode) }
    var currencySymbol by remember(state.settings) { mutableStateOf(state.settings.currencySymbol) }
    var costingMethod by remember(state.settings) { mutableStateOf(state.settings.costingMethod) }
    var taxEnabled by remember(state.settings) { mutableStateOf(state.settings.taxEnabled) }
    var taxRateInput by remember(state.settings) { mutableStateOf(state.settings.taxRatePercent.toString()) }
    var staleDaysInput by remember(state.settings) { mutableStateOf(state.settings.staleDaysThreshold.toString()) }
    var printPaperSize by remember(state.settings) { mutableStateOf(state.settings.printPaperSize) }
    var printShowStamp by remember(state.settings) { mutableStateOf(state.settings.printShowStamp) }
    var printShowSignature by remember(state.settings) { mutableStateOf(state.settings.printShowSignature) }
    var footer by remember(state.settings) { mutableStateOf(state.settings.invoiceFooter) }
    var autoBackup by remember(state.settings) { mutableStateOf(state.settings.autoBackupEnabled) }
    var logoUri by remember(state.settings) { mutableStateOf(state.settings.logoUri) }

    var editingStaff by remember { mutableStateOf<StaffUserEntity?>(null) }
    var showStaffDialog by remember { mutableStateOf(false) }

    var showExportEncryptDialog by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf("") }
    var exportCompressGzip by remember { mutableStateOf(true) }
    var pendingExportPassphrase by remember { mutableStateOf<String?>(null) }
    var pendingExportGzip by remember { mutableStateOf(false) }

    var showImportPassphraseDialog by remember { mutableStateOf(false) }
    var importPassphrase by remember { mutableStateOf("") }
    var pendingImportPassphrase by remember { mutableStateOf<String?>(null) }

    var showSyncQueueCard by remember { mutableStateOf(false) }
    var showJournalCard by remember { mutableStateOf(false) }
    var showPeriodsCard by remember { mutableStateOf(false) }
    var showPeriodDialog by remember { mutableStateOf(false) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) logoUri = uri.toString()
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportJsonToUri(
                context = context,
                uri = uri,
                passphrase = pendingExportPassphrase,
                compressGzip = pendingExportGzip
            )
            pendingExportPassphrase = null
            pendingExportGzip = false
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importJsonFromUri(context, uri, passphrase = pendingImportPassphrase)
            pendingImportPassphrase = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Theme & Session Controls
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("المظهر والجلسة", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("الوضع الليلي (كحلي وذهبي)")
                    }
                    Switch(
                        checked = state.isDarkTheme,
                        onCheckedChange = { viewModel.toggleDarkTheme() },
                        modifier = Modifier.testTag("theme_switch")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "الموظف النشط: ${state.activeStaff?.name ?: "زائر"} (${ArabicFormatters.staffRoleLabel(state.activeStaff?.role ?: "")})",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!googleEmail.isNullOrBlank()) {
                    Text("حساب Google للمزامنة: $googleEmail", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onLockStaff, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تبديل الموظف")
                    }
                    OutlinedButton(onClick = onSignOutGoogle, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("خروج Google")
                    }
                }
                if (isEmailAdmin && onOpenDatabaseAdmin != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenDatabaseAdmin,
                        modifier = Modifier.fillMaxWidth().testTag("settings_open_db_admin_btn")
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فتح لوحة تحكم قاعدة البيانات (الأدمن)")
                    }
                }
            }
        }

        // Store Settings (Complete ERP Configuration)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("إعدادات المتجر والضريبة والطباعة", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("اسم المتجر") },
                    enabled = canManage,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = managerName,
                    onValueChange = { managerName = it },
                    label = { Text("المسؤول / المدير") },
                    enabled = canManage,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = storePhone,
                        onValueChange = { storePhone = it },
                        label = { Text("هاتف المتجر") },
                        enabled = canManage,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = storeAddress,
                        onValueChange = { storeAddress = it },
                        label = { Text("عنوان المتجر") },
                        enabled = canManage,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("العملة:", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ArabicFormatters.supportedCurrencies) { (code, sym) ->
                        FilterChip(
                            selected = currencyCode == code,
                            onClick = {
                                if (canManage) {
                                    currencyCode = code
                                    currencySymbol = sym
                                }
                            },
                            label = { Text("$code ($sym)") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("طريقة حساب تكلفة البضاعة المباعة:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = costingMethod == CostingMethod.WEIGHTED_AVERAGE,
                        onClick = { if (canManage) costingMethod = CostingMethod.WEIGHTED_AVERAGE },
                        label = { Text("المتوسط المرجح") }
                    )
                    FilterChip(
                        selected = costingMethod == CostingMethod.LAST_COST,
                        onClick = { if (canManage) costingMethod = CostingMethod.LAST_COST },
                        label = { Text("آخر سعر شراء") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تفعيل ضريبة القيمة المضافة على المبيعات")
                    Switch(checked = taxEnabled, onCheckedChange = { if (canManage) taxEnabled = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = taxRateInput,
                        onValueChange = { taxRateInput = it },
                        label = { Text("نسبة الضريبة %") },
                        enabled = canManage && taxEnabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = staleDaysInput,
                        onValueChange = { staleDaysInput = it },
                        label = { Text("تنبيه الركود (يوم)") },
                        enabled = canManage,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("حجم ورق طباعة الفاتورة:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = printPaperSize == "A4",
                        onClick = { if (canManage) printPaperSize = "A4" },
                        label = { Text("ورق رسمي A4") }
                    )
                    FilterChip(
                        selected = printPaperSize == "THERMAL_80",
                        onClick = { if (canManage) printPaperSize = "THERMAL_80" },
                        label = { Text("حراري 80mm") }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = printShowStamp, onCheckedChange = { if (canManage) printShowStamp = it })
                        Text("الختم", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = printShowSignature, onCheckedChange = { if (canManage) printShowSignature = it })
                        Text("التوقيع", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = footer,
                    onValueChange = { footer = it },
                    label = { Text("تذييل الفاتورة للطباعة") },
                    enabled = canManage,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (logoUri.isNotBlank()) {
                        AsyncImage(
                            model = logoUri,
                            contentDescription = "شعار المتجر",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            logoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = canManage
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختيار شعار المتجر")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نسخ تلقائي ومزامنة تلقائية")
                    Switch(checked = autoBackup, onCheckedChange = { if (canManage) autoBackup = it })
                }

                if (canManage) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val parsedTax = ArabicFormatters.parseDoubleOrNull(taxRateInput)?.coerceIn(0.0, 50.0) ?: 0.0
                            val parsedStale = ArabicFormatters.parseLongOrNull(staleDaysInput)?.toInt()?.coerceIn(7, 365) ?: 60
                            viewModel.updateStoreSettings(
                                state.settings.copy(
                                    storeName = storeName.ifBlank { "خردوات المامو" },
                                    managerName = managerName.ifBlank { "أحمد نوار المامو" },
                                    storePhone = storePhone.trim(),
                                    storeAddress = storeAddress.trim(),
                                    currencyCode = currencyCode,
                                    currencySymbol = currencySymbol,
                                    costingMethod = costingMethod,
                                    taxEnabled = taxEnabled,
                                    taxRatePercent = parsedTax,
                                    staleDaysThreshold = parsedStale,
                                    printPaperSize = printPaperSize,
                                    printShowStamp = printShowStamp,
                                    printShowSignature = printShowSignature,
                                    invoiceFooter = footer,
                                    logoUri = logoUri,
                                    autoBackupEnabled = autoBackup
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_settings_btn")
                    ) { Text("حفظ إعدادات المتجر") }
                }
            }
        }

        // Encrypted AES-256 Backups, Instant Device Persistence & Cloud Sync
        item {
            GlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                Text("الحفظ الدائم في ذاكرة الجهاز والنسخ الاحتياطي", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = mamoColors.profitContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth().testTag("device_persistence_status_box")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "الحفظ التلقائي الفوري في ذاكرة الجهاز: مفعّل",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = mamoColors.profit
                        )
                        Text(
                            text = "المسار: ${deviceBackupStatus.locationLabel}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (deviceBackupStatus.internalSnapshotExists) {
                                val sizeKb = (deviceBackupStatus.internalSnapshotBytes / 1024.0).coerceAtLeast(0.1)
                                val timeText = if (deviceBackupStatus.lastSavedAtMs > 0L) {
                                    ArabicFormatters.formatDateTime(deviceBackupStatus.lastSavedAtMs, state.settings.dateFormatPattern)
                                } else "الآن"
                                "آخر نسخة محفوظة على الجهاز: $timeText (${String.format(java.util.Locale.US, "%.1f", sizeKb)} KB)"
                            } else {
                                "يتم حفظ كل فاتورة بيع وشراء تلقائياً في قاعدة SQLite ونسخة GZIP على ذاكرة الجهاز"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.settings.lastSyncAtMs > 0L)
                        "آخر مزامنة سحابية: ${ArabicFormatters.formatDateTime(state.settings.lastSyncAtMs)}"
                    else "لم تتم المزامنة السحابية بعد (البيانات محفوظة محلياً بأمان)",
                    style = MaterialTheme.typography.bodySmall
                )
                if (state.settings.lastAutoBackupAtMs > 0L) {
                    Text(
                        text = "آخر نسخ تلقائي: ${ArabicFormatters.formatDateTime(state.settings.lastAutoBackupAtMs)} (${state.settings.lastAutoBackupStatus})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.backupNowToDeviceStorage(context) },
                    modifier = Modifier.fillMaxWidth().testTag("save_device_snapshot_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ جميع المبيعات والفواتير في ذاكرة الجهاز الآن")
                }
                if (deviceBackupStatus.internalSnapshotExists && canManage) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { viewModel.restoreFromDeviceSnapshot(context) },
                        modifier = Modifier.fillMaxWidth().testTag("restore_device_snapshot_btn")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استعادة آخر نسخة محفوظة من ذاكرة الجهاز")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.syncCloudNow() },
                    enabled = !state.isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مزامنة سحابية آمنة الآن")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showExportEncryptDialog = true },
                        modifier = Modifier.weight(1f).testTag("export_backup_btn")
                    ) {
                        Icon(Icons.Default.EnhancedEncryption, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير مضغوط / مشفر", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showImportPassphraseDialog = true },
                        enabled = canManage,
                        modifier = Modifier.weight(1f).testTag("import_backup_btn")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استعادة (.gz / .json)", maxLines = 1)
                    }
                }
                if (backups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("سجل النسخ الاحتياطي (${backups.size})", style = MaterialTheme.typography.labelLarge)
                    backups.take(4).forEach { bak ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${if (bak.isEncrypted) "🔐 " else ""}${bak.fileName}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${bak.status} • #${bak.checksumSha256.take(8)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bak.status == "SUCCESS") mamoColors.profit else mamoColors.loss
                            )
                        }
                    }
                }
            }
        }

        // Staff Users & Granular Permissions
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("المستخدمون والصلاحيات (5 أدوار)", style = MaterialTheme.typography.titleLarge)
                    if (canManage) {
                        OutlinedButton(onClick = {
                            editingStaff = null
                            showStaffDialog = true
                        }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                state.staffUsers.forEachIndexed { idx, staff ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${staff.name} ${if (!staff.isActive) "(معطل)" else ""}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = buildString {
                                    append(ArabicFormatters.staffRoleLabel(staff.role))
                                    if (staff.canEditPrices) append(" • تعديل الأسعار")
                                    if (staff.canApproveStocktake) append(" • اعتماد الجرد")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canManage) {
                            IconButton(onClick = {
                                editingStaff = staff
                                showStaffDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل صلاحيات ${staff.name}")
                            }
                        }
                    }
                    if (idx < state.staffUsers.lastIndex) HorizontalDivider()
                }
            }
        }

        // Card 1: Offline Sync Queue Viewer
        item {
            val pendingCount = syncQueue.count { it.status == "PENDING" }
            val failedCount = syncQueue.count { it.status == "FAILED" }
            val syncedCount = syncQueue.count { it.status == "SYNCED" }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("طابور المزامنة السحابية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "معلق: $pendingCount | فاشل: $failedCount | متزامن: $syncedCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (failedCount > 0) mamoColors.loss else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showSyncQueueCard = !showSyncQueueCard }) {
                        Icon(
                            imageVector = if (showSyncQueueCard) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "عرض طابور المزامنة"
                        )
                    }
                }
                if (showSyncQueueCard) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { viewModel.syncCloudNow() },
                        enabled = !state.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة محاولة المزامنة الآن")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    syncQueue.take(10).forEach { sq ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${sq.entityType} (${sq.operation})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                text = "${sq.status}${if (sq.lastError.isNotBlank()) " — ${sq.lastError.take(20)}" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = when (sq.status) {
                                    "SYNCED" -> mamoColors.profit
                                    "FAILED", "CONFLICT" -> mamoColors.loss
                                    else -> mamoColors.warning
                                }
                            )
                        }
                    }
                }
            }
        }

        // Card 2: Double-Entry Journal Ledger Viewer
        if (canViewFinancialLedgers) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("دفتر قيود اليومية المزدوجة (${journalEntries.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("مدين = دائن تلقائياً لكل عملية", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { showJournalCard = !showJournalCard }) {
                            Icon(
                                imageVector = if (showJournalCard) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "عرض دفتر القيود"
                            )
                        }
                    }
                    if (showJournalCard) {
                        Spacer(modifier = Modifier.height(6.dp))
                        journalEntries.take(15).forEachIndexed { idx, j ->
                            val balanced = abs(j.entry.totalDebit - j.entry.totalCredit) < 0.01
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${j.entry.entryNumber} — ${j.entry.description}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(
                                        text = if (balanced) "متوازن (${ArabicFormatters.formatNumber(j.entry.totalDebit)})" else "فرق!",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (balanced) mamoColors.profit else mamoColors.loss
                                    )
                                }
                                j.lines.forEach { ln ->
                                    Text(
                                        text = "  • ${ln.accountCode} ${ln.accountName}: مدين ${ArabicFormatters.formatNumber(ln.debit)} | دائن ${ArabicFormatters.formatNumber(ln.credit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (idx < minOf(14, journalEntries.lastIndex)) HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Card 3: Accounting Periods Lock Control
        if (canManage) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("الفترات المحاسبية المقفلة (${accountingPeriods.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("منع التعديل أو الإلغاء بعد الإقفال", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { showPeriodsCard = !showPeriodsCard }) {
                            Icon(
                                imageVector = if (showPeriodsCard) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "عرض الفترات المحاسبية"
                            )
                        }
                    }
                    if (showPeriodsCard) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = { showPeriodDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("إقفال فترة محاسبية جديدة")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        accountingPeriods.forEach { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${p.name} (${ArabicFormatters.formatDate(p.startMs)} - ${ArabicFormatters.formatDate(p.endMs)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (p.isClosed) "مقفلة (${p.closedBy})" else "مفتوحة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (p.isClosed) mamoColors.warning else mamoColors.profit
                                )
                            }
                        }
                    }
                }
            }
        }

        // Audit Log
        item {
            Text("سجل التدقيق لجميع العمليات (${state.auditLogs.size})", style = MaterialTheme.typography.titleLarge)
        }
        items(state.auditLogs.take(40), key = { it.id }) { log ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(log.action, fontWeight = FontWeight.Bold)
                    Text(log.staffName, style = MaterialTheme.typography.labelSmall)
                }
                Text(log.details, style = MaterialTheme.typography.bodySmall)
                Text(
                    ArabicFormatters.formatDateTime(log.occurredAtMs, state.settings.dateFormatPattern),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Export Backup Passphrase & GZIP Dialog
    if (showExportEncryptDialog) {
        AlertDialog(
            onDismissRequest = { showExportEncryptDialog = false },
            title = { Text("تصدير النسخة الاحتياطية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل كلمة مرور (4 أحرف+) لتشفير الملف بتقنية AES-256، أو اتركه فارغاً لتصدير نسخة قياسية:")
                    OutlinedTextField(
                        value = exportPassphrase,
                        onValueChange = { exportPassphrase = it },
                        label = { Text("كلمة مرور التشفير (اختياري)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ضغط النسخة بـ GZIP لتوفير المساحة (~75%)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(
                            checked = exportCompressGzip,
                            onCheckedChange = { exportCompressGzip = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pass = exportPassphrase.trim().takeIf { it.length >= 4 }
                    val gz = exportCompressGzip
                    pendingExportPassphrase = pass
                    pendingExportGzip = gz
                    showExportEncryptDialog = false
                    val ext = when {
                        gz && pass != null -> "mamo.gz.enc.json"
                        gz -> "mamo.gz"
                        pass != null -> "mamo.json"
                        else -> "json"
                    }
                    exportJsonLauncher.launch("mamo_backup_${System.currentTimeMillis()}.$ext")
                    exportPassphrase = ""
                }) {
                    val label = when {
                        exportCompressGzip && exportPassphrase.trim().length >= 4 -> "تصدير مضغوط ومشفر"
                        exportCompressGzip -> "تصدير مضغوط GZIP"
                        exportPassphrase.trim().length >= 4 -> "تصدير مشفر AES-256"
                        else -> "تصدير JSON"
                    }
                    Text(label)
                }
            },
            dismissButton = { TextButton(onClick = { showExportEncryptDialog = false }) { Text("إلغاء") } }
        )
    }

    // Import Backup Passphrase Dialog
    if (showImportPassphraseDialog) {
        AlertDialog(
            onDismissRequest = { showImportPassphraseDialog = false },
            title = { Text("استعادة نسخة احتياطية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إذا كانت النسخة مشفرة AES-256، أدخل كلمة مرور فك التشفير أولاً ثم اختر الملف:")
                    OutlinedTextField(
                        value = importPassphrase,
                        onValueChange = { importPassphrase = it },
                        label = { Text("كلمة مرور فك التشفير (إن وجدت)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    pendingImportPassphrase = importPassphrase.trim().takeIf { it.isNotBlank() }
                    showImportPassphraseDialog = false
                    importPassphrase = ""
                    importJsonLauncher.launch(arrayOf("application/json", "*/*"))
                }) { Text("اختيار الملف") }
            },
            dismissButton = { TextButton(onClick = { showImportPassphraseDialog = false }) { Text("إلغاء") } }
        )
    }

    // Accounting Period Dialog
    if (showPeriodDialog) {
        var periodName by remember { mutableStateOf("إقفال الشهر الحالي") }
        AlertDialog(
            onDismissRequest = { showPeriodDialog = false },
            title = { Text("إقفال فترة محاسبية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم إقفال الفترة حتى تاريخ اليوم ومنع تعديل أو إلغاء الفواتير داخلها إلا بواسطة المدير العام.")
                    OutlinedTextField(
                        value = periodName,
                        onValueChange = { periodName = it },
                        label = { Text("اسم الفترة المحاسبية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        val start = cal.timeInMillis
                        val end = System.currentTimeMillis()
                        viewModel.createOrCloseAccountingPeriod(periodName, start, end, closeNow = true)
                        showPeriodDialog = false
                    },
                    enabled = periodName.isNotBlank()
                ) { Text("اعتماد الإقفال") }
            },
            dismissButton = { TextButton(onClick = { showPeriodDialog = false }) { Text("إلغاء") } }
        )
    }

    // Staff Add / Edit Dialog (All 5 Roles + Granular Flags)
    if (showStaffDialog) {
        val current = editingStaff
        var name by remember(current) { mutableStateOf(current?.name ?: "") }
        var role by remember(current) { mutableStateOf(current?.role ?: StaffRole.CASHIER) }
        var pin by remember(current) { mutableStateOf("") }
        var canProfits by remember(current) { mutableStateOf(current?.canViewProfits ?: false) }
        var canCancel by remember(current) { mutableStateOf(current?.canCancelInvoices ?: false) }
        var canInv by remember(current) { mutableStateOf(current?.canManageInventory ?: false) }
        var canExp by remember(current) { mutableStateOf(current?.canManageExpenses ?: false) }
        var canEditPrices by remember(current) { mutableStateOf(current?.canEditPrices ?: false) }
        var canWithdrawDeposit by remember(current) { mutableStateOf(current?.canWithdrawDeposit ?: false) }
        var canApproveStocktake by remember(current) { mutableStateOf(current?.canApproveStocktake ?: false) }
        var isActive by remember(current) { mutableStateOf(current?.isActive ?: true) }

        AlertDialog(
            onDismissRequest = { showStaffDialog = false },
            title = { Text(if (current == null) "إضافة موظف جديد" else "تعديل صلاحيات ${current.name}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الموظف") }, singleLine = true)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                        label = { Text(if (current == null) "رمز المرور PIN (4 أرقام+)" else "رمز PIN جديد (اتركه فارغاً للإبقاء)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    Text("الدور الوظيفي:", style = MaterialTheme.typography.labelLarge)
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
                                selected = role == r,
                                onClick = {
                                    role = r
                                    when (r) {
                                        StaffRole.OWNER -> {
                                            canProfits = true; canCancel = true; canInv = true; canExp = true
                                            canEditPrices = true; canWithdrawDeposit = true; canApproveStocktake = true
                                        }
                                        StaffRole.ACCOUNTANT -> {
                                            canProfits = true; canCancel = true; canInv = true; canExp = true
                                            canEditPrices = true; canWithdrawDeposit = true; canApproveStocktake = false
                                        }
                                        StaffRole.WAREHOUSE_KEEPER -> {
                                            canProfits = false; canCancel = false; canInv = true; canExp = false
                                            canEditPrices = false; canWithdrawDeposit = false; canApproveStocktake = false
                                        }
                                        else -> {
                                            canProfits = false; canCancel = false; canInv = false; canExp = false
                                            canEditPrices = false; canWithdrawDeposit = false; canApproveStocktake = false
                                        }
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    PermissionCheckboxRow("عرض الأرباح ورأس المال", canProfits) { canProfits = it }
                    PermissionCheckboxRow("إلغاء وتعديل الفواتير", canCancel) { canCancel = it }
                    PermissionCheckboxRow("إدارة المخزون والمشتريات", canInv) { canInv = it }
                    PermissionCheckboxRow("تسجيل المصاريف", canExp) { canExp = it }
                    PermissionCheckboxRow("تعديل أسعار البيع ومنح خصومات", canEditPrices) { canEditPrices = it }
                    PermissionCheckboxRow("الإيداع والسحب وإغلاق الصندوق", canWithdrawDeposit) { canWithdrawDeposit = it }
                    PermissionCheckboxRow("اعتماد تسويات الجرد المخزني", canApproveStocktake) { canApproveStocktake = it }
                    if (current != null && current.id != state.activeStaff?.id) {
                        PermissionCheckboxRow("الحساب نشط", isActive) { isActive = it }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveStaffUser(
                            existingId = current?.id,
                            name = name,
                            role = role,
                            pin = pin.takeIf { it.isNotBlank() },
                            canViewProfits = canProfits,
                            canCancelInvoices = canCancel,
                            canManageInventory = canInv,
                            canManageExpenses = canExp,
                            canManageSettings = role == StaffRole.OWNER,
                            isActive = isActive,
                            canEditPrices = canEditPrices,
                            canWithdrawDeposit = canWithdrawDeposit,
                            canApproveStocktake = canApproveStocktake
                        )
                        showStaffDialog = false
                    },
                    enabled = name.isNotBlank() && (current != null || pin.length >= 4)
                ) { Text("حفظ الموظف") }
            },
            dismissButton = { TextButton(onClick = { showStaffDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun PermissionCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
