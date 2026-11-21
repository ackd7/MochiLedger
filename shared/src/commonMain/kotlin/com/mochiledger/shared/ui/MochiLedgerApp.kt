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
import com.mochiledger.shared.ui.components.FlatIcon
import com.mochiledger.shared.ui.screens.AccountsScreen
import com.mochiledger.shared.ui.screens.AnalyticsScreen
import com.mochiledger.shared.ui.screens.AppSettingsScreen
import com.mochiledger.shared.ui.screens.TransactionsScreen
import com.mochiledger.shared.ui.theme.MochiLedgerTheme
import com.mochiledger.shared.ui.theme.mochiShape
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class MochiTab(val title: String, val iconKey: String) {
    TRANSACTIONS("账单", "ledger"),
    ANALYTICS("统计", "analytics"),
    ACCOUNTS("资产", "accounts"),
    SETTINGS("设置", "settings"),
}

@Composable
fun MochiLedgerApp(
    repository: LedgerRepository = remember { LedgerRepository() },
    desktopTopBar: (@Composable () -> Unit)? = null,
) {
    val state by repository.state.collectAsState()
    val supportsLsposed = remember { PlatformBridge.getAutoTrackRuntimeStatus().supportsLsposed }

    var currentTab by remember { mutableStateOf(MochiTab.TRANSACTIONS) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

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
                Column(modifier = Modifier.fillMaxSize()) {
                    // Integrated borderless desktop top bar (draggable + min/max/close)
                    desktopTopBar?.invoke()

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Desktop Navigation Sidebar (4 clean flat-icon tabs)
                        Column(
                            modifier = Modifier
                                .width(210.dp)
                                .fillMaxHeight()
                                .background(MiuixTheme.colorScheme.surface)
                                .padding(horizontal = 14.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("墨记 MochiLedger", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "跨端开源记账 · 同步互通",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            MochiTab.entries.forEach { tab ->
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
                                    FlatIcon(name = tab.iconKey, tint = fg, size = 19.dp)
                                    Spacer(Modifier.width(12.dp))
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
                                supportsLsposed = supportsLsposed,
                                onShowMessage = ::showMessage,
                                bottomInset = 0.dp,
                            )
                        }
                    }
                }
            } else {
                // Mobile Android Adaptive Scaffold with spacious 4-tab Floating Pill Bottom Bar
                val floatingBarInset = 98.dp
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
                            supportsLsposed = supportsLsposed,
                            onShowMessage = ::showMessage,
                            bottomInset = floatingBarInset,
                        )

                        // Floating enlarged Mochi bottom navigation bar with flat vector icons
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .fillMaxWidth()
                                .shadow(elevation = 14.dp, shape = mochiShape(28.dp))
                                .clip(mochiShape(28.dp))
                                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f))
                                .border(
                                    width = 1.dp,
                                    color = MiuixTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = mochiShape(28.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MochiTab.entries.forEach { tab ->
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
                                            .padding(vertical = 9.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        FlatIcon(name = tab.iconKey, tint = fg, size = 22.dp)
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = tab.title,
                                            fontSize = 12.sp,
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
                    .padding(bottom = if (isWideScreen) 24.dp else 112.dp, start = 20.dp, end = 20.dp),
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
    supportsLsposed: Boolean,
    onShowMessage: (String) -> Unit,
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
        MochiTab.SETTINGS -> AppSettingsScreen(
            state = state,
            repository = repository,
            supportsLsposed = supportsLsposed,
            onShowMessage = onShowMessage,
            bottomInset = bottomInset,
        )
    }
}
