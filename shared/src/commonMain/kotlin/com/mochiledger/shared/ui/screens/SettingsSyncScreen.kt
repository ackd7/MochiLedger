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
import com.mochiledger.shared.model.SelfHostedSyncConfig
import com.mochiledger.shared.model.WebDavConfig
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

private enum class SyncSubPage {
    HUB,
    WEBDAV,
    SELF_HOSTED,
    LOCAL,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSyncScreen(
    state: LedgerUiState,
    onUpdateWebDav: (WebDavConfig) -> Unit,
    onTestWebDav: () -> Unit,
    onSyncWebDav: () -> Unit,
    onBackupWebDav: () -> Unit,
    onUpdateSelfHosted: (SelfHostedSyncConfig) -> Unit,
    onTestSelfHosted: () -> Unit,
    onSyncSelfHosted: () -> Unit,
    onSyncAllEnabled: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImportJson: (String) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    var subPage by remember { mutableStateOf(SyncSubPage.HUB) }
    val webDav = state.metadata.webDav
    val selfHosted = state.metadata.selfHosted
    var importJsonText by remember { mutableStateOf("") }

    fun formatTimestamp(ms: Long): String {
        if (ms <= 0L) return "尚未同步"
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')} " +
            "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }

    val enabledCount = listOf(webDav.enabled, selfHosted.enabled).count { it }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (subPage != SyncSubPage.HUB) {
            item(key = "back_bar") {
                Row(
                    modifier = Modifier
                        .clip(mochiShape(14.dp))
                        .background(MiuixTheme.colorScheme.surface)
                        .clickable { subPage = SyncSubPage.HUB }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("← 返回同步与备份", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        when (subPage) {
            SyncSubPage.HUB -> {
                // Multi-source hub hero
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
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
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
                }

                // Secondary entries for each backup/sync method
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
                            ArrowPreference(
                                title = "本地文件备份与导入",
                                summary = "统一 SQLite / .mochi.json / CSV 导出与合并导入",
                                onClick = { subPage = SyncSubPage.LOCAL },
                            )
                        }
                    }
                }

                // Active Status Summary
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
                    SmallTitle(text = "WebDAV 云同步配置")
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
                    SmallTitle(text = "自建同步服务器配置")
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
                item(key = "local_detail") {
                    SmallTitle(text = "本地文件备份与导入")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "本地 SQLite 数据库路径：\n${state.databasePath}",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    text = "导出 .mochi.json",
                                    onClick = onExportJson,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    text = "导出 CSV 表格",
                                    onClick = onExportCsv,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            TextField(
                                value = importJsonText,
                                onValueChange = { importJsonText = it },
                                label = "粘贴 .mochi.json 内容以去重合并导入...",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                text = "去重合并导入 JSON 快照",
                                onClick = {
                                    if (importJsonText.isNotBlank()) {
                                        onImportJson(importJsonText)
                                        importJsonText = ""
                                    }
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
