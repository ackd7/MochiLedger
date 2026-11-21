package com.mochiledger.android.xposed

/**
 * Self-hook target: when LSPosed loads MochiLedger, MochiXposedEntry replaces isModuleActive() to return true.
 */
object XposedStatus {
    @JvmStatic
    fun isModuleActive(): Boolean = false
}
