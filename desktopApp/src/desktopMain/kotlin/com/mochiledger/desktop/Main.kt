package com.mochiledger.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mochiledger.shared.ui.MochiLedgerApp

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1160.dp, 760.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "墨记 MochiLedger",
    ) {
        MochiLedgerApp()
    }
}
