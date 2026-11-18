package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AccountingDao
import com.example.data.local.AccountingPeriodEntity
import com.example.data.local.AiLogEntity
import com.example.data.local.ApprovalStatus
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupRecordEntity
import com.example.data.local.CashAccountEntity
import com.example.data.local.CashAccountType
import com.example.data.local.CashTransactionEntity
import com.example.data.local.CashTxType
import com.example.data.local.CashboxClosingEntity
import com.example.data.local.ChartAccounts
import com.example.data.local.CostingMethod
import com.example.data.local.CustomerSpecialPriceEntity
import com.example.data.local.DebtPaymentEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.InvoiceItemEntity
import com.example.data.local.InvoiceStatus
import com.example.data.local.InvoiceType
import com.example.data.local.InvoiceWithItems
import com.example.data.local.JournalEntryWithLines
import com.example.data.local.MamoDatabase
import com.example.data.local.MeasureUnit
import com.example.data.local.MovementType
import com.example.data.local.PartyEntity
import com.example.data.local.PartyType
import com.example.data.local.PaymentMethod
import com.example.data.local.PricingTier
import com.example.data.local.ProductDao
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
import com.example.data.local.TransactionDao
import com.example.data.local.UltimateDao
import com.example.data.local.UserDao
import com.example.data.remote.CloudCollectionKind
import com.example.data.remote.SyncedPartyDoc
import com.example.data.remote.SyncedProductDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

class AccountingRepository(private val db: MamoDatabase) {
    val dao: AccountingDao = db.accountingDao()
    val uDao: UltimateDao = db.ultimateDao()
    val productDao: ProductDao = db.productDao()
    val transactionDao: TransactionDao = db.transactionDao()
    val userDao: UserDao = db.userDao()
    private val ultimate = UltimateAccountingService(db, dao, uDao)

    val storeSettings: Flow<StoreSettingsEntity?> = dao.observeStoreSettings()
    val staffUsers: Flow<List<StaffUserEntity>> = userDao.observeAll()
    val products: Flow<List<ProductEntity>> = productDao.observeActive()
    val allProductsForAdmin: Flow<List<ProductEntity>> = productDao.observeAll()
    val parties: Flow<List<PartyEntity>> = dao.observeParties()
    val invoices: Flow<List<InvoiceWithItems>> = dao.observeInvoicesWithItems()
    val stockMovements: Flow<List<StockMovementEntity>> = dao.observeStockMovements()
    val cashTransactions: Flow<List<CashTransactionEntity>> = transactionDao.observeAll()
    val cashboxBalance: Flow<Double> = transactionDao.observeBalance()
    val expenses: Flow<List<ExpenseEntity>> = dao.observeExpenses()
    val debtPayments: Flow<List<DebtPaymentEntity>> = dao.observeDebtPayments()
    val auditLogs: Flow<List<AuditLogEntity>> = dao.observeAuditLogs()

    // Ultimate Flows
    val productUnits: Flow<List<ProductUnitEntity>> = uDao.observeAllProductUnits()
    val customerSpecialPrices: Flow<List<CustomerSpecialPriceEntity>> = uDao.observeCustomerSpecialPrices()
    val priceHistory: Flow<List<ProductPriceHistoryEntity>> = uDao.observePriceHistory()
    val cashAccounts: Flow<List<CashAccountEntity>> = uDao.observeCashAccounts()
    val cashboxClosings: Flow<List<CashboxClosingEntity>> = uDao.observeCashboxClosings()
    val journalEntries: Flow<List<JournalEntryWithLines>> = uDao.observeJournalEntries()
    val stockCounts: Flow<List<StockCountEntity>> = uDao.observeStockCounts()
    val returns: Flow<List<ReturnEntity>> = uDao.observeReturns()
    val accountingPeriods: Flow<List<AccountingPeriodEntity>> = uDao.observeAccountingPeriods()
    val syncQueue: Flow<List<SyncQueueEntity>> = uDao.observeSyncQueue()
    val backups: Flow<List<BackupRecordEntity>> = uDao.observeBackups()
    val aiLogs: Flow<List<AiLogEntity>> = uDao.observeAiLogs()

    // Core Entity CRUD Operations (Product, Transaction, User)
    fun observeProductById(id: Long): Flow<ProductEntity?> = productDao.observeById(id)
    suspend fun getProductById(id: Long): ProductEntity? = productDao.getById(id)
    suspend fun getAllProducts(): List<ProductEntity> = productDao.getAll()
    suspend fun insertProduct(product: ProductEntity): Long = productDao.insert(product)
    suspend fun updateProduct(product: ProductEntity): Int = productDao.update(product)
    suspend fun deleteProduct(product: ProductEntity): Int = productDao.delete(product)
    suspend fun deleteProductById(id: Long): Int = productDao.deleteById(id)

    fun observeTransactionById(id: Long): Flow<CashTransactionEntity?> = transactionDao.observeById(id)
    suspend fun getTransactionById(id: Long): CashTransactionEntity? = transactionDao.getById(id)
    suspend fun getAllTransactions(): List<CashTransactionEntity> = transactionDao.getAll()
    suspend fun insertTransaction(transaction: CashTransactionEntity): Long = transactionDao.insert(transaction)
    suspend fun updateTransaction(transaction: CashTransactionEntity): Int = transactionDao.update(transaction)
    suspend fun deleteTransaction(transaction: CashTransactionEntity): Int = transactionDao.delete(transaction)
    suspend fun deleteTransactionById(id: Long): Int = transactionDao.deleteById(id)

    fun observeUserById(id: Long): Flow<StaffUserEntity?> = userDao.observeById(id)
    suspend fun getUserById(id: Long): StaffUserEntity? = userDao.getById(id)
    suspend fun getAllUsers(): List<StaffUserEntity> = userDao.getAll()
    suspend fun insertUser(user: StaffUserEntity): Long = userDao.insert(user)
    suspend fun updateUser(user: StaffUserEntity): Int = userDao.update(user)
    suspend fun deleteUser(user: StaffUserEntity): Int = userDao.delete(user)
    suspend fun deleteUserById(id: Long): Int = userDao.deleteById(id)

    suspend fun ensureDefaultStoreSettings() {
        db.withTransaction {
            if (dao.getStoreSettings() == null) {
                dao.upsertStoreSettings(StoreSettingsEntity())
            }
            ultimate.ensureDefaultCashAccount()
        }
    }

