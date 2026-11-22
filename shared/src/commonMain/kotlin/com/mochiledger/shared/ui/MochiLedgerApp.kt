package com.mochiledger.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.mochiledger.shared.platform.PlatformBackHandler
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
    desktopTopBar: (@Composable (String) -> Unit)? = null,
    windowCornerRadius: Dp = 0.dp,
) {
    val state by repository.state.collectAsState()
    val supportsLsposed = remember { PlatformBridge.getAutoTrackRuntimeStatus().supportsLsposed }

    var currentTab by remember { mutableStateOf(MochiTab.TRANSACTIONS) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    PlatformBackHandler(enabled = currentTab != MochiTab.TRANSACTIONS) {
        currentTab = MochiTab.TRANSACTIONS
    }

    fun showMessage(msg: String) {
        toastMessage = msg
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(3200)
            toastMessage = null
        }
    }

    // Daily first-open automatic backup (includes ledger records + sync server config)
    LaunchedEffect(Unit) {
        val dailyRes = repository.checkAndRunDailyFirstOpenBackup()
        if (dailyRes != null && dailyRes.success) {
            showMessage(dailyRes.message)
        }
    }

    // Periodic scheduled backup loop
    val autoBackup = state.metadata.autoBackup
    LaunchedEffect(autoBackup.scheduledEnabled, autoBackup.intervalHours) {
        if (!autoBackup.scheduledEnabled) return@LaunchedEffect
        while (true) {
            val res = repository.checkAndRunScheduledBackup()
            if (res != null && res.success) {
                showMessage(res.message)
            }
            delay(60_000L)
        }
    }

    MochiLedgerTheme(appearance = state.metadata.appearance) {
        val windowShape = mochiShape(windowCornerRadius)
        val rootModifier = if (windowCornerRadius > 0.dp) {
            Modifier
                .fillMaxSize()
                .clip(windowShape)
                .background(MiuixTheme.colorScheme.background)
                .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.25f), windowShape)
        } else {
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
        }

        BoxWithConstraints(modifier = rootModifier) {
            val isWideScreen = maxWidth >= 760.dp
            val floatingBarInset = 98.dp

            if (desktopTopBar != null) {
                // Unified Desktop layout: integrated top bar + centered content + floating bottom pill dock
                Column(modifier = Modifier.fillMaxSize()) {
                    desktopTopBar("墨记 · ${currentTab.title}")
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.TopCenter)
                                .widthIn(max = 920.dp),
                        ) {
                            MochiMainContent(
                                tab = currentTab,
                                state = state,
                                repository = repository,
                                supportsLsposed = supportsLsposed,
                                onShowMessage = ::showMessage,
                                bottomInset = floatingBarInset,
                            )
                        }

                        FloatingPillBottomBar(
                            currentTab = currentTab,
                            onSelectTab = { currentTab = it },
                            isWideScreen = isWideScreen,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            } else {
                // Unified Mobile layout: top app bar + content + floating bottom pill dock
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

                        FloatingPillBottomBar(
                            currentTab = currentTab,
                            onSelectTab = { currentTab = it },
                            isWideScreen = isWideScreen,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
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
                    .padding(bottom = 112.dp, start = 20.dp, end = 20.dp),
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
private fun FloatingPillBottomBar(
    currentTab: MochiTab,
    onSelectTab: (MochiTab) -> Unit,
    isWideScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val barModifier = modifier
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 14.dp)
        .let { m -> if (isWideScreen) m.widthIn(max = 460.dp).fillMaxWidth() else m.fillMaxWidth() }
        .shadow(elevation = 16.dp, shape = mochiShape(28.dp))
        .clip(mochiShape(28.dp))
        .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f))
        .border(
            width = 1.dp,
            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.25f),
            shape = mochiShape(28.dp),
        )
        .padding(horizontal = 8.dp, vertical = 8.dp)

    Box(modifier = barModifier) {
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
                        .clickable { onSelectTab(tab) }
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

@Composable
private fun MochiMainContent(
    tab: MochiTab,
    state: com.mochiledger.shared.data.LedgerUiState,
    repository: LedgerRepository,
    supportsLsposed: Boolean,
    onShowMessage: (String) -> Unit,
    bottomInset: Dp,
) {
    AnimatedContent(
        targetState = tab,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val forward = targetState.ordinal >= initialState.ordinal
            val offset = if (forward) 1 else -1
            (slideInHorizontally(tween(240)) { it / 8 * offset } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(180)) { -it / 10 * offset } + fadeOut(tween(160)))
        },
        label = "mainTabTransition",
    ) { targetTab ->
        when (targetTab) {
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
}
