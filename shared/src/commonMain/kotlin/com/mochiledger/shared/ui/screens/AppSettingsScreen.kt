package com.mochiledger.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochiledger.shared.data.LedgerUiState
import com.mochiledger.shared.model.AppAppearanceConfig
import com.mochiledger.shared.model.AppInfo
import com.mochiledger.shared.platform.PlatformBridge
import com.mochiledger.shared.sync.UpdateCheckResult
import com.mochiledger.shared.ui.components.MochiChip
import com.mochiledger.shared.ui.theme.MochiAccentPresets
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(
    state: LedgerUiState,
    onUpdateAppearance: (AppAppearanceConfig) -> Unit,
    onCheckUpdates: suspend () -> UpdateCheckResult,
    onShowMessage: (String) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    val appearance = state.metadata.appearance
    val scope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MochiAccentPresets.forEachIndexed { index, preset ->
                            MochiChip(preset.name, appearance.accentPreset == index, preset.primary) {
                                onUpdateAppearance(appearance.copy(accentPreset = index))
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "G2 连续圆角（Squircle）",
                    summary = "启用 Miuix / Mochi 风格的平滑连续圆角",
                    checked = appearance.enableSquircle,
                    onCheckedChange = { onUpdateAppearance(appearance.copy(enableSquircle = it)) },
                )
            }
        }

        // About, Project Repo & Version Update Checker
        item(key = "about_section") {
            SmallTitle(text = "关于与版本更新")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
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
                        onClick = {
                            PlatformBridge.openUrl("${AppInfo.GITHUB_URL}/releases")
                        },
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
                                        val res = onCheckUpdates()
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
                        }
                    }
                }
            }
        }
    }
}
