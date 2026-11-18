package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.StaffRole
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.UiMessage

enum class ErpSection(
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("الرئيسية", Icons.Default.Dashboard),
    SALES("المبيعات", Icons.AutoMirrored.Filled.ReceiptLong),
    PURCHASES("المشتريات", Icons.Default.AddShoppingCart),
    INVENTORY("المخزون", Icons.Default.Inventory2),
    PARTIES("الأطراف والديون", Icons.Default.People),
    CASH_EXPENSES("الصندوق والمصاريف", Icons.Default.AccountBalance),
    REPORTS("التقارير", Icons.Default.Assessment),
    AI_ASSISTANT("المساعد الذكي", Icons.Default.AutoAwesome),
    SETTINGS("الإعدادات", Icons.Default.Settings),
    DATABASE_ADMIN("قاعدة البيانات", Icons.Default.Storage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainErpShell(
    state: MamoMainUiState,
    viewModel: MamoViewModel,
    googleEmail: String?,
    isGuestMode: Boolean = false,
    onLockStaff: () -> Unit,
    onSignOutGoogle: () -> Unit
) {
    var currentSection by remember { mutableStateOf(ErpSection.DASHBOARD) }
    var openSaleCreator by remember { mutableStateOf(false) }
    var openPurchaseCreator by remember { mutableStateOf(false) }
    var openExpenseDialog by remember { mutableStateOf(false) }
    var partiesInitialTab by remember { mutableIntStateOf(0) }
    var cashInitialTab by remember { mutableIntStateOf(0) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = currentSection != ErpSection.DASHBOARD) {
        currentSection = ErpSection.DASHBOARD
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
                is UiMessage.Info -> snackbarHostState.showSnackbar(msg.text)
                is UiMessage.Error -> snackbarHostState.showSnackbar("تنبيه: ${msg.text}")
            }
        }
    }

    val isEmailAdmin = !isGuestMode && !googleEmail.isNullOrBlank() && state.activeStaff?.role == StaffRole.OWNER
    val pendingApprovalsCount = remember(state.staffUsers) {
        state.staffUsers.count { it.approvalStatus == com.example.data.local.ApprovalStatus.PENDING }
    }

    val primaryTabs = listOf(
        ErpSection.DASHBOARD,
        ErpSection.SALES,
        ErpSection.INVENTORY,
        ErpSection.PARTIES,
        ErpSection.CASH_EXPENSES
    )
    val overflowTabs = buildList {
        add(ErpSection.PURCHASES)
        add(ErpSection.REPORTS)
        add(ErpSection.AI_ASSISTANT)
        add(ErpSection.SETTINGS)
        if (isEmailAdmin) add(ErpSection.DATABASE_ADMIN)
    }

    val density = LocalDensity.current
    var isWide by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val wide = with(density) { size.width.toDp() >= 840.dp }
                if (wide != isWide) isWide = wide
            }
    ) {

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "${state.settings.storeName} — ${currentSection.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            val roleText = state.activeStaff?.let { "${it.name} • ${com.example.utils.ArabicFormatters.staffRoleLabel(it.role)}" } ?: "وضع التصفح"
                            Text(
                                text = if (state.dashboardMetrics.pendingSyncCount > 0)
                                    "$roleText • بانتظار المزامنة (${state.dashboardMetrics.pendingSyncCount})"
                                else roleText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(
                            onClick = { currentSection = ErpSection.AI_ASSISTANT },
                            modifier = Modifier.testTag("ai_assistant_topbar_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "المساعد المحاسبي الذكي GPT",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (isEmailAdmin) {
                            IconButton(
                                onClick = { currentSection = ErpSection.DATABASE_ADMIN },
                                modifier = Modifier.testTag("database_admin_topbar_btn")
                            ) {
                                androidx.compose.material3.BadgedBox(
                                    badge = {
                                        if (pendingApprovalsCount > 0) {
                                            androidx.compose.material3.Badge {
                                                Text(pendingApprovalsCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = "لوحة تحكم قاعدة البيانات",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.toggleDarkTheme() }) {
                            Icon(
                                imageVector = if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "تبديل الوضع الليلي والنهاري"
                            )
                        }
                        if (!isWide) {
                            Box {
                                IconButton(
                                    onClick = { moreMenuExpanded = true },
                                    modifier = Modifier.testTag("more_sections_menu")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "أقسام أخرى")
                                }
                                DropdownMenu(
                                    expanded = moreMenuExpanded,
                                    onDismissRequest = { moreMenuExpanded = false }
                                ) {
                                    overflowTabs.forEach { sec ->
                                        val label = if (sec == ErpSection.DATABASE_ADMIN && pendingApprovalsCount > 0) {
                                            "${sec.title} ($pendingApprovalsCount)"
                                        } else sec.title
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            leadingIcon = { Icon(sec.icon, contentDescription = null) },
                                            onClick = {
                                                currentSection = sec
                                                moreMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWide) {
                    NavigationBar {
                        primaryTabs.forEach { sec ->
                            NavigationBarItem(
                                selected = currentSection == sec,
                                onClick = { currentSection = sec },
                                icon = { Icon(sec.icon, contentDescription = sec.title) },
                                label = { Text(sec.title, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWide) {
                    NavigationRail {
                        ErpSection.entries
                            .filter { it != ErpSection.DATABASE_ADMIN || isEmailAdmin }
                            .forEach { sec ->
                                val showPendingBadge = sec == ErpSection.DATABASE_ADMIN && pendingApprovalsCount > 0
                                NavigationRailItem(
                                    selected = currentSection == sec,
                                    onClick = { currentSection = sec },
                                    icon = {
                                        if (showPendingBadge) {
                                            androidx.compose.material3.BadgedBox(
                                                badge = {
                                                    androidx.compose.material3.Badge {
                                                        Text(pendingApprovalsCount.toString())
                                                    }
                                                }
                                            ) {
                                                Icon(sec.icon, contentDescription = sec.title)
                                            }
                                        } else {
                                            Icon(sec.icon, contentDescription = sec.title)
                                        }
                                    },
                                    label = { Text(sec.title) }
                                )
                            }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (isGuestMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "وضع الزائر (تصفح محلي فقط)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = onLockStaff) {
                                    Text("تسجيل الدخول", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (currentSection) {
                        ErpSection.DASHBOARD -> DashboardScreen(
                            state = state,
                            viewModel = viewModel,
                            onQuickNewSale = {
                                openSaleCreator = true
                                currentSection = ErpSection.SALES
                            },
                            onQuickNewPurchase = {
                                openPurchaseCreator = true
                                currentSection = ErpSection.PURCHASES
                            },
                            onQuickNewExpense = {
                                cashInitialTab = 1
                                openExpenseDialog = true
                                currentSection = ErpSection.CASH_EXPENSES
                            },
                            onQuickDebtPayment = {
                                partiesInitialTab = 2
                                currentSection = ErpSection.PARTIES
                            },
                            onNavigateInventoryAlerts = {
                                currentSection = ErpSection.INVENTORY
                            },
                            onNavigateDebts = {
                                partiesInitialTab = 2
                                currentSection = ErpSection.PARTIES
                            }
                        )
                        ErpSection.SALES -> SalesScreen(
                            state = state,
                            viewModel = viewModel,
                            openCreatorInitially = openSaleCreator,
                            onCreatorHandled = { openSaleCreator = false }
                        )
                        ErpSection.PURCHASES -> PurchasesScreen(
                            state = state,
                            viewModel = viewModel,
                            openCreatorInitially = openPurchaseCreator,
                            onCreatorHandled = { openPurchaseCreator = false }
                        )
                        ErpSection.INVENTORY -> InventoryScreen(state = state, viewModel = viewModel)
                        ErpSection.PARTIES -> PartiesAndDebtsScreen(
                            state = state,
                            viewModel = viewModel,
                            initialTab = partiesInitialTab
                        )
                        ErpSection.CASH_EXPENSES -> CashAndExpensesScreen(
                            state = state,
                            viewModel = viewModel,
                            initialTab = cashInitialTab,
                            openExpenseDialogInitially = openExpenseDialog,
                            onExpenseDialogHandled = { openExpenseDialog = false }
                        )
                        ErpSection.REPORTS -> ReportsScreen(state = state, viewModel = viewModel)
                        ErpSection.AI_ASSISTANT -> AiAssistantScreen(state = state, viewModel = viewModel)
                        ErpSection.SETTINGS -> SettingsAndSecurityScreen(
                            state = state,
                            viewModel = viewModel,
                            googleEmail = googleEmail,
                            onOpenDatabaseAdmin = if (isEmailAdmin) {
                                { currentSection = ErpSection.DATABASE_ADMIN }
                            } else null,
                            onLockStaff = onLockStaff,
                            onSignOutGoogle = onSignOutGoogle
                        )
                        ErpSection.DATABASE_ADMIN -> DatabaseAdminScreen(
                            state = state,
                            viewModel = viewModel,
                            googleEmail = googleEmail
                        )
                    }
                    }
                }
            }
        }
    }
}
