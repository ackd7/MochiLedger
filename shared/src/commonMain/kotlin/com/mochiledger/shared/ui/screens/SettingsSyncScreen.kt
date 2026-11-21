package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochiledger.shared.data.LedgerUiState
import com.mochiledger.shared.model.AppAppearanceConfig
import com.mochiledger.shared.model.SelfHostedSyncConfig
import com.mochiledger.shared.model.WebDavConfig
import com.mochiledger.shared.ui.components.MochiChip
import com.mochiledger.shared.ui.theme.MochiAccentPresets
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImportJson: (String) -> Unit,
    onUpdateAppearance: (AppAppearanceConfig) -> Unit,
) {
    val webDav = state.metadata.webDav
    val selfHosted = state.metadata.selfHosted
    val appearance = state.metadata.appearance
    var importJsonText by remember { mutableStateOf("") }

    fun formatTimestamp(ms: Long): String {
        if (ms <= 0L) return "尚未同步"
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')} " +
            "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // WebDAV Sync Section
        item(key = "webdav") {
            SmallTitle(text = "WebDAV 云同步与备份（Windows ↔ Android 互通）")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = webDav.username,
                            onValueChange = { onUpdateWebDav(webDav.copy(username = it)) },
                            label = "用户名 / 邮箱",
                            modifier = Modifier.weight(1f),
                        )
                        TextField(
                            value = webDav.password,
                            onValueChange = { onUpdateWebDav(webDav.copy(password = it)) },
                            label = "应用专用密码",
                            modifier = Modifier.weight(1f),
                        )
                    }
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
                            text = "上传快照备份",
                            onClick = onBackupWebDav,
                            enabled = !state.isSyncing,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = if (state.isSyncing) "同步中..." else "双向合并同步",
                            onClick = onSyncWebDav,
                            enabled = !state.isSyncing,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1.2f),
                        )
                    }
                }
            }
        }

        // Self-hosted Sync Server Section
        item(key = "self_hosted") {
            SmallTitle(text = "自建同步服务器（内置 Ktor + Docker 服务）")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = selfHosted.serverUrl,
                        onValueChange = { onUpdateSelfHosted(selfHosted.copy(serverUrl = it)) },
                        label = "服务器地址（如 http://192.168.1.10:8787）",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = selfHosted.authToken,
                            onValueChange = { onUpdateSelfHosted(selfHosted.copy(authToken = it)) },
                            label = "Bearer Token（MOCHI_SYNC_TOKEN）",
                            modifier = Modifier.weight(1.3f),
                        )
                        TextField(
                            value = selfHosted.ledgerId,
                            onValueChange = { onUpdateSelfHosted(selfHosted.copy(ledgerId = it)) },
                            label = "账本 ID",
                            modifier = Modifier.weight(0.7f),
                        )
                    }
                    Text(
                        text = "上次同步：${formatTimestamp(selfHosted.lastSyncAt)}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "测试服务器",
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

        // Local Backup & Interop
        item(key = "local_backup") {
            SmallTitle(text = "本地数据互通（统一 SQLite / JSON / CSV）")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "本地 SQLite 路径：${state.databasePath}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "导出 .mochi.json 备份",
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
                        label = "粘贴 .mochi.json 内容以合并导入...",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = "合并导入 JSON 快照",
                        onClick = {
                            if (importJsonText.isNotBlank()) {
                                onImportJson(importJsonText)
                                importJsonText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Appearance (Miuix + Mochi G2 Squircle)
        item(key = "appearance") {
            SmallTitle(text = "外观与 Miuix / Mochi 动效")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("主题模式", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MochiChip("跟随系统", appearance.colorMode == 0) {
                            onUpdateAppearance(appearance.copy(colorMode = 0))
                        }
                        MochiChip("浅色", appearance.colorMode == 1) {
                            onUpdateAppearance(appearance.copy(colorMode = 1))
                        }
                        MochiChip("深色", appearance.colorMode == 2) {
                            onUpdateAppearance(appearance.copy(colorMode = 2))
                        }
                    }
                    Text("主题强调色", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MochiAccentPresets.forEachIndexed { index, preset ->
                            MochiChip(preset.name, appearance.accentPreset == index, preset.primary) {
                                onUpdateAppearance(appearance.copy(accentPreset = index))
                            }
                        }
                    }
                    SwitchPreference(
                        title = "G2 连续圆角（Squircle）",
                        summary = "启用 Miuix / Mochi 风格的平滑连续圆角",
                        checked = appearance.enableSquircle,
                        onCheckedChange = { onUpdateAppearance(appearance.copy(enableSquircle = it)) },
                    )
                }
            }
        }
    }
}
