package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mochiledger.shared.data.LedgerUiState
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.RecordSource
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.formatCents
import com.mochiledger.shared.model.parseAmountToCents
import com.mochiledger.shared.platform.PlatformBridge
import com.mochiledger.shared.ui.components.FlatIcon
import com.mochiledger.shared.ui.components.MochiChip
import com.mochiledger.shared.ui.components.TransactionRow
import com.mochiledger.shared.ui.theme.MochiColors
import com.mochiledger.shared.ui.theme.mochiHeroBrush
import com.mochiledger.shared.ui.theme.mochiShape
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TransactionsScreen(
    state: LedgerUiState,
    onSaveTransaction: (LedgerTransaction) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    val nowDt = remember {
        Instant.fromEpochMilliseconds(PlatformBridge.currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault())
    }
    var todayOnly by remember { mutableStateOf(true) }
    var selectedYear by remember { mutableStateOf(nowDt.year) }
    var selectedMonth by remember { mutableStateOf(nowDt.monthNumber) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<TransactionType?>(null) }
    var editingTx by remember { mutableStateOf<LedgerTransaction?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.id } }
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }

    val periodTxs = remember(state.transactions, todayOnly, selectedYear, selectedMonth) {
        val tz = TimeZone.currentSystemDefault()
        state.activeTransactions.filter { tx ->
            val dt = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(tz)
            if (todayOnly) {
                dt.year == nowDt.year && dt.monthNumber == nowDt.monthNumber && dt.dayOfMonth == nowDt.dayOfMonth
            } else {
                dt.year == selectedYear && dt.monthNumber == selectedMonth
            }
        }
    }
    val heroExpenseCents = remember(periodTxs) {
        periodTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    }
    val heroIncomeCents = remember(periodTxs) {
        periodTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
    }
    val heroNetCents = heroIncomeCents - heroExpenseCents

    val filteredTxs = remember(periodTxs, searchQuery, filterType) {
        periodTxs.filter { tx ->
            val matchType = filterType == null || tx.type == filterType
            val q = searchQuery.trim()
            val matchQuery = q.isEmpty() ||
                tx.merchant.contains(q, ignoreCase = true) ||
                tx.note.contains(q, ignoreCase = true) ||
                (categoryMap[tx.categoryId]?.name?.contains(q, ignoreCase = true) == true) ||
                (accountMap[tx.accountId]?.name?.contains(q, ignoreCase = true) == true)
            matchType && matchQuery
        }
    }

    val groupedByDay = remember(filteredTxs) {
        val tz = TimeZone.currentSystemDefault()
        filteredTxs.groupBy { tx ->
            val dt = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(tz)
            "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Mochi Liquid-Glass style Monthly Hero Card
            item(key = "hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(mochiShape(24.dp))
                        .background(mochiHeroBrush())
                        .padding(20.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (todayOnly) {
                                    "今日记账 · ${nowDt.monthNumber}月${nowDt.dayOfMonth}日"
                                } else {
                                    "${selectedYear}年${selectedMonth}月 账单"
                                },
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(mochiShape(10.dp))
                                        .background(Color.White.copy(alpha = if (todayOnly) 0.30f else 0.14f))
                                        .clickable { todayOnly = true }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("今日", color = Color.White, fontSize = 12.sp, fontWeight = if (todayOnly) FontWeight.Bold else FontWeight.Normal)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(mochiShape(10.dp))
                                        .background(Color.White.copy(alpha = if (!todayOnly) 0.30f else 0.14f))
                                        .clickable { todayOnly = false }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("本月", color = Color.White, fontSize = 12.sp, fontWeight = if (!todayOnly) FontWeight.Bold else FontWeight.Normal)
                                }
                                if (!todayOnly) {
                                    Box(
                                        modifier = Modifier
                                            .clip(mochiShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.16f))
                                            .clickable {
                                                if (selectedMonth == 1) {
                                                    selectedYear -= 1
                                                    selectedMonth = 12
                                                } else {
                                                    selectedMonth -= 1
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Text("◀", color = Color.White, fontSize = 11.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(mochiShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.16f))
                                            .clickable {
                                                if (selectedMonth == 12) {
                                                    selectedYear += 1
                                                    selectedMonth = 1
                                                } else {
                                                    selectedMonth += 1
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Text("▶", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (todayOnly) "今日结余" else "本月结余",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = formatCents(heroNetCents, showSign = true),
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(mochiShape(16.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(if (todayOnly) "今日支出" else "本月支出", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                                Text(
                                    text = formatCents(heroExpenseCents),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (todayOnly) "今日收入" else "本月收入", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                                Text(
                                    text = formatCents(heroIncomeCents),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            // Search & Filter Bar
            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "搜索商户、备注或分类...",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MochiChip("全部", filterType == null) { filterType = null }
                        MochiChip("支出", filterType == TransactionType.EXPENSE, MochiColors.Expense) {
                            filterType = TransactionType.EXPENSE
                        }
                        MochiChip("收入", filterType == TransactionType.INCOME, MochiColors.Income) {
                            filterType = TransactionType.INCOME
                        }
                        MochiChip("转账", filterType == TransactionType.TRANSFER, MochiColors.Transfer) {
                            filterType = TransactionType.TRANSFER
                        }
                    }
                }
            }

            if (groupedByDay.isEmpty()) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            FlatIcon("ledger", MiuixTheme.colorScheme.onSurfaceVariantSummary, size = 34.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(if (todayOnly) "今日暂无记账记录" else "本月暂无账单记录", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "点击右下角悬浮「+」按钮开始记账",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                groupedByDay.forEach { (dayKey, dayTxs) ->
                    val dayExpense = dayTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
                    val dayIncome = dayTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
                    item(key = "header_$dayKey") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = dayKey,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = "支 ${formatCents(dayExpense)}  收 ${formatCents(dayIncome)}",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    item(key = "card_$dayKey") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                dayTxs.forEachIndexed { idx, tx ->
                                    TransactionRow(
                                        tx = tx,
                                        category = categoryMap[tx.categoryId],
                                        account = accountMap[tx.accountId],
                                        targetAccount = tx.targetAccountId?.let { accountMap[it] },
                                        onClick = {
                                            editingTx = tx
                                            showAddDialog = true
                                        },
                                    )
                                    if (idx < dayTxs.lastIndex) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating '+' Action Button for manual bookkeeping
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 22.dp + bottomInset)
                .size(60.dp)
                .shadow(elevation = 10.dp, shape = mochiShape(20.dp))
                .clip(mochiShape(20.dp))
                .background(mochiHeroBrush())
                .clickable {
                    editingTx = null
                    showAddDialog = true
                },
            contentAlignment = Alignment.Center,
        ) {
            FlatIcon(name = "plus", tint = Color.White, size = 26.dp)
        }
    }

    if (showAddDialog) {
        TransactionEditorDialog(
            initial = editingTx,
            state = state,
            onDismiss = { showAddDialog = false },
            onSave = { tx ->
                onSaveTransaction(tx)
                showAddDialog = false
                val txDt = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
                val isTodayTx = txDt.year == nowDt.year && txDt.monthNumber == nowDt.monthNumber && txDt.dayOfMonth == nowDt.dayOfMonth
                if (!isTodayTx) {
                    todayOnly = false
                    selectedYear = txDt.year
                    selectedMonth = txDt.monthNumber
                }
                val dateTag = "${txDt.monthNumber.toString().padStart(2, '0')}-${txDt.dayOfMonth.toString().padStart(2, '0')} " +
                    "${txDt.hour.toString().padStart(2, '0')}:${txDt.minute.toString().padStart(2, '0')}"
                onShowMessage(if (editingTx == null) "已记账：${formatCents(tx.amountCents)}（$dateTag）" else "已更新账单（$dateTag）")
            },
            onDelete = editingTx?.let { existing ->
                {
                    onDeleteTransaction(existing.id)
                    showAddDialog = false
                    onShowMessage("已删除账单")
                }
            },
        )
    }
}

@Composable
private fun TransactionEditorDialog(
    initial: LedgerTransaction?,
    state: LedgerUiState,
    onDismiss: () -> Unit,
    onSave: (LedgerTransaction) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val tz = TimeZone.currentSystemDefault()
    var txType by remember { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
    var amountText by remember {
        mutableStateOf(
            initial?.let { "${it.amountCents / 100}.${(it.amountCents % 100).toString().padStart(2, '0')}" } ?: "",
        )
    }
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var selectedTimestamp by remember {
        mutableStateOf(initial?.timestamp ?: PlatformBridge.currentTimeMillis())
    }
    var showDateTimePicker by remember { mutableStateOf(false) }

    val selectedDt = remember(selectedTimestamp) {
        Instant.fromEpochMilliseconds(selectedTimestamp).toLocalDateTime(tz)
    }
    val formattedDateTime = remember(selectedDt) {
        "${selectedDt.year}-${selectedDt.monthNumber.toString().padStart(2, '0')}-${selectedDt.dayOfMonth.toString().padStart(2, '0')} " +
            "${selectedDt.hour.toString().padStart(2, '0')}:${selectedDt.minute.toString().padStart(2, '0')}"
    }

    val availableCategories = remember(state.categories, txType) {
        state.activeCategories.filter { it.type == txType }
    }
    var selectedCategoryId by remember(txType) {
        mutableStateOf(
            initial?.categoryId?.takeIf { id -> availableCategories.any { it.id == id } }
                ?: availableCategories.firstOrNull()?.id
                ?: if (txType == TransactionType.INCOME) "cat_other_inc" else "cat_other_exp",
        )
    }
    val accounts = state.activeAccounts
    var selectedAccountId by remember {
        mutableStateOf(initial?.accountId ?: accounts.firstOrNull()?.id ?: "acc_wechat")
    }
    var targetAccountId by remember {
        mutableStateOf(
            initial?.targetAccountId ?: accounts.firstOrNull { it.id != selectedAccountId }?.id,
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (initial == null) "记一笔" else "编辑账单",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MochiChip("支出", txType == TransactionType.EXPENSE, MochiColors.Expense) {
                        txType = TransactionType.EXPENSE
                    }
                    MochiChip("收入", txType == TransactionType.INCOME, MochiColors.Income) {
                        txType = TransactionType.INCOME
                    }
                    MochiChip("转账", txType == TransactionType.TRANSFER, MochiColors.Transfer) {
                        txType = TransactionType.TRANSFER
                    }
                }

                // Date & Time Selector Bar
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(mochiShape(13.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.35f), mochiShape(13.dp))
                            .clickable { showDateTimePicker = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FlatIcon("calendar", MiuixTheme.colorScheme.primary, size = 15.dp)
                        Text(
                            text = "$formattedDateTime ▾",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    MochiChip("现在", false) {
                        selectedTimestamp = PlatformBridge.currentTimeMillis()
                    }
                    MochiChip("昨天", false) {
                        selectedTimestamp = PlatformBridge.currentTimeMillis() - 86_400_000L
                    }
                }

                TextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) amountText = input
                    },
                    label = "金额（元，如 32.50）",
                    modifier = Modifier.fillMaxWidth(),
                )

                TextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = "收款人 / 商户（如 瑞幸咖啡、张三）",
                    modifier = Modifier.fillMaxWidth(),
                )

                TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注 / 买了什么（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )

                val rawAutoMsg = initial?.rawAutoMessage
                if (!rawAutoMsg.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(mochiShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "原始支付信息",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = rawAutoMsg,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }

                if (txType != TransactionType.TRANSFER) {
                    SmallTitle(text = "分类")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        availableCategories.forEach { cat ->
                            MochiChip(
                                label = cat.name,
                                selected = selectedCategoryId == cat.id,
                                accentColor = Color(cat.colorHex),
                                onClick = { selectedCategoryId = cat.id },
                            )
                        }
                    }
                }

                SmallTitle(text = if (txType == TransactionType.TRANSFER) "转出账户" else "账户")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    accounts.forEach { acc ->
                        MochiChip(
                            label = acc.name,
                            selected = selectedAccountId == acc.id,
                            accentColor = Color(acc.colorHex),
                            onClick = { selectedAccountId = acc.id },
                        )
                    }
                }

                if (txType == TransactionType.TRANSFER) {
                    SmallTitle(text = "转入账户")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        accounts.filter { it.id != selectedAccountId }.forEach { acc ->
                            MochiChip(
                                label = acc.name,
                                selected = targetAccountId == acc.id,
                                accentColor = Color(acc.colorHex),
                                onClick = { targetAccountId = acc.id },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onDelete != null) {
                        TextButton(
                            text = "删除",
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "保存",
                        onClick = {
                            val cents = parseAmountToCents(amountText) ?: return@TextButton
                            if (cents <= 0L) return@TextButton
                            val now = PlatformBridge.currentTimeMillis()
                            onSave(
                                LedgerTransaction(
                                    id = initial?.id ?: PlatformBridge.randomUuid(),
                                    type = txType,
                                    amountCents = cents,
                                    categoryId = if (txType == TransactionType.TRANSFER) "cat_transfer" else selectedCategoryId,
                                    accountId = selectedAccountId,
                                    targetAccountId = if (txType == TransactionType.TRANSFER) targetAccountId else null,
                                    timestamp = selectedTimestamp,
                                    merchant = merchant.trim(),
                                    note = note.trim(),
                                    source = initial?.source ?: RecordSource.MANUAL,
                                    rawAutoMessage = initial?.rawAutoMessage,
                                    updatedAt = now,
                                ),
                            )
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showDateTimePicker) {
        TransactionDateTimePickerDialog(
            initialMillis = selectedTimestamp,
            onDismiss = { showDateTimePicker = false },
            onConfirm = { newMillis ->
                selectedTimestamp = newMillis
                showDateTimePicker = false
            },
        )
    }
}

private fun txDaysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

private fun txFirstDayOffsetSunday(year: Int, month: Int): Int {
    return when (LocalDate(year, month, 1).dayOfWeek) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
    }
}

@Composable
private fun TransactionDateTimePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    val nowDt = remember {
        Instant.fromEpochMilliseconds(PlatformBridge.currentTimeMillis()).toLocalDateTime(tz)
    }
    val initDt = remember(initialMillis) {
        Instant.fromEpochMilliseconds(initialMillis).toLocalDateTime(tz)
    }

    var year by remember { mutableStateOf(initDt.year) }
    var month by remember { mutableStateOf(initDt.monthNumber) }
    var day by remember { mutableStateOf(initDt.dayOfMonth) }
    var hour by remember { mutableStateOf(initDt.hour) }
    var minute by remember { mutableStateOf(initDt.minute) }
    var showYearMonthGrid by remember { mutableStateOf(false) }

    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
    val years = remember(nowDt.year) { ((nowDt.year - 5)..(nowDt.year + 1)).toList() }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("选择记账时间", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Text(
                            text = "${year}年${month}月${day}日 ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    MochiChip("现在", false) {
                        val cur = Instant.fromEpochMilliseconds(PlatformBridge.currentTimeMillis()).toLocalDateTime(tz)
                        year = cur.year
                        month = cur.monthNumber
                        day = cur.dayOfMonth
                        hour = cur.hour
                        minute = cur.minute
                        showYearMonthGrid = false
                    }
                }

                HorizontalDivider()

                // Month/Year bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(mochiShape(10.dp))
                            .clickable { showYearMonthGrid = !showYearMonthGrid }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("${year}年${month}月", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (showYearMonthGrid) "▴" else "▾", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                    }
                    if (!showYearMonthGrid) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.surfaceContainer)
                                    .clickable {
                                        if (month == 1) {
                                            year -= 1
                                            month = 12
                                        } else {
                                            month -= 1
                                        }
                                        day = day.coerceAtMost(txDaysInMonth(year, month))
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text("‹", fontSize = 18.sp) }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.surfaceContainer)
                                    .clickable {
                                        if (month == 12) {
                                            year += 1
                                            month = 1
                                        } else {
                                            month += 1
                                        }
                                        day = day.coerceAtMost(txDaysInMonth(year, month))
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text("›", fontSize = 18.sp) }
                        }
                    }
                }

                if (showYearMonthGrid) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        years.forEach { y ->
                            MochiChip("${y}年", year == y) {
                                year = y
                                day = day.coerceAtMost(txDaysInMonth(y, month))
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in 0 until 3) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (c in 0 until 4) {
                                    val m = r * 4 + c + 1
                                    val sel = month == m
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(mochiShape(12.dp))
                                            .background(if (sel) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer)
                                            .clickable {
                                                month = m
                                                day = day.coerceAtMost(txDaysInMonth(year, m))
                                                showYearMonthGrid = false
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${m}月",
                                            color = if (sel) Color.White else MiuixTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekdays.forEach { wd ->
                            Text(
                                text = wd,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    val offset = txFirstDayOffsetSunday(year, month)
                    val totalDays = txDaysInMonth(year, month)
                    val cells = buildList<Int?> {
                        repeat(offset) { add(null) }
                        for (d in 1..totalDays) add(d)
                        while (size % 7 != 0) add(null)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        cells.chunked(7).forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                week.forEach { d ->
                                    if (d == null) {
                                        Spacer(Modifier.weight(1f).height(34.dp))
                                    } else {
                                        val isSel = d == day
                                        val isToday = year == nowDt.year && month == nowDt.monthNumber && d == nowDt.dayOfMonth
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                                .clickable { day = d },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .then(
                                                        when {
                                                            isSel -> Modifier.background(MiuixTheme.colorScheme.primary)
                                                            isToday -> Modifier.border(1.5.dp, MiuixTheme.colorScheme.primary, CircleShape)
                                                            else -> Modifier
                                                        },
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = "$d",
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = when {
                                                        isSel -> Color.White
                                                        isToday -> MiuixTheme.colorScheme.primary
                                                        else -> MiuixTheme.colorScheme.onSurface
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Hour & Minute Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("具体时间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TimeStepperPill(
                            valueText = "${hour.toString().padStart(2, '0')}时",
                            onMinus = { hour = (hour + 23) % 24 },
                            onPlus = { hour = (hour + 1) % 24 },
                        )
                        Text(":", fontWeight = FontWeight.Bold)
                        TimeStepperPill(
                            valueText = "${minute.toString().padStart(2, '0')}分",
                            onMinus = { minute = (minute + 59) % 60 },
                            onPlus = { minute = (minute + 1) % 60 },
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                    TextButton(
                        text = "确定时间",
                        onClick = {
                            val safeDay = day.coerceIn(1, txDaysInMonth(year, month))
                            val ldt = LocalDateTime(year, month, safeDay, hour, minute, 0)
                            onConfirm(ldt.toInstant(tz).toEpochMilliseconds())
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeStepperPill(
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(mochiShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface)
                .clickable(onClick = onMinus),
            contentAlignment = Alignment.Center,
        ) {
            Text("−", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(valueText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