    suspend fun updateStoreSettings(settings: StoreSettingsEntity, actingStaff: StaffUserEntity) {
        require(actingStaff.canManageSettings || actingStaff.role == StaffRole.OWNER) {
            "تعديل إعدادات المتجر متاح للإدارة فقط"
        }
        db.withTransaction {
            dao.upsertStoreSettings(settings.copy(id = 1, updatedAtMs = System.currentTimeMillis()))
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "تحديث إعدادات المتجر",
                    entityType = "SETTINGS",
                    entityId = "1",
                    details = "المتجر: ${settings.storeName} | العملة: ${settings.currencyCode} | الضريبة: ${settings.taxEnabled} (${settings.taxRatePercent}%)",
                    staffName = actingStaff.name
                )
            )
        }
    }

    suspend fun createOwnerProfile(name: String, pin: String): StaffUserEntity {
        require(name.isNotBlank()) { "يرجى إدخال اسم المدير" }
        require(pin.length >= 4) { "رمز الدخول يجب أن يكون 4 أرقام على الأقل" }
        return db.withTransaction {
            ensureDefaultStoreSettings()
            val owner = StaffUserEntity(
                name = name.trim(),
                role = StaffRole.OWNER,
                pinHash = AccountingEngine.hashPin(pin),
                canViewProfits = true,
                canCancelInvoices = true,
                canManageInventory = true,
                canManageExpenses = true,
                canManageSettings = true,
                canEditPrices = true,
                canWithdrawDeposit = true,
                canApproveStocktake = true,
                isActive = true
            )
            val id = dao.insertStaffUser(owner)
            val saved = owner.copy(id = id)
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "تهيئة حساب المدير",
                    entityType = "STAFF",
                    entityId = saved.syncId,
                    details = "تم إنشاء ملف الإدارة للمستخدم ${saved.name}",
                    staffName = saved.name
                )
            )
            ultimate.enqueueSync("STAFF_USER", saved.syncId)
            saved
        }
    }

    suspend fun addOrUpdateStaffUser(
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
        actingStaff: StaffUserEntity,
        canEditPrices: Boolean = role == StaffRole.OWNER || role == StaffRole.ACCOUNTANT,
        canWithdrawDeposit: Boolean = role == StaffRole.OWNER || role == StaffRole.ACCOUNTANT,
        canApproveStocktake: Boolean = role == StaffRole.OWNER
    ) {
        require(actingStaff.canManageSettings || actingStaff.role == StaffRole.OWNER) {
            "إدارة الموظفين والصلاحيات متاحة للإدارة فقط"
        }
        require(name.isNotBlank()) { "يرجى إدخال اسم الموظف" }
        db.withTransaction {
            if (existingId == null) {
                require(!pin.isNullOrBlank() && pin.length >= 4) { "رمز PIN مطلوب (4 أرقام على الأقل)" }
                val user = StaffUserEntity(
                    name = name.trim(),
                    role = role,
                    pinHash = AccountingEngine.hashPin(pin),
                    canViewProfits = canViewProfits,
                    canCancelInvoices = canCancelInvoices,
                    canManageInventory = canManageInventory,
                    canManageExpenses = canManageExpenses,
                    canManageSettings = canManageSettings,
                    isActive = isActive,
                    canEditPrices = canEditPrices,
                    canWithdrawDeposit = canWithdrawDeposit,
                    canApproveStocktake = canApproveStocktake
                )
                val id = dao.insertStaffUser(user)
                dao.insertAuditLog(
                    AuditLogEntity(
                        action = "إضافة موظف",
                        entityType = "STAFF",
                        entityId = id.toString(),
                        details = "${user.name} (${user.role})",
                        staffName = actingStaff.name
                    )
                )
                ultimate.enqueueSync("STAFF_USER", user.syncId)
            } else {
                val old = dao.getStaffById(existingId) ?: error("الموظف غير موجود")
                val updatedPin = if (!pin.isNullOrBlank()) AccountingEngine.hashPin(pin) else old.pinHash
                val updated = old.copy(
                    name = name.trim(),
                    role = role,
                    pinHash = updatedPin,
                    canViewProfits = canViewProfits,
                    canCancelInvoices = canCancelInvoices,
                    canManageInventory = canManageInventory,
                    canManageExpenses = canManageExpenses,
                    canManageSettings = canManageSettings,
                    isActive = isActive,
                    canEditPrices = canEditPrices,
                    canWithdrawDeposit = canWithdrawDeposit,
                    canApproveStocktake = canApproveStocktake,
                    isSynced = false
                )
                dao.updateStaffUser(updated)
                dao.insertAuditLog(
                    AuditLogEntity(
                        action = "تعديل صلاحيات موظف",
                        entityType = "STAFF",
                        entityId = existingId.toString(),
                        details = "$name ($role)",
                        staffName = actingStaff.name
                    )
                )
                ultimate.enqueueSync("STAFF_USER", updated.syncId)
            }
        }
    }

    suspend fun verifyStaffPin(staffId: Long, pin: String): StaffUserEntity? {
        val user = dao.getStaffById(staffId) ?: return null
        if (!user.isActive || user.approvalStatus != ApprovalStatus.APPROVED) return null
        return if (user.pinHash == AccountingEngine.hashPin(pin)) user else null
    }

    suspend fun registerAccountFromLogin(
        fullName: String,
        role: String,
        password: String,
        ownerVerifyPin: String?,
        identifier: String = ""
    ): StaffUserEntity {
        val cleanName = fullName.trim()
        val cleanIdentifier = identifier.trim()
        require(cleanName.isNotBlank()) { "يرجى إدخال الاسم الكامل" }
        require(password.length >= 4) { "كلمة المرور يجب أن تكون 4 أحرف أو أرقام على الأقل" }

        val validRequestedRole = when (role) {
            StaffRole.OWNER, StaffRole.ACCOUNTANT, StaffRole.CASHIER,
            StaffRole.WAREHOUSE_KEEPER, StaffRole.AUDITOR -> role
            else -> StaffRole.CASHIER
        }
        val now = System.currentTimeMillis()

        return db.withTransaction {
            ensureDefaultStoreSettings()
            val allStaff = dao.getAllStaffUsers()
            val existingOwner = allStaff.firstOrNull {
                it.role == StaffRole.OWNER && it.isActive && it.approvalStatus == ApprovalStatus.APPROVED
            }
            val dupName = allStaff.firstOrNull {
                it.name.equals(cleanName, ignoreCase = true) && it.approvalStatus != ApprovalStatus.REJECTED
            }
            require(dupName == null) { "يوجد حساب مسجل بنفس الاسم بالفعل" }

            val pinAttempt = ownerVerifyPin?.trim().orEmpty()
            val isApprovedImmediately: Boolean
            val assignedRole: String
            val approvalStatus: String
            val approvedBy: String
            val approvedAt: Long

            if (existingOwner == null) {
                isApprovedImmediately = true
                assignedRole = StaffRole.OWNER
                approvalStatus = ApprovalStatus.APPROVED
                approvedBy = cleanName
                approvedAt = now
            } else if (pinAttempt.isNotEmpty()) {
                require(existingOwner.pinHash == AccountingEngine.hashPin(pinAttempt)) {
                    "رمز المدير العام غير صحيح"
                }
                isApprovedImmediately = true
                assignedRole = validRequestedRole
                approvalStatus = ApprovalStatus.APPROVED
                approvedBy = existingOwner.name
                approvedAt = now
            } else {
                isApprovedImmediately = false
                assignedRole = validRequestedRole
                approvalStatus = ApprovalStatus.PENDING
                approvedBy = ""
                approvedAt = 0L
            }

            val isOwner = isApprovedImmediately && assignedRole == StaffRole.OWNER
            val isAccountant = isApprovedImmediately && assignedRole == StaffRole.ACCOUNTANT
            val isWarehouse = isApprovedImmediately && assignedRole == StaffRole.WAREHOUSE_KEEPER
            val user = StaffUserEntity(
                name = cleanName,
                role = assignedRole,
                pinHash = AccountingEngine.hashPin(password),
                canViewProfits = isOwner || isAccountant,
                canCancelInvoices = isOwner || isAccountant,
                canManageInventory = isOwner || isAccountant || isWarehouse,
                canManageExpenses = isOwner || isAccountant,
                canManageSettings = isOwner,
                canEditPrices = isOwner || isAccountant,
                canWithdrawDeposit = isOwner || isAccountant,
                canApproveStocktake = isOwner,
                isActive = isApprovedImmediately,
                createdAtMs = now,
                approvalStatus = approvalStatus,
                requestedRole = validRequestedRole,
                loginIdentifier = cleanIdentifier,
                requestedAtMs = now,
                approvedBy = approvedBy,
                approvedAtMs = approvedAt
            )
            val id = dao.insertStaffUser(user)
            val saved = user.copy(id = id)
            dao.insertAuditLog(
                AuditLogEntity(
                    action = if (isApprovedImmediately) "إنشاء حساب مستخدم جديد" else "طلب تسجيل مستخدم بانتظار موافقة الأدمن",
                    entityType = "STAFF",
                    entityId = saved.syncId,
                    details = if (isApprovedImmediately) {
                        "تم تسجيل الحساب ${saved.name} بصلاحية ${saved.role}"
                    } else {
                        "طلب انضمام ${saved.name} (${saved.requestedRole}) بانتظار اعتماد المدير العام"
                    },
                    staffName = saved.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("STAFF_USER", saved.syncId)
            saved
        }
    }

    suspend fun approvePendingStaff(
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
        actingStaff: StaffUserEntity
    ) {
        require(actingStaff.role == StaffRole.OWNER || actingStaff.canManageSettings) {
            "اعتماد المستخدمين وتحديد طبيعة العمل متاح للأدمن فقط"
        }
        val validRole = when (assignedRole) {
            StaffRole.OWNER, StaffRole.ACCOUNTANT, StaffRole.CASHIER,
            StaffRole.WAREHOUSE_KEEPER, StaffRole.AUDITOR -> assignedRole
            else -> StaffRole.CASHIER
        }
        val now = System.currentTimeMillis()
        db.withTransaction {
            val target = dao.getStaffById(staffId) ?: error("طلب المستخدم غير موجود")
            val updated = target.copy(
                role = validRole,
                canViewProfits = canViewProfits,
                canCancelInvoices = canCancelInvoices,
                canManageInventory = canManageInventory,
                canManageExpenses = canManageExpenses,
                canManageSettings = canManageSettings || validRole == StaffRole.OWNER,
                canEditPrices = canEditPrices,
                canWithdrawDeposit = canWithdrawDeposit,
                canApproveStocktake = canApproveStocktake,
                isActive = true,
                approvalStatus = ApprovalStatus.APPROVED,
                approvedBy = actingStaff.name,
                approvedAtMs = now,
                isSynced = false
            )
            dao.updateStaffUser(updated)
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "اعتماد وتحديد صلاحيات مستخدم جديد",
                    entityType = "STAFF",
                    entityId = updated.syncId,
                    details = "تم قبول ${updated.name} بصفة ${updated.role} بواسطة ${actingStaff.name}",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("STAFF_USER", updated.syncId)
        }
    }

    suspend fun rejectPendingStaff(staffId: Long, reason: String = "", actingStaff: StaffUserEntity) {
        require(actingStaff.role == StaffRole.OWNER || actingStaff.canManageSettings) {
            "رفض طلبات المستخدمين متاح للأدمن فقط"
        }
        val now = System.currentTimeMillis()
        db.withTransaction {
            val target = dao.getStaffById(staffId) ?: error("طلب المستخدم غير موجود")
            val updated = target.copy(
                isActive = false,
                approvalStatus = ApprovalStatus.REJECTED,
                canViewProfits = false,
                canCancelInvoices = false,
                canManageInventory = false,
                canManageExpenses = false,
                canManageSettings = false,
                canEditPrices = false,
                canWithdrawDeposit = false,
                canApproveStocktake = false,
                approvedBy = actingStaff.name,
                approvedAtMs = now,
                isSynced = false
            )
            dao.updateStaffUser(updated)
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "رفض طلب تسجيل مستخدم",
                    entityType = "STAFF",
                    entityId = updated.syncId,
                    details = "تم رفض ${updated.name}${if (reason.isNotBlank()) " — السبب: $reason" else ""}",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("STAFF_USER", updated.syncId)
        }
    }

    suspend fun authenticateWithStatus(
        identifier: String,
        password: String,
        mappedStaffId: Long?,
        googleEmail: String?
    ): LoginAuthResult {
        val cleanId = identifier.trim()
        if (cleanId.isBlank() || password.isBlank()) return LoginAuthResult.InvalidCredentials
        val hashed = AccountingEngine.hashPin(password)
        val allUsers = dao.getAllStaffUsers()
        if (allUsers.isEmpty()) return LoginAuthResult.InvalidCredentials

        val candidate = when {
            mappedStaffId != null -> allUsers.firstOrNull { it.id == mappedStaffId }
            else -> null
        } ?: allUsers.firstOrNull {
            it.name.equals(cleanId, ignoreCase = true) ||
                it.syncId.equals(cleanId, ignoreCase = true) ||
                (it.loginIdentifier.isNotBlank() && it.loginIdentifier.equals(cleanId, ignoreCase = true))
        } ?: if (!googleEmail.isNullOrBlank() && googleEmail.equals(cleanId, ignoreCase = true)) {
            allUsers.firstOrNull { it.role == StaffRole.OWNER && it.approvalStatus == ApprovalStatus.APPROVED }
        } else {
            null
        } ?: return LoginAuthResult.InvalidCredentials

        if (candidate.pinHash != hashed) return LoginAuthResult.InvalidCredentials
        return when (candidate.approvalStatus) {
            ApprovalStatus.PENDING -> LoginAuthResult.PendingApproval(candidate.name, candidate.requestedRole)
            ApprovalStatus.REJECTED -> LoginAuthResult.Rejected(candidate.name)
            else -> if (candidate.isActive) LoginAuthResult.Success(candidate) else LoginAuthResult.Inactive(candidate.name)
        }
    }

    suspend fun authenticateByCredentials(
        identifier: String,
        password: String,
        mappedStaffId: Long?,
        googleEmail: String?
    ): StaffUserEntity? {
        return when (val res = authenticateWithStatus(identifier, password, mappedStaffId, googleEmail)) {
            is LoginAuthResult.Success -> res.staff
            else -> null
        }
    }

    suspend fun findStaffForRecovery(
        identifier: String,
        mappedStaffId: Long?,
        googleEmail: String?
    ): StaffUserEntity? {
        val cleanId = identifier.trim()
        val activeUsers = dao.getAllStaffUsers().filter { it.isActive }
        if (activeUsers.isEmpty()) return null
        if (cleanId.isBlank()) {
            return if (!googleEmail.isNullOrBlank()) {
                activeUsers.firstOrNull { it.role == StaffRole.OWNER }
            } else null
        }
        return (mappedStaffId?.let { id -> activeUsers.firstOrNull { it.id == id } })
            ?: activeUsers.firstOrNull { it.name.equals(cleanId, ignoreCase = true) }
            ?: if (!googleEmail.isNullOrBlank() && googleEmail.equals(cleanId, ignoreCase = true)) {
                activeUsers.firstOrNull { it.role == StaffRole.OWNER }
            } else null
    }

    suspend fun resetPasswordByRecovery(
        staffId: Long,
        newPassword: String,
        recoveryMethod: String
    ): StaffUserEntity {
        require(newPassword.length >= 4) { "كلمة المرور الجديدة يجب أن تكون 4 أحرف أو أرقام على الأقل" }
        return db.withTransaction {
            val target = dao.getStaffById(staffId) ?: error("الحساب غير موجود")
            val updated = target.copy(
                pinHash = AccountingEngine.hashPin(newPassword),
                isSynced = false
            )
            dao.updateStaffUser(updated)
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "استعادة كلمة المرور",
                    entityType = "STAFF",
                    entityId = target.syncId,
                    details = "تمت إعادة تعيين كلمة مرور ${target.name} عبر: $recoveryMethod",
                    staffName = target.name
                )
            )
            updated
        }
    }

    suspend fun saveProduct(
        productId: Long?,
        name: String,
        category: String,
        barcode: String,
        costPrice: Double,
        sellingPrice: Double,
        quantity: Double,
        minStockLevel: Double,
        actingStaff: StaffUserEntity,
        sku: String = "",
        imageUri: String = "",
        baseUnit: String = MeasureUnit.PIECE,
        wholesalePrice: Double = sellingPrice,
        minSellingPrice: Double = costPrice,
        defaultSupplierName: String = ""
    ): Long {
        require(actingStaff.canManageInventory) { "ليس لديك صلاحية إضافة أو تعديل الأصناف" }
        require(name.isNotBlank()) { "اسم الصنف مطلوب" }
        require(costPrice >= 0.0 && sellingPrice >= 0.0 && quantity >= 0.0 && minStockLevel >= 0.0) {
            "القيم الرقمية يجب أن تكون موجبة"
        }
        val now = System.currentTimeMillis()
        val resolvedBarcode = if (barcode.trim().equals("AUTO", ignoreCase = true)) {
            AccountingEngine.generateEan13Barcode(now)
        } else {
            barcode.trim()
        }
        return db.withTransaction {
            if (productId == null) {
                val entity = ProductEntity(
                    name = name.trim(),
                    category = category.trim().ifBlank { "أدوات عامة" },
                    barcode = resolvedBarcode,
                    sku = sku.trim(),
                    imageUri = imageUri.trim(),
                    baseUnit = baseUnit.ifBlank { MeasureUnit.PIECE },
                    averageCost = AccountingEngine.roundMoney(costPrice),
                    lastPurchaseCost = AccountingEngine.roundMoney(costPrice),
                    sellingPrice = AccountingEngine.roundMoney(sellingPrice),
                    wholesalePrice = AccountingEngine.roundMoney(wholesalePrice),
                    minSellingPrice = AccountingEngine.roundMoney(minSellingPrice),
                    defaultSupplierName = defaultSupplierName.trim(),
                    quantity = AccountingEngine.roundQty(quantity),
                    minStockLevel = AccountingEngine.roundQty(minStockLevel),
                    createdAtMs = now,
                    updatedAtMs = now,
                    lastPurchaseDateMs = now
                )
                val newId = dao.insertProduct(entity)
                if (quantity > 0.0) {
                    val mv = StockMovementEntity(
                        productId = newId,
                        productSyncId = entity.syncId,
                        productName = entity.name,
                        movementType = MovementType.ADJUSTMENT,
                        quantityDelta = quantity,
                        unitCost = costPrice,
                        balanceAfter = quantity,
                        reason = "رصيد افتتاحي عند إضافة الصنف",
                        referenceId = entity.syncId,
                        staffName = actingStaff.name,
                        occurredAtMs = now
                    )
                    dao.insertStockMovement(mv)
                }
                dao.insertAuditLog(
                    AuditLogEntity(
                        action = "إضافة صنف للمخزون",
                        entityType = "PRODUCT",
                        entityId = entity.syncId,
                        details = "${entity.name} | الكمية: $quantity $baseUnit | التكلفة: $costPrice | البيع: $sellingPrice",
                        staffName = actingStaff.name,
                        occurredAtMs = now
                    )
                )
                ultimate.enqueueSync("PRODUCT", entity.syncId)
                newId
            } else {
                val existing = dao.getProductById(productId) ?: error("الصنف غير موجود")
                ultimate.logPriceChangeIfNeeded(existing.id, existing.name, "PURCHASE_COST", existing.averageCost, costPrice, existing.syncId, actingStaff.name, now)
                ultimate.logPriceChangeIfNeeded(existing.id, existing.name, "RETAIL_PRICE", existing.sellingPrice, sellingPrice, existing.syncId, actingStaff.name, now)
                ultimate.logPriceChangeIfNeeded(existing.id, existing.name, "WHOLESALE_PRICE", existing.wholesalePrice, wholesalePrice, existing.syncId, actingStaff.name, now)
                val updated = existing.copy(
                    name = name.trim(),
                    category = category.trim().ifBlank { "أدوات عامة" },
                    barcode = resolvedBarcode,
                    sku = sku.trim(),
                    imageUri = imageUri.trim(),
                    baseUnit = baseUnit.ifBlank { existing.baseUnit },
                    averageCost = AccountingEngine.roundMoney(costPrice),
                    lastPurchaseCost = AccountingEngine.roundMoney(costPrice),
                    sellingPrice = AccountingEngine.roundMoney(sellingPrice),
                    wholesalePrice = AccountingEngine.roundMoney(wholesalePrice),
                    minSellingPrice = AccountingEngine.roundMoney(minSellingPrice),
                    defaultSupplierName = defaultSupplierName.trim(),
                    minStockLevel = AccountingEngine.roundQty(minStockLevel),
                    updatedAtMs = now,
                    isSynced = false
                )
                dao.updateProduct(updated)
                dao.insertAuditLog(
                    AuditLogEntity(
                        action = "تعديل بيانات صنف",
                        entityType = "PRODUCT",
                        entityId = existing.syncId,
                        details = "${updated.name} | سعر البيع: $sellingPrice | الجملة: $wholesalePrice",
                        staffName = actingStaff.name,
                        occurredAtMs = now
                    )
                )
                ultimate.enqueueSync("PRODUCT", updated.syncId)
                productId
            }
        }
    }

    suspend fun saveProductUnit(
        unitId: Long?,
        productId: Long,
        unitName: String,
        conversionToBase: Double,
        sellingPrice: Double,
        wholesalePrice: Double,
        barcode: String,
        actingStaff: StaffUserEntity
    ): Long {
        require(actingStaff.canManageInventory) { "ليس لديك صلاحية إدارة وحدات الأصناف" }
        require(unitName.isNotBlank() && conversionToBase > 0.0) { "اسم الوحدة ومعامل التحويل مطلوبان" }
        val product = dao.getProductById(productId) ?: error("الصنف غير موجود")
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val id = uDao.insertProductUnit(
                ProductUnitEntity(
                    id = unitId ?: 0L,
                    productId = product.id,
                    unitName = unitName.trim(),
                    conversionToBase = conversionToBase,
                    sellingPrice = AccountingEngine.roundMoney(sellingPrice),
                    wholesalePrice = AccountingEngine.roundMoney(wholesalePrice),
                    barcode = barcode.trim()
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "تعريف وحدة قياس للصنف",
                    entityType = "PRODUCT_UNIT",
                    entityId = product.syncId,
                    details = "${product.name} | 1 ${unitName.trim()} = $conversionToBase ${product.baseUnit}",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            id
        }
    }

    suspend fun deleteProductUnit(unitId: Long, actingStaff: StaffUserEntity) {
        require(actingStaff.canManageInventory) { "ليس لديك صلاحية حذف الوحدات" }
        uDao.deleteProductUnitById(unitId)
    }

    suspend fun saveCustomerSpecialPrice(
        partyId: Long,
        productId: Long,
        specialPrice: Double,
        minQty: Double,
        actingStaff: StaffUserEntity
    ): Long {
        require(actingStaff.canEditPrices || actingStaff.role == StaffRole.OWNER) {
            "ليس لديك صلاحية تحديد أسعار خاصة للعملاء"
        }
        require(specialPrice >= 0.0 && minQty > 0.0) { "السعر والكمية الدنيا يجب أن يكونا صالحين" }
        val party = dao.getPartyById(partyId) ?: error("العميل غير موجود")
        val product = dao.getProductById(productId) ?: error("الصنف غير موجود")
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val existing = uDao.getCustomerSpecialPrice(partyId, productId)
            uDao.insertCustomerSpecialPrice(
                CustomerSpecialPriceEntity(
                    id = existing?.id ?: 0L,
                    syncId = existing?.syncId ?: CustomerSpecialPriceEntity(partyId = partyId, partyName = party.name, productId = productId, productName = product.name, specialPrice = specialPrice).syncId,
                    partyId = partyId,
                    partyName = party.name,
                    productId = productId,
                    productName = product.name,
                    specialPrice = AccountingEngine.roundMoney(specialPrice),
                    minQty = minQty,
                    updatedAtMs = now
                )
            )
        }
    }

    suspend fun deleteCustomerSpecialPrice(id: Long, actingStaff: StaffUserEntity) {
        require(actingStaff.canEditPrices || actingStaff.role == StaffRole.OWNER) { "صلاحية غير كافية" }
        uDao.deleteCustomerSpecialPriceById(id)
    }

    suspend fun resolveProductPriceForLine(
        productId: Long,
        unitName: String,
        pricingTier: String,
        partyId: Long?,
        requestedQuantity: Double = 1.0
    ): ResolvedPriceQuote = ultimate.resolveProductPriceForLine(productId, unitName, pricingTier, partyId, requestedQuantity)

    suspend fun adjustProductStock(
        productId: Long,
        newQuantity: Double,
        reason: String,
        actingStaff: StaffUserEntity
    ) {
        require(actingStaff.canManageInventory) { "ليس لديك صلاحية تعديل المخزون" }
        require(newQuantity >= 0.0) { "الكمية الفعلية لا يمكن أن تكون سالبة" }
        require(reason.isNotBlank()) { "يجب تسجيل سبب تعديل المخزون" }
        val now = System.currentTimeMillis()
        db.withTransaction {
            ultimate.assertDateNotLocked(now, actingStaff)
            val product = dao.getProductById(productId) ?: error("الصنف غير موجود")
            val delta = AccountingEngine.roundQty(newQuantity - product.quantity)
            dao.updateProduct(product.copy(quantity = AccountingEngine.roundQty(newQuantity), updatedAtMs = now, isSynced = false))
            val mv = StockMovementEntity(
                productId = product.id,
                productSyncId = product.syncId,
                productName = product.name,
                movementType = MovementType.ADJUSTMENT,
                quantityDelta = delta,
                unitCost = product.averageCost,
                balanceAfter = newQuantity,
                reason = reason.trim(),
                referenceId = product.syncId,
                staffName = actingStaff.name,
                occurredAtMs = now
            )
            dao.insertStockMovement(mv)
            val absVal = AccountingEngine.roundMoney(abs(delta * product.averageCost))
            if (absVal > 0.01) {
                ultimate.recordBalancedJournalEntry(
                    referenceType = "STOCK_ADJUSTMENT",
                    referenceSyncId = mv.syncId,
                    description = "تسوية مخزون (${product.name}): $reason",
                    staffName = actingStaff.name,
                    entryDateMs = now,
                    rawLines = if (delta >= 0.0) {
                        listOf(
                            JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", debit = absVal),
                            JournalDraftLine(ChartAccounts.INVENTORY_ADJUSTMENT_CODE, "فروقات المخزون", credit = absVal)
                        )
                    } else {
                        listOf(
                            JournalDraftLine(ChartAccounts.INVENTORY_ADJUSTMENT_CODE, "فروقات المخزون", debit = absVal),
                            JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", credit = absVal)
                        )
                    }
                )
            }
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "جرد وتعديل مخزون",
                    entityType = "PRODUCT",
                    entityId = product.syncId,
                    details = "${product.name}: من ${product.quantity} إلى $newQuantity | السبب: $reason",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("PRODUCT", product.syncId)
            ultimate.enqueueSync("STOCK_MOVEMENT", mv.syncId)
        }
    }

    suspend fun saveParty(
        partyId: Long?,
        partyType: String,
        name: String,
        phone: String,
        actingStaff: StaffUserEntity,
        address: String = "",
        openingBalance: Double = 0.0,
        notes: String = ""
    ): Long {
        require(name.isNotBlank()) { "الاسم مطلوب" }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            if (partyId == null) {
                val cleanOpening = openingBalance.coerceAtLeast(0.0)
                val party = PartyEntity(
                    partyType = partyType,
                    name = name.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    openingBalance = cleanOpening,
                    notes = notes.trim(),
                    totalAmount = cleanOpening,
                    balanceDue = cleanOpening,
                    createdAtMs = now,
                    updatedAtMs = now
                )
                val id = dao.insertParty(party)
                dao.insertAuditLog(
                    AuditLogEntity(
                        action = if (partyType == PartyType.CUSTOMER) "إضافة زبون" else "إضافة مورد",
                        entityType = "PARTY",
                        entityId = party.syncId,
                        details = "${party.name} (${party.phone})",
                        staffName = actingStaff.name,
                        occurredAtMs = now
                    )
                )
                ultimate.enqueueSync("PARTY", party.syncId)
                id
            } else {
                val existing = dao.getPartyById(partyId) ?: error("السجل غير موجود")
                val updated = existing.copy(
                    name = name.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    notes = notes.trim(),
                    updatedAtMs = now,
                    isSynced = false
                )
                dao.updateParty(updated)
                ultimate.enqueueSync("PARTY", updated.syncId)
                partyId
            }
        }
    }

    suspend fun postSaleInvoice(
        lines: List<InvoiceLineDraft>,
        invoiceDiscount: Double,
        paymentMethod: String,
        paidAmountInput: Double,
        partyId: Long?,
        customerName: String,
        customerPhone: String,
        notes: String,
        dueDateMs: Long?,
        actingStaff: StaffUserEntity,
        accountId: Long = 1L,
        overrideReason: String = "",
        saleDateMs: Long = System.currentTimeMillis()
    ): Long {
        require(lines.isNotEmpty()) { "أضف منتجاً واحداً على الأقل للفاتورة" }
        require(invoiceDiscount >= 0.0) { "الخصم لا يمكن أن يكون سالباً" }
        val now = System.currentTimeMillis()

        return db.withTransaction {
            ultimate.assertDateNotLocked(saleDateMs, actingStaff, overrideReason)
            val settings = dao.getStoreSettings() ?: StoreSettingsEntity()
            val account = ultimate.getAccountOrDefault(accountId)

            val resolvedParty: PartyEntity? = when {
                partyId != null -> dao.getPartyById(partyId)
                customerName.isNotBlank() && paymentMethod != PaymentMethod.CASH -> {
                    val newCustomer = PartyEntity(
                        partyType = PartyType.CUSTOMER,
                        name = customerName.trim(),
                        phone = customerPhone.trim(),
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                    val id = dao.insertParty(newCustomer)
                    newCustomer.copy(id = id)
                }
                else -> null
            }

            if (paymentMethod != PaymentMethod.CASH && resolvedParty == null) {
                throw IllegalArgumentException("يجب تحديد أو إدخال اسم الزبون للفواتير الآجلة أو الجزئية")
            }

            // Enforce seller pricing authority
            if (actingStaff.role != StaffRole.OWNER && !actingStaff.canEditPrices) {
                for (line in lines) {
                    val quote = ultimate.resolveProductPriceForLine(
                        productId = line.productId,
                        unitName = line.unitName,
                        pricingTier = line.pricingTier,
                        partyId = resolvedParty?.id,
                        requestedQuantity = line.quantity
                    )
                    if (abs(line.unitPrice - quote.unitPrice) > 0.01) {
                        throw SecurityException("ليس لديك صلاحية تعديل سعر البيع للصنف (${line.productName})")
                    }
                    if (line.lineDiscount > 0.0) {
                        throw SecurityException("ليس لديك صلاحية منح خصم إضافي على الأصناف")
                    }
                }
                if (invoiceDiscount > 0.0) {
                    throw SecurityException("ليس لديك صلاحية منح خصم على الفاتورة")
                }
            }

            val lineTotals = lines.map { AccountingEngine.calculateLineTotal(it.quantity, it.unitPrice, it.lineDiscount) }
            val subtotal = AccountingEngine.calculateCashboxBalance(lineTotals)
            val netBeforeTax = (subtotal - invoiceDiscount).coerceAtLeast(0.0)
            val taxRate = if (settings.taxEnabled && settings.taxRatePercent > 0.0) settings.taxRatePercent else 0.0
            val taxAmount = AccountingEngine.calculateTaxAmount(netBeforeTax, taxRate)
            val netTotal = AccountingEngine.roundMoney(netBeforeTax + taxAmount)
            val paidAmount = when (paymentMethod) {
                PaymentMethod.CASH -> netTotal
                PaymentMethod.CREDIT -> 0.0
                else -> paidAmountInput.coerceIn(0.0, netTotal)
            }
            val remainingAmount = AccountingEngine.roundMoney((netTotal - paidAmount).coerceAtLeast(0.0))

            val seq = dao.countInvoicesByType(InvoiceType.SALE) + 1
            val invoiceNumber = String.format(Locale.US, "S-%04d", seq)

            var totalCost = 0.0
            val itemEntities = mutableListOf<InvoiceItemEntity>()

            for ((idx, line) in lines.withIndex()) {
                val product = dao.getProductById(line.productId) ?: error("الصنف غير موجود")
                val baseQty = AccountingEngine.toBaseQuantity(line.quantity, line.conversionFactor)
                if (product.quantity + 0.0001 < baseQty) {
                    throw IllegalStateException("الكمية غير كافية للصنف (${product.name}) — المتاح: ${product.quantity} ${product.baseUnit}")
                }
                val baseUnitCost = if (settings.costingMethod == CostingMethod.LAST_COST) {
                    product.lastPurchaseCost
                } else {
                    product.averageCost
                }
                val newQty = AccountingEngine.roundQty((product.quantity - baseQty).coerceAtLeast(0.0))
                dao.updateProduct(
                    product.copy(
                        quantity = newQty,
                        lastSaleDateMs = maxOf(product.lastSaleDateMs, saleDateMs),
                        updatedAtMs = now,
                        isSynced = false
                    )
                )
                totalCost += AccountingEngine.roundMoney(baseUnitCost * baseQty)

                itemEntities.add(
                    InvoiceItemEntity(
                        invoiceId = 0L,
                        productId = product.id,
                        productSyncId = product.syncId,
                        productName = product.name,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                        discountAmount = line.lineDiscount,
                        lineTotal = lineTotals[idx],
                        unitCostAtPosting = baseUnitCost,
                        unitName = line.unitName,
                        conversionFactor = line.conversionFactor,
                        baseQuantity = baseQty,
                        pricingTier = line.pricingTier
                    )
                )

                val mv = StockMovementEntity(
                    productId = product.id,
                    productSyncId = product.syncId,
                    productName = product.name,
                    movementType = MovementType.SALE,
                    quantityDelta = -baseQty,
                    unitCost = baseUnitCost,
                    balanceAfter = newQty,
                    reason = "فاتورة بيع رقم $invoiceNumber (${line.quantity} ${line.unitName})",
                    referenceId = invoiceNumber,
                    staffName = actingStaff.name,
                    occurredAtMs = saleDateMs
                )
                dao.insertStockMovement(mv)
                ultimate.enqueueSync("PRODUCT", product.syncId)
                ultimate.enqueueSync("STOCK_MOVEMENT", mv.syncId)
            }

            val displayPartyName = resolvedParty?.name ?: customerName.trim().ifBlank { "زبون نقدي" }
            val displayPartyPhone = resolvedParty?.phone ?: customerPhone.trim()

            val invoice = InvoiceEntity(
                invoiceNumber = invoiceNumber,
                invoiceType = InvoiceType.SALE,
                partyId = resolvedParty?.id,
                partySyncId = resolvedParty?.syncId ?: "",
                partyName = displayPartyName,
                partyPhone = displayPartyPhone,
                status = InvoiceStatus.POSTED,
                paymentMethod = paymentMethod,
                subtotal = subtotal,
                discountAmount = invoiceDiscount,
                netTotal = netTotal,
                paidAmount = paidAmount,
                remainingAmount = remainingAmount,
                totalCost = AccountingEngine.roundMoney(totalCost),
                taxRate = taxRate,
                taxAmount = taxAmount,
                accountId = account.id,
                notes = notes.trim(),
                staffId = actingStaff.id,
                staffName = actingStaff.name,
                invoiceDateMs = saleDateMs,
                dueDateMs = if (remainingAmount > 0.0) dueDateMs else null,
                createdAtMs = now,
                updatedAtMs = now
            )

            val invoiceId = dao.insertInvoice(invoice)
            dao.insertInvoiceItems(itemEntities.map { it.copy(invoiceId = invoiceId) })

            if (paidAmount > 0.0) {
                val cashTx = CashTransactionEntity(
                    txType = CashTxType.SALE_RECEIPT,
                    amountSigned = paidAmount,
                    description = "تحصيل فاتورة بيع $invoiceNumber - $displayPartyName",
                    referenceId = invoice.syncId,
                    staffName = actingStaff.name,
                    occurredAtMs = saleDateMs,
                    accountId = account.id,
                    accountName = account.name
                )
                dao.insertCashTransaction(cashTx)
                ultimate.enqueueSync("CASH_TX", cashTx.syncId)
            }

            if (resolvedParty != null) {
                dao.updateParty(
                    resolvedParty.copy(
                        totalAmount = resolvedParty.totalAmount + netTotal,
                        paidAmount = resolvedParty.paidAmount + paidAmount,
                        balanceDue = resolvedParty.balanceDue + remainingAmount,
                        updatedAtMs = now,
                        isSynced = false
                    )
                )
                ultimate.enqueueSync("PARTY", resolvedParty.syncId)
            }

            val salesNetExTax = AccountingEngine.roundMoney(netTotal - taxAmount)
            ultimate.recordBalancedJournalEntry(
                referenceType = InvoiceType.SALE,
                referenceSyncId = invoice.syncId,
                description = "قيد فاتورة بيع $invoiceNumber - $displayPartyName",
                staffName = actingStaff.name,
                entryDateMs = saleDateMs,
                rawLines = listOf(
                    JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, debit = paidAmount),
                    JournalDraftLine(ChartAccounts.RECEIVABLES_CODE, "ذمم العملاء", debit = remainingAmount, partyId = resolvedParty?.id),
                    JournalDraftLine(ChartAccounts.SALES_REVENUE_CODE, "إيرادات المبيعات", credit = salesNetExTax),
                    JournalDraftLine(ChartAccounts.TAX_PAYABLE_CODE, "ضريبة المبيعات", credit = taxAmount),
                    JournalDraftLine(ChartAccounts.COGS_CODE, "تكلفة البضاعة المباعة", debit = totalCost),
                    JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", credit = totalCost)
                )
            )

            dao.insertAuditLog(
                AuditLogEntity(
                    action = "اعتماد فاتورة بيع",
                    entityType = "INVOICE",
                    entityId = invoice.syncId,
                    details = "فاتورة $invoiceNumber | الصافي: $netTotal | التكلفة: $totalCost | المتبقي: $remainingAmount",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("INVOICE", invoice.syncId)
            invoiceId
        }
    }

    suspend fun postPurchaseInvoice(
        lines: List<InvoiceLineDraft>,
        invoiceDiscount: Double,
        paymentMethod: String,
        paidAmountInput: Double,
        partyId: Long?,
        supplierName: String,
        supplierPhone: String,
        notes: String,
        dueDateMs: Long?,
        purchaseDateMs: Long,
        actingStaff: StaffUserEntity,
        extraCosts: Double = 0.0,
        accountId: Long = 1L,
        sourceOcrHash: String = "",
        overrideReason: String = ""
    ): Long {
        require(actingStaff.canManageInventory) { "ليس لديك صلاحية تسجيل فواتير الشراء" }
        require(lines.isNotEmpty()) { "أضف منتجاً واحداً على الأقل لفاتورة الشراء" }
        require(extraCosts >= 0.0 && invoiceDiscount >= 0.0) { "المصاريف الإضافية والخصم يجب أن تكون غير سالبة" }
        val now = System.currentTimeMillis()

        return db.withTransaction {
            ultimate.assertDateNotLocked(purchaseDateMs, actingStaff, overrideReason)
            if (sourceOcrHash.isNotBlank()) {
                val duplicate = dao.getAllInvoices().any {
                    it.status == InvoiceStatus.POSTED && it.sourceOcrHash == sourceOcrHash
                }
                if (duplicate) {
                    throw IllegalStateException("هذه الفاتورة المستوردة (OCR) معتمدة مسبقاً لمنع التكرار")
                }
            }

            val account = ultimate.getAccountOrDefault(accountId)
            val resolvedSupplier: PartyEntity? = when {
                partyId != null -> dao.getPartyById(partyId)
                supplierName.isNotBlank() -> {
                    val supplier = PartyEntity(
                        partyType = PartyType.SUPPLIER,
                        name = supplierName.trim(),
                        phone = supplierPhone.trim(),
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                    val id = dao.insertParty(supplier)
                    supplier.copy(id = id)
                }
                else -> null
            }

            if (paymentMethod != PaymentMethod.CASH && resolvedSupplier == null) {
                throw IllegalArgumentException("يجب تحديد اسم المورد للمشتريات الآجلة أو الجزئية")
            }

            val lineTotals = lines.map { AccountingEngine.calculateLineTotal(it.quantity, it.unitPrice, it.lineDiscount) }
            val subtotal = AccountingEngine.calculateCashboxBalance(lineTotals)
            val netTotal = AccountingEngine.roundMoney((subtotal - invoiceDiscount + extraCosts).coerceAtLeast(0.0))
            val paidAmount = when (paymentMethod) {
                PaymentMethod.CASH -> netTotal
                PaymentMethod.CREDIT -> 0.0
                else -> paidAmountInput.coerceIn(0.0, netTotal)
            }
            val remainingAmount = AccountingEngine.roundMoney((netTotal - paidAmount).coerceAtLeast(0.0))

            if (paidAmount > 0.0) {
                val cashAvailable = uDao.getBalanceForAccount(account.id)
                if (cashAvailable + 0.01 < paidAmount) {
                    throw IllegalStateException("رصيد (${account.name}) الحالي ($cashAvailable) لا يكفي لدفع المبلغ ($paidAmount)")
                }
            }

            val seq = dao.countInvoicesByType(InvoiceType.PURCHASE) + 1
            val invoiceNumber = String.format(Locale.US, "P-%04d", seq)
            val displaySupplierName = resolvedSupplier?.name ?: supplierName.trim().ifBlank { "مورد نقدي" }
            val displaySupplierPhone = resolvedSupplier?.phone ?: supplierPhone.trim()

            val itemEntities = mutableListOf<InvoiceItemEntity>()
            for ((idx, line) in lines.withIndex()) {
                val product = dao.getProductById(line.productId) ?: error("الصنف غير موجود")
                val baseQty = AccountingEngine.toBaseQuantity(line.quantity, line.conversionFactor)
                val shareOfExtra = if (subtotal > 0.0 && extraCosts > 0.0) {
                    extraCosts * (lineTotals[idx] / subtotal)
                } else 0.0
                val netLineRatio = if (subtotal > 0.0) ((subtotal - invoiceDiscount).coerceAtLeast(0.0) / subtotal) else 1.0
                val effectiveLineTotal = (lineTotals[idx] * netLineRatio) + shareOfExtra
                val effectiveBaseCost = effectiveLineTotal / baseQty
                val newAvgCost = AccountingEngine.calculateWeightedAverageCost(
                    currentQuantity = product.quantity,
                    currentAverageCost = product.averageCost,
                    addedQuantity = baseQty,
                    purchaseUnitCost = effectiveBaseCost
                )
                val newQty = AccountingEngine.roundQty(product.quantity + baseQty)
                ultimate.logPriceChangeIfNeeded(
                    productId = product.id,
                    productName = product.name,
                    changeType = "PURCHASE_COST",
                    oldPrice = product.lastPurchaseCost,
                    newPrice = effectiveBaseCost,
                    referenceId = invoiceNumber,
                    staffName = actingStaff.name,
                    changedAtMs = now
                )
                dao.updateProduct(
                    product.copy(
                        quantity = newQty,
                        averageCost = newAvgCost,
                        lastPurchaseCost = AccountingEngine.roundMoney(effectiveBaseCost),
                        defaultSupplierName = displaySupplierName,
                        lastPurchaseDateMs = purchaseDateMs,
                        updatedAtMs = now,
                        isSynced = false
                    )
                )

                itemEntities.add(
                    InvoiceItemEntity(
                        invoiceId = 0L,
                        productId = product.id,
                        productSyncId = product.syncId,
                        productName = product.name,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                        discountAmount = line.lineDiscount,
                        lineTotal = lineTotals[idx],
                        unitCostAtPosting = effectiveBaseCost,
                        unitName = line.unitName,
                        conversionFactor = line.conversionFactor,
                        baseQuantity = baseQty,
                        pricingTier = PricingTier.RETAIL
                    )
                )

                val mv = StockMovementEntity(
                    productId = product.id,
                    productSyncId = product.syncId,
                    productName = product.name,
                    movementType = MovementType.PURCHASE,
                    quantityDelta = baseQty,
                    unitCost = effectiveBaseCost,
                    balanceAfter = newQty,
                    reason = "فاتورة شراء رقم $invoiceNumber (${line.quantity} ${line.unitName})",
                    referenceId = invoiceNumber,
                    staffName = actingStaff.name,
                    occurredAtMs = purchaseDateMs
                )
                dao.insertStockMovement(mv)
                ultimate.enqueueSync("PRODUCT", product.syncId)
                ultimate.enqueueSync("STOCK_MOVEMENT", mv.syncId)
            }

            val invoice = InvoiceEntity(
                invoiceNumber = invoiceNumber,
                invoiceType = InvoiceType.PURCHASE,
                partyId = resolvedSupplier?.id,
                partySyncId = resolvedSupplier?.syncId ?: "",
                partyName = displaySupplierName,
                partyPhone = displaySupplierPhone,
                status = InvoiceStatus.POSTED,
                paymentMethod = paymentMethod,
                subtotal = subtotal,
                discountAmount = invoiceDiscount,
                netTotal = netTotal,
                paidAmount = paidAmount,
                remainingAmount = remainingAmount,
                totalCost = netTotal,
                extraCosts = extraCosts,
                accountId = account.id,
                sourceOcrHash = sourceOcrHash,
                notes = notes.trim(),
                staffId = actingStaff.id,
                staffName = actingStaff.name,
                invoiceDateMs = purchaseDateMs,
                dueDateMs = if (remainingAmount > 0.0) dueDateMs else null,
                createdAtMs = now,
                updatedAtMs = now
            )

            val invoiceId = dao.insertInvoice(invoice)
            dao.insertInvoiceItems(itemEntities.map { it.copy(invoiceId = invoiceId) })

            if (paidAmount > 0.0) {
                val cashTx = CashTransactionEntity(
                    txType = CashTxType.PURCHASE_PAYMENT,
                    amountSigned = -paidAmount,
                    description = "دفعة فاتورة شراء $invoiceNumber - $displaySupplierName",
                    referenceId = invoice.syncId,
                    staffName = actingStaff.name,
                    occurredAtMs = purchaseDateMs,
                    accountId = account.id,
                    accountName = account.name
                )
                dao.insertCashTransaction(cashTx)
                ultimate.enqueueSync("CASH_TX", cashTx.syncId)
            }

            if (resolvedSupplier != null) {
                dao.updateParty(
                    resolvedSupplier.copy(
                        totalAmount = resolvedSupplier.totalAmount + netTotal,
                        paidAmount = resolvedSupplier.paidAmount + paidAmount,
                        balanceDue = resolvedSupplier.balanceDue + remainingAmount,
                        updatedAtMs = now,
                        isSynced = false
                    )
                )
                ultimate.enqueueSync("PARTY", resolvedSupplier.syncId)
            }

            ultimate.recordBalancedJournalEntry(
                referenceType = InvoiceType.PURCHASE,
                referenceSyncId = invoice.syncId,
                description = "قيد فاتورة شراء $invoiceNumber - $displaySupplierName",
                staffName = actingStaff.name,
                entryDateMs = purchaseDateMs,
                rawLines = listOf(
                    JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", debit = netTotal),
                    JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, credit = paidAmount),
                    JournalDraftLine(ChartAccounts.SUPPLIER_PAYABLES_CODE, "ذمم الموردين", credit = remainingAmount, partyId = resolvedSupplier?.id)
                )
            )

            dao.insertAuditLog(
                AuditLogEntity(
                    action = "اعتماد فاتورة شراء",
                    entityType = "INVOICE",
                    entityId = invoice.syncId,
                    details = "فاتورة $invoiceNumber | المورد: $displaySupplierName | الإجمالي: $netTotal | مصاريف إضافية: $extraCosts",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("INVOICE", invoice.syncId)
            invoiceId
        }
    }

    suspend fun cancelInvoice(invoiceId: Long, reason: String, actingStaff: StaffUserEntity) {
        require(actingStaff.canCancelInvoices) { "ليس لديك صلاحية إلغاء الفواتير" }
        val now = System.currentTimeMillis()
        db.withTransaction {
            val withItems = dao.getInvoiceWithItems(invoiceId) ?: error("الفاتورة غير موجودة")
            val inv = withItems.invoice
            if (inv.status == InvoiceStatus.CANCELLED) {
                throw IllegalStateException("الفاتورة ملغاة مسبقاً")
            }
            ultimate.assertDateNotLocked(inv.invoiceDateMs, actingStaff, reason)
            val account = ultimate.getAccountOrDefault(inv.accountId)

            if (inv.invoiceType == InvoiceType.SALE) {
                if (inv.paidAmount > 0.0) {
                    val cashNow = uDao.getBalanceForAccount(account.id)
                    if (cashNow + 0.01 < inv.paidAmount) {
                        throw IllegalStateException("رصيد (${account.name}) الحالي ($cashNow) لا يكفي لإرجاع المبلغ المدفوع (${inv.paidAmount})")
                    }
                    dao.insertCashTransaction(
                        CashTransactionEntity(
                            txType = CashTxType.REVERSAL,
                            amountSigned = -inv.paidAmount,
                            description = "قيد عكسي لإلغاء فاتورة بيع ${inv.invoiceNumber}",
                            referenceId = inv.syncId,
                            staffName = actingStaff.name,
                            occurredAtMs = now,
                            accountId = account.id,
                            accountName = account.name
                        )
                    )
                }
                for (item in withItems.items) {
                    val product = dao.getProductById(item.productId) ?: continue
                    val qtyDelta = if (item.baseQuantity > 0.0) item.baseQuantity else item.quantity
                    val restoredQty = AccountingEngine.roundQty(product.quantity + qtyDelta)
                    dao.updateProduct(product.copy(quantity = restoredQty, updatedAtMs = now, isSynced = false))
                    dao.insertStockMovement(
                        StockMovementEntity(
                            productId = product.id,
                            productSyncId = product.syncId,
                            productName = product.name,
                            movementType = MovementType.REVERSAL,
                            quantityDelta = qtyDelta,
                            unitCost = item.unitCostAtPosting,
                            balanceAfter = restoredQty,
                            reason = "إلغاء فاتورة بيع ${inv.invoiceNumber}: $reason",
                            referenceId = inv.invoiceNumber,
                            staffName = actingStaff.name,
                            occurredAtMs = now
                        )
                    )
                    ultimate.enqueueSync("PRODUCT", product.syncId)
                }
                ultimate.recordBalancedJournalEntry(
                    referenceType = "INVOICE_CANCEL",
                    referenceSyncId = inv.syncId,
                    description = "قيد عكسي لإلغاء فاتورة بيع ${inv.invoiceNumber}",
                    staffName = actingStaff.name,
                    entryDateMs = now,
                    rawLines = listOf(
                        JournalDraftLine(ChartAccounts.SALES_REVENUE_CODE, "إيرادات المبيعات (عكسي)", debit = inv.netTotal - inv.taxAmount),
                        JournalDraftLine(ChartAccounts.TAX_PAYABLE_CODE, "ضريبة المبيعات (عكسي)", debit = inv.taxAmount),
                        JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, credit = inv.paidAmount),
                        JournalDraftLine(ChartAccounts.RECEIVABLES_CODE, "ذمم العملاء", credit = inv.remainingAmount, partyId = inv.partyId),
                        JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", debit = inv.totalCost),
                        JournalDraftLine(ChartAccounts.COGS_CODE, "تكلفة البضاعة المباعة", credit = inv.totalCost)
                    )
                )
            } else {
                for (item in withItems.items) {
                    val product = dao.getProductById(item.productId) ?: continue
                    val qtyDelta = if (item.baseQuantity > 0.0) item.baseQuantity else item.quantity
                    if (product.quantity + 0.0001 < qtyDelta) {
                        throw IllegalStateException("لا يمكن إلغاء فاتورة الشراء لأن جزءاً من (${product.name}) قد بيع بالفعل")
                    }
                    val newQty = AccountingEngine.roundQty(product.quantity - qtyDelta)
                    dao.updateProduct(product.copy(quantity = newQty, updatedAtMs = now, isSynced = false))
                    dao.insertStockMovement(
                        StockMovementEntity(
                            productId = product.id,
                            productSyncId = product.syncId,
                            productName = product.name,
                            movementType = MovementType.REVERSAL,
                            quantityDelta = -qtyDelta,
                            unitCost = item.unitCostAtPosting,
                            balanceAfter = newQty,
                            reason = "إلغاء فاتورة شراء ${inv.invoiceNumber}: $reason",
                            referenceId = inv.invoiceNumber,
                            staffName = actingStaff.name,
                            occurredAtMs = now
                        )
                    )
                    ultimate.enqueueSync("PRODUCT", product.syncId)
                }
                if (inv.paidAmount > 0.0) {
                    dao.insertCashTransaction(
                        CashTransactionEntity(
                            txType = CashTxType.REVERSAL,
                            amountSigned = inv.paidAmount,
                            description = "استرداد نقدي لإلغاء فاتورة شراء ${inv.invoiceNumber}",
                            referenceId = inv.syncId,
                            staffName = actingStaff.name,
                            occurredAtMs = now,
                            accountId = account.id,
                            accountName = account.name
                        )
                    )
                }
                ultimate.recordBalancedJournalEntry(
                    referenceType = "INVOICE_CANCEL",
                    referenceSyncId = inv.syncId,
                    description = "قيد عكسي لإلغاء فاتورة شراء ${inv.invoiceNumber}",
                    staffName = actingStaff.name,
                    entryDateMs = now,
                    rawLines = listOf(
                        JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, debit = inv.paidAmount),
                        JournalDraftLine(ChartAccounts.SUPPLIER_PAYABLES_CODE, "ذمم الموردين", debit = inv.remainingAmount, partyId = inv.partyId),
                        JournalDraftLine(ChartAccounts.INVENTORY_CODE, "المخزون", credit = inv.netTotal)
                    )
                )
            }

            if (inv.partyId != null) {
                dao.getPartyById(inv.partyId)?.let { party ->
                    dao.updateParty(
                        party.copy(
                            totalAmount = (party.totalAmount - inv.netTotal).coerceAtLeast(0.0),
                            paidAmount = (party.paidAmount - inv.paidAmount).coerceAtLeast(0.0),
                            balanceDue = (party.balanceDue - inv.remainingAmount).coerceAtLeast(0.0),
                            updatedAtMs = now,
                            isSynced = false
                        )
                    )
                    ultimate.enqueueSync("PARTY", party.syncId)
                }
            }

            dao.updateInvoice(
                inv.copy(
                    status = InvoiceStatus.CANCELLED,
                    notes = if (reason.isNotBlank()) "${inv.notes} [ملغاة: $reason]".trim() else inv.notes,
                    updatedAtMs = now,
                    isSynced = false
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "إلغاء فاتورة بقيد عكسي",
                    entityType = "INVOICE",
                    entityId = inv.syncId,
                    details = "فاتورة ${inv.invoiceNumber} | السبب: $reason",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("INVOICE", inv.syncId, operation = "STATUS_UPDATE")
        }
    }

    suspend fun editSaleInvoice(
        invoiceId: Long,
        newLines: List<InvoiceLineDraft>,
        newDiscount: Double,
        newPaymentMethod: String,
        newPaidAmount: Double,
        newCustomerName: String,
        newCustomerPhone: String,
        newNotes: String,
        newDueDateMs: Long?,
        actingStaff: StaffUserEntity,
        newSaleDateMs: Long = 0L
    ): Long {
        require(actingStaff.canCancelInvoices) { "ليس لديك صلاحية تعديل الفواتير" }
        return db.withTransaction {
            val existing = dao.getInvoiceWithItems(invoiceId) ?: error("الفاتورة غير موجودة")
            val effectiveDateMs = if (newSaleDateMs > 0L) newSaleDateMs else existing.invoice.invoiceDateMs
            cancelInvoice(invoiceId, "تعديل الفاتورة ${existing.invoice.invoiceNumber}", actingStaff)
            postSaleInvoice(
                lines = newLines,
                invoiceDiscount = newDiscount,
                paymentMethod = newPaymentMethod,
                paidAmountInput = newPaidAmount,
                partyId = existing.invoice.partyId,
                customerName = newCustomerName.ifBlank { existing.invoice.partyName },
                customerPhone = newCustomerPhone.ifBlank { existing.invoice.partyPhone },
                notes = newNotes,
                dueDateMs = newDueDateMs,
                actingStaff = actingStaff,
                accountId = existing.invoice.accountId,
                saleDateMs = effectiveDateMs
            )
        }
    }

    suspend fun recordExpense(
        category: String,
        amount: Double,
        description: String,
        paymentMethod: String,
        expenseDateMs: Long,
        actingStaff: StaffUserEntity,
        receiptImageUri: String = "",
        accountId: Long = 1L
    ) {
        require(actingStaff.canManageExpenses) { "ليس لديك صلاحية تسجيل المصاريف" }
        require(amount > 0.0) { "مبلغ المصروف يجب أن يكون أكبر من صفر" }
        val now = System.currentTimeMillis()
        db.withTransaction {
            ultimate.assertDateNotLocked(expenseDateMs, actingStaff)
            val account = ultimate.getAccountOrDefault(accountId)
            if (paymentMethod == PaymentMethod.CASH) {
                val cashAvailable = uDao.getBalanceForAccount(account.id)
                if (cashAvailable + 0.01 < amount) {
                    throw IllegalStateException("رصيد (${account.name}) الحالي ($cashAvailable) غير كافٍ لتسديد المصروف")
                }
            }
            val expense = ExpenseEntity(
                category = category,
                amount = AccountingEngine.roundMoney(amount),
                description = description.trim(),
                paymentMethod = paymentMethod,
                staffName = actingStaff.name,
                expenseDateMs = expenseDateMs,
                receiptImageUri = receiptImageUri.trim(),
                accountId = account.id
            )
            dao.insertExpense(expense)
            if (paymentMethod == PaymentMethod.CASH) {
                val cashTx = CashTransactionEntity(
                    txType = CashTxType.EXPENSE_PAYMENT,
                    amountSigned = -amount,
                    description = "مصروف ($category): ${description.trim().ifBlank { category }}",
                    referenceId = expense.syncId,
                    staffName = actingStaff.name,
                    occurredAtMs = expenseDateMs,
                    accountId = account.id,
                    accountName = account.name
                )
                dao.insertCashTransaction(cashTx)
                ultimate.enqueueSync("CASH_TX", cashTx.syncId)
            }
            ultimate.recordBalancedJournalEntry(
                referenceType = "EXPENSE",
                referenceSyncId = expense.syncId,
                description = "مصروف ($category): ${description.trim().ifBlank { category }}",
                staffName = actingStaff.name,
                entryDateMs = expenseDateMs,
                rawLines = listOf(
                    JournalDraftLine(ChartAccounts.OPERATING_EXPENSE_CODE, "مصاريف تشغيلية ($category)", debit = amount),
                    JournalDraftLine(
                        if (paymentMethod == PaymentMethod.CASH) ChartAccounts.CASHBOX_CODE else ChartAccounts.SUPPLIER_PAYABLES_CODE,
                        if (paymentMethod == PaymentMethod.CASH) account.name else "مصروفات مستحقة",
                        credit = amount
                    )
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "تسجيل مصروف",
                    entityType = "EXPENSE",
                    entityId = expense.syncId,
                    details = "$category | $amount | $description",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("EXPENSE", expense.syncId)
        }
    }

    suspend fun recordCashMovement(
        txType: String,
        amount: Double,
        description: String,
        actingStaff: StaffUserEntity,
        accountId: Long = 1L
    ) {
        if (txType == CashTxType.CAPITAL_IN || txType == CashTxType.PERSONAL_DRAWING || txType == CashTxType.OTHER_OUTFLOW) {
            require(actingStaff.canWithdrawDeposit || actingStaff.canManageExpenses || actingStaff.role == StaffRole.OWNER) {
                "ليس لديك صلاحية تنفيذ عمليات الإيداع أو السحب المالي"
            }
        }
        require(amount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }
        val isInflow = txType == CashTxType.CAPITAL_IN || txType == CashTxType.OTHER_INCOME
        val signedAmount = if (isInflow) amount else -amount
        val now = System.currentTimeMillis()
        db.withTransaction {
            ultimate.assertDateNotLocked(now, actingStaff)
            val account = ultimate.getAccountOrDefault(accountId)
            if (!isInflow) {
                val cashAvailable = uDao.getBalanceForAccount(account.id)
                if (cashAvailable + 0.01 < amount) {
                    throw IllegalStateException("رصيد (${account.name}) الحالي ($cashAvailable) لا يسمح بسحب ($amount)")
                }
            }
            val tx = CashTransactionEntity(
                txType = txType,
                amountSigned = signedAmount,
                description = description.trim().ifBlank { txType },
                staffName = actingStaff.name,
                occurredAtMs = now,
                accountId = account.id,
                accountName = account.name
            )
            dao.insertCashTransaction(tx)
            val journalLines = when (txType) {
                CashTxType.CAPITAL_IN -> listOf(
                    JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, debit = amount),
                    JournalDraftLine(ChartAccounts.CAPITAL_CODE, "رأس المال", credit = amount)
                )
                CashTxType.PERSONAL_DRAWING -> listOf(
                    JournalDraftLine(ChartAccounts.DRAWINGS_CODE, "السحوبات الشخصية للمدير", debit = amount),
                    JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, credit = amount)
                )
                CashTxType.OTHER_INCOME -> listOf(
                    JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, debit = amount),
                    JournalDraftLine(ChartAccounts.OTHER_INCOME_CODE, "إيرادات أخرى", credit = amount)
                )
                else -> listOf(
                    JournalDraftLine(ChartAccounts.OPERATING_EXPENSE_CODE, "مصروفات / إخراج أموال", debit = amount),
                    JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, credit = amount)
                )
            }
            ultimate.recordBalancedJournalEntry(
                referenceType = txType,
                referenceSyncId = tx.syncId,
                description = tx.description,
                staffName = actingStaff.name,
                entryDateMs = now,
                rawLines = journalLines
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "حركة صندوق مالية",
                    entityType = "CASHBOX",
                    entityId = tx.syncId,
                    details = "${tx.txType} | $signedAmount | ${tx.description}",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("CASH_TX", tx.syncId)
        }
    }

    suspend fun recordDebtPayment(
        partyId: Long,
        amount: Double,
        notes: String,
        actingStaff: StaffUserEntity,
        accountId: Long = 1L
    ) {
        require(amount > 0.0) { "مبلغ الدفعة يجب أن يكون أكبر من صفر" }
        val now = System.currentTimeMillis()
        db.withTransaction {
            ultimate.assertDateNotLocked(now, actingStaff)
            val account = ultimate.getAccountOrDefault(accountId)
            val party = dao.getPartyById(partyId) ?: error("الطرف غير موجود")
            if (amount > party.balanceDue + 0.01) {
                throw IllegalArgumentException("المبلغ المدفوع ($amount) أكبر من الرصيد المستحق (${party.balanceDue})")
            }
            val isCustomer = party.partyType == PartyType.CUSTOMER
            if (!isCustomer) {
                val cashAvailable = uDao.getBalanceForAccount(account.id)
                if (cashAvailable + 0.01 < amount) {
                    throw IllegalStateException("رصيد (${account.name}) الحالي ($cashAvailable) غير كافٍ لسداد المورد")
                }
            }

            val payment = DebtPaymentEntity(
                partyId = party.id,
                partySyncId = party.syncId,
                partyName = party.name,
                partyType = party.partyType,
                amount = AccountingEngine.roundMoney(amount),
                notes = notes.trim(),
                staffName = actingStaff.name,
                paymentDateMs = now
            )
            dao.insertDebtPayment(payment)

            dao.updateParty(
                party.copy(
                    paidAmount = party.paidAmount + amount,
                    balanceDue = (party.balanceDue - amount).coerceAtLeast(0.0),
                    updatedAtMs = now,
                    isSynced = false
                )
            )

            val cashTx = CashTransactionEntity(
                txType = if (isCustomer) CashTxType.DEBT_COLLECTION else CashTxType.SUPPLIER_PAYMENT,
                amountSigned = if (isCustomer) amount else -amount,
                description = if (isCustomer) "تحصيل دين من الزبون: ${party.name}" else "سداد دين للمورد: ${party.name}",
                referenceId = payment.syncId,
                staffName = actingStaff.name,
                occurredAtMs = now,
                accountId = account.id,
                accountName = account.name
            )
            dao.insertCashTransaction(cashTx)

            ultimate.recordBalancedJournalEntry(
                referenceType = if (isCustomer) CashTxType.DEBT_COLLECTION else CashTxType.SUPPLIER_PAYMENT,
                referenceSyncId = payment.syncId,
                description = cashTx.description,
                staffName = actingStaff.name,
                entryDateMs = now,
                rawLines = if (isCustomer) {
                    listOf(
                        JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, debit = amount),
                        JournalDraftLine(ChartAccounts.RECEIVABLES_CODE, "ذمم العملاء", credit = amount, partyId = party.id)
                    )
                } else {
                    listOf(
                        JournalDraftLine(ChartAccounts.SUPPLIER_PAYABLES_CODE, "ذمم الموردين", debit = amount, partyId = party.id),
                        JournalDraftLine(ChartAccounts.CASHBOX_CODE, account.name, credit = amount)
                    )
                }
            )

            dao.insertAuditLog(
                AuditLogEntity(
                    action = if (isCustomer) "تحصيل دفعة دين" else "سداد دفعة لمورد",
                    entityType = "DEBT_PAYMENT",
                    entityId = payment.syncId,
                    details = "${party.name} | المبلغ: $amount | $notes",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("DEBT_PAYMENT", payment.syncId)
            ultimate.enqueueSync("PARTY", party.syncId)
            ultimate.enqueueSync("CASH_TX", cashTx.syncId)
        }
    }

    // Delegated Ultimate Operations
    suspend fun postSaleReturn(
        originalInvoiceId: Long,
        productId: Long,
        unitName: String,
        conversionFactor: Double,
        returnedQty: Double,
        refundCashAmount: Double,
        reason: String,
        actingStaff: StaffUserEntity,
        accountId: Long = 1L
    ): Long = db.withTransaction {
        ultimate.postSaleReturn(originalInvoiceId, productId, unitName, conversionFactor, returnedQty, refundCashAmount, reason, accountId, actingStaff)
    }

    suspend fun postPurchaseReturn(
        originalInvoiceId: Long,
        productId: Long,
        unitName: String,
        conversionFactor: Double,
        returnedQty: Double,
        refundCashAmount: Double,
        reason: String,
        actingStaff: StaffUserEntity,
        accountId: Long = 1L
    ): Long = db.withTransaction {
        ultimate.postPurchaseReturn(originalInvoiceId, productId, unitName, conversionFactor, returnedQty, refundCashAmount, reason, accountId, actingStaff)
    }

    suspend fun writeOffDamagedStock(productId: Long, damagedBaseQty: Double, reason: String, actingStaff: StaffUserEntity) {
        db.withTransaction { ultimate.writeOffDamagedStock(productId, damagedBaseQty, reason, actingStaff) }
    }

    suspend fun writeOffBadDebt(partyId: Long, amount: Double, reason: String, actingStaff: StaffUserEntity) {
        db.withTransaction { ultimate.writeOffBadDebt(partyId, amount, reason, actingStaff) }
    }

    suspend fun saveCashAccount(name: String, accountType: String, actingStaff: StaffUserEntity): Long {
        require(actingStaff.canManageSettings || actingStaff.canWithdrawDeposit || actingStaff.role == StaffRole.OWNER) {
            "ليس لديك صلاحية إضافة حساب مالي"
        }
        require(name.isNotBlank()) { "يرجى إدخال اسم الحساب" }
        return db.withTransaction {
            ultimate.ensureDefaultCashAccount()
            uDao.insertCashAccount(CashAccountEntity(name = name.trim(), accountType = accountType, isDefault = false))
        }
    }

    suspend fun transferBetweenCashAccounts(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        notes: String,
        actingStaff: StaffUserEntity
    ) {
        db.withTransaction { ultimate.transferBetweenAccounts(fromAccountId, toAccountId, amount, notes, actingStaff) }
    }

    suspend fun closeCashboxDay(
        accountId: Long,
        actualCountedBalance: Double,
        notes: String,
        lockPeriod: Boolean,
        actingStaff: StaffUserEntity
    ): CashboxClosingEntity = db.withTransaction {
        ultimate.closeCashboxDay(accountId, actualCountedBalance, notes, lockPeriod, actingStaff)
    }

    suspend fun submitStockCount(productId: Long, countedQty: Double, notes: String, actingStaff: StaffUserEntity): Long =
        db.withTransaction { ultimate.submitStockCount(productId, countedQty, notes, actingStaff) }

    suspend fun approveStockCount(stockCountId: Long, actingStaff: StaffUserEntity) {
        db.withTransaction { ultimate.approveStockCount(stockCountId, actingStaff) }
    }

    suspend fun createOrCloseAccountingPeriod(
        name: String,
        startMs: Long,
        endMs: Long,
        closeNow: Boolean,
        actingStaff: StaffUserEntity
    ): Long {
        require(actingStaff.role == StaffRole.OWNER || actingStaff.canManageSettings) {
            "إدارة الفترات المحاسبية متاحة للإدارة فقط"
        }
        require(name.isNotBlank() && endMs > startMs) { "بيانات الفترة المحاسبية غير صالحة" }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val id = uDao.insertAccountingPeriod(
                AccountingPeriodEntity(
                    name = name.trim(),
                    startMs = startMs,
                    endMs = endMs,
                    isClosed = closeNow,
                    closedBy = if (closeNow) actingStaff.name else "",
                    closedAtMs = if (closeNow) now else 0L
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    action = if (closeNow) "إقفال فترة محاسبية" else "فتح فترة محاسبية",
                    entityType = "PERIOD",
                    entityId = id.toString(),
                    details = name.trim(),
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            id
        }
    }

    suspend fun saveAiLog(
        staffName: String,
        modelId: String,
        prompt: String,
        responseSummary: String,
        wasOfflineFallback: Boolean
    ) {
        uDao.insertAiLog(
            AiLogEntity(
                staffName = staffName,
                modelId = modelId,
                prompt = prompt.take(400),
                responseSummary = responseSummary.take(600),
                wasOfflineFallback = wasOfflineFallback
            )
        )
    }

    suspend fun recordAutoBackupResult(
        fileName: String,
        isEncrypted: Boolean,
        checksumSha256: String,
        recordCount: Int,
        status: String,
        notes: String,
        createdBy: String
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            uDao.insertBackupRecord(
                BackupRecordEntity(
                    backupType = if (isEncrypted) "AUTO_ENCRYPTED" else "MANUAL",
                    fileName = fileName,
                    isEncrypted = isEncrypted,
                    checksumSha256 = checksumSha256,
                    recordCount = recordCount,
                    status = status,
                    notes = notes,
                    createdBy = createdBy,
                    createdAtMs = now
                )
            )
            val s = dao.getStoreSettings() ?: StoreSettingsEntity()
            dao.upsertStoreSettings(s.copy(lastAutoBackupAtMs = now, lastAutoBackupStatus = status))
        }
    }

    suspend fun exportJsonBackup(): String {
        val payload = collectBackupPayload()
        return BackupJsonSerializer.toJson(payload)
    }

    suspend fun exportEncryptedBackup(passphrase: String, actingStaff: StaffUserEntity): Pair<String, BackupRecordEntity> {
        val payload = collectBackupPayload()
        val plain = BackupJsonSerializer.toJson(payload)
        val encrypted = AccountingEngine.encryptBackupText(plain, passphrase)
        val checksum = AccountingEngine.sha256Hex(plain)
        val count = BackupJsonSerializer.totalRecordCount(payload)
        val record = BackupRecordEntity(
            backupType = "MANUAL_ENCRYPTED",
            fileName = "mamo_backup_${System.currentTimeMillis()}.mamo.json",
            isEncrypted = true,
            checksumSha256 = checksum,
            recordCount = count,
            status = "SUCCESS",
            notes = "تصدير يدوي مشفر AES-256-GCM",
            createdBy = actingStaff.name
        )
        uDao.insertBackupRecord(record)
        return encrypted to record
    }

    suspend fun exportBackupBytes(
        compressGzip: Boolean,
        passphrase: String?,
        actingStaff: StaffUserEntity
    ): Pair<ByteArray, BackupRecordEntity> {
        val payload = collectBackupPayload()
        val plain = BackupJsonSerializer.toJson(payload)
        val checksum = AccountingEngine.sha256Hex(plain)
        val count = BackupJsonSerializer.totalRecordCount(payload)
        val cleanPass = passphrase?.trim().orEmpty()
        val useEncryption = cleanPass.length >= 4

        val outputBytes: ByteArray
        val fileName: String
        val backupType: String
        val notesText: String
        val now = System.currentTimeMillis()

        when {
            compressGzip && useEncryption -> {
                val envelope = AccountingEngine.encryptCompressedBackup(plain, cleanPass)
                outputBytes = envelope.toByteArray(Charsets.UTF_8)
                fileName = "mamo_backup_$now.mamo.gz.enc.json"
                backupType = "GZIP_ENCRYPTED"
                val rawLen = plain.toByteArray(Charsets.UTF_8).size.coerceAtLeast(1)
                val ratio = ((1.0 - (outputBytes.size.toDouble() / rawLen)) * 100.0).coerceAtLeast(0.0)
                notesText = "نسخة مضغوطة GZIP ومشفرة AES-256-GCM (توفير ~${ratio.toInt()}%)"
            }
            compressGzip -> {
                val rawBytes = plain.toByteArray(Charsets.UTF_8)
                outputBytes = AccountingEngine.compressGzip(rawBytes)
                fileName = "mamo_backup_$now.mamo.gz"
                backupType = "GZIP_COMPRESSED"
                val ratio = ((1.0 - (outputBytes.size.toDouble() / rawBytes.size.coerceAtLeast(1))) * 100.0).coerceAtLeast(0.0)
                notesText = "نسخة مضغوطة GZIP (${outputBytes.size} بايت من أصل ${rawBytes.size} بايت — توفير ${ratio.toInt()}%)"
            }
            useEncryption -> {
                val encrypted = AccountingEngine.encryptBackupText(plain, cleanPass)
                outputBytes = encrypted.toByteArray(Charsets.UTF_8)
                fileName = "mamo_backup_$now.mamo.json"
                backupType = "MANUAL_ENCRYPTED"
                notesText = "تصدير يدوي مشفر AES-256-GCM"
            }
            else -> {
                outputBytes = plain.toByteArray(Charsets.UTF_8)
                fileName = "mamo_backup_$now.json"
                backupType = "MANUAL_JSON"
                notesText = "تصدير يدوي JSON"
            }
        }

        val record = BackupRecordEntity(
            backupType = backupType,
            fileName = fileName,
            isEncrypted = useEncryption,
            checksumSha256 = checksum,
            recordCount = count,
            status = "SUCCESS",
            notes = notesText,
            createdBy = actingStaff.name,
            createdAtMs = now
        )
        uDao.insertBackupRecord(record)
        return outputBytes to record
    }

    suspend fun importBackupBytes(
        bytes: ByteArray,
        passphrase: String?,
        actingStaff: StaffUserEntity
    ) {
        val text = if (AccountingEngine.isGzipBytes(bytes)) {
            String(AccountingEngine.decompressGzip(bytes), Charsets.UTF_8)
        } else {
            String(bytes, Charsets.UTF_8)
        }
        importBackupContent(text, passphrase, actingStaff)
    }

    private suspend fun collectBackupPayload(): BackupPayload {
        val settings = dao.getStoreSettings() ?: StoreSettingsEntity()
        return BackupPayload(
            settings = settings,
            staffUsers = dao.getAllStaffUsers(),
            products = dao.getAllProducts(),
            parties = dao.getAllParties(),
            invoices = dao.getAllInvoices(),
            invoiceItems = dao.getAllInvoiceItems(),
            stockMovements = dao.getAllStockMovements(),
            cashTransactions = dao.getAllCashTransactions(),
            expenses = dao.getAllExpenses(),
            debtPayments = dao.getAllDebtPayments(),
            auditLogs = dao.getAllAuditLogs(),
            productUnits = uDao.getAllProductUnits(),
            customerSpecialPrices = uDao.getAllCustomerSpecialPrices(),
            priceHistory = uDao.getAllPriceHistory(),
            cashAccounts = uDao.getAllCashAccounts(),
            cashboxClosings = uDao.getAllCashboxClosings(),
            journalEntries = uDao.getAllJournalEntries(),
            journalEntryLines = uDao.getAllJournalEntryLines(),
            stockCounts = uDao.getAllStockCounts(),
            returns = uDao.getAllReturns(),
            accountingPeriods = uDao.getAllAccountingPeriods()
        )
    }

    suspend fun importBackupContent(content: String, passphrase: String?, actingStaff: StaffUserEntity) {
        val trimmed = content.trim()
        val plainJson = when {
            trimmed.contains("\"MAMO_GZIP_ENCRYPTED_BACKUP_V1\"") -> {
                val pass = passphrase?.trim().orEmpty()
                require(pass.isNotBlank()) { "هذه النسخة المضغوطة مشفرة — يرجى إدخال كلمة مرور فك التشفير" }
                AccountingEngine.decryptCompressedBackup(trimmed, pass)
            }
            trimmed.contains("\"MAMO_ENCRYPTED_BACKUP_V1\"") -> {
                val pass = passphrase?.trim().orEmpty()
                require(pass.isNotBlank()) { "هذه النسخة مشفرة — يرجى إدخال كلمة مرور فك التشفير" }
                AccountingEngine.decryptBackupText(trimmed, pass)
            }
            else -> trimmed
        }
        importJsonBackup(plainJson, actingStaff)
    }

    suspend fun importJsonBackup(jsonString: String, actingStaff: StaffUserEntity) {
        require(actingStaff.canManageSettings || actingStaff.role == StaffRole.OWNER) {
            "استعادة النسخة الاحتياطية متاحة للإدارة فقط"
        }
        val payload = BackupJsonSerializer.fromJson(jsonString)
        db.withTransaction {
            dao.clearInvoiceItems()
            dao.clearInvoices()
            dao.clearStockMovements()
            dao.clearCashTransactions()
            dao.clearExpenses()
            dao.clearDebtPayments()
            dao.clearAuditLogs()
            dao.clearProducts()
            dao.clearParties()
            dao.clearStaffUsers()
            ultimate.clearAllUltimateTables()

            dao.upsertStoreSettings(payload.settings)
            payload.cashAccounts.forEach { uDao.insertCashAccount(it) }
            ultimate.ensureDefaultCashAccount()
            payload.staffUsers.forEach { dao.insertStaffUser(it) }
            payload.products.forEach { dao.insertProduct(it) }
            payload.productUnits.forEach { uDao.insertProductUnit(it) }
            payload.parties.forEach { dao.insertParty(it) }
            payload.customerSpecialPrices.forEach { uDao.insertCustomerSpecialPrice(it) }
            payload.priceHistory.forEach { uDao.insertPriceHistory(it) }
            payload.invoices.forEach { dao.insertInvoice(it) }
            dao.insertInvoiceItems(payload.invoiceItems)
            payload.stockMovements.forEach { dao.insertStockMovement(it) }
            payload.cashTransactions.forEach { dao.insertCashTransaction(it) }
            payload.expenses.forEach { dao.insertExpense(it) }
            payload.debtPayments.forEach { dao.insertDebtPayment(it) }
            payload.cashboxClosings.forEach { uDao.insertCashboxClosing(it) }
            payload.journalEntries.forEach { uDao.insertJournalEntry(it) }
            if (payload.journalEntryLines.isNotEmpty()) uDao.insertJournalEntryLines(payload.journalEntryLines)
            payload.stockCounts.forEach { uDao.insertStockCount(it) }
            payload.returns.forEach { uDao.insertReturn(it) }
            payload.accountingPeriods.forEach { uDao.insertAccountingPeriod(it) }
            payload.auditLogs.forEach { dao.insertAuditLog(it) }

            val recCount = BackupJsonSerializer.totalRecordCount(payload)
            uDao.insertBackupRecord(
                BackupRecordEntity(
                    backupType = "RESTORE",
                    fileName = "restored_${System.currentTimeMillis()}.json",
                    isEncrypted = false,
                    checksumSha256 = AccountingEngine.sha256Hex(jsonString),
                    recordCount = recCount,
                    status = "SUCCESS",
                    notes = "استعادة ${payload.products.size} صنف و ${payload.invoices.size} فاتورة",
                    createdBy = actingStaff.name
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "استعادة نسخة احتياطية JSON",
                    entityType = "BACKUP",
                    entityId = "restore",
                    details = "تمت استعادة ${payload.products.size} صنف و ${payload.invoices.size} فاتورة",
                    staffName = actingStaff.name
                )
            )
        }
    }

    suspend fun setProductArchived(productId: Long, archived: Boolean, actingStaff: StaffUserEntity) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val product = dao.getProductById(productId) ?: error("الصنف غير موجود")
            dao.updateProduct(product.copy(isArchived = archived, updatedAtMs = now, isSynced = false))
            dao.insertAuditLog(
                AuditLogEntity(
                    action = if (archived) "أرشفة صنف من لوحة قاعدة البيانات" else "استعادة صنف مؤرشف",
                    entityType = "DB_ADMIN",
                    entityId = product.syncId,
                    details = "${product.name} (ID=${product.id})",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("PRODUCT", product.syncId)
        }
    }

    suspend fun adminUpdateStaffStatusAndPin(
        staffId: Long,
        newPin: String?,
        isActive: Boolean,
        actingStaff: StaffUserEntity
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val target = dao.getStaffById(staffId) ?: error("الموظف غير موجود")
            if (target.id == actingStaff.id && !isActive) {
                throw IllegalStateException("لا يمكن تعطيل حساب الأدمن النشط حالياً")
            }
            val pinHash = if (!newPin.isNullOrBlank()) {
                require(newPin.length >= 4) { "رمز PIN يجب أن يكون 4 أرقام على الأقل" }
                AccountingEngine.hashPin(newPin)
            } else {
                target.pinHash
            }
            dao.updateStaffUser(target.copy(pinHash = pinHash, isActive = isActive, isSynced = false))
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "إدارة حساب موظف من لوحة قاعدة البيانات",
                    entityType = "DB_ADMIN",
                    entityId = target.syncId,
                    details = "${target.name} | نشط: $isActive | تغيير PIN: ${!newPin.isNullOrBlank()}",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            ultimate.enqueueSync("STAFF_USER", target.syncId)
        }
    }

    suspend fun markAllRecordsForResync(actingStaff: StaffUserEntity) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.markAllProductsUnsynced()
            dao.markAllPartiesUnsynced()
            dao.markAllInvoicesUnsynced()
            dao.markAllCashTxUnsynced()
            dao.markAllExpensesUnsynced()
            dao.markAllDebtPaymentsUnsynced()
            dao.markAllStockMovementsUnsynced()
            dao.markAllStaffUnsynced()
            dao.markAllAuditLogsUnsynced()
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "جدولة إعادة مزامنة شاملة للسحابة",
                    entityType = "DB_ADMIN",
                    entityId = "resync_all",
                    details = "تم تعليم كافة السجلات المحلية لإعادة التحقق والمزامنة السحابية",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
        }
    }

    suspend fun runDatabaseIntegrityReport(actingStaff: StaffUserEntity): DatabaseIntegrityReport {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val products = dao.getAllProducts()
            val parties = dao.getAllParties()
            val invoices = dao.getAllInvoices()
            val cashTxs = dao.getAllCashTransactions()
            val expenses = dao.getAllExpenses()
            val debtPayments = dao.getAllDebtPayments()
            val stockMovements = dao.getAllStockMovements()
            val staffUsers = dao.getAllStaffUsers()
            val auditLogs = dao.getAllAuditLogs()
            val journals = uDao.getAllJournalEntries()
            val returnsList = uDao.getAllReturns()

            val cashQueryBalance = dao.getCashboxBalanceSync()
            val cashLedgerSum = AccountingEngine.calculateCashboxBalance(cashTxs.map { it.amountSigned })
            val isCashOk = AccountingEngine.isCashboxReconciled(cashQueryBalance, cashLedgerSum)

            val activeProducts = products.filter { !it.isArchived }
            val inventoryValue = activeProducts.sumOf { it.quantity * it.averageCost }
            val receivables = parties.filter { it.partyType == PartyType.CUSTOMER }.sumOf { it.balanceDue }
            val payables = parties.filter { it.partyType == PartyType.SUPPLIER }.sumOf { it.balanceDue }
            val assetsNetWorth = cashQueryBalance + inventoryValue + receivables - payables

            val injected = cashTxs.filter { it.txType == CashTxType.CAPITAL_IN }.sumOf { it.amountSigned }
            val drawings = cashTxs.filter { it.txType == CashTxType.PERSONAL_DRAWING }.sumOf { -it.amountSigned }
            val postedSales = invoices.filter { it.status == InvoiceStatus.POSTED && it.invoiceType == InvoiceType.SALE }
            val totalRev = postedSales.sumOf { it.netTotal }
            val totalCogs = postedSales.sumOf { it.totalCost }
            val totalExp = expenses.sumOf { it.amount }
            val otherIncome = cashTxs.filter { it.txType == CashTxType.OTHER_INCOME }.sumOf { it.amountSigned }
            val otherOutflow = cashTxs.filter { it.txType == CashTxType.OTHER_OUTFLOW }.sumOf { -it.amountSigned }
            val cumulativeProfit = AccountingEngine.calculateNetProfit(totalRev + otherIncome, totalCogs, totalExp + otherOutflow)
            val inventoryAdjustmentsValue = stockMovements
                .filter { it.movementType == MovementType.ADJUSTMENT || it.movementType == MovementType.DAMAGED }
                .sumOf { it.quantityDelta * it.unitCost }
            val equityCheck = injected - drawings + cumulativeProfit + inventoryAdjustmentsValue
            val isEquityBalanced = abs(assetsNetWorth - equityCheck) < 1.0

            val unbalancedJournals = journals.count { abs(it.totalDebit - it.totalCredit) >= 0.01 }
            val negativeStockCount = products.count { it.quantity < 0.0 }
            val archivedCount = products.count { it.isArchived }
            val unsyncedCount =
                products.count { !it.isSynced } +
                    parties.count { !it.isSynced } +
                    invoices.count { !it.isSynced } +
                    cashTxs.count { !it.isSynced } +
                    expenses.count { !it.isSynced } +
                    debtPayments.count { !it.isSynced } +
                    stockMovements.count { !it.isSynced } +
                    staffUsers.count { !it.isSynced } +
                    auditLogs.count { !it.isSynced }

            val totalRecords =
                products.size + parties.size + invoices.size + cashTxs.size +
                    expenses.size + debtPayments.size + stockMovements.size +
                    staffUsers.size + auditLogs.size + journals.size + returnsList.size

            val report = DatabaseIntegrityReport(
                checkedAtMs = now,
                cashboxQueryBalance = cashQueryBalance,
                cashboxLedgerSum = cashLedgerSum,
                isCashboxReconciled = isCashOk,
                assetsNetWorth = assetsNetWorth,
                equityCheck = equityCheck,
                isEquityBalanced = isEquityBalanced,
                negativeStockProductsCount = negativeStockCount,
                archivedProductsCount = archivedCount,
                unsyncedTotalCount = unsyncedCount,
                unbalancedJournalCount = unbalancedJournals,
                totalRecordsCount = totalRecords
            )

            dao.insertAuditLog(
                AuditLogEntity(
                    action = "فحص سلامة وتطابق قاعدة البيانات",
                    entityType = "DB_ADMIN",
                    entityId = "integrity_check",
                    details = "السجلات: $totalRecords | غير متزامن: $unsyncedCount | تطابق الصندوق: $isCashOk | قيود غير متوازنة: $unbalancedJournals",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            report
        }
    }

    suspend fun mergeMissingFromCloud(
        cloudProducts: List<SyncedProductDoc>,
        cloudParties: List<SyncedPartyDoc>,
        actingStaff: StaffUserEntity
    ): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            var addedProducts = 0
            for (cp in cloudProducts) {
                if (cp.syncId.isNotBlank() && dao.getProductBySyncId(cp.syncId) == null) {
                    dao.insertProduct(
                        ProductEntity(
                            syncId = cp.syncId,
                            name = cp.name,
                            category = cp.category,
                            barcode = cp.barcode.orEmpty(),
                            averageCost = cp.averageCost,
                            lastPurchaseCost = cp.averageCost,
                            sellingPrice = cp.sellingPrice,
                            wholesalePrice = cp.sellingPrice,
                            minSellingPrice = cp.averageCost,
                            quantity = cp.quantity,
                            minStockLevel = cp.minStockLevel,
                            isArchived = cp.isArchived,
                            createdAtMs = now,
                            updatedAtMs = now,
                            isSynced = true
                        )
                    )
                    addedProducts++
                }
            }

            var addedParties = 0
            for (cpt in cloudParties) {
                if (cpt.syncId.isNotBlank() && dao.getPartyBySyncId(cpt.syncId) == null) {
                    dao.insertParty(
                        PartyEntity(
                            syncId = cpt.syncId,
                            partyType = cpt.partyType,
                            name = cpt.name,
                            phone = cpt.phone.orEmpty(),
                            totalAmount = cpt.totalAmount,
                            paidAmount = cpt.paidAmount,
                            balanceDue = cpt.balanceDue,
                            createdAtMs = now,
                            updatedAtMs = now,
                            isSynced = true
                        )
                    )
                    addedParties++
                }
            }

            dao.insertAuditLog(
                AuditLogEntity(
                    action = "جلب السجلات المفقودة من السحابة",
                    entityType = "DB_ADMIN",
                    entityId = "cloud_merge",
                    details = "تم استيراد $addedProducts أصناف و $addedParties أطراف من Firestore",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
            addedProducts to addedParties
        }
    }

    suspend fun resetTransactionalDatabase(adminPin: String, actingStaff: StaffUserEntity) {
        require(actingStaff.role == StaffRole.OWNER) { "تصفير قاعدة البيانات متاح للمدير العام فقط" }
        val verified = verifyStaffPin(actingStaff.id, adminPin)
            ?: throw IllegalArgumentException("رمز المرور PIN غير صحيح")
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.clearInvoiceItems()
            dao.clearInvoices()
            dao.clearStockMovements()
            dao.clearCashTransactions()
            dao.clearExpenses()
            dao.clearDebtPayments()
            dao.clearAuditLogs()
            dao.clearProducts()
            dao.clearParties()
            ultimate.clearAllUltimateTables()
            ultimate.ensureDefaultCashAccount()
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "إعادة تصفير قاعدة البيانات بالكامل",
                    entityType = "DB_ADMIN",
                    entityId = "reset_db",
                    details = "تم مسح جميع المعاملات والأصناف والفواتير مع الإبقاء على الإعدادات وحسابات الموظفين",
                    staffName = verified.name,
                    occurredAtMs = now
                )
            )
        }
    }

    suspend fun resetSyncedFlagsForDeletedCloudCollections(
        collections: Set<CloudCollectionKind>,
        wipeProfile: Boolean,
        deletedDocsCount: Int,
        actingStaff: StaffUserEntity
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            if (CloudCollectionKind.PRODUCTS in collections) {
                dao.markAllProductsUnsynced()
            }
            if (CloudCollectionKind.PARTIES in collections) {
                dao.markAllPartiesUnsynced()
            }
            if (CloudCollectionKind.INVOICES in collections) {
                dao.markAllInvoicesUnsynced()
            }
            if (CloudCollectionKind.LEDGER_ENTRIES in collections) {
                dao.markAllCashTxUnsynced()
                dao.markAllExpensesUnsynced()
                dao.markAllDebtPaymentsUnsynced()
                dao.markAllStockMovementsUnsynced()
                dao.markAllStaffUnsynced()
                dao.markAllAuditLogsUnsynced()
            }
            if (wipeProfile) {
                val s = dao.getStoreSettings() ?: StoreSettingsEntity()
                dao.upsertStoreSettings(s.copy(lastSyncAtMs = 0L, updatedAtMs = now))
            }
            val scopeLabels = collections.joinToString("، ") { it.arabicLabel } +
                if (wipeProfile) " + ملف المتجر" else ""
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "حذف بيانات من السحابة Firebase",
                    entityType = "CLOUD_ADMIN",
                    entityId = if (wipeProfile) "cloud_wipe_all" else "cloud_wipe_selective",
                    details = "تم حذف $deletedDocsCount سجل من Firestore ($scopeLabels) مع بقاء القاعدة المحلية سليمة",
                    staffName = actingStaff.name,
                    occurredAtMs = now
                )
            )
        }
    }

    suspend fun measureDatabaseSize(context: Context): DatabaseStorageInfo = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("mamo_accounting.db")
        val walFile = File("${dbFile.path}-wal")
        val shmFile = File("${dbFile.path}-shm")
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L
        val walBytes = if (walFile.exists()) walFile.length() else 0L
        val shmBytes = if (shmFile.exists()) shmFile.length() else 0L
        DatabaseStorageInfo(
            dbFileBytes = dbBytes,
            walFileBytes = walBytes,
            shmFileBytes = shmBytes,
            totalBytes = dbBytes + walBytes + shmBytes,
            auditLogsCount = dao.countAuditLogs(),
            syncQueueCount = uDao.countSyncQueueItems(),
            priceHistoryCount = uDao.countPriceHistory(),
            aiLogsCount = uDao.countAiLogs(),
            measuredAtMs = System.currentTimeMillis()
        )
    }

    suspend fun compactAndPruneDatabase(
        context: Context,
        retentionDays: Int,
        actingStaff: StaffUserEntity
    ): DatabaseCompressionReport {
        require(actingStaff.role == StaffRole.OWNER || actingStaff.canManageSettings) {
            "ضغط وتنظيف قاعدة البيانات متاح للأدمن فقط"
        }
        val safeDays = retentionDays.coerceAtLeast(1)
        val beforeInfo = measureDatabaseSize(context)
        val now = System.currentTimeMillis()
        val cutoffMs = now - safeDays.toLong() * 86_400_000L

        var prunedAudit = 0
        var prunedSync = 0
        var prunedPrices = 0
        var prunedAi = 0

        db.withTransaction {
            prunedSync = uDao.pruneSyncedQueueItemsBefore(cutoffMs)
            prunedAudit = dao.pruneAuditLogsBefore(cutoffMs)
            prunedPrices = uDao.prunePriceHistoryBefore(cutoffMs)
            prunedAi = uDao.pruneAiLogsBefore(cutoffMs)
        }

        // PRAGMA wal_checkpoint & SQLite VACUUM MUST execute outside any Room transaction
        withContext(Dispatchers.IO) {
            val writable = db.openHelper.writableDatabase
            writable.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                cursor.moveToFirst()
            }
            writable.execSQL("VACUUM")
        }

        val midInfo = measureDatabaseSize(context)
        val midSaved = (beforeInfo.totalBytes - midInfo.totalBytes).coerceAtLeast(0L)

        db.withTransaction {
            dao.insertAuditLog(
                AuditLogEntity(
                    action = "تنظيف وضغط قاعدة البيانات (VACUUM)",
                    entityType = "DB_ADMIN",
                    entityId = "vacuum",
                    details = "تنظيف ${prunedAudit + prunedSync + prunedPrices + prunedAi} سجل أقدم من $safeDays يوم | الحجم قبل: ${beforeInfo.totalBytes} بايت | بعد: ${midInfo.totalBytes} بايت | التوفير: $midSaved بايت",
                    staffName = actingStaff.name,
                    occurredAtMs = System.currentTimeMillis()
                )
            )
        }

        withContext(Dispatchers.IO) {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                cursor.moveToFirst()
            }
        }

        val afterInfo = measureDatabaseSize(context)
        val saved = (beforeInfo.totalBytes - afterInfo.totalBytes).coerceAtLeast(0L)
        val pct = if (beforeInfo.totalBytes > 0L) {
            AccountingEngine.roundMoney((saved.toDouble() / beforeInfo.totalBytes.toDouble()) * 100.0)
        } else 0.0

        return DatabaseCompressionReport(
            sizeBeforeBytes = beforeInfo.totalBytes,
            sizeAfterBytes = afterInfo.totalBytes,
            savedBytes = saved,
            savedPercent = pct,
            deletedAuditLogs = prunedAudit,
            deletedSyncItems = prunedSync,
            deletedPriceHistory = prunedPrices,
            deletedAiLogs = prunedAi,
            retentionDays = safeDays,
            completedAtMs = System.currentTimeMillis()
        )
    }

    suspend fun checkpointDatabaseWal() = withContext(Dispatchers.IO) {
        runCatching {
            val writable = db.openHelper.writableDatabase
            writable.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                cursor.moveToFirst()
            }
        }
    }
}
