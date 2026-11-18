package com.example.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.StaffRole
import com.example.data.local.StaffUserEntity
import kotlin.math.roundToInt

private val MamoDeepMidnight = Color(0xFF050E1E)
private val MamoRoyalNavy = Color(0xFF0A1D3D)
private val MamoElectricBlue = Color(0xFF1E88FF)
private val MamoCyanGlow = Color(0xFF38BDF8)
private val MamoPearlWhite = Color(0xFFF8FAFC)
private val MamoGoldWarm = Color(0xFFE5B84B)
private val MamoOrangeAccent = Color(0xFFF59E0B)
private val MamoOrangeWarm = Color(0xFFFB923C)
private val MamoErrorRed = Color(0xFFEF4444)

private data class LoginStrings(
    val storeTitleAr: String,
    val storeTitleEn: String,
    val welcomeHeadline: String,
    val welcomeSubline: String,
    val identifierLabel: String,
    val passwordLabel: String,
    val rememberMe: String,
    val forgotPassword: String,
    val loginButton: String,
    val orDivider: String,
    val googleButton: String,
    val createAccount: String,
    val guestMode: String,
    val signaturePrefix: String,
    val signatureOwner: String,
    val signatureOwnerEn: String,
    val registeredHint: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MamoLuxuryLoginScreen(
    isDarkTheme: Boolean,
    savedIdentifier: String,
    rememberMeInitial: Boolean,
    loginError: String?,
    loginInfoMessage: String? = null,
    shakeTick: Int,
    googleEmail: String?,
    staffList: List<StaffUserEntity>,
    credentialManager: CredentialManager? = null,
    onToggleTheme: () -> Unit,
    onCredentialsLogin: (identifier: String, password: String, rememberMe: Boolean) -> Unit,
    onCreateAccount: (
        fullName: String,
        identifier: String,
        password: String,
        role: String,
        ownerVerifyPin: String?,
        onDone: () -> Unit
    ) -> Unit,
    onRecoverPassword: (
        identifier: String,
        newPassword: String,
        ownerVerifyPin: String?,
        onDone: () -> Unit
    ) -> Unit,
    onEnterGuestMode: () -> Unit,
    onGoogleAuthSuccess: () -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeCredentialManager = remember(context, credentialManager) {
        credentialManager ?: CredentialManager.create(context)
    }

    var isEnglish by remember { mutableStateOf(false) }
    var identifier by remember(savedIdentifier) { mutableStateOf(savedIdentifier) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember(rememberMeInitial) { mutableStateOf(rememberMeInitial) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    var showCreateAccountSheet by remember { mutableStateOf(false) }
    var showForgotPasswordSheet by remember { mutableStateOf(false) }

    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(shakeTick) {
        if (shakeTick > 0) {
            val pattern = listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)
            for (target in pattern) {
                shakeOffset.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 48, easing = LinearEasing)
                )
            }
        }
    }

    val strings = remember(isEnglish) {
        if (isEnglish) {
            LoginStrings(
                storeTitleAr = "متجر خردوات المامو",
                storeTitleEn = "MAMO HARDWARE STORE",
                welcomeHeadline = "Welcome to the World of Al-Mamo",
                welcomeSubline = "Everything you need in tools & hardware, in one place.",
                identifierLabel = "Username or Email",
                passwordLabel = "Password",
                rememberMe = "Remember me",
                forgotPassword = "Forgot password?",
                loginButton = "Sign In",
                orDivider = "OR CLOUD SYNC",
                googleButton = "Continue with Google",
                createAccount = "Create Account",
                guestMode = "Enter as Guest",
                signaturePrefix = "Designed & Supervised by",
                signatureOwner = "أحمد نوار المامو",
                signatureOwnerEn = "Designed by Ahmad Nawwar Al-Mamo",
                registeredHint = "Active local accounts"
            )
        } else {
            LoginStrings(
                storeTitleAr = "متجر خردوات المامو",
                storeTitleEn = "MAMO HARDWARE STORE",
                welcomeHeadline = "مرحباً بك في عالم المامو",
                welcomeSubline = "كل ما تحتاجه من أدوات وخردوات في مكان واحد.",
                identifierLabel = "اسم المستخدم أو البريد الإلكتروني",
                passwordLabel = "كلمة المرور",
                rememberMe = "تذكرني",
                forgotPassword = "نسيت كلمة المرور؟",
                loginButton = "تسجيل الدخول",
                orDivider = "أو المزامنة السحابية",
                googleButton = "تسجيل الدخول بحساب Google",
                createAccount = "إنشاء حساب جديد",
                guestMode = "الدخول كزائر",
                signaturePrefix = "تصميم وإشراف",
                signatureOwner = "أحمد نوار المامو",
                signatureOwnerEn = "Designed by Ahmad Nawwar Al-Mamo",
                registeredHint = "الحسابات المسجلة"
            )
        }
    }

    val layoutDir = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    val displayedError = loginError ?: googleError

    val bgBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                MamoDeepMidnight,
                MamoRoyalNavy,
                Color(0xFF0A2045),
                MamoDeepMidnight
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF071631),
                Color(0xFF0E2C5E),
                Color(0xFFEAF2FC),
                MamoPearlWhite
            )
        )
    }

    val density = LocalDensity.current
    var isWide by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .onSizeChanged { size ->
                    val wide = with(density) { size.width.toDp() >= 800.dp }
                    if (wide != isWide) isWide = wide
                }
        ) {
            // Volumetric atmospheric background lights (cached shaders)
            AtmosphericBackgroundLights(isDarkTheme = isDarkTheme)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Floating Glass Controls Bar (AR/EN toggle + Day/Night toggle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MamoGoldWarm.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { isEnglish = !isEnglish }
                            .minimumInteractiveComponentSize()
                            .testTag("login_lang_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "تبديل اللغة",
                                tint = MamoGoldWarm,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isEnglish) "AR | EN" else "عربي | EN",
                                color = MamoPearlWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!googleEmail.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MamoElectricBlue.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, MamoCyanGlow.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = MamoCyanGlow,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = googleEmail,
                                        color = MamoPearlWhite,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier.testTag("login_theme_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "تبديل الوضع الليلي",
                                    tint = MamoGoldWarm
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 1040.dp)
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Mamo3DHeroStage(
                                compact = false,
                                isDarkTheme = isDarkTheme
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            BrandTitlesBlock(strings = strings, isDarkTheme = true)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                        ) {
                            GlassLoginCard(
                                    isDarkTheme = isDarkTheme,
                                    strings = strings,
                                    identifier = identifier,
                                    onIdentifierChange = {
                                        identifier = it
                                        googleError = null
                                        onClearError()
                                    },
                                    password = password,
                                    onPasswordChange = {
                                        password = it
                                        googleError = null
                                        onClearError()
                                    },
                                    passwordVisible = passwordVisible,
                                    onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                                    rememberMe = rememberMe,
                                    onRememberMeChange = { rememberMe = it },
                                    displayedError = displayedError,
                                    infoMessage = loginInfoMessage,
                                    isGoogleLoading = isGoogleLoading,
                                    googleEmail = googleEmail,
                                    staffCount = staffList.count { it.isActive && it.approvalStatus == com.example.data.local.ApprovalStatus.APPROVED },
                                    onForgotPassword = { showForgotPasswordSheet = true },
                                    onSubmitLogin = {
                                        onCredentialsLogin(identifier, password, rememberMe)
                                    },
                                    onGoogleSignIn = {
                                        googleError = null
                                        isGoogleLoading = true
                                        onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = activeCredentialManager,
                                            onAuthSuccess = {
                                                isGoogleLoading = false
                                                onGoogleAuthSuccess()
                                            },
                                            onAuthError = { msg ->
                                                isGoogleLoading = false
                                                googleError = msg
                                            },
                                            scope = scope,
                                            onAuthCancelled = {
                                                isGoogleLoading = false
                                                googleError = if (isEnglish) {
                                                    "Google sign-in was cancelled."
                                                } else {
                                                    "تم إلغاء تسجيل الدخول بحساب Google."
                                                }
                                            }
                                        )
                                    },
                                    onCreateAccountClick = { showCreateAccountSheet = true },
                                    onGuestClick = onEnterGuestMode
                                )
                        }
                    }
                } else {
                    // Mobile-first vertical composition
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Mamo3DHeroStage(
                            compact = true,
                            isDarkTheme = isDarkTheme
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        BrandTitlesBlock(strings = strings, isDarkTheme = true)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp)
                            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                    ) {
                        GlassLoginCard(
                                isDarkTheme = isDarkTheme,
                                strings = strings,
                                identifier = identifier,
                                onIdentifierChange = {
                                    identifier = it
                                    googleError = null
                                    onClearError()
                                },
                                password = password,
                                onPasswordChange = {
                                    password = it
                                    googleError = null
                                    onClearError()
                                },
                                passwordVisible = passwordVisible,
                                onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                                rememberMe = rememberMe,
                                onRememberMeChange = { rememberMe = it },
                                displayedError = displayedError,
                                infoMessage = loginInfoMessage,
                                isGoogleLoading = isGoogleLoading,
                                googleEmail = googleEmail,
                                staffCount = staffList.count { it.isActive && it.approvalStatus == com.example.data.local.ApprovalStatus.APPROVED },
                                onForgotPassword = { showForgotPasswordSheet = true },
                                onSubmitLogin = {
                                    onCredentialsLogin(identifier, password, rememberMe)
                                },
                                onGoogleSignIn = {
                                    googleError = null
                                    isGoogleLoading = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = activeCredentialManager,
                                        onAuthSuccess = {
                                            isGoogleLoading = false
                                            onGoogleAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isGoogleLoading = false
                                            googleError = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = {
                                            isGoogleLoading = false
                                            googleError = if (isEnglish) {
                                                "Google sign-in was cancelled."
                                            } else {
                                                "تم إلغاء تسجيل الدخول بحساب Google."
                                            }
                                        }
                                    )
                                },
                                onCreateAccountClick = { showCreateAccountSheet = true },
                                onGuestClick = onEnterGuestMode
                            )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Designer & Supervisor Golden Signature Footer
                GoldSupervisorSignatureFooter(
                    strings = strings,
                    isDarkTheme = isDarkTheme,
                    isEnglish = isEnglish
                )

                Spacer(modifier = Modifier.navigationBarsPadding().height(8.dp))
            }
        }
    }

    if (showCreateAccountSheet) {
        CreateAccountModalSheet(
            hasExistingOwner = staffList.any {
                it.role == StaffRole.OWNER && it.isActive && it.approvalStatus == com.example.data.local.ApprovalStatus.APPROVED
            },
            onDismiss = { showCreateAccountSheet = false },
            onSubmit = { fullName, idText, pass, role, ownerPin ->
                onCreateAccount(fullName, idText, pass, role, ownerPin) {
                    showCreateAccountSheet = false
                }
            }
        )
    }

    if (showForgotPasswordSheet) {
        ForgotPasswordModalSheet(
            initialIdentifier = identifier,
            googleEmail = googleEmail,
            onDismiss = { showForgotPasswordSheet = false },
            onSubmit = { idText, newPass, ownerPin ->
                onRecoverPassword(idText, newPass, ownerPin) {
                    showForgotPasswordSheet = false
                }
            }
        )
    }
}

