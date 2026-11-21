package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mochiledger.shared.data.LedgerUiState
import com.mochiledger.shared.model.formatCents
import com.mochiledger.shared.platform.PlatformBridge
import com.mochiledger.shared.ui.components.CategoryDonutChart
import com.mochiledger.shared.ui.components.DailySpendingBarChart
import com.mochiledger.shared.ui.components.FlatIcon
import com.mochiledger.shared.ui.components.MochiChip
import com.mochiledger.shared.ui.components.MochiProgressBar
import com.mochiledger.shared.ui.theme.MochiColors
import com.mochiledger.shared.ui.theme.mochiShape
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(state: LedgerUiState, bottomInset: Dp = 0.dp) {
    val nowDt = remember {
        Instant.fromEpochMilliseconds(PlatformBridge.currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault())
    }
    var selectedYear by remember { mutableStateOf(nowDt.year) }
    var selectedMonth by remember { mutableStateOf(nowDt.monthNumber) }
    var showExpense by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }

    val summary = remember(state.transactions, selectedYear, selectedMonth) {
        state.monthlySummary(selectedYear, selectedMonth)
    }
    val activeStats = if (showExpense) summary.expenseByCategory else summary.incomeByCategory
    val activeTotal = if (showExpense) summary.expenseCents else summary.incomeCents

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MochiChip("支出分析", showExpense, MochiColors.Expense) { showExpense = true }
                    MochiChip("收入分析", !showExpense, MochiColors.Income) { showExpense = false }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MochiChip("◀", false) {
                        if (selectedMonth == 1) {
                            selectedYear -= 1
                            selectedMonth = 12
                        } else {
                            selectedMonth -= 1
                        }
                    }
                    // Clickable date pill opening quick year/month picker
                    Row(
                        modifier = Modifier
                            .clip(mochiShape(14.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.35f), mochiShape(14.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FlatIcon("calendar", MiuixTheme.colorScheme.primary, size = 15.dp)
                        Text(
                            text = "${selectedYear}-${selectedMonth.toString().padStart(2, '0')}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                    MochiChip("▶", false) {
                        if (selectedMonth == 12) {
                            selectedYear += 1
                            selectedMonth = 1
                        } else {
                            selectedMonth += 1
                        }
                    }
                }
            }
        }

        item(key = "donut") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CategoryDonutChart(
                        stats = activeStats,
                        totalLabel = if (showExpense) "本月总支出" else "本月总收入",
                        totalAmountText = formatCents(activeTotal),
                    )
                    Spacer(Modifier.height(16.dp))
                    if (activeStats.isEmpty()) {
                        Text(
                            "暂无该月分类数据",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            activeStats.forEach { stat ->
                                val catColor = Color(stat.category.colorHex)
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(mochiShape(8.dp))
                                                    .background(catColor.copy(alpha = 0.16f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                FlatIcon(stat.category.iconKey, catColor, size = 15.dp)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "${stat.category.name} (${stat.transactionCount}笔)",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        Text(
                                            "${formatCents(stat.amountCents)}  ${(stat.percentage * 100).roundToInt()}%",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    MochiProgressBar(progress = stat.percentage, color = catColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "daily_trend") {
            SmallTitle(text = "每日支出趋势")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    DailySpendingBarChart(
                        dailyCents = summary.dailyExpenseCents,
                        barColor = MiuixTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1日", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Text("15日", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Text("31日", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }

        if (state.activeBudgets.isNotEmpty()) {
            item(key = "budgets") {
                SmallTitle(text = "月度预算监控")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        state.activeBudgets.forEach { budget ->
                            val spent = if (budget.categoryId == null) {
                                summary.expenseCents
                            } else {
                                summary.expenseByCategory.firstOrNull { it.category.id == budget.categoryId }?.amountCents ?: 0L
                            }
                            val ratio = if (budget.limitCents > 0L) spent.toFloat() / budget.limitCents.toFloat() else 0f
                            val barColor = when {
                                ratio >= 1f -> MochiColors.Expense
                                ratio >= 0.8f -> MochiColors.WarningAmber
                                else -> MochiColors.Income
                            }
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(budget.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "${formatCents(spent)} / ${formatCents(budget.limitCents)}",
                                        fontSize = 13.sp,
                                        color = barColor,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                MochiProgressBar(progress = ratio, color = barColor)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        QuickYearMonthPickerDialog(
            initialYear = selectedYear,
            initialMonth = selectedMonth,
            currentYear = nowDt.year,
            currentMonth = nowDt.monthNumber,
            onDismiss = { showDatePicker = false },
            onSelect = { y, m ->
                selectedYear = y
                selectedMonth = m
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun QuickYearMonthPickerDialog(
    initialYear: Int,
    initialMonth: Int,
    currentYear: Int,
    currentMonth: Int,
    onDismiss: () -> Unit,
    onSelect: (Int, Int) -> Unit,
) {
    var pickerYear by remember { mutableStateOf(initialYear) }
    var pickerMonth by remember { mutableStateOf(initialMonth) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("快捷选择统计月份", fontSize = 17.sp, fontWeight = FontWeight.Bold)

                // Quick shortcuts
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MochiChip("本月", pickerYear == currentYear && pickerMonth == currentMonth) {
                        onSelect(currentYear, currentMonth)
                    }
                    val prevY = if (currentMonth == 1) currentYear - 1 else currentYear
                    val prevM = if (currentMonth == 1) 12 else currentMonth - 1
                    MochiChip("上月", pickerYear == prevY && pickerMonth == prevM) {
                        onSelect(prevY, prevM)
                    }
                    MochiChip("去年同月", pickerYear == currentYear - 1 && pickerMonth == currentMonth) {
                        onSelect(currentYear - 1, currentMonth)
                    }
                }

                // Year selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MochiChip("◀ ${pickerYear - 1}", false) { pickerYear -= 1 }
                    Text("${pickerYear} 年", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    MochiChip("${pickerYear + 1} ▶", false) { pickerYear += 1 }
                }

                // 3x4 Month Grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (rowIdx in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (colIdx in 0 until 4) {
                                val m = rowIdx * 4 + colIdx + 1
                                val selected = pickerMonth == m
                                val bg = if (selected) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainer
                                }
                                val fg = if (selected) Color.White else MiuixTheme.colorScheme.onSurface
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(mochiShape(12.dp))
                                        .background(bg)
                                        .clickable {
                                            pickerMonth = m
                                            onSelect(pickerYear, m)
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${m}月", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                    TextButton(
                        text = "确定",
                        onClick = { onSelect(pickerYear, pickerMonth) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
