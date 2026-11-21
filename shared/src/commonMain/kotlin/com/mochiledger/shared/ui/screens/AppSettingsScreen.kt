package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochiledger.shared.data.LedgerRepository
import com.mochiledger.shared.data.LedgerUiState
import com.mochiledger.shared.model.AppInfo
import com.mochiledger.shared.platform.PlatformBridge
import com.mochiledger.shared.sync.UpdateCheckResult
import com.mochiledger.shared.ui.components.MochiChip
import com.mochiledger.shared.ui.theme.MochiAccentPresets
import com.mochiledger.shared.ui.theme.mochiShape
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class SettingsSubPage {
    MAIN,
    SYNC,
    AUTO_TRACK,
    CHANGELOG,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(
    state: LedgerUiState,
    repository: LedgerRepository,
    supportsLsposed: Boolean,
    onShowMessage: (String) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    var subPage by remember { mutableStateOf(SettingsSubPage.MAIN) }
    val appearance = state.metadata.appearance
    val scope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }

    when (subPage) {
        SettingsSubPage.SYNC -> {
            Column(modifier = Modifier.fillMaxSize()) {
                BackHeader("← 返回设置", "同步与备份") { subPage = SettingsSubPage.MAIN }
                SettingsSyncScreen(
                    state = state,
                    onUpdateWebDav = repository::updateWebDavConfig,
                    onTestWebDav = { scope.launch { onShowMessage(repository.testWebDav().message) } },
                    onSyncWebDav = { scope.launch { onShowMessage(repository.syncWithWebDav().message) } },
                    onBackupWebDav = { scope.launch { onShowMessage(repository.backupToWebDav().message) } },
                    onUpdateSelfHosted = repository::updateSelfHostedConfig,
                    onTestSelfHosted = { scope.launch { onShowMessage(repository.testSelfHosted().message) } },
                    onSyncSelfHosted = { scope.launch { onShowMessage(repository.syncWithSelfHosted().message) } },
                    onSyncAllEnabled = { scope.launch { onShowMessage(repository.syncAllEnabledSources().message) } },
                    onExportJson = {
                        val res = repository.exportJsonBackup()
                        onShowMessage(res.fold({ "已导出至：$it" }, { "导出失败：${it.message}" }))
                    },
                    onExportCsv = {
                        val res = repository.exportCsvBackup()
                        onShowMessage(res.fold({ "已导出 CSV 至：$it" }, { "导出失败：${it.message}" }))
                    },
                    onImportJson = { json -> onShowMessage(repository.importJsonSnapshot(json).message) },
                    bottomInset = bottomInset,
                )
            }
            return
        }

        SettingsSubPage.AUTO_TRACK -> {
            Column(modifier = Modifier.fillMaxSize()) {
                BackHeader("← 返回设置", "自动记账 (LSPosed)") { subPage = SettingsSubPage.MAIN }
                AutoTrackScreen(
                    state = state,
                    onUpdateSettings = repository::updateAutoTrackSettings,
                    onParsePreview = { pkg, title, content -> repository.parsePaymentPreview(pkg, title, content) },
                    onRecordParsed = { parsed -> repository.recordParsedPayment(parsed) != null },
                    onSaveRule = repository::upsertRule,
                    onDeleteRule = repository::deleteRule,
                    onShowMessage = onShowMessage,
                    bottomInset = bottomInset,
                )
            }
            return
        }

        SettingsSubPage.CHANGELOG -> {
            Column(modifier = Modifier.fillMaxSize()) {
                BackHeader("← 返回设置", "更新日志 (Changelog)") { subPage = SettingsSubPage.MAIN }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + bottomInset),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppInfo.CHANGELOG.forEach { entry ->
                        item(key = entry.version) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "${entry.version} · ${entry.title}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                    entry.changes.forEach { line ->
                                        Text(
                                            text = "• $line",
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return
        }

        SettingsSubPage.MAIN -> Unit
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Secondary Menu Entries: Sync & (Android) Auto-Track
        item(key = "feature_entries") {
            SmallTitle(text = "数据与扩展")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    val syncSummary = buildList {
                        if (state.metadata.webDav.enabled) add("WebDAV")
                        if (state.metadata.selfHosted.enabled) add("自建服务器")
                    }.let { list ->
                        if (list.isEmpty()) "配置 WebDAV、自建服务器与本地备份" else "已开启：${list.joinToString(" + ")}"
                    }
                    ArrowPreference(
                        title = "同步与备份",
                        summary = syncSummary,
                        onClick = { subPage = SettingsSubPage.SYNC },
                    )
                    if (supportsLsposed) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ArrowPreference(
                            title = "自动记账（LSPosed / 通知监听）",
                            summary = "监听微信与支付宝支付通知、分类规则与模拟器",
                            onClick = { subPage = SettingsSubPage.AUTO_TRACK },
                        )
                    }
                }
            }
        }

        // Theme & Colors
        item(key = "theme_section") {
            SmallTitle(text = "主题与颜色")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("主题模式", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MochiChip("跟随系统", appearance.colorMode == 0) {
                            repository.updateAppearance(appearance.copy(colorMode = 0))
                        }
                        MochiChip("浅色", appearance.colorMode == 1) {
                            repository.updateAppearance(appearance.copy(colorMode = 1))
                        }
                        MochiChip("深色", appearance.colorMode == 2) {
                            repository.updateAppearance(appearance.copy(colorMode = 2))
                        }
                    }

                    Text("主题强调色", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MochiAccentPresets.forEachIndexed { index, preset ->
                            MochiChip(preset.name, appearance.accentPreset == index, preset.primary) {
                                repository.updateAppearance(appearance.copy(accentPreset = index))
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "G2 连续圆角（Squircle）",
                    summary = "启用 Miuix / Mochi 风格的平滑连续圆角",
                    checked = appearance.enableSquircle,
                    onCheckedChange = { repository.updateAppearance(appearance.copy(enableSquircle = it)) },
                )
            }
        }

        // About, Changelog, Repo & Update Checker
        item(key = "about_section") {
            SmallTitle(text = "关于与更新")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = "更新日志（Changelog）",
                        summary = "查看 v${AppInfo.VERSION_NAME} 及历史版本更新内容",
                        onClick = { subPage = SettingsSubPage.CHANGELOG },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "项目开源地址",
                        summary = AppInfo.GITHUB_URL,
                        onClick = {
                            val opened = PlatformBridge.openUrl(AppInfo.GITHUB_URL)
                            if (!opened) onShowMessage("请访问：${AppInfo.GITHUB_URL}")
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "当前版本",
                        summary = "v${AppInfo.VERSION_NAME} · MochiLedger",
                        onClick = { PlatformBridge.openUrl("${AppInfo.GITHUB_URL}/releases") },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TextButton(
                                text = if (checkingUpdate) "正在检查更新..." else "检查版本更新",
                                onClick = {
                                    scope.launch {
                                        checkingUpdate = true
                                        val res = repository.checkForUpdates()
                                        updateResult = res
                                        checkingUpdate = false
                                        onShowMessage(res.message)
                                    }
                                },
                                enabled = !checkingUpdate,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.weight(1f),
                            )
                            if (updateResult?.hasUpdate == true) {
                                TextButton(
                                    text = "前往下载 v${updateResult?.latestVersion}",
                                    onClick = {
                                        PlatformBridge.openUrl(updateResult?.releaseUrl ?: "${AppInfo.GITHUB_URL}/releases")
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        updateResult?.let { res ->
                            Text(
                                text = res.message,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (res.hasUpdate && res.releaseNotes.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = res.releaseNotes,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackHeader(
    backLabel: String,
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(mochiShape(12.dp))
                .background(MiuixTheme.colorScheme.surface)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(backLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
