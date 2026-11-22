package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochiledger.shared.data.LedgerUiState
import com.mochiledger.shared.model.AutoTrackRule
import com.mochiledger.shared.model.AutoTrackSettings
import com.mochiledger.shared.model.ParsedPayment
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.model.TransactionType
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
    val expenseCategories = remember(state.categories) {
        state.activeCategories.filter { it.type == TransactionType.EXPENSE }
    }

    // Auto-refresh permission status every 2s so returning from Android settings updates immediately
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

    // Only check LSPosed permission when LSPosed scheme is enabled; only check NotificationListener when regular scheme is enabled
    val missingPermissionsCount = if (runtime.supportsLsposed) {
        buildList {
            if (settings.useLsposedMode) add(runtime.lsposedActive)
            if (settings.fallbackNotificationListener) add(runtime.notificationListenerEnabled)
            if (settings.floatingConfirmEnabled) add(runtime.overlayPermissionGranted)
            add(runtime.batteryOptimizationIgnored)
            add(runtime.postNotificationsGranted)
        }.count { !it }
    } else {
        0
    }

    val lsposedLockedByRegular = settings.fallbackNotificationListener && !settings.useLsposedMode
    val regularLockedByLsposed = settings.useLsposedMode && !settings.fallbackNotificationListener

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Master Switch & Floating Confirm
        item(key = "master_switches") {
            SmallTitle(text = "基础设置")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SwitchPreference(
                        title = "启用自动记账",
                        summary = "开启后自动识别微信与支付宝交易",
                        checked = settings.enabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(enabled = it)) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchPreference(
                        title = "付款后悬浮确认条",
                        summary = "顶部弹出快捷卡片，1 秒补全备注",
                        checked = settings.floatingConfirmEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(floatingConfirmEnabled = it)) },
                    )
                    if (settings.floatingConfirmEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("停留时长", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            listOf(5, 6, 10, 15).forEach { sec ->
                                MochiChip("${sec}秒", settings.floatingConfirmSeconds == sec) {
                                    onUpdateSettings(settings.copy(floatingConfirmSeconds = sec))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Scheme 1: Root / LSPosed (mutually exclusive with Regular scheme; grayed & locked when Regular is active)
        item(key = "scheme_lsposed") {
            SmallTitle(text = "Root / LSPosed 方案")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (lsposedLockedByRegular) 0.48f else 1f),
            ) {
                Column {
                    SwitchPreference(
                        title = "启用 LSPosed 监听",
                        summary = if (lsposedLockedByRegular) {
                            "已开启常规方案，请先关闭常规方案"
                        } else {
                            "支持读取收银台收款人、商品明细与支付页"
                        },
                        checked = settings.useLsposedMode,
                        enabled = !lsposedLockedByRegular,
                        onCheckedChange = { enable ->
                            if (lsposedLockedByRegular) return@SwitchPreference
                            onUpdateSettings(
                                settings.copy(
                                    useLsposedMode = enable,
                                    fallbackNotificationListener = if (enable) false else settings.fallbackNotificationListener,
                                ),
                            )
                        },
                    )
                    if (settings.useLsposedMode) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchPreference(
                            title = "微信支付",
                            summary = "捕获微信收款人、通知与支付成功页",
                            checked = settings.hookWeChat,
                            onCheckedChange = { onUpdateSettings(settings.copy(hookWeChat = it)) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchPreference(
                            title = "支付宝",
                            summary = "捕获支付宝收款人、通知与支付成功页",
                            checked = settings.hookAlipay,
                            onCheckedChange = { onUpdateSettings(settings.copy(hookAlipay = it)) },
                        )
                    }
                }
            }
        }

        // Scheme 2: Regular Non-Root Scheme (grayed & locked when LSPosed is active)
        item(key = "scheme_regular") {
            SmallTitle(text = "常规方案")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (regularLockedByLsposed) 0.48f else 1f),
            ) {
                Column {
                    SwitchPreference(
                        title = "启用系统通知监听",
                        summary = if (regularLockedByLsposed) {
                            "已开启 LSPosed 方案，请先关闭 LSPosed"
                        } else {
                            "免 Root，通过系统通知栏识别收付款"
                        },
                        checked = settings.fallbackNotificationListener,
                        enabled = !regularLockedByLsposed,
                        onCheckedChange = { enable ->
                            if (regularLockedByLsposed) return@SwitchPreference
                            onUpdateSettings(
                                settings.copy(
                                    fallbackNotificationListener = enable,
                                    useLsposedMode = if (enable) false else settings.useLsposedMode,
                                ),
                            )
                        },
                    )
                }
            }
        }

        // Clean Permission Health Check (Android only; only checks permissions relevant to enabled schemes)
        if (runtime.supportsLsposed) {
            item(key = "permission_health") {
                SmallTitle(
                    text = if (missingPermissionsCount == 0) "权限体检 · 已就绪" else "权限体检 · 缺 $missingPermissionsCount 项",
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        var hasPrevRow = false
                        if (settings.useLsposedMode) {
                            PermissionCheckRow(
                                title = "LSPosed 模块",
                                desc = if (runtime.lsposedActive) "已激活" else "请在 LSPosed 勾选微信与支付宝",
                                granted = runtime.lsposedActive,
                                actionLabel = null,
                                onAction = {},
                            )
                            hasPrevRow = true
                        }
                        if (settings.fallbackNotificationListener) {
                            if (hasPrevRow) HorizontalDivider()
                            PermissionCheckRow(
                                title = "通知使用权",
                                desc = if (runtime.notificationListenerEnabled) "已授权" else "需开启系统通知读取权限",
                                granted = runtime.notificationListenerEnabled,
                                actionLabel = "去开启",
                                onAction = { PlatformBridge.openNotificationListenerSettings() },
                            )
                            hasPrevRow = true
                        }
                        if (settings.floatingConfirmEnabled) {
                            if (hasPrevRow) HorizontalDivider()
                            PermissionCheckRow(
                                title = "悬浮窗权限",
                                desc = if (runtime.overlayPermissionGranted) "已授权" else "用于付款后弹出快捷备注条",
                                granted = runtime.overlayPermissionGranted,
                                actionLabel = "去授权",
                                onAction = { PlatformBridge.openOverlayPermissionSettings() },
                            )
                            hasPrevRow = true
                        }
                        if (hasPrevRow) HorizontalDivider()
                        PermissionCheckRow(
                            title = "后台电池优化",
                            desc = if (runtime.batteryOptimizationIgnored) "已加入白名单" else "建议设为无限制防杀后台",
                            granted = runtime.batteryOptimizationIgnored,
                            actionLabel = "去设置",
                            onAction = { PlatformBridge.openBatteryOptimizationSettings() },
                        )
                        HorizontalDivider()
                        PermissionCheckRow(
                            title = "记账通知提醒",
                            desc = if (runtime.postNotificationsGranted) "已开启" else "用于推送自动记账结果",
                            granted = runtime.postNotificationsGranted,
                            actionLabel = "去开启",
                            onAction = { PlatformBridge.openAppNotificationSettings() },
                        )
                    }
                }
            }
        }

        // Interactive Payment Simulator (clean single-line buttons + horizontal scrollable examples)
        item(key = "simulator") {
            SmallTitle(text = "解析测试")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MochiChip("微信支付", simChannel == PaymentChannel.WECHAT, MochiColors.WeChatGreen) {
                            simChannel = PaymentChannel.WECHAT
                            simTitle = "微信支付"
                            simContent = "商户：瑞幸咖啡 商品：生椰拿铁大杯 付款方式：零钱 支付成功 ￥19.90"
                        }
                        MochiChip("支付宝", simChannel == PaymentChannel.ALIPAY, MochiColors.AlipayBlue) {
                            simChannel = PaymentChannel.ALIPAY
                            simTitle = "支付宝交易提醒"
                            simContent = "你已通过花呗向美团外卖付款38.50元"
                        }
                        MochiChip("微信收款", false, MochiColors.Income) {
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
                        label = "支付内容",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "测试解析",
                            onClick = {
                                previewResult = onParsePreview(simChannel.packageName, simTitle, simContent)
                                if (previewResult == null) onShowMessage("未提取到有效支付信息")
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "测试入账",
                            onClick = {
                                val parsed = onParsePreview(simChannel.packageName, simTitle, simContent)
                                previewResult = parsed
                                if (parsed == null) {
                                    onShowMessage("未识别到支付金额")
                                } else {
                                    val saved = onRecordParsed(parsed)
                                    onShowMessage(
                                        if (saved) "已记账：${parsed.merchant} ${formatCents(parsed.amountCents)}"
                                        else "防重复拦截（${settings.dedupeWindowSeconds}秒内相同金额）",
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
                                Text("收款人：${p.merchant} · ${formatCents(p.amountCents)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "分类：$catName · 钱包：${p.suggestedAccountName} · 备注：${p.note}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Auto Classification Rules (full-width inputs + horizontally scrollable category bar — zero awkward wrapping!)
        item(key = "rules") {
            SmallTitle(text = "智能分类规则")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = newRuleName,
                        onValueChange = { newRuleName = it },
                        label = "规则名称（如 咖啡奶茶）",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = newRuleKeyword,
                        onValueChange = { newRuleKeyword = it },
                        label = "关键词，用 | 分隔（如 瑞幸|星巴克）",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        expenseCategories.forEach { cat ->
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
                            onShowMessage("已添加分类规则")
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.activeRules.isNotEmpty()) {
                        HorizontalDivider()
                        state.activeRules.forEachIndexed { idx, rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${rule.name} → ${categoryMap[rule.targetCategoryId]?.name ?: rule.targetCategoryId}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        rule.keyword,
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    text = "删除",
                                    onClick = {
                                        onDeleteRule(rule.id)
                                        onShowMessage("已删除：${rule.name}")
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
}

@Composable
private fun PermissionCheckRow(
    title: String,
    desc: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    val badgeColor = if (granted) MochiColors.Income else MochiColors.Expense
    val badgeText = if (granted) "已就绪" else "未开启"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                MochiGlassBadge(text = badgeText, color = badgeColor)
            }
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
