package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
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
import com.mochiledger.shared.model.CategoryStat
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.formatCents
import com.mochiledger.shared.platform.PlatformBridge
import com.mochiledger.shared.ui.components.CategoryDonutChart
import com.mochiledger.shared.ui.components.DailySpendingBarChart
import com.mochiledger.shared.ui.components.FlatIcon
import com.mochiledger.shared.ui.components.MochiChip
import com.mochiledger.shared.ui.components.MochiProgressBar
import com.mochiledger.shared.ui.components.TransactionRow
import com.mochiledger.shared.ui.theme.MochiColors
import com.mochiledger.shared.ui.theme.mochiShape
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private enum class StatsPeriodMode(val label: String) {
    DAY("按日"),
    MONTH("按月"),
    YEAR("按年"),
}

private enum class StatsTypeFilter(val label: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    ALL("全部"),
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(state: LedgerUiState, bottomInset: Dp = 0.dp) {
    val nowDt = remember {
        Instant.fromEpochMilliseconds(PlatformBridge.currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault())
    }
    var periodMode by remember { mutableStateOf(StatsPeriodMode.MONTH) }
    var selectedYear by remember { mutableStateOf(nowDt.year) }
    var selectedMonth by remember { mutableStateOf(nowDt.monthNumber) }
    var selectedDay by remember { mutableStateOf(nowDt.dayOfMonth) }
    var typeFilter by remember { mutableStateOf(StatsTypeFilter.EXPENSE) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val tz = TimeZone.currentSystemDefault()
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.id } }

    // Filter transactions by selected Day / Month / Year
    val periodTxs = remember(state.transactions, periodMode, selectedYear, selectedMonth, selectedDay) {
        state.activeTransactions.filter { tx ->
            val dt = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(tz)
            when (periodMode) {
                StatsPeriodMode.DAY -> dt.year == selectedYear && dt.monthNumber == selectedMonth && dt.dayOfMonth == selectedDay
                StatsPeriodMode.MONTH -> dt.year == selectedYear && dt.monthNumber == selectedMonth
                StatsPeriodMode.YEAR -> dt.year == selectedYear
            }
        }
    }

    val typeFilteredTxs = remember(periodTxs, typeFilter) {
        when (typeFilter) {
            StatsTypeFilter.EXPENSE -> periodTxs.filter { it.type == TransactionType.EXPENSE }
            StatsTypeFilter.INCOME -> periodTxs.filter { it.type == TransactionType.INCOME }
            StatsTypeFilter.ALL -> periodTxs
        }
    }

    val activeTotal = remember(typeFilteredTxs, typeFilter) {
        when (typeFilter) {
            StatsTypeFilter.EXPENSE -> typeFilteredTxs.sumOf { it.amountCents }
            StatsTypeFilter.INCOME -> typeFilteredTxs.sumOf { it.amountCents }
            StatsTypeFilter.ALL -> {
                val inc = typeFilteredTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
                val exp = typeFilteredTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
                inc - exp
            }
        }
    }

    val activeStats = remember(typeFilteredTxs, categoryMap) {
        val grouped = mutableMapOf<String, Pair<Long, Int>>()
        var sum = 0L
        for (tx in typeFilteredTxs) {
            sum += tx.amountCents
            val prev = grouped[tx.categoryId] ?: (0L to 0)
            grouped[tx.categoryId] = (prev.first + tx.amountCents) to (prev.second + 1)
        }
        grouped.entries.mapNotNull { (catId, pair) ->
            val cat = categoryMap[catId] ?: return@mapNotNull null
            val pct = if (sum > 0L) pair.first.toFloat() / sum.toFloat() else 0f
            CategoryStat(cat, pair.first, pct, pair.second)
        }.sortedByDescending { it.amountCents }
    }

    // Reset selected category if it no longer exists in current filter
    val effectiveCategoryId = selectedCategoryId?.takeIf { id -> activeStats.any { it.category.id == id } }
    val detailTxs = remember(typeFilteredTxs, effectiveCategoryId) {
        if (effectiveCategoryId == null) typeFilteredTxs
        else typeFilteredTxs.filter { it.categoryId == effectiveCategoryId }
    }

    val dailyExpenseCents = remember(periodTxs) {
        val map = mutableMapOf<Int, Long>()
        for (tx in periodTxs) {
            if (tx.type == TransactionType.EXPENSE) {
                val dt = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(tz)
                map[dt.dayOfMonth] = (map[dt.dayOfMonth] ?: 0L) + tx.amountCents
            }
        }
        map
    }

    val dateDisplayText = when (periodMode) {
        StatsPeriodMode.DAY -> "${selectedYear}年${selectedMonth.toString().padStart(2, '0')}月${selectedDay.toString().padStart(2, '0')}日"
        StatsPeriodMode.MONTH -> "${selectedYear}年${selectedMonth.toString().padStart(2, '0')}月"
        StatsPeriodMode.YEAR -> "${selectedYear}年"
    }

    val periodShortLabel = when (periodMode) {
        StatsPeriodMode.DAY -> {
            if (selectedYear == nowDt.year && selectedMonth == nowDt.monthNumber && selectedDay == nowDt.dayOfMonth) "今日"
            else "${selectedMonth}月${selectedDay}日"
        }
        StatsPeriodMode.MONTH -> {
            if (selectedYear == nowDt.year && selectedMonth == nowDt.monthNumber) "本月"
            else if (selectedYear == nowDt.year) "${selectedMonth}月"
            else "${selectedYear}年${selectedMonth}月"
        }
        StatsPeriodMode.YEAR -> if (selectedYear == nowDt.year) "今年" else "${selectedYear}年"
    }

    val totalLabel = when (typeFilter) {
        StatsTypeFilter.EXPENSE -> "${periodShortLabel}总支出"
        StatsTypeFilter.INCOME -> "${periodShortLabel}总收入"
        StatsTypeFilter.ALL -> "${periodShortLabel}净结余"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top Date Selector Box (placed directly above 支出 / 收入 / 全部)
        item(key = "top_date_selector") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MochiChip("◀", false) {
                        when (periodMode) {
                            StatsPeriodMode.DAY -> {
                                if (selectedDay > 1) {
                                    selectedDay -= 1
                                } else {
                                    if (selectedMonth == 1) {
                                        selectedYear -= 1
                                        selectedMonth = 12
                                    } else {
                                        selectedMonth -= 1
                                    }
                                    selectedDay = daysInMonth(selectedYear, selectedMonth)
                                }
                            }
                            StatsPeriodMode.MONTH -> {
                                if (selectedMonth == 1) {
                                    selectedYear -= 1
                                    selectedMonth = 12
                                } else {
                                    selectedMonth -= 1
                                }
                            }
                            StatsPeriodMode.YEAR -> selectedYear -= 1
                        }
                    }
                    Row(
                        modifier = Modifier
                            .clip(mochiShape(14.dp))
                            .background(MiuixTheme.colorScheme.surface)
                            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.35f), mochiShape(14.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlatIcon("calendar", MiuixTheme.colorScheme.primary, size = 16.dp)
                        Text(
                            text = "$dateDisplayText ▾",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                    }
                    MochiChip("▶", false) {
                        when (periodMode) {
                            StatsPeriodMode.DAY -> {
                                val maxD = daysInMonth(selectedYear, selectedMonth)
                                if (selectedDay < maxD) {
                                    selectedDay += 1
                                } else {
                                    if (selectedMonth == 12) {
                                        selectedYear += 1
                                        selectedMonth = 1
                                    } else {
                                        selectedMonth += 1
                                    }
                                    selectedDay = 1
                                }
                            }
                            StatsPeriodMode.MONTH -> {
                                if (selectedMonth == 12) {
                                    selectedYear += 1
                                    selectedMonth = 1
                                } else {
                                    selectedMonth += 1
                                }
                            }
                            StatsPeriodMode.YEAR -> selectedYear += 1
                        }
                    }
                }
            }
        }

        // Centered type filter (支出 / 收入 / 全部) directly below Date Selector
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MochiChip("支出", typeFilter == StatsTypeFilter.EXPENSE, MochiColors.Expense) {
                        typeFilter = StatsTypeFilter.EXPENSE
                        selectedCategoryId = null
                    }
                    MochiChip("收入", typeFilter == StatsTypeFilter.INCOME, MochiColors.Income) {
                        typeFilter = StatsTypeFilter.INCOME
                        selectedCategoryId = null
                    }
                    MochiChip("全部", typeFilter == StatsTypeFilter.ALL, MiuixTheme.colorScheme.primary) {
                        typeFilter = StatsTypeFilter.ALL
                        selectedCategoryId = null
                    }
                }
            }
        }

        // Main Summary & Donut Card
        item(key = "donut") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CategoryDonutChart(
                        stats = activeStats,
                        totalLabel = totalLabel,
                        totalAmountText = formatCents(activeTotal, showSign = typeFilter == StatsTypeFilter.ALL),
                    )
                    Spacer(Modifier.height(16.dp))
                    if (activeStats.isEmpty()) {
                        Text(
                            "暂无该时段分类数据",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            activeStats.forEach { stat ->
                                val catColor = Color(stat.category.colorHex)
                                val isSelectedCat = effectiveCategoryId == stat.category.id
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(mochiShape(12.dp))
                                        .background(if (isSelectedCat) catColor.copy(alpha = 0.10f) else Color.Transparent)
                                        .clickable {
                                            selectedCategoryId = if (isSelectedCat) null else stat.category.id
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                ) {
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
                                                fontWeight = if (isSelectedCat) FontWeight.Bold else FontWeight.Medium,
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

        // Category-Filtered Transaction Details Card
        item(key = "category_details") {
            SmallTitle(text = "分类账单明细（点击类别筛选）")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    if (activeStats.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MochiChip("全部分类", effectiveCategoryId == null) {
                                selectedCategoryId = null
                            }
                            activeStats.forEach { stat ->
                                MochiChip(
                                    label = stat.category.name,
                                    selected = effectiveCategoryId == stat.category.id,
                                    accentColor = Color(stat.category.colorHex),
                                ) {
                                    selectedCategoryId = if (effectiveCategoryId == stat.category.id) null else stat.category.id
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }

                    if (detailTxs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "该条件下暂无账单明细",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    } else {
                        detailTxs.forEachIndexed { idx, tx ->
                            TransactionRow(
                                tx = tx,
                                category = categoryMap[tx.categoryId],
                                account = accountMap[tx.accountId],
                                targetAccount = tx.targetAccountId?.let { accountMap[it] },
                                onClick = {},
                            )
                            if (idx < detailTxs.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Daily Spending Bar Chart (shown in Month mode)
        if (periodMode == StatsPeriodMode.MONTH) {
            item(key = "daily_trend") {
                val monthDays = daysInMonth(selectedYear, selectedMonth)
                SmallTitle(text = "${selectedYear}年${selectedMonth}月 每日支出趋势")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        DailySpendingBarChart(
                            dailyCents = dailyExpenseCents,
                            daysInMonth = monthDays,
                            barColor = MiuixTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("1日", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Text("15日", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Text("${monthDays}日", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        FineGrainedDatePickerDialog(
            initialMode = periodMode,
            initialYear = selectedYear,
            initialMonth = selectedMonth,
            initialDay = selectedDay,
            currentYear = nowDt.year,
            currentMonth = nowDt.monthNumber,
            currentDay = nowDt.dayOfMonth,
            onDismiss = { showDatePicker = false },
            onConfirm = { mode, y, m, d ->
                periodMode = mode
                selectedYear = y
                selectedMonth = m
                selectedDay = d.coerceIn(1, daysInMonth(y, m))
                showDatePicker = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FineGrainedDatePickerDialog(
    initialMode: StatsPeriodMode,
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    currentYear: Int,
    currentMonth: Int,
    currentDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (StatsPeriodMode, Int, Int, Int) -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var pickerYear by remember { mutableStateOf(initialYear) }
    var pickerMonth by remember { mutableStateOf(initialMonth) }
    var pickerDay by remember { mutableStateOf(initialDay) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("选择统计日期", fontSize = 17.sp, fontWeight = FontWeight.Bold)

                // Mode tabs: 按日 / 按月 / 按年
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsPeriodMode.entries.forEach { m ->
                        MochiChip(m.label, mode == m) { mode = m }
                    }
                }

                // Quick shortcuts
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MochiChip("今天", false) {
                        onConfirm(StatsPeriodMode.DAY, currentYear, currentMonth, currentDay)
                    }
                    MochiChip("本月", false) {
                        onConfirm(StatsPeriodMode.MONTH, currentYear, currentMonth, currentDay)
                    }
                    val prevY = if (currentMonth == 1) currentYear - 1 else currentYear
                    val prevM = if (currentMonth == 1) 12 else currentMonth - 1
                    MochiChip("上月", false) {
                        onConfirm(StatsPeriodMode.MONTH, prevY, prevM, 1)
                    }
                    MochiChip("今年", false) {
                        onConfirm(StatsPeriodMode.YEAR, currentYear, currentMonth, currentDay)
                    }
                    MochiChip("去年", false) {
                        onConfirm(StatsPeriodMode.YEAR, currentYear - 1, currentMonth, 1)
                    }
                }

                when (mode) {
                    StatsPeriodMode.YEAR -> {
                        val years = ((currentYear - 4)..(currentYear + 1)).toList()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            years.chunked(3).forEach { rowYears ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowYears.forEach { y ->
                                        val selected = pickerYear == y
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(mochiShape(12.dp))
                                                .background(if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer)
                                                .clickable {
                                                    pickerYear = y
                                                    onConfirm(StatsPeriodMode.YEAR, y, pickerMonth, pickerDay)
                                                }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "${y}年",
                                                color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    StatsPeriodMode.MONTH -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MochiChip("◀ ${pickerYear - 1}", false) { pickerYear -= 1 }
                            Text("${pickerYear} 年", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            MochiChip("${pickerYear + 1} ▶", false) { pickerYear += 1 }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (rowIdx in 0 until 3) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    for (colIdx in 0 until 4) {
                                        val m = rowIdx * 4 + colIdx + 1
                                        val selected = pickerMonth == m
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(mochiShape(12.dp))
                                                .background(if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer)
                                                .clickable {
                                                    pickerMonth = m
                                                    onConfirm(StatsPeriodMode.MONTH, pickerYear, m, 1)
                                                }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "${m}月",
                                                color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    StatsPeriodMode.DAY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MochiChip("◀", false) {
                                if (pickerMonth == 1) {
                                    pickerYear -= 1
                                    pickerMonth = 12
                                } else {
                                    pickerMonth -= 1
                                }
                                pickerDay = pickerDay.coerceAtMost(daysInMonth(pickerYear, pickerMonth))
                            }
                            Text("${pickerYear}年${pickerMonth}月", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            MochiChip("▶", false) {
                                if (pickerMonth == 12) {
                                    pickerYear += 1
                                    pickerMonth = 1
                                } else {
                                    pickerMonth += 1
                                }
                                pickerDay = pickerDay.coerceAtMost(daysInMonth(pickerYear, pickerMonth))
                            }
                        }
                        val totalDays = daysInMonth(pickerYear, pickerMonth)
                        val rows = (1..totalDays).toList().chunked(7)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rows.forEach { week ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    for (idx in 0 until 7) {
                                        val d = week.getOrNull(idx)
                                        if (d == null) {
                                            Spacer(Modifier.weight(1f))
                                        } else {
                                            val selected = pickerDay == d
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(mochiShape(10.dp))
                                                    .background(if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer)
                                                    .clickable {
                                                        pickerDay = d
                                                        onConfirm(StatsPeriodMode.DAY, pickerYear, pickerMonth, d)
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    "$d",
                                                    color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                    TextButton(
                        text = "确定",
                        onClick = { onConfirm(mode, pickerYear, pickerMonth, pickerDay) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