@Composable
private fun AtmosphericBackgroundLights(isDarkTheme: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val topCenter = Offset(w * 0.5f, h * 0.16f)
                val topRadius = (w * 0.72f).coerceAtLeast(1f)
                val topBrush = Brush.radialGradient(
                    colors = listOf(MamoElectricBlue.copy(alpha = 0.28f), Color.Transparent),
                    center = topCenter,
                    radius = topRadius
                )
                val rightCenter = Offset(w * 0.86f, h * 0.30f)
                val rightRadius = (w * 0.45f).coerceAtLeast(1f)
                val rightBrush = Brush.radialGradient(
                    colors = listOf(
                        MamoOrangeAccent.copy(alpha = if (isDarkTheme) 0.14f else 0.10f),
                        Color.Transparent
                    ),
                    center = rightCenter,
                    radius = rightRadius
                )
                val leftCenter = Offset(w * 0.14f, h * 0.65f)
                val leftRadius = (w * 0.48f).coerceAtLeast(1f)
                val leftBrush = Brush.radialGradient(
                    colors = listOf(
                        MamoCyanGlow.copy(alpha = if (isDarkTheme) 0.14f else 0.10f),
                        Color.Transparent
                    ),
                    center = leftCenter,
                    radius = leftRadius
                )
                onDrawBehind {
                    drawCircle(brush = topBrush, radius = topRadius, center = topCenter)
                    drawCircle(brush = rightBrush, radius = rightRadius, center = rightCenter)
                    drawCircle(brush = leftBrush, radius = leftRadius, center = leftCenter)
                }
            }
    )
}

