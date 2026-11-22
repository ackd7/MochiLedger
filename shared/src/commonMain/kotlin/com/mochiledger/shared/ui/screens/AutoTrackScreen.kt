package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
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
    var runtime by remember { mutableStateOf(PlatformBridge.getAutoTrackRuntimeStatus()) }
    val settings = state.metadata.autoTrack
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }

    // Auto-refresh permission health status every 2s so returning from Android system settings updates immediately
    LaunchedEffect(Unit) {
        if (!runtime.supportsLsposed) return@LaunchedEffect
        while (true) {
            delay(2000L)
            runtime = PlatformBridge.getAutoTrackRuntimeStatus()
        }
    }

    var simChannel by remember { mutableStateOf(PaymentChannel.WECHAT) }
    var simTitle by remember { mutableStateOf("微信支付") }
    var simContent by remember { mutableStateOf("商户：瑞幸咖啡 商品：生椰拿铁大杯 付款方式：零钱 支付成功 ￥19.90") }
    var previewResult by remember { mutableStateOf<ParsedPayment?>(null) }

    var newRuleName by remember { mutableStateOf("") }
    var newRuleKeyword by remember { mutableStateOf("") }
    var newRuleCategoryId by remember { mutableStateOf("cat_food") }

    val missingPermissionsCount = if (runtime.supportsLsposed) {
        listOf(
            runtime.lsposedActive || runtime.notificationListenerEnabled,
            runtime.overlayPermissionGranted || !settings.floatingConfirmEnabled,
            runtime.batteryOptimizationIgnored,
            runtime.postNotificationsGranted,
        ).count { !it }
    } else {
        0
    }

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

        // Permission Health Check Panel (Android)
        if (runtime.supportsLsposed) {
            item(key = "permission_health") {
                SmallTitle(
                    text = if (missingPermissionsCount == 0) {
                        "权限健康体检（全部就绪）"
                    } else {
                        "权限健康体检（缺少 $missingPermissionsCount 项，请及时开启防失效）"
                    },
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PermissionCheckRow(
                            title = "LSPosed 深度抓取（方案 1）",
                            desc = if (runtime.lsposedActive) "已激活，可自动抓取支付页商户与商品明细" else "未激活（可在 LSPosed 勾选微信/支付宝，或用下方通知监听替代）",
                            granted = runtime.lsposedActive,
                            optional = runtime.notificationListenerEnabled,
                            actionLabel = null,
                            onAction = {},
                        )
                        HorizontalDivider()
                        PermissionCheckRow(
                            title = "系统通知使用权（免 Root 监听）",
                            desc = if (runtime.notificationListenerEnabled) "已授权，支持微信/支付宝通知自动记账" else "未授权，无 LSPosed 时必须开启此权限",
                            granted = runtime.notificationListenerEnabled,
                            optional = runtime.lsposedActive,
                            actionLabel = "去开启",
                            onAction = { PlatformBridge.openNotificationListenerSettings() },
                        )
                        HorizontalDivider()
                        PermissionCheckRow(
                            title = "悬浮窗权限（付款后 1 秒备注确认条）",
                            desc = if (runtime.overlayPermissionGranted) "已授权，付款后可弹出快捷标签卡片补全买了什么" else "未授权，开启后即可在付款后一键点选早餐/午饭/超市标签",
                            granted = runtime.overlayPermissionGranted,
                            optional = !settings.floatingConfirmEnabled,
                            actionLabel = "去授权",
                            onAction = { PlatformBridge.openOverlayPermissionSettings() },
                        )
                        HorizontalDivider()
                        PermissionCheckRow(
                            title = "后台电池优化白名单（防杀后台）",
                            desc = if (runtime.batteryOptimizationIgnored) "已加入无限制白名单，后台监听更稳定" else "未开启，小米/华为等系统可能会清理后台导致漏记",
                            granted = runtime.batteryOptimizationIgnored,
                            optional = false,
                            actionLabel = "去设置",
                            onAction = { PlatformBridge.openBatteryOptimizationSettings() },
                        )
                        HorizontalDivider()
                        PermissionCheckRow(
                            title = "记账结果通知权限",
                            desc = if (runtime.postNotificationsGranted) "已允许发送自动记账提醒" else "未开启，自动记账后无法收到通知提醒",
                            granted = runtime.postNotificationsGranted,
                            optional = false,
                            actionLabel = "去开启",
                            onAction = { PlatformBridge.openAppNotificationSettings() },
                        )
                        TextButton(
                            text = "重新检测权限状态",
                            onClick = {
                                runtime = PlatformBridge.getAutoTrackRuntimeStatus()
                                onShowMessage("已刷新自动记账权限状态")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // Hook Switches
        item(key = "switches") {
            SmallTitle(text = "监听通道与悬浮确认设置")
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
                        summary = "识别微信付款、转账、红包与支付页商品明细",
                        checked = settings.hookWeChat,
                        onCheckedChange = { onUpdateSettings(settings.copy(hookWeChat = it)) },
                    )
                    SwitchPreference(
                        title = "监听支付宝 (com.eg.android.AlipayGphone)",
                        summary = "识别支付宝交易提醒、花呗消费与支付页商品明细",
                        checked = settings.hookAlipay,
                        onCheckedChange = { onUpdateSettings(settings.copy(hookAlipay = it)) },
                    )
                    SwitchPreference(
                        title = "系统通知监听降级方案（免 Root）",
                        summary = "未启用 LSPosed 时通过 NotificationListenerService 兜底",
                        checked = settings.fallbackNotificationListener,
                        onCheckedChange = { onUpdateSettings(settings.copy(fallbackNotificationListener = it)) },
                    )
                    SwitchPreference(
                        title = "付款后弹出轻量悬浮确认条",
                        summary = "自动记账后顶部弹出小卡片，1 秒点选快捷标签或补充买了什么（免 Root）",
                        checked = settings.floatingConfirmEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(floatingConfirmEnabled = it)) },
                    )
                    if (settings.floatingConfirmEnabled) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("悬浮条自动收起倒计时", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(5, 6, 10, 15).forEach { sec ->
                                    MochiChip("${sec} 秒", settings.floatingConfirmSeconds == sec) {
                                        onUpdateSettings(settings.copy(floatingConfirmSeconds = sec))
                                    }
                                }
                            }
                        }
                    }
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

@Composable
private fun PermissionCheckRow(
    title: String,
    desc: String,
    granted: Boolean,
    optional: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    val badgeColor = when {
        granted -> MochiColors.Income
        optional -> MochiColors.WarningAmber
        else -> MochiColors.Expense
    }
    val badgeText = when {
        granted -> "已就绪"
        optional -> "可选"
        else -> "缺少权限"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MochiGlassBadge(text = badgeText, color = badgeColor)
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (!granted && actionLabel != null) {
            TextButton(
                text = actionLabel,
                onClick = onAction,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
