package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.mochiledger.shared.data.LedgerUiState
import com.mochiledger.shared.model.AutoTrackRule
import com.mochiledger.shared.model.AutoTrackSettings
import com.mochiledger.shared.model.ParsedPayment
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.model.formatCents
import com.mochiledger.shared.platform.PlatformBridge
import com.mochiledger.shared.ui.components.MochiChip
import com.mochiledger.shared.ui.components.MochiGlassBadge
import com.mochiledger.shared.ui.theme.MochiColors
import com.mochiledger.shared.ui.theme.mochiShape
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AutoTrackScreen(
    state: LedgerUiState,
    onUpdateSettings: (AutoTrackSettings) -> Unit,
    onParsePreview: (String?, String, String) -> ParsedPayment?,
    onRecordParsed: (ParsedPayment) -> Boolean,
    onSaveRule: (AutoTrackRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    val runtime = remember { PlatformBridge.getAutoTrackRuntimeStatus() }
    val settings = state.metadata.autoTrack
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }

    var simChannel by remember { mutableStateOf(PaymentChannel.WECHAT) }
    var simTitle by remember { mutableStateOf("微信支付") }
    var simContent by remember { mutableStateOf("向瑞幸咖啡付款19.90元，已从零钱扣除") }
    var previewResult by remember { mutableStateOf<ParsedPayment?>(null) }

    var newRuleName by remember { mutableStateOf("") }
    var newRuleKeyword by remember { mutableStateOf("") }
    var newRuleCategoryId by remember { mutableStateOf("cat_food") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // LSPosed Status Hero
        item(key = "lsposed_status") {
            val statusColor = when {
                runtime.lsposedActive -> MochiColors.Income
                runtime.supportsLsposed -> MochiColors.WarningAmber
                else -> MiuixTheme.colorScheme.primary
            }
            val statusTitle = when {
                runtime.lsposedActive -> "LSPosed 模块已激活"
                runtime.supportsLsposed -> "LSPosed 模块未激活（请在 LSPosed 中勾选微信/支付宝）"
                else -> "当前为桌面端（自动记账规则与 Android 互通同步）"
            }
            val statusDesc = when {
                runtime.lsposedActive -> "已启用双层 Hook：微信/支付宝通知拦截 + 支付成功页 UI 捕获（即使关闭通知推送也能自动入账）。"
                runtime.supportsLsposed -> "也可开启下方「系统通知监听」作为无 Root 降级方案，或通过 mochiledger://add 协议接入 AutoAccounting。"
                else -> "当前为桌面端，可在 Android 端使用 LSPosed 与 AutoAccounting 协议自动记账。"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(mochiShape(22.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(1.dp, statusColor.copy(alpha = 0.35f), mochiShape(22.dp))
                    .padding(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MochiGlassBadge(text = runtime.platformName, color = statusColor)
                        if (runtime.supportsLsposed) {
                            MochiGlassBadge(
                                text = if (runtime.lsposedActive) "Hook Active" else "Hook Inactive",
                                color = statusColor,
                            )
                        }
                    }
                    Text(statusTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    Text(statusDesc, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    if (runtime.supportsLsposed && !runtime.notificationListenerEnabled) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            text = "打开系统通知使用权设置（备用监听）",
                            onClick = { PlatformBridge.openNotificationListenerSettings() },
                        )
                    }
                }
            }
        }

        // Hook Switches
        item(key = "switches") {
            SmallTitle(text = "监听通道设置")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SwitchPreference(
                        title = "启用自动记账",
                        summary = "接收 LSPosed Hook 与系统通知事件",
                        checked = settings.enabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(enabled = it)) },
                    )
                    SwitchPreference(
                        title = "监听微信支付 (com.tencent.mm)",
                        summary = "识别微信付款、转账、红包与收款通知",
                        checked = settings.hookWeChat,
                        onCheckedChange = { onUpdateSettings(settings.copy(hookWeChat = it)) },
                    )
                    SwitchPreference(
                        title = "监听支付宝 (com.eg.android.AlipayGphone)",
                        summary = "识别支付宝交易提醒、花呗消费、余额宝收益与收款",
                        checked = settings.hookAlipay,
                        onCheckedChange = { onUpdateSettings(settings.copy(hookAlipay = it)) },
                    )
                    SwitchPreference(
                        title = "系统通知监听降级方案",
                        summary = "未启用 LSPosed 时通过 NotificationListenerService 兜底",
                        checked = settings.fallbackNotificationListener,
                        onCheckedChange = { onUpdateSettings(settings.copy(fallbackNotificationListener = it)) },
                    )
                }
            }
        }

        // Open DeepLink Integration for AutoAccounting / Tasker
        item(key = "deeplink_protocol") {
            SmallTitle(text = "开放协议（兼容 AutoAccounting / Tasker）")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("静默记账 URI 协议", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = "mochiledger://add?type=expense&amount=32.50&merchant=星巴克&category=餐饮美食&account=支付宝&remark=拿铁",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = "支持 type (expense/income/transfer)、amount/money、merchant/shop、category/cat、account、remark/note 参数，调用后后台静默入账不打断当前页面。",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // Interactive Payment Notification Simulator
        item(key = "simulator") {
            SmallTitle(text = "LSPosed / 通知解析模拟器（支持实时入账测试）")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MochiChip("微信支付示例", simChannel == PaymentChannel.WECHAT, MochiColors.WeChatGreen) {
                            simChannel = PaymentChannel.WECHAT
                            simTitle = "微信支付"
                            simContent = "向瑞幸咖啡付款19.90元，已从零钱扣除"
                        }
                        MochiChip("支付宝示例", simChannel == PaymentChannel.ALIPAY, MochiColors.AlipayBlue) {
                            simChannel = PaymentChannel.ALIPAY
                            simTitle = "支付宝交易提醒"
                            simContent = "你已通过花呗向美团外卖付款38.50元"
                        }
                        MochiChip("收款示例", false, MochiColors.Income) {
                            simChannel = PaymentChannel.WECHAT
                            simTitle = "微信支付"
                            simContent = "微信支付收款50.00元"
                        }
                    }

                    TextField(
                        value = simTitle,
                        onValueChange = { simTitle = it },
                        label = "通知标题",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = simContent,
                        onValueChange = { simContent = it },
                        label = "通知正文 / Hook 载荷",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "测试解析",
                            onClick = {
                                previewResult = onParsePreview(simChannel.packageName, simTitle, simContent)
                                if (previewResult == null) {
                                    onShowMessage("未能从该文本中提取到有效支付信息")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "解析并立即自动记账",
                            onClick = {
                                val parsed = onParsePreview(simChannel.packageName, simTitle, simContent)
                                previewResult = parsed
                                if (parsed == null) {
                                    onShowMessage("未识别到支付金额或渠道")
                                } else {
                                    val saved = onRecordParsed(parsed)
                                    onShowMessage(
                                        if (saved) "自动记账成功：${parsed.merchant} ${formatCents(parsed.amountCents)}"
                                        else "已触发防重复拦截（${settings.dedupeWindowSeconds}秒内相同金额）"
                                    )
                                }
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    previewResult?.let { p ->
                        val catName = categoryMap[p.suggestedCategoryId]?.name ?: "未分类"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(mochiShape(14.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer)
                                .padding(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("解析结果预览", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "渠道：${p.channel.label}  |  类型：${p.type.name}  |  金额：${formatCents(p.amountCents)}",
                                    fontSize = 12.sp,
                                )
                                Text(
                                    "商户：${p.merchant}  |  推荐分类：$catName  |  推荐账户：${p.suggestedAccountName}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Auto Classification Rules
        item(key = "rules") {
            SmallTitle(text = "智能分类规则（关键词用 | 分隔）")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = newRuleName,
                            onValueChange = { newRuleName = it },
                            label = "规则名（如 咖啡奶茶）",
                            modifier = Modifier.weight(1f),
                        )
                        TextField(
                            value = newRuleKeyword,
                            onValueChange = { newRuleKeyword = it },
                            label = "关键词（如 瑞幸|星巴克）",
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.activeCategories.take(4).forEach { cat ->
                                MochiChip(cat.name, newRuleCategoryId == cat.id, Color(cat.colorHex)) {
                                    newRuleCategoryId = cat.id
                                }
                            }
                        }
                        TextButton(
                            text = "添加规则",
                            onClick = {
                                if (newRuleName.isBlank() || newRuleKeyword.isBlank()) return@TextButton
                                onSaveRule(
                                    AutoTrackRule(
                                        id = "",
                                        name = newRuleName.trim(),
                                        keyword = newRuleKeyword.trim(),
                                        targetCategoryId = newRuleCategoryId,
                                    ),
                                )
                                newRuleName = ""
                                newRuleKeyword = ""
                                onShowMessage("已添加自动分类规则")
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }

                    HorizontalDivider()

                    state.activeRules.forEachIndexed { idx, rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${rule.name} → ${categoryMap[rule.targetCategoryId]?.name ?: rule.targetCategoryId}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    rule.keyword,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                text = "删除",
                                onClick = {
                                    onDeleteRule(rule.id)
                                    onShowMessage("已删除规则：${rule.name}")
                                },
                            )
                        }
                        if (idx < state.activeRules.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
