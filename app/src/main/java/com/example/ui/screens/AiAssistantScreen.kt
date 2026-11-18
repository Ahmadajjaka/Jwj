package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.AiChatMessage
import com.example.data.remote.GroqAiService
import com.example.ui.MamoMainUiState
import com.example.ui.MamoViewModel
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters

@Composable
fun AiAssistantScreen(
    state: MamoMainUiState,
    viewModel: MamoViewModel
) {
    val messages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedAiModel.collectAsStateWithLifecycle()
    val aiLogs by viewModel.aiLogs.collectAsStateWithLifecycle()
    val isKeyConfigured = viewModel.isGroqKeyConfigured
    val mamoColors = LocalMamoColors.current

    var inputText by remember { mutableStateOf("") }
    var voiceStatusNotice by remember { mutableStateOf<String?>(null) }
    var showLogsHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotBlank()) {
                voiceStatusNotice = null
                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
            }
        }
    }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = remember {
        listOf(
            "ملخص مالي اليوم" to "أعطني ملخصاً مالياً شاملاً لوضع المتجر اليوم (المبيعات، الأرباح، الصندوق، المصاريف)",
            "نواقص واقتراح الطلب" to "ما هي المنتجات الناقصة وما كميات الشراء المقترحة لإعادة التزويد؟",
            "تحليل الأرباح والهوامش" to "حلل الأرباح وهوامش الربح واقترح خطوات عملية لتحسين العائد",
            "الديون المتأخرة والمخاطر" to "راجع ديون الزبائن والموردين ونبّهني للحالات المتأخرة وخطوات التحصيل",
            "الأصناف الراكدة" to "ما هي الأصناف الراكدة في المستودع وما الخطة المقترحة لتصريفها؟",
            "تنبيهات وأخطاء محتملة" to "افحص أرقام المتجر وابحث عن أي تنبيهات أو أخطاء محاسبية محتملة"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "المساعد المحاسبي الذكي (Groq GPT)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "تحليل محلي ذكي + Groq GPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = mamoColors.profit
                )
            }
            if (aiLogs.isNotEmpty()) {
                TextButton(
                    onClick = { showLogsHistory = !showLogsHistory },
                    modifier = Modifier.testTag("ai_logs_toggle_btn")
                ) {
                    Icon(Icons.Default.History, contentDescription = "سجل الاستشارات", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("السجل (${aiLogs.size})")
                    Icon(
                        imageVector = if (showLogsHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (messages.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearAiChat() },
                    modifier = Modifier.testTag("ai_clear_chat_btn")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "مسح المحادثة", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("جديد")
                }
            }
        }

        // Model selector chips (Groq GPT-OSS 120B / 20B)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {},
                label = { Text("تحليل محلي + سحابي") },
                leadingIcon = {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
            GroqAiService.SUPPORTED_MODELS.forEach { model ->
                FilterChip(
                    selected = selectedModelId == model.id,
                    onClick = { viewModel.setSelectedAiModel(model.id) },
                    label = { Text(model.displayName) },
                    modifier = Modifier.testTag("ai_model_chip_${model.id}")
                )
            }
        }

        if (!isKeyConfigured) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = mamoColors.warning)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "وضع التحليل المحلي مفعّل (Groq API اختياري)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = mamoColors.warning
                        )
                        Text(
                            text = "يمكنك الاستفسار الآن بالتحليل المحاسبي المحلي الفوري، أو إضافة GROQ_API_KEY في Secrets لتفعيل GPT السحابي.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (!voiceStatusNotice.isNullOrBlank()) {
            Text(
                text = voiceStatusNotice!!,
                style = MaterialTheme.typography.bodySmall,
                color = mamoColors.warning
            )
        }

        if (showLogsHistory && aiLogs.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("آخر استفسارات الذكاء الاصطناعي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                aiLogs.take(5).forEachIndexed { idx, log ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                inputText = log.prompt
                                showLogsHistory = false
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = log.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (log.wasOfflineFallback) "محلي" else log.modelId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = "${log.staffName} • ${ArabicFormatters.formatDateTime(log.createdAtMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (idx < minOf(4, aiLogs.lastIndex)) HorizontalDivider()
                }
            }
        }

        // Quick accounting & hardware store prompts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEachIndexed { idx, (label, prompt) ->
                SuggestionChip(
                    onClick = {
                        if (!isLoading) {
                            viewModel.askGroqAi(prompt)
                        }
                    },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("ai_quick_prompt_$idx")
                )
            }
        }

        // Chat feed
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "مستشار ${state.settings.storeName} الذكي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اسأل كتابةً أو عبر الميكروفون الصوتي عن المبيعات، الأرباح، النواقص، الأصناف الراكدة، أو تحصيل الديون.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AiMessageBubble(msg)
                    }
                    if (isLoading) {
                        item(key = "loading_indicator") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "جاري التحليل المحاسبي...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Composer bar with Voice Input + Send
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن للمساعد المحاسبي...")
                            }
                            voiceLauncher.launch(intent)
                        } catch (_: Exception) {
                            voiceStatusNotice = "خدمة التعرف الصوتي غير متوفرة على هذا الجهاز — يرجى كتابة السؤال يدوياً."
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("ai_voice_input_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "إدخال صوتي بالعربية",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("اكتب أو سجّل سؤالك المحاسبي هنا...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_prompt_input"),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val q = inputText
                            inputText = ""
                            viewModel.askGroqAi(q)
                        }
                    }
                )
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (inputText.isNotBlank() && !isLoading) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val q = inputText
                            inputText = ""
                            viewModel.askGroqAi(q)
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("ai_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        tint = if (inputText.isNotBlank() && !isLoading) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiMessageBubble(message: AiChatMessage) {
    val mamoColors = LocalMamoColors.current
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = when {
        message.isError -> mamoColors.loss.copy(alpha = 0.12f)
        message.isUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }
    val contentColor = when {
        message.isError -> mamoColors.loss
        message.isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (message.isError) BorderStroke(1.dp, mamoColors.loss.copy(alpha = 0.45f)) else null

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            border = border,
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!message.isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (message.isError) Icons.Default.ErrorOutline else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        val modelLabel = when {
                            message.isError -> "تنبيه"
                            message.modelId == "MAMO-Offline" -> "تحليل محلي ذكي"
                            else -> GroqAiService.SUPPORTED_MODELS
                                .firstOrNull { it.id == message.modelId }
                                ?.displayName ?: "Groq GPT"
                        }
                        Text(
                            text = modelLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = ArabicFormatters.formatDateTime(message.timestampMs, "HH:mm"),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
