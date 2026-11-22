package com.mochiledger.shared.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.mochiledger.shared.model.AutoBackupConfig
import com.mochiledger.shared.model.SelfHostedSyncConfig
import com.mochiledger.shared.model.WebDavConfig
import com.mochiledger.shared.model.formatFileSize
import com.mochiledger.shared.platform.PlatformBackHandler
import com.mochiledger.shared.ui.components.FlatIcon
import com.mochiledger.shared.ui.components.MochiChip
import com.mochiledger.shared.ui.components.MochiGlassBadge
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class SyncSubPage(val title: String) {
    HUB("同步与备份"),
    WEBDAV("WebDAV 云同步"),
    SELF_HOSTED("自建同步服务器"),
    TUTORIAL("备份教程"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSyncScreen(
    state: LedgerUiState,
    onBackToSettings: () -> Unit,
    onUpdateWebDav: (WebDavConfig) -> Unit,
    onTestWebDav: () -> Unit,
    onSyncWebDav: () -> Unit,
    onBackupWebDav: () -> Unit,
    onUpdateSelfHosted: (SelfHostedSyncConfig) -> Unit,
    onTestSelfHosted: () -> Unit,
    onSyncSelfHosted: () -> Unit,
    onSyncAllEnabled: () -> Unit,
    onUpdateAutoBackup: (AutoBackupConfig) -> Unit,
    onManualBackupNow: () -> Unit,
    onExportBackupWithPicker: () -> Unit,
    onPickImportBackupFile: (Boolean) -> Unit,
    onPickImportStatementCsv: (Boolean) -> Unit,
    onRestoreLocalBackup: (String, Boolean) -> Unit,
    onDeleteLocalBackup: (String) -> Unit,
    onExportCsv: () -> Unit,
    onImportJson: (String, Boolean) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    var subPage by remember { mutableStateOf(SyncSubPage.HUB) }
    val webDav = state.metadata.webDav
    val selfHosted = state.metadata.selfHosted
    val autoBackup = state.metadata.autoBackup
    var importJsonText by remember { mutableStateOf("") }
    var restoreServerConfigOnImport by remember { mutableStateOf(true) }
    var importMissingFromCsv by remember { mutableStateOf(true) }

    fun handleBack() {
        if (subPage != SyncSubPage.HUB) {
            subPage = SyncSubPage.HUB
        } else {
            onBackToSettings()
        }
    }

    PlatformBackHandler(enabled = true) { handleBack() }

    fun formatTimestamp(ms: Long): String {
        if (ms <= 0L) return "尚未同步"
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')} " +
            "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }

    val webDavReady = webDav.enabled && webDav.serverUrl.isNotBlank() && webDav.username.isNotBlank()
    val selfHostedReady = selfHosted.enabled && selfHosted.serverUrl.isNotBlank()

    Column(modifier = Modifier.fillMaxSize()) {
        // Unified icon-only back header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(mochiShape(12.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .clickable { handleBack() },
                contentAlignment = Alignment.Center,
            ) {
                FlatIcon("back", MiuixTheme.colorScheme.onSurface, size = 18.dp)
            }
            Text(subPage.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        AnimatedContent(
            targetState = subPage,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = targetState != SyncSubPage.HUB
                if (forward) {
                    (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(180)))
                }
            },
            label = "syncSubPageTransition",
        ) { page ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (page) {
                    SyncSubPage.HUB -> {
                        // 1. Live Cloud Sync Status Overview Card (WebDAV + Self-Hosted)
                        item(key = "sync_status_card") {
                            SmallTitle(text = "云同步状态")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SyncServiceStatusRow(
                                        title = "WebDAV 云同步",
                                        subtitle = when {
                                            webDavReady && webDav.lastSyncAt > 0L -> "上次同步：${formatTimestamp(webDav.lastSyncAt)}"
                                            webDavReady -> "已配置，点击立即同步验证"
                                            webDav.enabled -> "已开启但信息不完整，点击补全"
                                            else -> "未启用 · 点击配置坚果云 / Nextcloud / Alist"
                                        },
                                        statusText = when {
                                            webDavReady && webDav.lastSyncAt > 0L -> "运行正常"
                                            webDavReady -> "待同步"
                                            webDav.enabled -> "待配置"
                                            else -> "未启用"
                                        },
                                        statusColor = when {
                                            webDavReady && webDav.lastSyncAt > 0L -> MochiColors.Income
                                            webDav.enabled -> MochiColors.WarningAmber
                                            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        },
                                        onClick = { subPage = SyncSubPage.WEBDAV },
                                    )
                                    HorizontalDivider()
                                    SyncServiceStatusRow(
                                        title = "自建同步服务器",
                                        subtitle = when {
                                            selfHostedReady && selfHosted.lastSyncAt > 0L -> "${selfHosted.serverUrl} · 上次：${formatTimestamp(selfHosted.lastSyncAt)}"
                                            selfHostedReady -> "${selfHosted.serverUrl} · 点击立即同步验证"
                                            selfHosted.enabled -> "已开启但未填写地址"
                                            else -> "未启用 · 点击配置路由器 / NAS / Cloudflare 同步服"
                                        },
                                        statusText = when {
                                            selfHostedReady && selfHosted.lastSyncAt > 0L -> "运行正常"
                                            selfHostedReady -> "待同步"
                                            selfHosted.enabled -> "待配置"
                                            else -> "未启用"
                                        },
                                        statusColor = when {
                                            selfHostedReady && selfHosted.lastSyncAt > 0L -> MochiColors.Income
                                            selfHosted.enabled -> MochiColors.WarningAmber
                                            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        },
                                        onClick = { subPage = SyncSubPage.SELF_HOSTED },
                                    )
                                    TextButton(
                                        text = if (state.isSyncing) "多源同步中..." else "一键同步已启用服务器",
                                        onClick = onSyncAllEnabled,
                                        enabled = !state.isSyncing,
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        // 2. Manual Backup & Import/Export (Directly in Hub, no extra subpage)
                        item(key = "manual_backup_card") {
                            SmallTitle(text = "备份与导入")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TextButton(
                                        text = "立即手动备份",
                                        onClick = onManualBackupNow,
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        TextButton(
                                            text = "导出备份文件",
                                            onClick = onExportBackupWithPicker,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = "导入备份文件",
                                            onClick = { onPickImportBackupFile(restoreServerConfigOnImport) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    TextButton(
                                        text = "导出 CSV 账单表格",
                                        onClick = onExportCsv,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    HorizontalDivider()
                                    SwitchPreference(
                                        title = "导入时恢复服务器配置",
                                        summary = "自动还原备份内保存的 WebDAV、自建服与自动备份设置",
                                        checked = restoreServerConfigOnImport,
                                        onCheckedChange = { restoreServerConfigOnImport = it },
                                    )
                                    TextField(
                                        value = importJsonText,
                                        onValueChange = { importJsonText = it },
                                        label = "或粘贴 .mochi.json 文本导入...",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (importJsonText.isNotBlank()) {
                                        TextButton(
                                            text = "从粘贴文本导入",
                                            onClick = {
                                                onImportJson(importJsonText, restoreServerConfigOnImport)
                                                importJsonText = ""
                                            },
                                            colors = ButtonDefaults.textButtonColorsPrimary(),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Auto Backup Policy
                        item(key = "auto_backup_card") {
                            SmallTitle(text = "自动备份")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SwitchPreference(
                                        title = "每天首次打开自动备份",
                                        summary = "每天首次启动软件时自动保存一份完整本地备份",
                                        checked = autoBackup.dailyFirstOpenEnabled,
                                        onCheckedChange = { onUpdateAutoBackup(autoBackup.copy(dailyFirstOpenEnabled = it)) },
                                    )
                                    HorizontalDivider()
                                    SwitchPreference(
                                        title = "定时自动备份",
                                        summary = "按设定周期自动保存备份并轮转清理旧份",
                                        checked = autoBackup.scheduledEnabled,
                                        onCheckedChange = { onUpdateAutoBackup(autoBackup.copy(scheduledEnabled = it)) },
                                    )
                                    if (autoBackup.scheduledEnabled) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            listOf(1, 3, 6, 12, 24).forEach { hours ->
                                                MochiChip(
                                                    label = if (hours == 24) "每天" else "每 ${hours}h",
                                                    selected = autoBackup.intervalHours == hours,
                                                ) {
                                                    onUpdateAutoBackup(autoBackup.copy(intervalHours = hours))
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                    SwitchPreference(
                                        title = "备份包含服务器信息",
                                        summary = "同时保存 WebDAV 与自建同步服务器配置，换机一键还原",
                                        checked = autoBackup.includeServerConfig,
                                        onCheckedChange = { onUpdateAutoBackup(autoBackup.copy(includeServerConfig = it)) },
                                    )
                                    HorizontalDivider()
                                    SwitchPreference(
                                        title = "自动备份时同步云端",
                                        summary = "触发自动备份后同时同步至已启用的云同步服务器",
                                        checked = autoBackup.alsoSyncRemote,
                                        onCheckedChange = { onUpdateAutoBackup(autoBackup.copy(alsoSyncRemote = it)) },
                                    )
                                    Text(
                                        text = "上次自动备份：${if (autoBackup.lastAutoBackupAt > 0L) formatTimestamp(autoBackup.lastAutoBackupAt) else "尚未执行"}",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }

                        // 4. WeChat / Alipay Official Statement CSV Enrichment
                        item(key = "statement_csv_card") {
                            SmallTitle(text = "官方账单补全")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "导入微信或支付宝官方导出的 CSV 账单，自动按时间与金额匹配已有账单补全商户和商品明细（不重复记账）。",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    SwitchPreference(
                                        title = "同时导入漏记账单",
                                        summary = "关闭则仅补全已有账单的商品说明",
                                        checked = importMissingFromCsv,
                                        onCheckedChange = { importMissingFromCsv = it },
                                    )
                                    TextButton(
                                        text = "选择微信 / 支付宝账单 (.csv)",
                                        onClick = { onPickImportStatementCsv(importMissingFromCsv) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        // 5. Local Backup History
                        item(key = "local_history_card") {
                            SmallTitle(text = "本地备份历史（${state.localBackups.size} 份）")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "备份目录：${state.backupDirectoryPath}",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    if (state.localBackups.isEmpty()) {
                                        Text(
                                            text = "暂无本地备份，点击上方「立即手动备份」即可创建。",
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    } else {
                                        state.localBackups.forEachIndexed { idx, file ->
                                            if (idx > 0) HorizontalDivider()
                                            val triggerLabel = when (file.triggerType) {
                                                "daily" -> "每日首开"
                                                "scheduled" -> "定时"
                                                else -> "手动"
                                            }
                                            val triggerColor = when (file.triggerType) {
                                                "daily" -> MochiColors.Income
                                                "scheduled" -> MiuixTheme.colorScheme.primary
                                                else -> Color(0xFFE88B00)
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        MochiGlassBadge(triggerLabel, triggerColor)
                                                        Text(formatFileSize(file.sizeBytes), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                    Text(
                                                        text = formatTimestamp(file.lastModifiedMs),
                                                        fontSize = 11.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    )
                                                }
                                                Text(file.fileName, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                    TextButton(
                                                        text = "导入恢复",
                                                        onClick = { onRestoreLocalBackup(file.absolutePath, restoreServerConfigOnImport) },
                                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    TextButton(
                                                        text = "删除",
                                                        onClick = { onDeleteLocalBackup(file.absolutePath) },
                                                        modifier = Modifier.weight(0.6f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 6. Bottom Tutorial Entry Card
                        item(key = "backup_tutorial_entry") {
                            SmallTitle(text = "帮助")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = "备份与同步使用教程",
                                    summary = "查看本地备份恢复、WebDAV、自建服务器及账单导入步骤",
                                    onClick = { subPage = SyncSubPage.TUTORIAL },
                                )
                            }
                        }
                    }

                    SyncSubPage.WEBDAV -> {
                        item(key = "webdav_detail") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SwitchPreference(
                                        title = "加入一键多源同步",
                                        summary = "开启后将与其他方式一起联合备份与读取",
                                        checked = webDav.enabled,
                                        onCheckedChange = { onUpdateWebDav(webDav.copy(enabled = it)) },
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MochiChip("坚果云", webDav.serverUrl.contains("jianguoyun")) {
                                            onUpdateWebDav(webDav.copy(serverUrl = "https://dav.jianguoyun.com/dav/"))
                                        }
                                        MochiChip("Nextcloud", webDav.serverUrl.contains("remote.php/dav")) {
                                            onUpdateWebDav(webDav.copy(serverUrl = "https://your-cloud.example/remote.php/dav/files/user/"))
                                        }
                                        MochiChip("Alist", webDav.serverUrl.contains(":5244")) {
                                            onUpdateWebDav(webDav.copy(serverUrl = "http://127.0.0.1:5244/dav/"))
                                        }
                                    }
                                    TextField(
                                        value = webDav.serverUrl,
                                        onValueChange = { onUpdateWebDav(webDav.copy(serverUrl = it)) },
                                        label = "WebDAV 服务器地址",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    TextField(
                                        value = webDav.username,
                                        onValueChange = { onUpdateWebDav(webDav.copy(username = it)) },
                                        label = "用户名 / 邮箱",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    TextField(
                                        value = webDav.password,
                                        onValueChange = { onUpdateWebDav(webDav.copy(password = it)) },
                                        label = "应用专用密码",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    TextField(
                                        value = webDav.remoteDirectory,
                                        onValueChange = { onUpdateWebDav(webDav.copy(remoteDirectory = it)) },
                                        label = "远端目录名（默认 MochiLedger）",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = "上次同步：${formatTimestamp(webDav.lastSyncAt)}",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        TextButton(
                                            text = "测试连接",
                                            onClick = onTestWebDav,
                                            enabled = !state.isSyncing,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = "上传快照",
                                            onClick = onBackupWebDav,
                                            enabled = !state.isSyncing,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = if (state.isSyncing) "同步中..." else "双向同步",
                                            onClick = onSyncWebDav,
                                            enabled = !state.isSyncing,
                                            colors = ButtonDefaults.textButtonColorsPrimary(),
                                            modifier = Modifier.weight(1.1f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SyncSubPage.SELF_HOSTED -> {
                        item(key = "self_hosted_detail") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SwitchPreference(
                                        title = "加入一键多源同步",
                                        summary = "连接独立部署的自建同步服务端（路由器 / NAS / Cloudflare）",
                                        checked = selfHosted.enabled,
                                        onCheckedChange = { onUpdateSelfHosted(selfHosted.copy(enabled = it)) },
                                    )
                                    TextField(
                                        value = selfHosted.serverUrl,
                                        onValueChange = { onUpdateSelfHosted(selfHosted.copy(serverUrl = it)) },
                                        label = "服务器地址（如 https://sync.example.com）",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    TextField(
                                        value = selfHosted.authToken,
                                        onValueChange = { onUpdateSelfHosted(selfHosted.copy(authToken = it)) },
                                        label = "Bearer Token（MOCHI_SYNC_TOKEN）",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    TextField(
                                        value = selfHosted.ledgerId,
                                        onValueChange = { onUpdateSelfHosted(selfHosted.copy(ledgerId = it)) },
                                        label = "账本 ID（默认 default）",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = "上次同步：${formatTimestamp(selfHosted.lastSyncAt)}",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        TextButton(
                                            text = "测试连接",
                                            onClick = onTestSelfHosted,
                                            enabled = !state.isSyncing,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = if (state.isSyncing) "同步中..." else "立即双向同步",
                                            onClick = onSyncSelfHosted,
                                            enabled = !state.isSyncing,
                                            colors = ButtonDefaults.textButtonColorsPrimary(),
                                            modifier = Modifier.weight(1.2f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SyncSubPage.TUTORIAL -> {
                        item(key = "tutorial_content") {
                            TutorialStepCard(
                                step = "1",
                                title = "本地手动与自动备份",
                                body = "• 点击「立即手动备份」会立即在本地备份目录生成 .mochi.json 文件。\n" +
                                    "• 开启「每天首次打开自动备份」或「定时自动备份」后，软件会自动在后台留存历史份并自动清理超过 30 份的旧文件。\n" +
                                    "• 开启「备份包含服务器信息」后，导出的备份文件不仅含全部账单与资产，还包含 WebDAV 和自建服务器地址与密码，换机导入可一键还原。",
                            )
                        }
                        item(key = "tutorial_restore") {
                            TutorialStepCard(
                                step = "2",
                                title = "如何导入与换机恢复",
                                body = "• 换新手机或重装电脑后，点击「导入备份文件」，选中之前导出的 .mochi.json 即可。\n" +
                                    "• 导入采用智能去重合并机制：相同账单与账户不会重复叠加，并自动还原你的同步服务器配置。",
                            )
                        }
                        item(key = "tutorial_cloud") {
                            TutorialStepCard(
                                step = "3",
                                title = "WebDAV 与自建服务器多端同步",
                                body = "• WebDAV（如坚果云）：在坚果云网页端「安全选项 → 添加应用」生成应用专用密码，点击上方「WebDAV 云同步」填入并测试连接即可。\n" +
                                    "• 自建同步服务器：支持部署在 OpenWrt 路由器、NAS Docker 或 Cloudflare Workers，配合 Cloudflare Tunnel 可实现手机 5G 与电脑全天候 HTTPS 同步。",
                            )
                        }
                        item(key = "tutorial_csv") {
                            TutorialStepCard(
                                step = "4",
                                title = "微信 / 支付宝官方账单补全商品明细",
                                body = "• 微信：我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单（用于个人对账）导出 CSV。\n" +
                                    "• 支付宝：我的 → 账单 → 右上角「…」→ 开具交易流水证明（用于个人对账）导出 CSV。\n" +
                                    "• 在「官方账单补全」选择该 CSV，墨记会自动按时间与金额匹配已有账单并补全商品说明。",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncServiceStatusRow(
    title: String,
    subtitle: String,
    statusText: String,
    statusColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(mochiShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                MochiGlassBadge(statusText, statusColor)
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Text("配置 ›", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TutorialStepCard(
    step: String,
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MochiGlassBadge("步骤 $step", MiuixTheme.colorScheme.primary)
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = body,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                lineHeight = 20.sp,
            )
        }
    }
}
