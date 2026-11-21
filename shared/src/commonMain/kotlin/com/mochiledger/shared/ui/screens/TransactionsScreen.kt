package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
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
    var selectedYear by remember { mutableStateOf(nowDt.year) }
    var selectedMonth by remember { mutableStateOf(nowDt.monthNumber) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<TransactionType?>(null) }
    var editingTx by remember { mutableStateOf<LedgerTransaction?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val summary = remember(state.transactions, selectedYear, selectedMonth) {
        state.monthlySummary(selectedYear, selectedMonth)
    }
    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.id } }
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }

    val filteredTxs = remember(state.transactions, selectedYear, selectedMonth, searchQuery, filterType) {
        val tz = TimeZone.currentSystemDefault()
        state.activeTransactions.filter { tx ->
            val dt = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(tz)
            val matchMonth = dt.year == selectedYear && dt.monthNumber == selectedMonth
            val matchType = filterType == null || tx.type == filterType
            val q = searchQuery.trim()
            val matchQuery = q.isEmpty() ||
                tx.merchant.contains(q, ignoreCase = true) ||
                tx.note.contains(q, ignoreCase = true) ||
                (categoryMap[tx.categoryId]?.name?.contains(q, ignoreCase = true) == true)
            matchMonth && matchType && matchQuery
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
                                text = "${selectedYear}年${selectedMonth}月 账单概览",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(mochiShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.18f))
                                        .clickable {
                                            if (selectedMonth == 1) {
                                                selectedYear -= 1
                                                selectedMonth = 12
                                            } else {
                                                selectedMonth -= 1
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("上月", color = Color.White, fontSize = 12.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(mochiShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.18f))
                                        .clickable {
                                            if (selectedMonth == 12) {
                                                selectedYear += 1
                                                selectedMonth = 1
                                            } else {
                                                selectedMonth += 1
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("下月", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "本月结余",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = formatCents(summary.netCents, showSign = true),
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
                                Text("本月支出", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                                Text(
                                    text = formatCents(summary.expenseCents),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("本月收入", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                                Text(
                                    text = formatCents(summary.incomeCents),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text("本月暂无账单记录", fontWeight = FontWeight.SemiBold)
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
                onShowMessage(if (editingTx == null) "已记账：${formatCents(tx.amountCents)}" else "已更新账单")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionEditorDialog(
    initial: LedgerTransaction?,
    state: LedgerUiState,
    onDismiss: () -> Unit,
    onSave: (LedgerTransaction) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var txType by remember { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
    var amountText by remember {
        mutableStateOf(
            initial?.let { "${it.amountCents / 100}.${(it.amountCents % 100).toString().padStart(2, '0')}" } ?: "",
        )
    }
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val availableCategories = remember(state.categories, txType) {
        state.activeCategories.filter { it.type == txType }
    }
    var selectedCategoryId by remember(txType) {
        mutableStateOf(
            initial?.categoryId?.takeIf { id -> availableCategories.any { it.id == id } }
                ?: availableCategories.firstOrNull()?.id
                ?: "cat_other_exp",
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
                modifier = Modifier.padding(18.dp),
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
                    label = "商户 / 对象（如 星巴克、美团）",
                    modifier = Modifier.fillMaxWidth(),
                )

                TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )

                if (txType != TransactionType.TRANSFER) {
                    SmallTitle(text = "分类")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                    timestamp = initial?.timestamp ?: now,
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
}
