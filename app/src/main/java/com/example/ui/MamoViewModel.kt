package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.AccountingPeriodEntity
import com.example.data.local.AiLogEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.ApprovalStatus
import com.example.data.local.BackupRecordEntity
import com.example.data.local.CashAccountEntity
import com.example.data.local.CashTransactionEntity
import com.example.data.local.CashTxType
import com.example.data.local.CashboxClosingEntity
import com.example.data.local.CustomerSpecialPriceEntity
import com.example.data.local.DebtPaymentEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.InvoiceWithItems
import com.example.data.local.JournalEntryWithLines
import com.example.data.local.MamoDatabase
import com.example.data.local.MeasureUnit
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductPriceHistoryEntity
import com.example.data.local.ProductUnitEntity
import com.example.data.local.ReturnEntity
import com.example.data.local.StaffRole
import com.example.data.local.StaffUserEntity
import com.example.data.local.StockCountEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.StoreSettingsEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.remote.AiChatMessage
import com.example.data.remote.CloudCollectionKind
import com.example.data.remote.CloudDatabaseSnapshot
import com.example.data.remote.CloudDeleteResult
import com.example.data.remote.CloudSyncSummary
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.remote.GroqAiResult
import com.example.data.remote.GroqAiService
import com.example.data.remote.MamoAiTools
import com.example.data.repository.AccountingEngine
import com.example.data.repository.AccountingRepository
import com.example.data.repository.BalanceSheetSummary
import com.example.data.repository.CapitalSummary
import com.example.data.repository.CashFlowSummary
import com.example.data.repository.ChartPoint
import com.example.data.repository.ChartRange
import com.example.data.repository.DashboardMetrics
import com.example.data.repository.DatabaseCompressionReport
import com.example.data.repository.DatabaseIntegrityReport
import com.example.data.repository.DatabaseStorageInfo
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.LoginAuthResult
import com.example.data.repository.PartyStatement
import com.example.data.repository.PeriodFilter
import com.example.data.repository.ProductProfitabilityRow
import com.example.data.repository.ProfitLossSummary
import com.example.data.repository.ResolvedPriceQuote
import com.example.data.repository.UserPreferencesRepository
import com.example.utils.ArabicFormatters
import com.example.utils.BarcodeLabelHelper
import com.example.utils.DeviceBackupStatus
import com.example.utils.DeviceLocalBackupManager
import com.example.utils.ExportAndShareHelper
import com.example.utils.InvoiceOcrParser
import com.example.utils.ParsedPurchaseOcrDraft
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiMessage {
    data class Info(val text: String) : UiMessage
    data class Error(val text: String) : UiMessage
}

data class MamoMainUiState(
    val settings: StoreSettingsEntity = StoreSettingsEntity(),
    val staffUsers: List<StaffUserEntity> = emptyList(),
    val activeStaff: StaffUserEntity? = null,
    val isDarkTheme: Boolean = false,
    val products: List<ProductEntity> = emptyList(),
    val parties: List<PartyEntity> = emptyList(),
    val invoices: List<InvoiceWithItems> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val cashTransactions: List<CashTransactionEntity> = emptyList(),
    val cashboxBalance: Double = 0.0,
    val expenses: List<ExpenseEntity> = emptyList(),
    val debtPayments: List<DebtPaymentEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val dashboardMetrics: DashboardMetrics = DashboardMetrics(),
    val chartRange: ChartRange = ChartRange.WEEKLY,
    val isSyncing: Boolean = false,
    val lastSyncSummary: CloudSyncSummary? = null
)

