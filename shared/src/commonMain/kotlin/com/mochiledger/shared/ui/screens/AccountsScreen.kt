package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mochiledger.shared.data.LedgerUiState
import com.mochiledger.shared.model.AccountType
import com.mochiledger.shared.model.LedgerAccount
import com.mochiledger.shared.model.formatCents
import com.mochiledger.shared.model.parseAmountToCents
import com.mochiledger.shared.ui.components.FlatIcon
import com.mochiledger.shared.ui.components.MochiChip
import com.mochiledger.shared.ui.components.resolveAccountVisual
import com.mochiledger.shared.ui.theme.MochiColors
import com.mochiledger.shared.ui.theme.mochiHeroBrush
import com.mochiledger.shared.ui.theme.mochiShape
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccountsScreen(
    state: LedgerUiState,
    onSaveAccount: (LedgerAccount) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    val summaries = remember(state.accounts, state.transactions) { state.accountSummaries() }
    val totalAssets = summaries.filter { !it.account.type.isLiability && it.currentBalanceCents >= 0 }.sumOf { it.currentBalanceCents }
    val totalLiabilities = summaries.filter { it.account.type.isLiability || it.currentBalanceCents < 0 }.sumOf {
        if (it.currentBalanceCents < 0) -it.currentBalanceCents else 0L
    }
    val netWorth = summaries.sumOf { it.currentBalanceCents }

    var editingAccount by remember { mutableStateOf<LedgerAccount?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }

    val grouped = remember(summaries) { summaries.groupBy { it.account.type } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "net_worth") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(mochiShape(24.dp))
                        .background(mochiHeroBrush())
                        .padding(20.dp),
                ) {
                    Column {
                        Text("净资产总览", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = formatCents(netWorth),
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
                                Text("总资产", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                                Text(formatCents(totalAssets), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("总负债", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                                Text(formatCents(totalLiabilities), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (grouped.isEmpty()) {
                item(key = "empty_accounts") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            FlatIcon("accounts", MiuixTheme.colorScheme.onSurfaceVariantSummary, size = 34.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("暂无资产账户", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "点击右下角悬浮「+」按钮添加微信、支付宝、银行卡或现金账户",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                grouped.forEach { (type, list) ->
                    item(key = "group_${type.name}") {
                        SmallTitle(text = type.label)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                list.forEachIndexed { idx, summary ->
                                    val acc = summary.account
                                    val (iconKey, brandColor) = resolveAccountVisual(acc.name, acc.type, acc.colorHex)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                editingAccount = acc
                                                showAccountDialog = true
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(mochiShape(13.dp))
                                                .background(brandColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            FlatIcon(iconKey, brandColor, size = 22.dp)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(acc.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                            val sub = if (acc.creditLimitCents > 0L) {
                                                "信用额度 ${formatCents(acc.creditLimitCents)}"
                                            } else {
                                                acc.note.ifBlank { acc.type.label }
                                            }
                                            Text(sub, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                        }
                                        Text(
                                            text = formatCents(summary.currentBalanceCents),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (summary.currentBalanceCents < 0) MochiColors.Expense else MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                    if (idx < list.lastIndex) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Unified bottom-right floating '+' Action Button for adding accounts
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 22.dp + bottomInset)
                .size(60.dp)
                .shadow(elevation = 10.dp, shape = mochiShape(20.dp))
                .clip(mochiShape(20.dp))
                .background(mochiHeroBrush())
                .clickable {
                    editingAccount = null
                    showAccountDialog = true
                },
            contentAlignment = Alignment.Center,
        ) {
            FlatIcon(name = "plus", tint = Color.White, size = 26.dp)
        }
    }

    if (showAccountDialog) {
        AccountEditorDialog(
            initial = editingAccount,
            onDismiss = { showAccountDialog = false },
            onSave = { acc ->
                onSaveAccount(acc)
                showAccountDialog = false
                onShowMessage("已保存账户：${acc.name}")
            },
            onDelete = editingAccount?.let { existing ->
                {
                    onDeleteAccount(existing.id)
                    showAccountDialog = false
                    onShowMessage("已删除账户：${existing.name}")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditorDialog(
    initial: LedgerAccount?,
    onDismiss: () -> Unit,
    onSave: (LedgerAccount) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: AccountType.DIGITAL_WALLET) }
    var balanceText by remember {
        mutableStateOf(
            initial?.let { "${it.initialBalanceCents / 100}.${kotlin.math.abs(it.initialBalanceCents % 100).toString().padStart(2, '0')}" } ?: "0.00",
        )
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (initial == null) "新增账户" else "编辑账户", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextField(value = name, onValueChange = { name = it }, label = "账户名称（如 建设银行）", modifier = Modifier.fillMaxWidth())
                SmallTitle(text = "账户类型")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountType.entries.forEach { t ->
                        MochiChip(t.label, type == t) { type = t }
                    }
                }
                TextField(
                    value = balanceText,
                    onValueChange = { input -> if (input.all { it.isDigit() || it == '.' || it == '-' }) balanceText = input },
                    label = "初始余额（元）",
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(value = note, onValueChange = { note = it }, label = "备注（可选）", modifier = Modifier.fillMaxWidth())

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onDelete != null) {
                        TextButton(text = "删除", onClick = onDelete, modifier = Modifier.weight(1f))
                    }
                    TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                    TextButton(
                        text = "保存",
                        onClick = {
                            if (name.isBlank()) return@TextButton
                            val cents = parseAmountToCents(balanceText.removePrefix("-"))?.let {
                                if (balanceText.startsWith("-")) -it else it
                            } ?: 0L
                            val (resolvedIcon, resolvedColor) = resolveAccountVisual(name.trim(), type)
                            onSave(
                                LedgerAccount(
                                    id = initial?.id ?: "",
                                    name = name.trim(),
                                    type = type,
                                    initialBalanceCents = cents,
                                    iconKey = resolvedIcon,
                                    colorHex = resolvedColor.toArgb().toLong() and 0xFFFFFFFFL,
                                    note = note.trim(),
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
