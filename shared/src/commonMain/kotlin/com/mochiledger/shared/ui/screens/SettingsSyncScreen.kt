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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class SyncSubPage(val title: String) {
    HUB("同步与备份"),
    WEBDAV("WebDAV 云同步"),
    SELF_HOSTED("自建同步服务器"),
    LOCAL("备份与恢复"),
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

    val enabledCount = listOf(webDav.enabled, selfHosted.enabled).count { it }

    Column(modifier = Modifier.fillMaxSize()) {
        // Single unified icon-only back header (no text, no stacked double back bars)
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
                        item(key = "sync_hero") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(mochiShape(24.dp))
                                    .background(mochiHeroBrush())
                                    .padding(20.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("多端同步与备份中心", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Box(
                                            modifier = Modifier
                                                .clip(mochiShape(10.dp))
                                                .background(Color.White.copy(alpha = 0.2f))
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            Text("已启用 $enabledCount 路云同步", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Text(
                                        "支持同时开启 WebDAV 与自建服务器进行联合备份与读取；多源合并账单与资产时自动去重，重复记录仅保留一份。",
                                        color = Color.White.copy(alpha = 0.86f),
                                        fontSize = 12.sp,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(mochiShape(14.dp))
                                            .background(Color.White.copy(alpha = 0.22f))
                                            .clickable(enabled = !state.isSyncing) { onSyncAllEnabled() }
                                            .padding(vertical = 11.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (state.isSyncing) "多源同步中..." else "一键多源同步 / 读取合并",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "entries") {
                            SmallTitle(text = "备份与同步方式（点击进入配置）")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    ArrowPreference(
                                        title = "WebDAV 云同步",
                                        summary = if (webDav.enabled) {
                                            "已启用 · 上次同步：${formatTimestamp(webDav.lastSyncAt)}"
                                        } else {
                                            "未启用 · 支持坚果云、Nextcloud、Alist 等"
                                        },
                                        onClick = { subPage = SyncSubPage.WEBDAV },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    ArrowPreference(
                                        title = "自建同步服务器",
                                        summary = if (selfHosted.enabled) {
                                            "已启用 · ${selfHosted.serverUrl}"
                                        } else {
                                            "未启用 · 连接独立部署的同步服务端"
                                        },
                                        onClick = { subPage = SyncSubPage.SELF_HOSTED },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    val backupSummary = buildList {
                                        if (autoBackup.dailyFirstOpenEnabled) add("每日首开备份")
                                        if (autoBackup.scheduledEnabled) add("每${autoBackup.intervalHours}h定时")
                                    }.let { tags ->
                                        val prefix = if (tags.isEmpty()) "手动备份" else tags.joinToString(" · ")
                                        "$prefix · 已有 ${state.localBackups.size} 份本地备份（含服务器配置）"
                                    }
                                    ArrowPreference(
                                        title = "定时备份、手动备份与导入恢复",
                                        summary = backupSummary,
                                        onClick = { subPage = SyncSubPage.LOCAL },
                                    )
                                }
                            }
                        }

                        item(key = "dedupe_note") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MochiGlassBadge("多源去重保护", MochiColors.Income)
                                        Text("自动冲突合并与去重", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                    Text(
                                        "当同时从 WebDAV、自建服务器或本地 JSON 读取数据时，系统会按记录 UUID 以及（类型、金额、时间、商户、账户名）双重指纹自动去重，多余重复项自动舍弃。",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
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
                                        summary = "连接独立部署的自建同步服务端（NAS / VPS / Docker）",
                                        checked = selfHosted.enabled,
                                        onCheckedChange = { onUpdateSelfHosted(selfHosted.copy(enabled = it)) },
                                    )
                                    TextField(
                                        value = selfHosted.serverUrl,
                                        onValueChange = { onUpdateSelfHosted(selfHosted.copy(serverUrl = it)) },
                                        label = "服务器地址（如 http://192.168.1.10:8787）",
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

                    SyncSubPage.LOCAL -> {
                        item(key = "auto_backup_card") {
                            SmallTitle(text = "自动备份策略")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SwitchPreference(
                                        title = "每天首次打开软件自动备份",
                                        summary = "每天第一次启动墨记时，自动保存全量本地备份",
                                        checked = autoBackup.dailyFirstOpenEnabled,
                                        onCheckedChange = { onUpdateAutoBackup(autoBackup.copy(dailyFirstOpenEnabled = it)) },
                                    )
                                    HorizontalDivider()
                                    SwitchPreference(
                                        title = "定时自动备份",
                                        summary = "软件运行期间按设定周期自动备份，防止数据丢失",
                                        checked = autoBackup.scheduledEnabled,
                                        onCheckedChange = { onUpdateAutoBackup(autoBackup.copy(scheduledEnabled = it)) },
                                    )
                                    if (autoBackup.scheduledEnabled) {
                                        Text(
                                            text = "定时备份间隔",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            listOf(1, 3, 6, 12, 24).forEach { hours ->
                                                MochiChip(
                                                    label = if (hours == 24) "每天" else "每 $hours 小时",
                                                    selected = autoBackup.intervalHours == hours,
                                                ) {
                                                    onUpdateAutoBackup(autoBackup.copy(intervalHours = hours))
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                    SwitchPreference(
                                        title = "备份包含同步服务器信息",
                                        summary = "同时备份 WebDAV、自建服地址与 Token、自动记账与备份配置",
                                        checked = autoBackup.includeServerConfig,
                                        onCheckedChange = { onUpdateAutoBackup(autoBackup.copy(includeServerConfig = it)) },
                                    )
                                    HorizontalDivider()
                                    SwitchPreference(
                                        title = "自动备份时联动云端同步",
                                        summary = "触发自动备份后同时同步至已开启的 WebDAV / 自建服务器",
                                        checked = autoBackup.alsoSyncRemote,
                                        onCheckedChange = { onUpdateAutoBackup(autoBackup.copy(alsoSyncRemote = it)) },
                                    )
                                    Text(
                                        text = "上次自动备份：${if (autoBackup.lastAutoBackupAt > 0L) formatTimestamp(autoBackup.lastAutoBackupAt) else "尚未执行"}" +
                                            if (autoBackup.lastDailyBackupDate.isNotBlank()) " · 今日首开：${autoBackup.lastDailyBackupDate}" else "",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }

                        item(key = "manual_backup_card") {
                            SmallTitle(text = "手动备份与导入")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TextButton(
                                        text = "立即手动备份（含账单与服务器配置）",
                                        onClick = onManualBackupNow,
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        TextButton(
                                            text = "另存为 .mochi.json",
                                            onClick = onExportBackupWithPicker,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = "选择文件导入备份",
                                            onClick = { onPickImportBackupFile(restoreServerConfigOnImport) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        TextButton(
                                            text = "导出 CSV 账单表格",
                                            onClick = onExportCsv,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    HorizontalDivider()
                                    SwitchPreference(
                                        title = "导入时一并恢复服务器配置",
                                        summary = "恢复备份文件内记录的 WebDAV、自建服及自动备份设置",
                                        checked = restoreServerConfigOnImport,
                                        onCheckedChange = { restoreServerConfigOnImport = it },
                                    )
                                    TextField(
                                        value = importJsonText,
                                        onValueChange = { importJsonText = it },
                                        label = "或粘贴 .mochi.json 文本内容导入...",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (importJsonText.isNotBlank()) {
                                        TextButton(
                                            text = "从粘贴文本去重合并导入",
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

                        item(key = "local_history_card") {
                            SmallTitle(text = "本地备份历史（共 ${state.localBackups.size} 份）")
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "备份目录：${state.backupDirectoryPath}\n数据库：${state.databasePath}",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    if (state.localBackups.isEmpty()) {
                                        Text(
                                            text = "暂无本地备份文件，点击上方「立即手动备份」即可创建第一份完整备份。",
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    } else {
                                        state.localBackups.forEachIndexed { idx, file ->
                                            if (idx > 0) HorizontalDivider()
                                            val triggerLabel = when (file.triggerType) {
                                                "daily" -> "每日首开"
                                                "scheduled" -> "定时备份"
                                                else -> "手动备份"
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
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        MochiGlassBadge(triggerLabel, triggerColor)
                                                        Text(
                                                            text = formatFileSize(file.sizeBytes),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    }
                                                    Text(
                                                        text = formatTimestamp(file.lastModifiedMs),
                                                        fontSize = 11.sp,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    )
                                                }
                                                Text(
                                                    text = file.fileName,
                                                    fontSize = 12.sp,
                                                    color = MiuixTheme.colorScheme.onSurface,
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
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
                    }
                }
            }
        }
    }
}