class MamoViewModel(
    private val repo: AccountingRepository,
    private val syncRepo: FirestoreSyncRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val currentUserId: String,
    private val appContext: Context? = null
) : ViewModel() {

    private val autoPersistMutex = Mutex()
    private val _deviceBackupStatus = MutableStateFlow(
        appContext?.let { DeviceLocalBackupManager.inspectStatus(it) } ?: DeviceBackupStatus(
            locationLabel = DeviceLocalBackupManager.defaultLocationLabel()
        )
    )
    val deviceBackupStatus: StateFlow<DeviceBackupStatus> = _deviceBackupStatus.asStateFlow()

    private val _chartRange = MutableStateFlow(ChartRange.WEEKLY)
    private val _isSyncing = MutableStateFlow(false)
    private val _lastSyncSummary = MutableStateFlow<CloudSyncSummary?>(null)
    private val _cloudSnapshot = MutableStateFlow<CloudDatabaseSnapshot?>(null)
    val cloudSnapshot: StateFlow<CloudDatabaseSnapshot?> = _cloudSnapshot.asStateFlow()
    private val _integrityReport = MutableStateFlow<DatabaseIntegrityReport?>(null)
    val integrityReport: StateFlow<DatabaseIntegrityReport?> = _integrityReport.asStateFlow()
    private val _dbStorageInfo = MutableStateFlow<DatabaseStorageInfo?>(null)
    val dbStorageInfo: StateFlow<DatabaseStorageInfo?> = _dbStorageInfo.asStateFlow()
    private val _compressionReport = MutableStateFlow<DatabaseCompressionReport?>(null)
    val compressionReport: StateFlow<DatabaseCompressionReport?> = _compressionReport.asStateFlow()
    private val _cloudDeleteResult = MutableStateFlow<CloudDeleteResult?>(null)
    val cloudDeleteResult: StateFlow<CloudDeleteResult?> = _cloudDeleteResult.asStateFlow()
    private val _isDbBusy = MutableStateFlow(false)
    val isDbBusy: StateFlow<Boolean> = _isDbBusy.asStateFlow()
    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()
    private val _loginInfoMessage = MutableStateFlow<String?>(null)
    val loginInfoMessage: StateFlow<String?> = _loginInfoMessage.asStateFlow()
    private val _loginShakeTick = MutableStateFlow(0)
    val loginShakeTick: StateFlow<Int> = _loginShakeTick.asStateFlow()
    private val _loginWelcomeMessage = MutableStateFlow<String?>(null)
    val loginWelcomeMessage: StateFlow<String?> = _loginWelcomeMessage.asStateFlow()

    val rememberMe: StateFlow<Boolean> = prefsRepo.rememberMe
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    val savedLoginIdentifier: StateFlow<String> = prefsRepo.savedLoginIdentifier
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")
    val isGuestSession: StateFlow<Boolean> = prefsRepo.isGuestSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    val isSessionReady: StateFlow<Boolean> = combine(
        repo.storeSettings,
        repo.staffUsers,
        prefsRepo.activeStaffId,
        prefsRepo.isGuestSession
    ) { _, _, _, _ -> true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Ultimate Domain StateFlows
    val productUnits: StateFlow<List<ProductUnitEntity>> = repo.productUnits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val customerSpecialPrices: StateFlow<List<CustomerSpecialPriceEntity>> = repo.customerSpecialPrices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val priceHistory: StateFlow<List<ProductPriceHistoryEntity>> = repo.priceHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val cashAccounts: StateFlow<List<CashAccountEntity>> = repo.cashAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val cashboxClosings: StateFlow<List<CashboxClosingEntity>> = repo.cashboxClosings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val journalEntries: StateFlow<List<JournalEntryWithLines>> = repo.journalEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val stockCounts: StateFlow<List<StockCountEntity>> = repo.stockCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val returns: StateFlow<List<ReturnEntity>> = repo.returns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val accountingPeriods: StateFlow<List<AccountingPeriodEntity>> = repo.accountingPeriods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val syncQueue: StateFlow<List<SyncQueueEntity>> = repo.syncQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val backups: StateFlow<List<BackupRecordEntity>> = repo.backups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val aiLogs: StateFlow<List<AiLogEntity>> = repo.aiLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _ocrDraft = MutableStateFlow<ParsedPurchaseOcrDraft?>(null)
    val ocrDraft: StateFlow<ParsedPurchaseOcrDraft?> = _ocrDraft.asStateFlow()
    private val _isOcrParsing = MutableStateFlow(false)
    val isOcrParsing: StateFlow<Boolean> = _isOcrParsing.asStateFlow()

    private val groqAiService = GroqAiService()
    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()
    private val _selectedAiModel = MutableStateFlow(GroqAiService.DEFAULT_MODEL_ID)
    val selectedAiModel: StateFlow<String> = _selectedAiModel.asStateFlow()
    val isGroqKeyConfigured: Boolean get() = groqAiService.hasConfiguredKey()

    val allProductsForAdmin: StateFlow<List<ProductEntity>> = repo.allProductsForAdmin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminUserId: String get() = currentUserId

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repo.ensureDefaultStoreSettings()
        }
    }

    private val corePart1 = combine(
        repo.storeSettings,
        repo.staffUsers,
        prefsRepo.activeStaffId,
        prefsRepo.isDarkTheme,
        repo.products
    ) { settings, staff, activeStaffId, isDark, products ->
        CorePart1(settings ?: StoreSettingsEntity(), staff, activeStaffId, isDark, products)
    }

    private val corePart2 = combine(
        repo.parties,
        repo.invoices,
        repo.stockMovements,
        repo.cashTransactions,
        repo.cashboxBalance
    ) { parties, invoices, movements, cashTxs, cashBalance ->
        CorePart2(parties, invoices, movements, cashTxs, cashBalance)
    }

    private val corePart3 = combine(
        repo.expenses,
        repo.debtPayments,
        repo.auditLogs,
        _chartRange,
        _isSyncing
    ) { expenses, payments, logs, chartRange, syncing ->
        CorePart3(expenses, payments, logs, chartRange, syncing)
    }

    val uiState: StateFlow<MamoMainUiState> = combine(
        corePart1,
        corePart2,
        corePart3,
        _lastSyncSummary,
        syncQueue
    ) { p1, p2, p3, syncSummary, queue ->
        val activeStaff = p1.staffUsers.firstOrNull {
            it.id == p1.activeStaffId && it.isActive && it.approvalStatus == ApprovalStatus.APPROVED
        }
        val pendingCount = queue.count { it.status == "PENDING" || it.status == "FAILED" }
        val metrics = MamoReportCalculators.computeDashboardMetrics(
            products = p1.products,
            parties = p2.parties,
            invoices = p2.invoices,
            cashTransactions = p2.cashTransactions,
            cashboxBalance = p2.cashboxBalance,
            expenses = p3.expenses,
            pendingSyncCount = pendingCount,
            staleDaysThreshold = p1.settings.staleDaysThreshold
        )
        MamoMainUiState(
            settings = p1.settings,
            staffUsers = p1.staffUsers,
            activeStaff = activeStaff,
            isDarkTheme = p1.isDark,
            products = p1.products,
            parties = p2.parties,
            invoices = p2.invoices,
            stockMovements = p2.stockMovements,
            cashTransactions = p2.cashTransactions,
            cashboxBalance = p2.cashboxBalance,
            expenses = p3.expenses,
            debtPayments = p3.debtPayments,
            auditLogs = p3.auditLogs,
            dashboardMetrics = metrics,
            chartRange = p3.chartRange,
            isSyncing = p3.isSyncing,
            lastSyncSummary = syncSummary
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = MamoMainUiState()
        )

    fun toggleDarkTheme() {
        viewModelScope.launch { prefsRepo.setDarkTheme(!uiState.value.isDarkTheme) }
    }

    fun setChartRange(range: ChartRange) {
        _chartRange.value = range
    }

    // Auth & Session
    fun createOwner(name: String, pin: String) {
        viewModelScope.launch {
            runAction("تم تهيئة حساب المدير بنجاح") {
                val owner = repo.createOwnerProfile(name, pin)
                Firebase.auth.currentUser?.email?.takeIf { it.isNotBlank() }?.let { email ->
                    prefsRepo.bindAccountIdentifier(email, owner.id)
                }
                prefsRepo.setActiveStaffId(owner.id)
                _loginWelcomeMessage.value = "مرحباً بك في عالم المامو، ${owner.name}"
                if (Firebase.auth.currentUser != null) syncNowInternal()
            }
        }
    }

    fun unlockStaff(staffId: Long, pin: String) {
        viewModelScope.launch {
            val verified = repo.verifyStaffPin(staffId, pin)
            if (verified != null) {
                _pinError.value = null
                _loginError.value = null
                prefsRepo.setActiveStaffId(verified.id)
                _loginWelcomeMessage.value = "أهلاً بعودتك، ${verified.name}!"
            } else {
                _pinError.value = "رمز المرور غير صحيح أو الحساب معطل"
            }
        }
    }

    fun loginWithCredentials(identifier: String, password: String, rememberMe: Boolean, googleEmail: String?) {
        val cleanId = identifier.trim()
        if (cleanId.isBlank() || password.isBlank()) {
            _loginInfoMessage.value = null
            _loginError.value = "يرجى إدخال اسم المستخدم أو البريد وكلمة المرور"
            _loginShakeTick.value++
            return
        }
        viewModelScope.launch {
            val staffList = uiState.value.staffUsers
            if (staffList.isEmpty()) {
                _loginInfoMessage.value = null
                _loginError.value = "لا توجد حسابات مسجلة بعد. اضغط «إنشاء حساب جديد» أو سجّل بحساب Google."
                _loginShakeTick.value++
                return@launch
            }
            val mappedId = prefsRepo.findStaffIdByIdentifier(cleanId)
            when (val result = repo.authenticateWithStatus(cleanId, password, mappedId, googleEmail)) {
                is LoginAuthResult.Success -> {
                    _loginError.value = null
                    _loginInfoMessage.value = null
                    _pinError.value = null
                    prefsRepo.setRememberMe(rememberMe, cleanId)
                    prefsRepo.setActiveStaffId(result.staff.id)
                    _loginWelcomeMessage.value = "مرحباً بك في عالم المامو، ${result.staff.name}!"
                }
                is LoginAuthResult.PendingApproval -> {
                    _loginInfoMessage.value = null
                    val roleLabel = ArabicFormatters.staffRoleLabel(result.requestedRole)
                    _loginError.value = "حسابك (${result.staffName} — طلب: $roleLabel) بانتظار موافقة مدير النظام وتحديد طبيعة عملك وصلاحياتك."
                    _loginShakeTick.value++
                }
                is LoginAuthResult.Rejected -> {
                    _loginInfoMessage.value = null
                    _loginError.value = "تم رفض طلب انضمام الحساب (${result.staffName}) من قبل مدير النظام."
                    _loginShakeTick.value++
                }
                is LoginAuthResult.Inactive -> {
                    _loginInfoMessage.value = null
                    _loginError.value = "تم تعطيل هذا الحساب (${result.staffName}). يرجى مراجعة المدير العام."
                    _loginShakeTick.value++
                }
                LoginAuthResult.InvalidCredentials -> {
                    _loginInfoMessage.value = null
                    _loginError.value = "بيانات الدخول غير صحيحة. تأكد من اسم المستخدم وكلمة المرور."
                    _loginShakeTick.value++
                }
            }
        }
    }

    fun registerNewAccount(
        fullName: String,
        identifier: String,
        password: String,
        role: String,
        ownerVerifyPin: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val created = repo.registerAccountFromLogin(fullName, role, password, ownerVerifyPin, identifier)
                if (identifier.isNotBlank()) {
                    prefsRepo.bindAccountIdentifier(identifier, created.id)
                    prefsRepo.setRememberMe(true, identifier.trim())
                }
                prefsRepo.bindAccountIdentifier(fullName, created.id)
                _loginError.value = null

                if (created.approvalStatus == ApprovalStatus.APPROVED && created.isActive) {
                    _loginInfoMessage.value = null
                    prefsRepo.setActiveStaffId(created.id)
                    _loginWelcomeMessage.value = "تم إنشاء حسابك بنجاح! أهلاً بك في عالم المامو، ${created.name}"
                    if (Firebase.auth.currentUser != null) runCatching { syncNowInternal() }
                } else {
                    val notice = "تم إرسال طلب تسجيل (${created.name}) بنجاح — الحساب بانتظار موافقة الأدمن وتحديد طبيعة العمل والصلاحيات."
                    _loginInfoMessage.value = notice
                    _messages.emit(UiMessage.Info(notice))
                }
                onSuccess()
            } catch (e: Exception) {
                _loginInfoMessage.value = null
                _loginError.value = e.localizedMessage ?: "تعذر إنشاء الحساب"
                _loginShakeTick.value++
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر إنشاء الحساب"))
            }
        }
    }

    fun approvePendingStaff(
        staffId: Long,
        assignedRole: String,
        canViewProfits: Boolean,
        canCancelInvoices: Boolean,
        canManageInventory: Boolean,
        canManageExpenses: Boolean,
        canManageSettings: Boolean,
        canEditPrices: Boolean,
        canWithdrawDeposit: Boolean,
        canApproveStocktake: Boolean,
        onDone: () -> Unit = {}
    ) {
        val admin = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم قبول الموظف وتحديد طبيعة عمله وصلاحياته بنجاح") {
                repo.approvePendingStaff(
                    staffId = staffId,
                    assignedRole = assignedRole,
                    canViewProfits = canViewProfits,
                    canCancelInvoices = canCancelInvoices,
                    canManageInventory = canManageInventory,
                    canManageExpenses = canManageExpenses,
                    canManageSettings = canManageSettings,
                    canEditPrices = canEditPrices,
                    canWithdrawDeposit = canWithdrawDeposit,
                    canApproveStocktake = canApproveStocktake,
                    actingStaff = admin
                )
                onDone()
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun rejectPendingStaff(staffId: Long, reason: String = "", onDone: () -> Unit = {}) {
        val admin = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم رفض طلب تسجيل المستخدم") {
                repo.rejectPendingStaff(staffId, reason, admin)
                onDone()
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun recoverPassword(
        identifier: String,
        newPassword: String,
        ownerVerifyPin: String?,
        googleEmail: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val mappedId = prefsRepo.findStaffIdByIdentifier(identifier)
                val target = repo.findStaffForRecovery(identifier, mappedId, googleEmail)
                    ?: error("لم يتم العثور على حساب مطابق لاسم المستخدم أو البريد المدخل")
                val owner = uiState.value.staffUsers.firstOrNull {
                    it.role == StaffRole.OWNER && it.isActive && it.approvalStatus == ApprovalStatus.APPROVED
                }
                val isGoogleVerifiedOwner = !googleEmail.isNullOrBlank() && target.role == StaffRole.OWNER
                val isOwnerPinVerified = owner != null && !ownerVerifyPin.isNullOrBlank() &&
                    owner.pinHash == AccountingEngine.hashPin(ownerVerifyPin.trim())
                if (!isGoogleVerifiedOwner && !isOwnerPinVerified) {
                    error("لإعادة تعيين كلمة المرور بأمان، يرجى ربط حساب Google (للمدير) أو إدخال كلمة مرور المدير العام.")
                }
                val method = if (isGoogleVerifiedOwner) "تأكيد حساب Google ($googleEmail)" else "اعتماد المدير العام"
                val updated = repo.resetPasswordByRecovery(target.id, newPassword, method)
                _loginError.value = null
                _messages.emit(UiMessage.Info("تم تحديث كلمة مرور ${updated.name} بنجاح"))
                onSuccess()
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر استعادة كلمة المرور"))
            }
        }
    }

    fun enterGuestMode() {
        viewModelScope.launch {
            _loginError.value = null
            _loginInfoMessage.value = null
            prefsRepo.setGuestSession(true)
            _loginWelcomeMessage.value = "مرحباً بك في عالم المامو — وضع الزائر"
        }
    }

    fun exitGuestMode() {
        viewModelScope.launch { prefsRepo.setGuestSession(false) }
    }

    fun consumeWelcomeMessage() { _loginWelcomeMessage.value = null }
    fun clearLoginError() {
        _loginError.value = null
        _loginInfoMessage.value = null
    }

    fun lockStaffSession() {
        viewModelScope.launch {
            _pinError.value = null
            _loginError.value = null
            _loginInfoMessage.value = null
            prefsRepo.setGuestSession(false)
            prefsRepo.setActiveStaffId(null)
        }
    }

    // Inventory & Multi-Unit & Pricing
    fun saveProduct(
        productId: Long?,
        name: String,
        category: String,
        barcode: String,
        costPrice: Double,
        sellingPrice: Double,
        quantity: Double,
        minStockLevel: Double,
        sku: String = "",
        imageUri: String = "",
        baseUnit: String = MeasureUnit.PIECE,
        wholesalePrice: Double = sellingPrice,
        minSellingPrice: Double = costPrice,
        defaultSupplierName: String = ""
    ) {
        val staff = requireActiveStaff() ?: return
        if (!staff.canManageInventory) {
            emitError("ليس لديك صلاحية تعديل المخزون")
            return
        }
        viewModelScope.launch {
            runAction("تم حفظ بيانات الصنف") {
                repo.saveProduct(
                    productId, name, category, barcode, costPrice, sellingPrice, quantity, minStockLevel, staff,
                    sku, imageUri, baseUnit, wholesalePrice, minSellingPrice, defaultSupplierName
                )
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun saveProductUnit(
        unitId: Long?,
        productId: Long,
        unitName: String,
        conversionToBase: Double,
        sellingPrice: Double,
        wholesalePrice: Double,
        barcode: String
    ) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم حفظ وحدة القياس للصنف") {
                repo.saveProductUnit(unitId, productId, unitName, conversionToBase, sellingPrice, wholesalePrice, barcode, staff)
            }
        }
    }

    fun deleteProductUnit(unitId: Long) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم حذف وحدة القياس") { repo.deleteProductUnit(unitId, staff) }
        }
    }

    fun saveCustomerSpecialPrice(partyId: Long, productId: Long, specialPrice: Double, minQty: Double) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم حفظ السعر الخاص للعميل") {
                repo.saveCustomerSpecialPrice(partyId, productId, specialPrice, minQty, staff)
            }
        }
    }

    suspend fun resolveProductPriceForLine(
        productId: Long,
        unitName: String,
        pricingTier: String,
        partyId: Long?,
        requestedQuantity: Double = 1.0
    ): ResolvedPriceQuote = repo.resolveProductPriceForLine(productId, unitName, pricingTier, partyId, requestedQuantity)

    fun adjustStock(productId: Long, newQuantity: Double, reason: String) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم تسجيل الجرد وتعديل الكمية") {
                repo.adjustProductStock(productId, newQuantity, reason, staff)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun writeOffDamagedStock(productId: Long, damagedBaseQty: Double, reason: String) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم شطب البضاعة التالفة وتسجيل القيد المحاسبي") {
                repo.writeOffDamagedStock(productId, damagedBaseQty, reason, staff)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun submitStockCount(productId: Long, countedQty: Double, notes: String) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم تسجيل الجرد الفعلي وبانتظار اعتماد المدير") {
                repo.submitStockCount(productId, countedQty, notes, staff)
            }
        }
    }

    fun approveStockCount(stockCountId: Long) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم اعتماد تسوية الجرد وتحديث المخزون") {
                repo.approveStockCount(stockCountId, staff)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    // Parties & Debts
    fun saveParty(
        partyId: Long?,
        partyType: String,
        name: String,
        phone: String,
        address: String = "",
        openingBalance: Double = 0.0,
        notes: String = ""
    ) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم حفظ البيانات بنجاح") {
                repo.saveParty(partyId, partyType, name, phone, staff, address, openingBalance, notes)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun writeOffBadDebt(partyId: Long, amount: Double, reason: String) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم شطب الدين المعدوم وترحيل القيد المحاسبي") {
                repo.writeOffBadDebt(partyId, amount, reason, staff)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    // Sales, Purchases & Returns
    fun postSaleInvoice(
        lines: List<InvoiceLineDraft>,
        invoiceDiscount: Double,
        paymentMethod: String,
        paidAmount: Double,
        partyId: Long?,
        customerName: String,
        customerPhone: String,
        notes: String,
        dueDateMs: Long?,
        accountId: Long = 1L,
        saleDateMs: Long = System.currentTimeMillis(),
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val staff = requireActiveStaff(onError) ?: return
        viewModelScope.launch {
            runAction("تم اعتماد فاتورة البيع وتحديث المخزون والصندوق", onError = onError) {
                repo.postSaleInvoice(
                    lines = lines,
                    invoiceDiscount = invoiceDiscount,
                    paymentMethod = paymentMethod,
                    paidAmountInput = paidAmount,
                    partyId = partyId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    notes = notes,
                    dueDateMs = dueDateMs,
                    actingStaff = staff,
                    accountId = accountId,
                    saleDateMs = saleDateMs
                )
                onSuccess()
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun editSaleInvoice(
        invoiceId: Long,
        lines: List<InvoiceLineDraft>,
        invoiceDiscount: Double,
        paymentMethod: String,
        paidAmount: Double,
        customerName: String,
        customerPhone: String,
        notes: String,
        dueDateMs: Long?,
        saleDateMs: Long = 0L,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val staff = requireActiveStaff(onError) ?: return
        if (!staff.canCancelInvoices) {
            val msg = "صلاحية تعديل الفواتير المعتمدة للمدير والمحاسب فقط"
            emitError(msg)
            onError(msg)
            return
        }
        viewModelScope.launch {
            runAction("تم تعديل الفاتورة وتحديث القيود المحاسبية", onError = onError) {
                repo.editSaleInvoice(
                    invoiceId = invoiceId,
                    newLines = lines,
                    newDiscount = invoiceDiscount,
                    newPaymentMethod = paymentMethod,
                    newPaidAmount = paidAmount,
                    newCustomerName = customerName,
                    newCustomerPhone = customerPhone,
                    newNotes = notes,
                    newDueDateMs = dueDateMs,
                    actingStaff = staff,
                    newSaleDateMs = saleDateMs
                )
                onSuccess()
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun cancelInvoice(invoiceId: Long, reason: String) {
        val staff = requireActiveStaff() ?: return
        if (!staff.canCancelInvoices) {
            emitError("ليس لديك صلاحية إلغاء الفواتير")
            return
        }
        viewModelScope.launch {
            runAction("تم إلغاء الفاتورة بقيد عكسي وتصحيح المخزون والصندوق") {
                repo.cancelInvoice(invoiceId, reason.ifBlank { "إلغاء فاتورة" }, staff)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun postPurchaseInvoice(
        lines: List<InvoiceLineDraft>,
        invoiceDiscount: Double,
        paymentMethod: String,
        paidAmount: Double,
        partyId: Long?,
        supplierName: String,
        supplierPhone: String,
        notes: String,
        dueDateMs: Long?,
        purchaseDateMs: Long,
        extraCosts: Double = 0.0,
        accountId: Long = 1L,
        sourceOcrHash: String = "",
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val staff = requireActiveStaff(onError) ?: return
        viewModelScope.launch {
            runAction("تم اعتماد فاتورة الشراء وتحديث متوسط التكلفة والمخزون", onError = onError) {
                repo.postPurchaseInvoice(
                    lines, invoiceDiscount, paymentMethod, paidAmount, partyId,
                    supplierName, supplierPhone, notes, dueDateMs, purchaseDateMs, staff,
                    extraCosts, accountId, sourceOcrHash
                )
                onSuccess()
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun postSaleReturn(
        originalInvoiceId: Long,
        productId: Long,
        unitName: String,
        conversionFactor: Double,
        returnedQty: Double,
        refundCashAmount: Double,
        reason: String,
        accountId: Long = 1L
    ) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم اعتماد مرتجع المبيعات وتحديث المخزون والصندوق") {
                repo.postSaleReturn(originalInvoiceId, productId, unitName, conversionFactor, returnedQty, refundCashAmount, reason, staff, accountId)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun postPurchaseReturn(
        originalInvoiceId: Long,
        productId: Long,
        unitName: String,
        conversionFactor: Double,
        returnedQty: Double,
        refundCashAmount: Double,
        reason: String,
        accountId: Long = 1L
    ) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم اعتماد مرتجع المشتريات وتحديث الذمة والمخزون") {
                repo.postPurchaseReturn(originalInvoiceId, productId, unitName, conversionFactor, returnedQty, refundCashAmount, reason, staff, accountId)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    // Cash Accounts, Expenses, Closings & Periods
    fun recordExpense(
        category: String,
        amount: Double,
        description: String,
        paymentMethod: String,
        expenseDateMs: Long,
        receiptImageUri: String = "",
        accountId: Long = 1L
    ) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم تسجيل المصروف بنجاح") {
                repo.recordExpense(category, amount, description, paymentMethod, expenseDateMs, staff, receiptImageUri, accountId)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun recordCashMovement(txType: String, amount: Double, description: String, accountId: Long = 1L) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم ترحيل الحركة المالية للصندوق") {
                repo.recordCashMovement(txType, amount, description, staff, accountId)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun saveCashAccount(name: String, accountType: String) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم إضافة الحساب المالي بنجاح") { repo.saveCashAccount(name, accountType, staff) }
        }
    }

    fun transferBetweenCashAccounts(fromAccountId: Long, toAccountId: Long, amount: Double, notes: String) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم تنفيذ التحويل بين الحسابات") {
                repo.transferBetweenCashAccounts(fromAccountId, toAccountId, amount, notes, staff)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun closeCashboxDay(accountId: Long, actualCountedBalance: Double, notes: String, lockPeriod: Boolean) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم إغلاق الصندوق اليومي وترحيل فروقات التسوية") {
                repo.closeCashboxDay(accountId, actualCountedBalance, notes, lockPeriod, staff)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun createOrCloseAccountingPeriod(name: String, startMs: Long, endMs: Long, closeNow: Boolean) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction(if (closeNow) "تم إقفال الفترة المحاسبية" else "تم فتح الفترة المحاسبية") {
                repo.createOrCloseAccountingPeriod(name, startMs, endMs, closeNow, staff)
            }
        }
    }

    fun recordDebtPayment(partyId: Long, amount: Double, notes: String, accountId: Long = 1L) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم تسجيل الدفعة وتحديث الذمة المالية والصندوق") {
                repo.recordDebtPayment(partyId, amount, notes, staff, accountId)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun updateStoreSettings(settings: StoreSettingsEntity) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم حفظ إعدادات المتجر") {
                repo.updateStoreSettings(settings, staff)
                launch { autoSyncIfEnabled() }
            }
        }
    }

    fun saveStaffUser(
        existingId: Long?,
        name: String,
        role: String,
        pin: String?,
        canViewProfits: Boolean,
        canCancelInvoices: Boolean,
        canManageInventory: Boolean,
        canManageExpenses: Boolean,
        canManageSettings: Boolean,
        isActive: Boolean,
        canEditPrices: Boolean = role == StaffRole.OWNER || role == StaffRole.ACCOUNTANT,
        canWithdrawDeposit: Boolean = role == StaffRole.OWNER || role == StaffRole.ACCOUNTANT,
        canApproveStocktake: Boolean = role == StaffRole.OWNER
    ) {
        val staff = requireActiveStaff() ?: return
        viewModelScope.launch {
            runAction("تم تحديث بيانات الموظف والصلاحيات") {
                repo.addOrUpdateStaffUser(
                    existingId, name, role, pin,
                    canViewProfits, canCancelInvoices, canManageInventory,
                    canManageExpenses, canManageSettings, isActive, staff,
                    canEditPrices, canWithdrawDeposit, canApproveStocktake
                )
                launch { autoSyncIfEnabled() }
            }
        }
    }

    // Barcode Scanner & Purchase OCR
    fun scanBarcodeFromImageUri(context: Context, uri: Uri, onBarcodeFound: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val (bmp, _) = InvoiceOcrParser.loadBitmapFromUri(context, uri)
                if (bmp == null) {
                    _messages.emit(UiMessage.Error("تعذر قراءة الصورة لقراءة الباركود"))
                    return@launch
                }
                val codes = InvoiceOcrParser.scanBarcodesFromBitmap(bmp)
                if (codes.isEmpty()) {
                    _messages.emit(UiMessage.Error("لم يتم العثور على باركود واضح في الصورة"))
                } else {
                    onBarcodeFound(codes.first())
                }
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر مسح الباركود"))
            }
        }
    }

    fun parsePurchaseOcrUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isOcrParsing.value = true
            try {
                val draft = InvoiceOcrParser.parsePurchaseInvoiceUri(
                    context = context,
                    uri = uri,
                    existingProducts = uiState.value.products,
                    groqService = groqAiService
                )
                _ocrDraft.value = draft
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر تحليل ملف الفاتورة"))
            } finally {
                _isOcrParsing.value = false
            }
        }
    }

    fun clearOcrDraft() {
        _ocrDraft.value = null
    }

    fun exportBarcodeLabelsPdf(context: Context, products: List<ProductEntity>) {
        if (products.isEmpty()) {
            emitError("لا توجد أصناف لطباعة ملصقات الباركود")
            return
        }
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    BarcodeLabelHelper.generateLabelsPdfFile(context, products, uiState.value.settings)
                }
                ExportAndShareHelper.shareFile(context, file, "application/pdf", "مشاركة ملصقات الباركود PDF")
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر إنشاء ملف الباركود"))
            }
        }
    }

    // Cloud Sync & Backups
    fun syncCloudNow() {
        if (Firebase.auth.currentUser == null) {
            emitError("المزامنة السحابية تتطلب تسجيل الدخول بحساب Google أولاً")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val res = syncNowInternal()
                _messages.emit(
                    UiMessage.Info("تمت المزامنة السحابية: ${res.syncedInvoices} فواتير، ${res.syncedProducts} أصناف، ${res.syncedLedgerEntries} قيود")
                )
            } catch (e: Exception) {
                Log.w("MamoSync", "Sync failed: ${e.message}", e)
                _messages.emit(UiMessage.Error("تعذر المزامنة الآن (العمل محفوظ محلياً): ${e.localizedMessage}"))
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun syncNowInternal(): CloudSyncSummary {
        val res = syncRepo.syncAllUnsyncedFromRoom(repo.dao, repo.uDao).getOrThrow()
        _lastSyncSummary.value = res
        return res
    }

    private suspend fun autoSyncIfEnabled() {
        if (uiState.value.settings.autoBackupEnabled && Firebase.auth.currentUser != null) {
            try {
                syncNowInternal()
            } catch (e: Exception) {
                Log.w("MamoSync", "Deferred offline sync pending: ${e.message}")
            }
        }
    }

    fun exportJsonToUri(
        context: Context,
        uri: Uri,
        passphrase: String? = null,
        compressGzip: Boolean = false
    ) {
        val staff = uiState.value.activeStaff
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    if (staff != null) {
                        repo.exportBackupBytes(compressGzip, passphrase, staff).first
                    } else {
                        val plain = repo.exportJsonBackup().toByteArray(Charsets.UTF_8)
                        if (compressGzip) AccountingEngine.compressGzip(plain) else plain
                    }
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    } ?: error("تعذر فتح ملف التصدير")
                }
                val isEnc = !passphrase.isNullOrBlank() && passphrase.trim().length >= 4
                val infoMsg = when {
                    compressGzip && isEnc -> "تم تصدير النسخة المضغوطة GZIP والمشفرة AES-256 بنجاح"
                    compressGzip -> "تم تصدير النسخة الاحتياطية المضغوطة GZIP بنجاح"
                    isEnc -> "تم تصدير النسخة الاحتياطية المشفرة AES-256 بنجاح"
                    else -> "تم تصدير النسخة الاحتياطية JSON بنجاح"
                }
                _messages.emit(UiMessage.Info(infoMsg))
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "فشل تصدير النسخة الاحتياطية"))
            }
        }
    }

    fun importJsonFromUri(context: Context, uri: Uri, passphrase: String? = null) {
        val staff = uiState.value.activeStaff ?: return
        if (!staff.canManageSettings) {
            emitError("استعادة النسخة الاحتياطية متاحة للإدارة فقط")
            return
        }
        viewModelScope.launch {
            try {
                val rawBytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("تعذر قراءة الملف")
                }
                repo.importBackupBytes(rawBytes, passphrase, staff)
                scheduleInstantDeviceSave("استعادة نسخة احتياطية")
                _messages.emit(UiMessage.Info("تمت استعادة النسخة الاحتياطية والتحقق من سلامتها بنجاح"))
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "ملف النسخة الاحتياطية غير صالح"))
            }
        }
    }

    fun refreshDeviceBackupStatus(context: Context) {
        val current = _deviceBackupStatus.value
        _deviceBackupStatus.value = DeviceLocalBackupManager.inspectStatus(
            context = context,
            sharedDocumentsSaved = current.sharedDocumentsSaved,
            lastTriggerReason = current.lastTriggerReason
        )
    }

    fun backupNowToDeviceStorage(context: Context) {
        val staff = uiState.value.activeStaff
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = autoPersistMutex.withLock {
                    DeviceLocalBackupManager.persistInstantDeviceSnapshot(
                        context = context,
                        repo = repo,
                        triggerReason = "نسخ يدوي فوري ل ذاكرة الجهاز"
                    )
                }
                _deviceBackupStatus.value = status
                if (staff != null) {
                    val plain = repo.exportJsonBackup()
                    val checksum = AccountingEngine.sha256Hex(plain)
                    repo.recordAutoBackupResult(
                        fileName = "mamo_device_snapshot.mamo.gz",
                        isEncrypted = false,
                        checksumSha256 = checksum,
                        recordCount = uiState.value.products.size + uiState.value.invoices.size,
                        status = "SUCCESS",
                        notes = status.locationLabel,
                        createdBy = staff.name
                    )
                }
                val extra = if (status.sharedDocumentsSaved) " ومجلد المستندات" else ""
                _messages.emit(
                    UiMessage.Info("تم حفظ جميع المبيعات والفواتير في ذاكرة الجهاز${extra} (${status.internalSnapshotBytes} بايت)")
                )
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر الحفظ الفوري في ذاكرة الجهاز"))
            }
        }
    }

    fun restoreFromDeviceSnapshot(context: Context) {
        val staff = uiState.value.activeStaff ?: return
        if (!staff.canManageSettings) {
            emitError("استعادة النسخة الاحتياطية متاحة للإدارة فقط")
            return
        }
        viewModelScope.launch {
            try {
                val bytes = DeviceLocalBackupManager.readLatestInternalSnapshotBytes(context)
                    ?: throw IllegalStateException("لا توجد نسخة محفوظة في ذاكرة التطبيق حالياً")
                repo.importBackupBytes(bytes, null, staff)
                refreshDeviceBackupStatus(context)
                _messages.emit(UiMessage.Info("تمت استعادة المبيعات والفواتير والبيانات من ذاكرة الجهاز بنجاح"))
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر استعادة النسخة من ذاكرة الجهاز"))
            }
        }
    }

    private fun scheduleInstantDeviceSave(reason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.checkpointDatabaseWal() }
            val ctx = appContext ?: return@launch
            runCatching {
                autoPersistMutex.withLock {
                    _deviceBackupStatus.value = DeviceLocalBackupManager.persistInstantDeviceSnapshot(
                        context = ctx,
                        repo = repo,
                        triggerReason = reason
                    )
                }
            }
        }
    }

    fun runDailyAutoBackupCheck(context: Context) {
        val settings = uiState.value.settings
        val staff = uiState.value.activeStaff ?: return
        if (!settings.autoBackupEnabled) return
        val intervalMs = settings.autoBackupIntervalHours.coerceAtLeast(1) * 3_600_000L
        val now = System.currentTimeMillis()
        if (now - settings.lastAutoBackupAtMs < intervalMs) {
            refreshDeviceBackupStatus(context)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = autoPersistMutex.withLock {
                    DeviceLocalBackupManager.persistInstantDeviceSnapshot(
                        context = context,
                        repo = repo,
                        triggerReason = "نسخ تلقائي يومي في ذاكرة الجهاز"
                    )
                }
                _deviceBackupStatus.value = status
                val plain = repo.exportJsonBackup()
                val checksum = AccountingEngine.sha256Hex(plain)
                repo.recordAutoBackupResult(
                    fileName = "mamo_device_snapshot.mamo.gz",
                    isEncrypted = false,
                    checksumSha256 = checksum,
                    recordCount = uiState.value.products.size + uiState.value.invoices.size,
                    status = "SUCCESS",
                    notes = "نسخة مضغوطة تلقائية محفوظة في ذاكرة الجهاز",
                    createdBy = staff.name
                )
            } catch (e: Exception) {
                repo.recordAutoBackupResult("auto_failed.mamo.gz", false, "", 0, "FAILED", e.localizedMessage.orEmpty(), staff.name)
            }
        }
    }

    // DB Admin Dashboard Actions
    private fun requireEmailAdmin(): StaffUserEntity? {
        val email = Firebase.auth.currentUser?.email
        val staff = uiState.value.activeStaff
        if (email.isNullOrBlank() || staff?.role != StaffRole.OWNER) {
            emitError("لوحة تحكم قاعدة البيانات متاحة فقط لمدير النظام المسجل بالبريد الإلكتروني")
            return null
        }
        return staff
    }

    fun runDatabaseIntegrityCheck() {
        val admin = requireEmailAdmin() ?: return
        viewModelScope.launch {
            _isDbBusy.value = true
            try {
                val report = repo.runDatabaseIntegrityReport(admin)
                _integrityReport.value = report
                _messages.emit(UiMessage.Info("تم إتمام فحص سلامة وتطابق قاعدة البيانات"))
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر فحص قاعدة البيانات"))
            } finally {
                _isDbBusy.value = false
            }
        }
    }

    fun refreshCloudDatabaseSnapshot() {
        requireEmailAdmin() ?: return
        viewModelScope.launch {
            _isDbBusy.value = true
            try {
                val snap = syncRepo.fetchCloudDatabaseSnapshot().getOrThrow()
                _cloudSnapshot.value = snap
                _messages.emit(
                    UiMessage.Info("بيانات السحابة: ${snap.products.size} أصناف، ${snap.invoices.size} فواتير، ${snap.ledgerEntries.size} قيود")
                )
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error("تعذر جلب بيانات السحابة: ${e.localizedMessage}"))
            } finally {
                _isDbBusy.value = false
            }
        }
    }

    fun forceFullCloudResync() {
        val admin = requireEmailAdmin() ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            _isDbBusy.value = true
            try {
                repo.markAllRecordsForResync(admin)
                val res = syncNowInternal()
                _cloudSnapshot.value = syncRepo.fetchCloudDatabaseSnapshot().getOrNull()
                _messages.emit(
                    UiMessage.Info("تمت المزامنة الشاملة: ${res.syncedProducts} أصناف، ${res.syncedParties} أطراف، ${res.syncedInvoices} فواتير، ${res.syncedLedgerEntries} قيود")
                )
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error("تعذر إتمام المزامنة الشاملة: ${e.localizedMessage}"))
            } finally {
                _isSyncing.value = false
                _isDbBusy.value = false
            }
        }
    }

    fun restoreMissingFromCloud() {
        val admin = requireEmailAdmin() ?: return
        viewModelScope.launch {
            _isDbBusy.value = true
            try {
                val snap = syncRepo.fetchCloudDatabaseSnapshot().getOrThrow()
                _cloudSnapshot.value = snap
                val (addedProducts, addedParties) = repo.mergeMissingFromCloud(snap.products, snap.parties, admin)
                _messages.emit(UiMessage.Info("تم استيراد $addedProducts أصناف و $addedParties أطراف من السحابة"))
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error("تعذر الاستيراد من السحابة: ${e.localizedMessage}"))
            } finally {
                _isDbBusy.value = false
            }
        }
    }

    fun toggleProductArchived(productId: Long, archived: Boolean) {
        val admin = requireEmailAdmin() ?: return
        viewModelScope.launch {
            runAction(if (archived) "تمت أرشفة الصنف" else "تمت استعادة الصنف") {
                repo.setProductArchived(productId, archived, admin)
                autoSyncIfEnabled()
            }
        }
    }

    fun adminUpdateStaffStatusAndPin(staffId: Long, newPin: String?, isActive: Boolean) {
        val admin = requireEmailAdmin() ?: return
        viewModelScope.launch {
            runAction("تم تحديث حالة وصلاحية الموظف") {
                repo.adminUpdateStaffStatusAndPin(staffId, newPin, isActive, admin)
                autoSyncIfEnabled()
            }
        }
    }

    fun resetTransactionalDatabase(adminPin: String, onSuccess: () -> Unit = {}) {
        val admin = requireEmailAdmin() ?: return
        viewModelScope.launch {
            _isDbBusy.value = true
            try {
                repo.resetTransactionalDatabase(adminPin, admin)
                _integrityReport.value = repo.runDatabaseIntegrityReport(admin)
                _messages.emit(UiMessage.Info("تم تصفير قاعدة البيانات التشغيلية بنجاح"))
                onSuccess()
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "فشل تصفير قاعدة البيانات"))
            } finally {
                _isDbBusy.value = false
            }
        }
    }

    fun refreshDatabaseStorageInfo(context: Context) {
        viewModelScope.launch {
            runCatching { repo.measureDatabaseSize(context) }
                .onSuccess { _dbStorageInfo.value = it }
        }
    }

    fun compactAndPruneDatabase(context: Context, retentionDays: Int = 60) {
        val admin = requireEmailAdmin() ?: return
        viewModelScope.launch {
            _isDbBusy.value = true
            try {
                val report = repo.compactAndPruneDatabase(context, retentionDays, admin)
                _compressionReport.value = report
                _dbStorageInfo.value = repo.measureDatabaseSize(context)
                _messages.emit(
                    UiMessage.Info("تم ضغط القاعدة (VACUUM) وتنظيف ${report.totalPrunedRecords} سجل قديم وتوفير ${report.savedBytes} بايت (${report.savedPercent}%)")
                )
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر ضغط قاعدة البيانات"))
            } finally {
                _isDbBusy.value = false
            }
        }
    }

    fun deleteCloudData(
        adminPin: String,
        collections: Set<CloudCollectionKind>,
        wipeProfile: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        val admin = requireEmailAdmin() ?: return
        if (collections.isEmpty() && !wipeProfile) {
            emitError("اختر قسماً واحداً على الأقل للحذف من السحابة")
            return
        }
        viewModelScope.launch {
            _isDbBusy.value = true
            try {
                repo.verifyStaffPin(admin.id, adminPin)
                    ?: throw IllegalArgumentException("رمز المرور PIN للأدمن غير صحيح")
                val result = syncRepo.deleteCloudData(collections, wipeProfile).getOrThrow()
                repo.resetSyncedFlagsForDeletedCloudCollections(
                    collections = collections,
                    wipeProfile = wipeProfile,
                    deletedDocsCount = result.totalDeletedDocs,
                    actingStaff = admin
                )
                _cloudDeleteResult.value = result
                _cloudSnapshot.value = syncRepo.fetchCloudDatabaseSnapshot().getOrNull()
                _messages.emit(
                    UiMessage.Info("تم حذف ${result.totalDeletedDocs} سجل من سحابة Firebase بنجاح")
                )
                onSuccess()
            } catch (e: Exception) {
                _messages.emit(UiMessage.Error(e.localizedMessage ?: "تعذر حذف البيانات من سحابة Firebase"))
            } finally {
                _isDbBusy.value = false
            }
        }
    }

    // Delegated Report & Chart Calculators
    fun buildSalesChartSeries(range: ChartRange): List<ChartPoint> =
        MamoReportCalculators.buildSalesChartSeries(uiState.value, range)

    fun buildPurchasesChartSeries(range: ChartRange): List<ChartPoint> =
        MamoReportCalculators.buildPurchasesChartSeries(uiState.value, range)

    fun buildExpensesVsRevenueSeries(range: ChartRange): List<ChartPoint> =
        MamoReportCalculators.buildExpensesVsRevenueSeries(uiState.value, range)

    fun getTopSellingProducts(limit: Int = 5): List<ProductProfitabilityRow> =
        MamoReportCalculators.getTopSellingProducts(uiState.value, limit)

    fun computeProfitLoss(period: PeriodFilter): ProfitLossSummary =
        MamoReportCalculators.computeProfitLoss(uiState.value, returns.value, period)

    fun computeCapitalSummary(): CapitalSummary =
        MamoReportCalculators.computeCapitalSummary(uiState.value)

    fun computeBalanceSheet(): BalanceSheetSummary =
        MamoReportCalculators.computeBalanceSheet(uiState.value)

    fun computeCashFlow(period: PeriodFilter): CashFlowSummary =
        MamoReportCalculators.computeCashFlow(uiState.value, period)

    fun buildPartyStatement(party: PartyEntity): PartyStatement =
        MamoReportCalculators.buildPartyStatement(uiState.value, party)

    fun exportPdfReport(context: Context, title: String, periodLabel: String, rows: List<Pair<String, String>>) {
        val file = ExportAndShareHelper.generateReportPdfFile(context, title, periodLabel, rows, uiState.value.settings)
        ExportAndShareHelper.shareFile(context, file, "application/pdf", "مشاركة التقرير PDF")
    }

    fun exportCsvReport(context: Context, prefix: String, headers: List<String>, rows: List<List<String>>) {
        val file = ExportAndShareHelper.generateCsvFile(context, prefix, headers, rows)
        ExportAndShareHelper.shareFile(context, file, "text/csv", "تصدير إلى Excel")
    }

    // MAMO AI (Groq GPT + Grounded Offline Intelligence + Audit Logs)
    fun setSelectedAiModel(modelId: String) {
        if (GroqAiService.SUPPORTED_MODELS.any { it.id == modelId }) {
            _selectedAiModel.value = modelId
        }
    }

    fun clearAiChat() {
        _aiMessages.value = emptyList()
    }

    fun askGroqAi(question: String) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank() || _isAiLoading.value) return
        val modelId = _selectedAiModel.value
        val previousHistory = _aiMessages.value
        val staff = uiState.value.activeStaff
        val canViewProfits = staff?.canViewProfits ?: false
        val userMsg = AiChatMessage(isUser = true, content = cleanQuestion, modelId = modelId)
        _aiMessages.value = previousHistory + userMsg
        _isAiLoading.value = true

        viewModelScope.launch {
            val topProducts = getTopSellingProducts(5)
            val systemPrompt = MamoAiTools.buildGroundedSystemPrompt(uiState.value, canViewProfits, topProducts)

            val replyContent: String
            var usedOffline = false
            if (!groqAiService.hasConfiguredKey()) {
                usedOffline = true
                replyContent = MamoAiTools.generateOfflineAnswer(cleanQuestion, uiState.value, canViewProfits, topProducts)
            } else {
                when (val result = groqAiService.sendChatCompletion(modelId, systemPrompt, previousHistory, cleanQuestion)) {
                    is GroqAiResult.Success -> {
                        replyContent = result.reply
                    }
                    is GroqAiResult.Failure -> {
                        usedOffline = true
                        val offline = MamoAiTools.generateOfflineAnswer(cleanQuestion, uiState.value, canViewProfits, topProducts)
                        replyContent = "$offline\n\n(ملاحظة الاتصال السحابي: ${result.errorMessage})"
                    }
                }
            }

            _aiMessages.value = _aiMessages.value + AiChatMessage(
                isUser = false,
                content = replyContent,
                modelId = if (usedOffline) "MAMO-Offline" else modelId,
                isError = false
            )
            runCatching {
                repo.saveAiLog(
                    staffName = staff?.name ?: "زائر",
                    modelId = if (usedOffline) "MAMO-Offline" else modelId,
                    prompt = cleanQuestion,
                    responseSummary = replyContent,
                    wasOfflineFallback = usedOffline
                )
            }
            _isAiLoading.value = false
        }
    }

    private fun requireActiveStaff(onError: ((String) -> Unit)? = null): StaffUserEntity? {
        val staff = uiState.value.activeStaff
        if (staff == null) {
            val msg = "وضع الزائر للتصفح فقط — يرجى تسجيل الدخول بحساب موظف أو مدير لإتمام العملية"
            emitError(msg)
            onError?.invoke(msg)
        }
        return staff
    }

    private fun emitError(msg: String) {
        viewModelScope.launch { _messages.emit(UiMessage.Error(msg)) }
    }

    private suspend fun runAction(
        successMessage: String,
        onError: ((String) -> Unit)? = null,
        block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
    ) {
        try {
            kotlinx.coroutines.coroutineScope { block() }
            scheduleInstantDeviceSave(successMessage)
            _messages.emit(UiMessage.Info(successMessage))
        } catch (e: Exception) {
            val errorText = e.localizedMessage ?: "حدث خطأ غير متوقع"
            onError?.invoke(errorText)
            _messages.emit(UiMessage.Error(errorText))
        }
    }

    companion object {
        fun provideFactory(context: Context, currentUserId: String): ViewModelProvider.Factory {
            val appCtx = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val deps = MamoStartupContainer.getOrInitBlocking(appCtx)
                    return MamoViewModel(deps.repo, deps.syncRepo, deps.prefsRepo, currentUserId, appCtx) as T
                }
            }
        }
    }

    private data class CorePart1(
        val settings: StoreSettingsEntity,
        val staffUsers: List<StaffUserEntity>,
        val activeStaffId: Long?,
        val isDark: Boolean,
        val products: List<ProductEntity>
    )

    private data class CorePart2(
        val parties: List<PartyEntity>,
        val invoices: List<InvoiceWithItems>,
        val stockMovements: List<StockMovementEntity>,
        val cashTransactions: List<CashTransactionEntity>,
        val cashboxBalance: Double
    )

    private data class CorePart3(
        val expenses: List<ExpenseEntity>,
        val debtPayments: List<DebtPaymentEntity>,
        val auditLogs: List<AuditLogEntity>,
        val chartRange: ChartRange,
        val isSyncing: Boolean
    )
}

