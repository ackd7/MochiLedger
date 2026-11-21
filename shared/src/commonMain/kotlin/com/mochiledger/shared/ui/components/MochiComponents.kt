package com.mochiledger.shared.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochiledger.shared.model.CategoryStat
import com.mochiledger.shared.model.LedgerAccount
import com.mochiledger.shared.model.LedgerCategory
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.RecordSource
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.formatCents
import com.mochiledger.shared.ui.theme.LocalMochiDark
import com.mochiledger.shared.ui.theme.MochiColors
import com.mochiledger.shared.ui.theme.mochiShape
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun categoryEmoji(iconKey: String): String = when (iconKey) {
    "food" -> "🍜"
    "transport" -> "🚕"
    "shopping" -> "🛍️"
    "home" -> "🏠"
    "game" -> "🎮"
    "digital" -> "💻"
    "medical" -> "💊"
    "gift" -> "🎁"
    "salary" -> "💼"
    "invest" -> "📈"
    "refund" -> "↩️"
    "transfer" -> "🔄"
    "wechat" -> "💬"
    "alipay" -> "💙"
    "credit" -> "💳"
    "bank" -> "🏦"
    "cash" -> "💵"
    else -> "✨"
}

@Composable
fun MochiGlassBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(mochiShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .border(0.8.dp, color.copy(alpha = 0.28f), mochiShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun MochiChip(
    label: String,
    selected: Boolean,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val bg = if (selected) accentColor.copy(alpha = 0.18f) else MiuixTheme.colorScheme.surfaceContainer
    val border = if (selected) accentColor else MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
    val textColor = if (selected) accentColor else MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(mochiShape(14.dp))
            .background(bg)
            .border(1.dp, border, mochiShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun TransactionRow(
    tx: LedgerTransaction,
    category: LedgerCategory?,
    account: LedgerAccount?,
    targetAccount: LedgerAccount?,
    onClick: () -> Unit,
) {
    val catColor = Color(category?.colorHex ?: 0xFF4F6EF7)
    val timeText = run {
        val dt = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }
    val amountColor = when (tx.type) {
        TransactionType.EXPENSE -> MochiColors.Expense
        TransactionType.INCOME -> MochiColors.Income
        TransactionType.TRANSFER -> MochiColors.Transfer
    }
    val amountText = when (tx.type) {
        TransactionType.EXPENSE -> "-${formatCents(tx.amountCents)}"
        TransactionType.INCOME -> "+${formatCents(tx.amountCents)}"
        TransactionType.TRANSFER -> formatCents(tx.amountCents)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(mochiShape(14.dp))
                .background(catColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = categoryEmoji(category?.iconKey ?: "tag"),
                fontSize = 19.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.merchant.ifBlank { category?.name ?: "未分类" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tx.source != RecordSource.MANUAL) {
                    Spacer(Modifier.width(6.dp))
                    val badgeColor = when (tx.source) {
                        RecordSource.WECHAT_LSPOSED -> MochiColors.WeChatGreen
                        RecordSource.ALIPAY_LSPOSED -> MochiColors.AlipayBlue
                        else -> MiuixTheme.colorScheme.primary
                    }
                    MochiGlassBadge(text = tx.source.label, color = badgeColor)
                }
            }
            Spacer(Modifier.height(3.dp))
            val accountDesc = if (tx.type == TransactionType.TRANSFER && targetAccount != null) {
                "${account?.name ?: "账户"} → ${targetAccount.name}"
            } else {
                account?.name ?: "默认账户"
            }
            val subText = listOfNotNull(
                category?.name,
                accountDesc,
                tx.note.takeIf { it.isNotBlank() && it != tx.merchant },
                timeText,
            ).joinToString(" · ")
            Text(
                text = subText,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = amountText,
            color = amountColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun CategoryDonutChart(
    stats: List<CategoryStat>,
    totalLabel: String,
    totalAmountText: String,
    size: Dp = 180.dp,
) {
    val isDark = LocalMochiDark.current
    val trackColor = if (isDark) Color(0xFF2B2D36) else Color(0xFFE9ECEF)
    val sweepScale by animateFloatAsState(
        targetValue = if (stats.isEmpty()) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "donutSweep",
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 24.dp.toPx()
            val diameter = this.size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            var startAngle = -90f
            stats.forEach { stat ->
                val sweep = (stat.percentage * 360f * sweepScale).coerceAtLeast(2f)
                drawArc(
                    color = Color(stat.category.colorHex),
                    startAngle = startAngle,
                    sweepAngle = (sweep - 2.5f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalLabel,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = totalAmountText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun DailySpendingBarChart(
    dailyCents: Map<Int, Long>,
    daysInMonth: Int = 31,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxVal = (dailyCents.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val isDark = LocalMochiDark.current
    val emptyBarColor = if (isDark) Color(0xFF2B2D36) else Color(0xFFECEFF4)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (daysInMonth - 1)) / daysInMonth).coerceAtLeast(3f)
        for (day in 1..daysInMonth) {
            val value = dailyCents[day] ?: 0L
            val ratio = (value.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
            val h = if (value > 0L) (size.height * ratio).coerceAtLeast(6.dp.toPx()) else 4.dp.toPx()
            val x = (day - 1) * (barWidth + gap)
            drawRoundRect(
                color = if (value > 0L) barColor else emptyBarColor,
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
fun MochiProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "barProgress",
    )
    val isDark = LocalMochiDark.current
    val track = if (isDark) Color(0xFF2D303A) else Color(0xFFE9ECEF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
