package com.mochiledger.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochiledger.shared.data.LedgerRepository
import com.mochiledger.shared.platform.PlatformBridge
import com.mochiledger.shared.ui.screens.AccountsScreen
import com.mochiledger.shared.ui.screens.AnalyticsScreen
import com.mochiledger.shared.ui.screens.AppSettingsScreen
import com.mochiledger.shared.ui.screens.AutoTrackScreen
import com.mochiledger.shared.ui.screens.SettingsSyncScreen
import com.mochiledger.shared.ui.screens.TransactionsScreen
import com.mochiledger.shared.ui.theme.MochiLedgerTheme
import com.mochiledger.shared.ui.theme.mochiShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class MochiTab(val title: String, val emoji: String) {
    TRANSACTIONS("账单", "📒"),
    ANALYTICS("统计", "📊"),
    ACCOUNTS("资产", "💳"),
    AUTO_TRACK("自动记账", "⚡"),
    SYNC("同步", "☁️"),
    SETTINGS("设置", "⚙️"),
}

@Composable
fun MochiLedgerApp(
    repository: LedgerRepository = remember { LedgerRepository() },
) {
    val state by repository.state.collectAsState()
    val supportsLsposed = remember { PlatformBridge.getAutoTrackRuntimeStatus().supportsLsposed }
    val visibleTabs = remember(supportsLsposed) {
        if (supportsLsposed) {
            MochiTab.entries
        } else {
            MochiTab.entries.filterNot { it == MochiTab.AUTO_TRACK }
        }
    }

    var currentTab by remember { mutableStateOf(MochiTab.TRANSACTIONS) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun showMessage(msg: String) {
        toastMessage = msg
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(3200)
            toastMessage = null
        }
    }

    MochiLedgerTheme(appearance = state.metadata.appearance) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
            val isWideScreen = maxWidth >= 760.dp

            if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Desktop / Tablet Navigation Sidebar (no LSPosed tab on Windows)
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .background(MiuixTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text("墨记 MochiLedger", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "跨端开源记账 · 同步互通",
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        visibleTabs.forEach { tab ->
                            val selected = currentTab == tab
                            val bg = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
                            val fg = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(mochiShape(14.dp))
                                    .background(bg)
                                    .clickable { currentTab = tab }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(tab.emoji, fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = tab.title,
                                    color = fg,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }

                    // Main Content
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        MochiMainContent(
                            tab = currentTab,
                            state = state,
                            repository = repository,
                            onShowMessage = ::showMessage,
                            scope = scope,
                            bottomInset = 0.dp,
                        )
                    }
                }
            } else {
                // Mobile Android Adaptive Scaffold with larger Floating Pill Bottom Bar
                val floatingBarInset = 96.dp
                Scaffold(
                    topBar = {
                        SmallTopAppBar(title = "墨记 · ${currentTab.title}")
                    },
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        MochiMainContent(
                            tab = currentTab,
                            state = state,
                            repository = repository,
                            onShowMessage = ::showMessage,
                            scope = scope,
                            bottomInset = floatingBarInset,
                        )

                        // Floating enlarged Mochi bottom navigation bar
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .fillMaxWidth()
                                .shadow(elevation = 14.dp, shape = mochiShape(28.dp))
                                .clip(mochiShape(28.dp))
                                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f))
                                .border(
                                    width = 1.dp,
                                    color = MiuixTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = mochiShape(28.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                visibleTabs.forEach { tab ->
                                    val selected = currentTab == tab
                                    val fg = if (selected) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(mochiShape(20.dp))
                                            .background(
                                                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                else Color.Transparent,
                                            )
                                            .clickable { currentTab = tab }
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(tab.emoji, fontSize = 20.sp)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = tab.title,
                                            fontSize = 11.sp,
                                            color = fg,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Mochi Toast Banner
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isWideScreen) 24.dp else 108.dp, start = 20.dp, end = 20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(mochiShape(18.dp))
                        .background(Color(0xE61F2430))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = toastMessage.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MochiMainContent(
    tab: MochiTab,
    state: com.mochiledger.shared.data.LedgerUiState,
    repository: LedgerRepository,
    onShowMessage: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    bottomInset: Dp,
) {
    when (tab) {
        MochiTab.TRANSACTIONS -> TransactionsScreen(
            state = state,
            onSaveTransaction = repository::upsertTransaction,
            onDeleteTransaction = repository::deleteTransaction,
            onShowMessage = onShowMessage,
            bottomInset = bottomInset,
        )
        MochiTab.ANALYTICS -> AnalyticsScreen(
            state = state,
            bottomInset = bottomInset,
        )
        MochiTab.ACCOUNTS -> AccountsScreen(
            state = state,
            onSaveAccount = repository::upsertAccount,
            onDeleteAccount = repository::deleteAccount,
            onShowMessage = onShowMessage,
            bottomInset = bottomInset,
        )
        MochiTab.AUTO_TRACK -> AutoTrackScreen(
            state = state,
            onUpdateSettings = repository::updateAutoTrackSettings,
            onParsePreview = { pkg, title, content -> repository.parsePaymentPreview(pkg, title, content) },
            onRecordParsed = { parsed -> repository.recordParsedPayment(parsed) != null },
            onSaveRule = repository::upsertRule,
            onDeleteRule = repository::deleteRule,
            onShowMessage = onShowMessage,
            bottomInset = bottomInset,
        )
        MochiTab.SYNC -> SettingsSyncScreen(
            state = state,
            onUpdateWebDav = repository::updateWebDavConfig,
            onTestWebDav = {
                scope.launch { onShowMessage(repository.testWebDav().message) }
            },
            onSyncWebDav = {
                scope.launch { onShowMessage(repository.syncWithWebDav().message) }
            },
            onBackupWebDav = {
                scope.launch { onShowMessage(repository.backupToWebDav().message) }
            },
            onUpdateSelfHosted = repository::updateSelfHostedConfig,
            onTestSelfHosted = {
                scope.launch { onShowMessage(repository.testSelfHosted().message) }
            },
            onSyncSelfHosted = {
                scope.launch { onShowMessage(repository.syncWithSelfHosted().message) }
            },
            onSyncAllEnabled = {
                scope.launch { onShowMessage(repository.syncAllEnabledSources().message) }
            },
            onExportJson = {
                val res = repository.exportJsonBackup()
                onShowMessage(res.fold({ "已导出至：$it" }, { "导出失败：${it.message}" }))
            },
            onExportCsv = {
                val res = repository.exportCsvBackup()
                onShowMessage(res.fold({ "已导出 CSV 至：$it" }, { "导出失败：${it.message}" }))
            },
            onImportJson = { json ->
                onShowMessage(repository.importJsonSnapshot(json).message)
            },
            bottomInset = bottomInset,
        )
        MochiTab.SETTINGS -> AppSettingsScreen(
            state = state,
            onUpdateAppearance = repository::updateAppearance,
            onCheckUpdates = { repository.checkForUpdates() },
            onShowMessage = onShowMessage,
            bottomInset = bottomInset,
        )
    }
}