@Composable
private fun Mamo3DHeroStage(
    compact: Boolean,
    isDarkTheme: Boolean
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        )
    }

    val stageHeight = if (compact) 196.dp else 255.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(stageHeight),
        contentAlignment = Alignment.Center
    ) {
        // Volumetric light aura behind the 3D scene
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(stageHeight * 0.94f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MamoElectricBlue.copy(alpha = 0.42f),
                            MamoOrangeAccent.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        val context = LocalContext.current
        val heroRequest = remember(context) {
            ImageRequest.Builder(context)
                .data(R.drawable.img_mamo_3d_hero)
                .size(688, 384)
                .allowHardware(true)
                .crossfade(false)
                .build()
        }

        // Crisp 3D Metallic Hero Container without GPU-stalling 3D matrix rotation/shadow churn
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(stageHeight - 10.dp)
                .graphicsLayer {
                    val p = entrance.value
                    alpha = 0.85f + 0.15f * p
                    translationY = (1f - p) * 10f
                },
            shape = RoundedCornerShape(28.dp),
            color = MamoRoyalNavy,
            border = BorderStroke(
                width = 1.4.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MamoCyanGlow.copy(alpha = 0.85f),
                        MamoGoldWarm.copy(alpha = 0.75f),
                        MamoElectricBlue.copy(alpha = 0.55f)
                    )
                )
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = heroRequest,
                    contentDescription = "مشهد ثلاثي الأبعاد لصندوق أدوات متجر خردوات المامو",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Soft vignette + bottom cinematic light sheen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    MamoDeepMidnight.copy(alpha = 0.65f)
                                )
                            )
                        )
                )

                // Floating 3D Metallic Hardware Badge - Top Start
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MamoDeepMidnight.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MamoGoldWarm.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MamoOrangeAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "PRO HARDWARE 3D",
                            color = MamoPearlWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Floating 3D Electric Glow Badge - Top End
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MamoElectricBlue.copy(alpha = 0.34f),
                    border = BorderStroke(1.dp, MamoCyanGlow.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hardware,
                            contentDescription = null,
                            tint = MamoGoldWarm,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "MAMO ERP",
                            color = MamoPearlWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandTitlesBlock(
    strings: LoginStrings,
    isDarkTheme: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = strings.storeTitleAr,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp),
            fontWeight = FontWeight.ExtraBold,
            color = MamoPearlWhite,
            textAlign = TextAlign.Center
        )
        Text(
            text = strings.storeTitleEn,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.4.sp),
            color = MamoGoldWarm,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = if (isDarkTheme) 0.10f else 0.14f),
            border = BorderStroke(1.dp, MamoCyanGlow.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.welcomeHeadline,
                    style = MaterialTheme.typography.titleSmall,
                    color = MamoCyanGlow,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.welcomeSubline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MamoPearlWhite.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GlassLoginCard(
    isDarkTheme: Boolean,
    strings: LoginStrings,
    identifier: String,
    onIdentifierChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    rememberMe: Boolean,
    onRememberMeChange: (Boolean) -> Unit,
    displayedError: String?,
    infoMessage: String?,
    isGoogleLoading: Boolean,
    googleEmail: String?,
    staffCount: Int,
    onForgotPassword: () -> Unit,
    onSubmitLogin: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onCreateAccountClick: () -> Unit,
    onGuestClick: () -> Unit
) {
    var idFocused by remember { mutableStateOf(false) }
    var passFocused by remember { mutableStateOf(false) }

    val cardBg = if (isDarkTheme) {
        Color(0xD60C1A32)
    } else {
        Color(0xEBFFFFFF)
    }
    val textPrimary = if (isDarkTheme) MamoPearlWhite else MamoRoyalNavy
    val textSecondary = if (isDarkTheme) Color(0xFFB3C4DB) else Color(0xFF475569)

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            displayedError != null -> MamoErrorRed.copy(alpha = 0.85f)
            idFocused || passFocused -> MamoElectricBlue.copy(alpha = 0.85f)
            isDarkTheme -> MamoGoldWarm.copy(alpha = 0.42f)
            else -> Color.White.copy(alpha = 0.90f)
        },
        animationSpec = tween(260),
        label = "card_border_glow"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cardBg,
        border = BorderStroke(1.4.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Focus-glowing Username / Email Field
            val idBorderColor by animateColorAsState(
                targetValue = if (idFocused) MamoElectricBlue else textSecondary.copy(alpha = 0.35f),
                label = "id_border"
            )
            OutlinedTextField(
                value = identifier,
                onValueChange = onIdentifierChange,
                label = { Text(strings.identifierLabel) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (idFocused) MamoElectricBlue else MamoGoldWarm
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MamoElectricBlue,
                    unfocusedBorderColor = idBorderColor,
                    focusedLabelColor = MamoElectricBlue,
                    unfocusedLabelColor = textSecondary,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedContainerColor = if (isDarkTheme) Color(0x40102547) else Color(0xFFF4F8FF),
                    unfocusedContainerColor = if (isDarkTheme) Color(0x28102547) else Color(0xFFF9FBFF)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { idFocused = it.isFocused }
                    .testTag("login_identifier_input")
            )

            // Focus-glowing Password Field with Visibility Toggle
            val passBorderColor by animateColorAsState(
                targetValue = if (passFocused) MamoElectricBlue else textSecondary.copy(alpha = 0.35f),
                label = "pass_border"
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(strings.passwordLabel) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (passFocused) MamoElectricBlue else MamoGoldWarm
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = onTogglePasswordVisible,
                        modifier = Modifier.testTag("login_toggle_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                            tint = textSecondary
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmitLogin() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MamoElectricBlue,
                    unfocusedBorderColor = passBorderColor,
                    focusedLabelColor = MamoElectricBlue,
                    unfocusedLabelColor = textSecondary,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedContainerColor = if (isDarkTheme) Color(0x40102547) else Color(0xFFF4F8FF),
                    unfocusedContainerColor = if (isDarkTheme) Color(0x28102547) else Color(0xFFF9FBFF)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { passFocused = it.isFocused }
                    .testTag("login_password_input")
            )

            // Remember Me + Forgot Password Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onRememberMeChange(!rememberMe) }
                        .padding(end = 6.dp)
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = onRememberMeChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MamoElectricBlue,
                            uncheckedColor = textSecondary,
                            checkmarkColor = MamoPearlWhite
                        ),
                        modifier = Modifier.testTag("login_remember_checkbox")
                    )
                    Text(
                        text = strings.rememberMe,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                TextButton(
                    onClick = onForgotPassword,
                    modifier = Modifier.testTag("login_forgot_password_link")
                ) {
                    Text(
                        text = strings.forgotPassword,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkTheme) MamoCyanGlow else MamoElectricBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Pending / Info Feedback Banner
            if (!infoMessage.isNullOrBlank() && displayedError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MamoGoldWarm.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, MamoGoldWarm.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth().testTag("login_info_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MamoGoldWarm,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = infoMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkTheme) MamoPearlWhite else MamoRoyalNavy,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Error Feedback Banner
            if (!displayedError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MamoErrorRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MamoErrorRed.copy(alpha = 0.50f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MamoErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = displayedError,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Primary 3D Tactile Login Button
            Tactile3DPrimaryButton(
                text = strings.loginButton,
                onClick = onSubmitLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_submit_3d_btn")
            )

            // Divider + Google Cloud Sign-In Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = textSecondary.copy(alpha = 0.25f))
                Text(
                    text = strings.orDivider,
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = textSecondary.copy(alpha = 0.25f))
            }

            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !isGoogleLoading,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.2.dp,
                    if (isDarkTheme) MamoGoldWarm.copy(alpha = 0.55f) else MamoRoyalNavy.copy(alpha = 0.30f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isDarkTheme) Color(0x22FFFFFF) else Color(0xFFF8FAFC),
                    contentColor = textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isGoogleLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MamoElectricBlue
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MamoOrangeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (googleEmail.isNullOrBlank()) strings.googleButton else "${strings.googleButton} ($googleEmail)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Create Account + Guest Mode Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCreateAccountClick,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MamoElectricBlue.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("login_create_account_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = if (isDarkTheme) MamoCyanGlow else MamoElectricBlue,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.createAccount,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isDarkTheme) MamoCyanGlow else MamoElectricBlue,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onGuestClick,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MamoOrangeAccent.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("login_guest_mode_btn")
                ) {
                    Text(
                        text = strings.guestMode,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isDarkTheme) MamoGoldWarm else Color(0xFFB45309),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            if (staffCount > 0) {
                Text(
                    text = "${strings.registeredHint}: $staffCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun Tactile3DPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val topOffset by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "3d_btn_press_offset"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "3d_btn_scale"
    )

    Box(
        modifier = modifier
            .height(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // 3D Extruded Bottom Depth Lip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF061634), Color(0xFF0E3E8A), Color(0xFF9A6B12))
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        )

        // Elevated Top Metallic-Electric Face
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .offset(y = topOffset)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MamoRoyalNavy,
                            MamoElectricBlue,
                            Color(0xFF0B5ED7)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MamoCyanGlow.copy(alpha = 0.85f),
                            MamoGoldWarm.copy(alpha = 0.9f),
                            MamoCyanGlow.copy(alpha = 0.85f)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Subtle Top Glass Specular Highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(14.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 3.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    tint = MamoGoldWarm,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = text,
                    color = MamoPearlWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun GoldSupervisorSignatureFooter(
    strings: LoginStrings,
    isDarkTheme: Boolean,
    isEnglish: Boolean
) {
    val labelColor = if (isDarkTheme) Color(0xFFCBD5E1) else MamoRoyalNavy.copy(alpha = 0.82f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Stylized golden decorative crest + sweeping gold flourish (symbolic visual emblem)
        Canvas(
            modifier = Modifier
                .width(190.dp)
                .height(22.dp)
        ) {
            val midX = size.width / 2f
            val midY = size.height / 2f
            val goldBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    MamoGoldWarm,
                    Color(0xFFFFE599),
                    MamoGoldWarm,
                    Color.Transparent
                )
            )
            val leftPath = Path().apply {
                moveTo(10f, midY + 2f)
                cubicTo(midX * 0.42f, midY - 12f, midX * 0.72f, midY + 10f, midX - 14f, midY)
            }
            val rightPath = Path().apply {
                moveTo(midX + 14f, midY)
                cubicTo(midX + midX * 0.28f, midY - 10f, midX + midX * 0.58f, midY + 12f, size.width - 10f, midY - 2f)
            }
            drawPath(leftPath, brush = goldBrush, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
            drawPath(rightPath, brush = goldBrush, style = Stroke(width = 2.6f, cap = StrokeCap.Round))

            // Central golden diamond emblem
            val diamond = Path().apply {
                moveTo(midX, midY - 6f)
                lineTo(midX + 7f, midY)
                lineTo(midX, midY + 6f)
                lineTo(midX - 7f, midY)
                close()
            }
            drawPath(diamond, color = MamoGoldWarm)
        }

        Text(
            text = strings.storeTitleAr,
            style = MaterialTheme.typography.titleSmall,
            color = labelColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = strings.signaturePrefix,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        Text(
            text = if (isEnglish) strings.signatureOwnerEn else strings.signatureOwner,
            style = MaterialTheme.typography.titleMedium,
            color = MamoGoldWarm,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        if (!isEnglish) {
            Text(
                text = strings.signatureOwnerEn,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = labelColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAccountModalSheet(
    hasExistingOwner: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        fullName: String,
        identifier: String,
        password: String,
        role: String,
        ownerVerifyPin: String?
    ) -> Unit
) {
    var fullName by remember { mutableStateOf(if (!hasExistingOwner) "أحمد نوار المامو" else "") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember {
        mutableStateOf(if (!hasExistingOwner) StaffRole.OWNER else StaffRole.CASHIER)
    }
    var ownerVerifyPin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MamoElectricBlue)
                Text("إنشاء حساب جديد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = if (!hasExistingOwner) {
                    "سيتم تسجيل هذا الحساب بصلاحية المدير العام لمتجر خردوات المامو."
                } else {
                    "سيتم إرسال طلبك إلى قائمة انتظار الأدمن لقبول تسجيل الدخول وتحديد طبيعة العمل والصلاحيات، أو أدخل رمز المدير العام للتفويض الفوري."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("الاسم الكامل") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("register_fullname_input")
            )

            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("اسم المستخدم أو البريد الإلكتروني") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("register_identifier_input")
            )

            if (hasExistingOwner) {
                Text("طبيعة العمل المطلوبة:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val roleOptions = listOf(
                        StaffRole.CASHIER to "كاشير / مبيعات",
                        StaffRole.ACCOUNTANT to "محاسب",
                        StaffRole.WAREHOUSE_KEEPER to "أمين مخزن",
                        StaffRole.AUDITOR to "مراقب فقط",
                        StaffRole.OWNER to "مدير"
                    )
                    items(roleOptions.size) { index ->
                        val (role, label) = roleOptions[index]
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(label) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("كلمة المرور (4 خانات على الأقل)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("register_password_input")
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("تأكيد كلمة المرور") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("register_confirm_password_input")
            )

            if (hasExistingOwner) {
                OutlinedTextField(
                    value = ownerVerifyPin,
                    onValueChange = { ownerVerifyPin = it },
                    label = { Text("رمز المدير العام للتفويض الفوري (اختياري)") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("register_owner_pin_input")
                )
            }

            if (!localError.isNullOrBlank()) {
                Text(localError ?: "", color = MamoErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    when {
                        fullName.isBlank() -> localError = "يرجى إدخال الاسم الكامل"
                        password.length < 4 -> localError = "كلمة المرور يجب أن تكون 4 خانات على الأقل"
                        password != confirmPassword -> localError = "كلمتا المرور غير متطابقتين"
                        else -> {
                            localError = null
                            onSubmit(
                                fullName.trim(),
                                identifier.trim().ifBlank { fullName.trim() },
                                password,
                                selectedRole,
                                ownerVerifyPin.trim().takeIf { hasExistingOwner && it.isNotEmpty() }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("register_submit_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (hasExistingOwner && ownerVerifyPin.isBlank()) "إرسال طلب الانضمام للأدمن" else "إنشاء الحساب والدخول"
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgotPasswordModalSheet(
    initialIdentifier: String,
    googleEmail: String?,
    onDismiss: () -> Unit,
    onSubmit: (identifier: String, newPassword: String, ownerVerifyPin: String?) -> Unit
) {
    var identifier by remember { mutableStateOf(initialIdentifier) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var ownerVerifyPin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MamoOrangeAccent)
                Text("استعادة كلمة المرور", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = if (!googleEmail.isNullOrBlank()) {
                    "أنت مرتبط بحساب Google ($googleEmail). يمكنك إعادة تعيين كلمة مرور المدير أو أي موظف."
                } else {
                    "لأمان البيانات المحاسبية، يتطلب تعيين كلمة مرور جديدة إدخال اعتماد المدير العام أو ربط حساب Google أولاً."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("اسم المستخدم أو البريد") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("كلمة المرور الجديدة") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("تأكيد كلمة المرور الجديدة") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (googleEmail.isNullOrBlank()) {
                OutlinedTextField(
                    value = ownerVerifyPin,
                    onValueChange = { ownerVerifyPin = it },
                    label = { Text("كلمة مرور المدير العام للتفويض") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!localError.isNullOrBlank()) {
                Text(localError ?: "", color = MamoErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    when {
                        identifier.isBlank() && googleEmail.isNullOrBlank() -> localError = "أدخل اسم المستخدم"
                        newPassword.length < 4 -> localError = "كلمة المرور يجب أن تكون 4 خانات على الأقل"
                        newPassword != confirmPassword -> localError = "كلمتا المرور غير متطابقتين"
                        else -> {
                            localError = null
                            onSubmit(identifier.trim(), newPassword, ownerVerifyPin.takeIf { it.isNotBlank() })
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("تحديث كلمة المرور")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
