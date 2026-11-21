package com.mochiledger.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mochiledger.shared.ui.MochiLedgerApp
import com.mochiledger.shared.ui.components.FlatIcon
import com.mochiledger.shared.ui.theme.MochiColors
import com.mochiledger.shared.ui.theme.mochiShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1160.dp, 760.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "墨记 MochiLedger",
        undecorated = true,
    ) {
        MochiLedgerApp(
            desktopTopBar = {
                WindowDraggableArea {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(MiuixTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(mochiShape(8.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f))
                                    .clickable { windowState.isMinimized = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                FlatIcon("minimize", MiuixTheme.colorScheme.onSurface, size = 14.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(mochiShape(8.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f))
                                    .clickable {
                                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                            WindowPlacement.Floating
                                        } else {
                                            WindowPlacement.Maximized
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                FlatIcon("maximize", MiuixTheme.colorScheme.onSurface, size = 13.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(mochiShape(8.dp))
                                    .background(MochiColors.Expense.copy(alpha = 0.16f))
                                    .clickable { exitApplication() },
                                contentAlignment = Alignment.Center,
                            ) {
                                FlatIcon("close", MochiColors.Expense, size = 14.dp)
                            }
                        }
                    }
                }
            },
        )
    }
}