data class MamoAppDependencies(
    val auth: FirebaseAuth,
    val credentialManager: CredentialManager,
    val repo: AccountingRepository,
    val syncRepo: FirestoreSyncRepository,
    val prefsRepo: UserPreferencesRepository,
    val initialDarkTheme: Boolean
)

object MamoStartupContainer {
    @Volatile
    private var cached: MamoAppDependencies? = null

    val isReady: Boolean
        get() = cached != null

    fun currentOrNull(): MamoAppDependencies? = cached

    suspend fun warmUp(context: Context): MamoAppDependencies {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            getOrInitBlocking(context)
        }
    }

    fun getOrInitBlocking(context: Context): MamoAppDependencies {
        cached?.let { return it }
        return synchronized(this) {
            cached?.let { return it }
            val appContext = context.applicationContext
            runCatching { FirebaseApp.initializeApp(appContext) }
            val auth = Firebase.auth
            val db = MamoDatabase.getInstance(appContext)
            runCatching { db.openHelper.writableDatabase }
            val repo = AccountingRepository(db)
            val databaseId = appContext.getString(R.string.firestore_database_id)
            val firestore = FirebaseFirestore.getInstance(databaseId)
            val syncRepo = FirestoreSyncRepository(firestore, auth)
            val prefsRepo = UserPreferencesRepository(appContext)
            val initialDark = kotlinx.coroutines.runBlocking {
                runCatching { prefsRepo.isDarkTheme.first() }.getOrDefault(false)
            }
            val credentialManager = CredentialManager.create(appContext)
            MamoAppDependencies(
                auth = auth,
                credentialManager = credentialManager,
                repo = repo,
                syncRepo = syncRepo,
                prefsRepo = prefsRepo,
                initialDarkTheme = initialDark
            ).also { cached = it }
        }
    }
}
