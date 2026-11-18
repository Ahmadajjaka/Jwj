package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.StaffRole
import com.example.ui.MamoAppDependencies
import com.example.ui.MamoStartupContainer
import com.example.ui.MamoViewModel
import com.example.ui.auth.MamoLuxuryLoginScreen
import com.example.ui.auth.OwnerSetupScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutGoogle
import com.example.ui.screens.MainErpShell
import com.example.ui.theme.LocalMamoColors
import com.example.ui.theme.MamoTheme
import com.example.utils.DeviceLocalBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MamoAppRoot()
        }
    }

    override fun onStop() {
        super.onStop()
        val deps = MamoStartupContainer.currentOrNull() ?: return
        val appCtx = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                DeviceLocalBackupManager.persistInstantDeviceSnapshot(
                    context = appCtx,
                    repo = deps.repo,
                    triggerReason = "حفظ تلقائي عند الخروج أو تصغير التطبيق"
                )
            }
        }
    }
}

@Composable
fun MamoAppRoot() {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    var deps by remember { mutableStateOf(MamoStartupContainer.currentOrNull()) }
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(appContext) {
        if (deps == null) {
            deps = MamoStartupContainer.warmUp(appContext)
        }
        delay(420)
        showSplash = false
    }

    val loadedDeps = deps
    if (loadedDeps == null) {
        MamoTheme(darkTheme = false) {
            MamoSplashScreen()
        }
        return
    }

    MamoLoadedRoot(
        deps = loadedDeps,
        showSplash = showSplash
    )
}

@Composable
private fun MamoLoadedRoot(
    deps: MamoAppDependencies,
    showSplash: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by deps.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = deps.auth.currentUser)

    val sessionKey = currentUser?.uid ?: "local"
    key(sessionKey) {
        val viewModel: MamoViewModel = viewModel(
            key = sessionKey,
            factory = MamoViewModel.provideFactory(context, sessionKey)
        )
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val isSessionReady by viewModel.isSessionReady.collectAsStateWithLifecycle()
        val rememberMe by viewModel.rememberMe.collectAsStateWithLifecycle()
        val savedIdentifier by viewModel.savedLoginIdentifier.collectAsStateWithLifecycle()
        val isGuestSession by viewModel.isGuestSession.collectAsStateWithLifecycle()
        val loginError by viewModel.loginError.collectAsStateWithLifecycle()
        val loginInfoMessage by viewModel.loginInfoMessage.collectAsStateWithLifecycle()
        val shakeTick by viewModel.loginShakeTick.collectAsStateWithLifecycle()
        val welcomeMessage by viewModel.loginWelcomeMessage.collectAsStateWithLifecycle()

        LaunchedEffect(uiState.activeStaff?.id) {
            if (uiState.activeStaff != null) {
                viewModel.runDailyAutoBackupCheck(context)
            }
        }

        val handleGoogleSignOut: () -> Unit = {
            viewModel.lockStaffSession()
            if (currentUser != null) {
                signOutGoogle(deps.credentialManager, scope)
            }
        }

        val activeDarkTheme = if (!isSessionReady || showSplash) deps.initialDarkTheme else uiState.isDarkTheme
        MamoTheme(darkTheme = activeDarkTheme) {
            if (!isSessionReady || showSplash) {
                MamoSplashScreen()
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        currentUser != null && uiState.staffUsers.isEmpty() && !isGuestSession -> {
                            OwnerSetupScreen(
                                googleEmail = currentUser?.email,
                                onCreateOwner = { name, pin -> viewModel.createOwner(name, pin) },
                                onSignOutGoogle = handleGoogleSignOut
                            )
                        }
                        uiState.activeStaff == null && !isGuestSession -> {
                            MamoLuxuryLoginScreen(
                                isDarkTheme = uiState.isDarkTheme,
                                savedIdentifier = savedIdentifier,
                                rememberMeInitial = rememberMe,
                                loginError = loginError,
                                loginInfoMessage = loginInfoMessage,
                                shakeTick = shakeTick,
                                googleEmail = currentUser?.email,
                                staffList = uiState.staffUsers,
                                credentialManager = deps.credentialManager,
                                onToggleTheme = { viewModel.toggleDarkTheme() },
                                onCredentialsLogin = { id, pass, rem ->
                                    viewModel.loginWithCredentials(id, pass, rem, currentUser?.email)
                                },
                                onCreateAccount = { fullName, id, pass, role, ownerPin, onDone ->
                                    viewModel.registerNewAccount(
                                        fullName = fullName,
                                        identifier = id,
                                        password = pass,
                                        role = role,
                                        ownerVerifyPin = ownerPin,
                                        onSuccess = onDone
                                    )
                                },
                                onRecoverPassword = { id, newPass, ownerPin, onDone ->
                                    viewModel.recoverPassword(
                                        identifier = id,
                                        newPassword = newPass,
                                        ownerVerifyPin = ownerPin,
                                        googleEmail = currentUser?.email,
                                        onSuccess = onDone
                                    )
                                },
                                onEnterGuestMode = { viewModel.enterGuestMode() },
                                onGoogleAuthSuccess = {
                                    val singleOwner = uiState.staffUsers.singleOrNull {
                                        it.isActive && it.role == StaffRole.OWNER && it.approvalStatus == com.example.data.local.ApprovalStatus.APPROVED
                                    }
                                    if (singleOwner != null) {
                                        viewModel.exitGuestMode()
                                    }
                                },
                                onClearError = { viewModel.clearLoginError() }
                            )
                        }
                        else -> {
                            MainErpShell(
                                state = uiState,
                                viewModel = viewModel,
                                googleEmail = currentUser?.email,
                                isGuestMode = isGuestSession,
                                onLockStaff = { viewModel.lockStaffSession() },
                                onSignOutGoogle = handleGoogleSignOut
                            )
                        }
                    }

                    // Elegant animated Welcome Toast Banner after successful login
                    LaunchedEffect(welcomeMessage) {
                        if (!welcomeMessage.isNullOrBlank()) {
                            delay(3400)
                            viewModel.consumeWelcomeMessage()
                        }
                    }
                    AnimatedVisibility(
                        visible = !welcomeMessage.isNullOrBlank(),
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .safeDrawingPadding()
                            .padding(top = 12.dp, start = 18.dp, end = 18.dp)
                    ) {
                        val mamoColors = LocalMamoColors.current
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF0A1D3D),
                            border = BorderStroke(1.2.dp, mamoColors.gold)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = mamoColors.gold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = welcomeMessage.orEmpty(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
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

@Composable
private fun MamoSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF061124), Color(0xFF0D2346), Color(0xFF08162E))
                )
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF102A54),
                border = BorderStroke(2.dp, Color(0xFFD4AF37)),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Handyman,
                        contentDescription = "شعار خردوات المامو",
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "MAMO ACCOUNTING",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFD4AF37)
            )
            Text(
                text = "خردوات المامو — النظام المحاسبي المتكامل",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "إدارة وإشراف: أحمد نوار المامو",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB7C7DF)
            )
        }
    }
}
