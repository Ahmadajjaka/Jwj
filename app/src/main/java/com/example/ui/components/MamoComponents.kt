package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.ChartPoint
import com.example.ui.theme.LocalMamoColors
import com.example.utils.ArabicFormatters

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlightGold: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val mamoColors = LocalMamoColors.current
    val borderColor = if (highlightGold) mamoColors.gold.copy(alpha = 0.65f) else mamoColors.glassBorder
    val baseModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Card(
        modifier = baseModifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = mamoColors.glassSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (mamoColors.isDark) 0.dp else 2.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun MetricKpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    backgroundColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = backgroundColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val mamoColors = LocalMamoColors.current
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(mamoColors.gold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = mamoColors.gold,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun InteractiveBarChart(
    title: String,
    points: List<ChartPoint>,
    primaryLabel: String,
    secondaryLabel: String? = null,
    currencySymbol: String = "ل.س",
    primaryColor: Color,
    secondaryColor: Color = Color.Gray
) {
    var selectedIdx by remember(points) { mutableIntStateOf(-1) }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(primaryColor))
                Spacer(modifier = Modifier.width(6.dp))
                Text(primaryLabel, style = MaterialTheme.typography.labelSmall)
            }
            if (secondaryLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(secondaryColor))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(secondaryLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (points.isEmpty() || points.all { it.primaryValue == 0.0 && it.secondaryValue == 0.0 }) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "لا توجد بيانات كافية للرسم البياني في هذه الفترة",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@GlassCard
        }

        if (selectedIdx in points.indices) {
            val pt = points[selectedIdx]
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = primaryColor.copy(alpha = 0.10f)
            ) {
                val msg = buildString {
                    append("${pt.label}: $primaryLabel ${ArabicFormatters.formatMoney(pt.primaryValue, currencySymbol)}")
                    if (secondaryLabel != null) {
                        append("  |  $secondaryLabel ${ArabicFormatters.formatMoney(pt.secondaryValue, currencySymbol)}")
                    }
                }
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        val maxVal = points.maxOf { maxOf(it.primaryValue, it.secondaryValue) }.coerceAtLeast(1.0)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.isNotEmpty()) {
                            val groupWidth = size.width.toFloat() / points.size
                            val idx = (offset.x / groupWidth).toInt().coerceIn(0, points.lastIndex)
                            selectedIdx = idx
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            for (i in 0..3) {
                val y = h * (i / 3f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            val groupW = w / points.size
            val hasSecondary = secondaryLabel != null
            points.forEachIndexed { idx, pt ->
                val center = groupW * idx + groupW / 2f
                val barW = if (hasSecondary) (groupW * 0.28f).coerceAtMost(24f) else (groupW * 0.45f).coerceAtMost(36f)
                val pHeight = ((pt.primaryValue / maxVal).toFloat() * (h - 12f)).coerceAtLeast(if (pt.primaryValue > 0) 6f else 0f)
                val pLeft = if (hasSecondary) center - barW - 3f else center - barW / 2f
                drawRoundRect(
                    color = if (idx == selectedIdx) primaryColor else primaryColor.copy(alpha = 0.85f),
                    topLeft = Offset(pLeft, h - pHeight),
                    size = Size(barW, pHeight),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                if (hasSecondary) {
                    val sHeight = ((pt.secondaryValue / maxVal).toFloat() * (h - 12f)).coerceAtLeast(if (pt.secondaryValue > 0) 6f else 0f)
                    drawRoundRect(
                        color = if (idx == selectedIdx) secondaryColor else secondaryColor.copy(alpha = 0.85f),
                        topLeft = Offset(center + 3f, h - sHeight),
                        size = Size(barW, sHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            points.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun InteractiveLineChart(
    title: String,
    points: List<ChartPoint>,
    currencySymbol: String = "ل.س",
    lineColor: Color
) {
    var selectedIdx by remember(points) { mutableIntStateOf(-1) }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (points.isEmpty() || points.all { it.primaryValue == 0.0 }) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "لا توجد مبيعات مسجلة لعرض المنحنى",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@GlassCard
        }

        if (selectedIdx in points.indices) {
            val pt = points[selectedIdx]
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${pt.label}: ${ArabicFormatters.formatMoney(pt.primaryValue, currencySymbol)}",
                style = MaterialTheme.typography.labelMedium,
                color = lineColor
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        val maxVal = points.maxOf { it.primaryValue }.coerceAtLeast(1.0)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.size > 1) {
                            val step = size.width.toFloat() / (points.size - 1)
                            selectedIdx = ((offset.x + step / 2f) / step).toInt().coerceIn(0, points.lastIndex)
                        } else {
                            selectedIdx = 0
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            for (i in 0..3) {
                val y = h * (i / 3f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            val coords = points.mapIndexed { idx, pt ->
                val x = if (points.size == 1) w / 2f else (idx.toFloat() / (points.size - 1)) * w
                val y = h - 10f - ((pt.primaryValue / maxVal).toFloat() * (h - 24f))
                Offset(x, y)
            }

            if (coords.size > 1) {
                val fillPath = Path().apply {
                    moveTo(coords.first().x, h)
                    coords.forEach { lineTo(it.x, it.y) }
                    lineTo(coords.last().x, h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                )

                val strokePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        lineTo(coords[i].x, coords[i].y)
                    }
                }
                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }

            coords.forEachIndexed { idx, pt ->
                drawCircle(
                    color = if (idx == selectedIdx) Color.White else lineColor,
                    radius = if (idx == selectedIdx) 9f else 6f,
                    center = pt
                )
                drawCircle(
                    color = lineColor,
                    radius = if (idx == selectedIdx) 9f else 6f,
                    center = pt,
                    style = Stroke(width = 3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun matchesInvoiceDateFilter(
    invoiceDateMs: Long,
    filter: com.example.data.repository.InvoiceDateFilter
): Boolean {
    return when (filter.mode) {
        com.example.data.repository.InvoiceDateFilterMode.ALL -> true
        com.example.data.repository.InvoiceDateFilterMode.TODAY -> {
            invoiceDateMs >= ArabicFormatters.startOfTodayMs()
        }
        com.example.data.repository.InvoiceDateFilterMode.WEEK -> {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            invoiceDateMs >= cal.timeInMillis
        }
        com.example.data.repository.InvoiceDateFilterMode.MONTH -> {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            invoiceDateMs >= cal.timeInMillis
        }
        com.example.data.repository.InvoiceDateFilterMode.CUSTOM -> {
            val startOk = filter.customStartMs == null || invoiceDateMs >= filter.customStartMs
            val endOk = filter.customEndMs == null || invoiceDateMs <= filter.customEndMs
            startOk && endOk
        }
    }
}

fun parseOcrInvoiceDateOrNow(dateText: String): Long {
    val normalized = ArabicFormatters.normalizeDigits(dateText).trim()
    if (normalized.isBlank()) return System.currentTimeMillis()
    val candidate = Regex("""\b\d{4}[-/]\d{1,2}[-/]\d{1,2}\b|\b\d{1,2}[-/]\d{1,2}[-/]\d{4}\b""")
        .find(normalized)?.value ?: normalized
    val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy")
    for (p in patterns) {
        val parsed = runCatching {
            val fmt = java.text.SimpleDateFormat(p, java.util.Locale.US).apply { isLenient = false }
            fmt.parse(candidate)?.time
        }.getOrNull()
        if (parsed != null && parsed > 946_684_800_000L) {
            val src = java.util.Calendar.getInstance().apply { timeInMillis = parsed }
            val now = java.util.Calendar.getInstance()
            now.set(java.util.Calendar.YEAR, src.get(java.util.Calendar.YEAR))
            now.set(java.util.Calendar.MONTH, src.get(java.util.Calendar.MONTH))
            now.set(java.util.Calendar.DAY_OF_MONTH, src.get(java.util.Calendar.DAY_OF_MONTH))
            return now.timeInMillis
        }
    }
    return System.currentTimeMillis()
}

private fun mergeUtcDateWithTime(utcDateMs: Long, hour: Int, minute: Int): Long {
    val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcDateMs
    }
    return java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, utcCal.get(java.util.Calendar.YEAR))
        set(java.util.Calendar.MONTH, utcCal.get(java.util.Calendar.MONTH))
        set(java.util.Calendar.DAY_OF_MONTH, utcCal.get(java.util.Calendar.DAY_OF_MONTH))
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun localMsToUtcDayStart(localMs: Long): Long {
    val localCal = java.util.Calendar.getInstance().apply { timeInMillis = localMs }
    return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(java.util.Calendar.YEAR, localCal.get(java.util.Calendar.YEAR))
        set(java.util.Calendar.MONTH, localCal.get(java.util.Calendar.MONTH))
        set(java.util.Calendar.DAY_OF_MONTH, localCal.get(java.util.Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun InvoiceDateTimeSelector(
    selectedMs: Long,
    onDateTimeSelected: (Long) -> Unit,
    label: String = "تاريخ ووقت الفاتورة",
    dateFormatPattern: String = "dd/MM/yyyy",
    modifier: Modifier = Modifier
) {
    val mamoColors = LocalMamoColors.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val currentCal = remember(selectedMs) {
        java.util.Calendar.getInstance().apply { timeInMillis = selectedMs }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = ArabicFormatters.formatDateTime(selectedMs, dateFormatPattern),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { showDatePicker = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("التاريخ", style = MaterialTheme.typography.labelMedium)
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = { showTimePicker = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("الوقت", style = MaterialTheme.typography.labelMedium)
                }
                androidx.compose.material3.TextButton(
                    onClick = { onDateTimeSelected(System.currentTimeMillis()) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("الآن", style = MaterialTheme.typography.labelMedium, color = mamoColors.gold)
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = localMsToUtcDayStart(selectedMs)
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        val pickedUtc = datePickerState.selectedDateMillis
                        if (pickedUtc != null) {
                            onDateTimeSelected(
                                mergeUtcDateWithTime(
                                    utcDateMs = pickedUtc,
                                    hour = currentCal.get(java.util.Calendar.HOUR_OF_DAY),
                                    minute = currentCal.get(java.util.Calendar.MINUTE)
                                )
                            )
                        }
                        showDatePicker = false
                    }
                ) { Text("تأكيد") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timeState = androidx.compose.material3.rememberTimePickerState(
            initialHour = currentCal.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = currentCal.get(java.util.Calendar.MINUTE),
            is24Hour = true
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("تحديد ساعة الفاتورة") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TimePicker(state = timeState)
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        val updated = java.util.Calendar.getInstance().apply {
                            timeInMillis = selectedMs
                            set(java.util.Calendar.HOUR_OF_DAY, timeState.hour)
                            set(java.util.Calendar.MINUTE, timeState.minute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        onDateTimeSelected(updated)
                        showTimePicker = false
                    }
                ) { Text("تأكيد") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTimePicker = false }) { Text("إلغاء") }
            }
        )
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun InvoiceDateFilterBar(
    filter: com.example.data.repository.InvoiceDateFilter,
    onFilterChanged: (com.example.data.repository.InvoiceDateFilter) -> Unit,
    filteredCount: Int,
    totalCount: Int,
    dateFormatPattern: String = "dd/MM/yyyy",
    modifier: Modifier = Modifier
) {
    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(com.example.data.repository.InvoiceDateFilterMode.entries.size) { index ->
                val mode = com.example.data.repository.InvoiceDateFilterMode.entries[index]
                androidx.compose.material3.FilterChip(
                    selected = filter.mode == mode,
                    onClick = {
                        if (mode == com.example.data.repository.InvoiceDateFilterMode.CUSTOM) {
                            pickingStartDate = true
                        } else {
                            onFilterChanged(com.example.data.repository.InvoiceDateFilter(mode = mode))
                        }
                    },
                    label = { Text(mode.label, maxLines = 1) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rangeDesc = if (filter.mode == com.example.data.repository.InvoiceDateFilterMode.CUSTOM) {
                val fromStr = filter.customStartMs?.let { ArabicFormatters.formatDate(it, dateFormatPattern) } ?: "البداية"
                val toStr = filter.customEndMs?.let { ArabicFormatters.formatDate(it, dateFormatPattern) } ?: "اليوم"
                "الفترة: $fromStr ← $toStr"
            } else {
                "فلتر التاريخ: ${filter.mode.label}"
            }
            Text(
                text = "$rangeDesc • المعروض: $filteredCount من $totalCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (filter.mode == com.example.data.repository.InvoiceDateFilterMode.CUSTOM) {
                androidx.compose.material3.TextButton(
                    onClick = { pickingStartDate = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("تعديل الفترة", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (pickingStartDate) {
        val startState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = localMsToUtcDayStart(
                filter.customStartMs ?: (System.currentTimeMillis() - 7L * 86_400_000L)
            )
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        val utc = startState.selectedDateMillis
                        val startMs = utc?.let { mergeUtcDateWithTime(it, 0, 0) }
                        onFilterChanged(
                            filter.copy(
                                mode = com.example.data.repository.InvoiceDateFilterMode.CUSTOM,
                                customStartMs = startMs
                            )
                        )
                        pickingStartDate = false
                        pickingEndDate = true
                    }
                ) { Text("التالي (تاريخ النهاية)") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pickingStartDate = false }) { Text("إلغاء") }
            }
        ) {
            androidx.compose.material3.DatePicker(
                state = startState,
                title = { Text("من تاريخ", modifier = Modifier.padding(16.dp)) }
            )
        }
    }

    if (pickingEndDate) {
        val endState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = localMsToUtcDayStart(
                filter.customEndMs ?: System.currentTimeMillis()
            )
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        val utc = endState.selectedDateMillis
                        val endMs = utc?.let { mergeUtcDateWithTime(it, 23, 59) + 59_999L }
                        onFilterChanged(
                            filter.copy(
                                mode = com.example.data.repository.InvoiceDateFilterMode.CUSTOM,
                                customEndMs = endMs
                            )
                        )
                        pickingEndDate = false
                    }
                ) { Text("تطبيق الفلتر") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pickingEndDate = false }) { Text("تطبيق من البداية فقط") }
            }
        ) {
            androidx.compose.material3.DatePicker(
                state = endState,
                title = { Text("إلى تاريخ", modifier = Modifier.padding(16.dp)) }
            )
        }
    }
}

