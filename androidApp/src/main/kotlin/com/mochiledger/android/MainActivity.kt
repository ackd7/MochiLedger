package com.mochiledger.android

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.mochiledger.shared.platform.AndroidPlatformContext
import com.mochiledger.shared.ui.MochiLedgerApp
import java.nio.charset.Charset

class MainActivity : ComponentActivity() {
    private var pendingImportCallback: ((Result<Pair<String, String>>) -> Unit)? = null
    private var pendingCsvCallback: ((Result<Pair<String, String>>) -> Unit)? = null
    private var pendingExportContent: String? = null
    private var pendingExportCallback: ((Result<String>) -> Unit)? = null

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val cb = pendingImportCallback
        pendingImportCallback = null
        if (cb == null) return@registerForActivityResult
        if (uri == null) {
            cb(Result.failure(IllegalStateException("已取消选择文件")))
            return@registerForActivityResult
        }
        val res = runCatching {
            val fileName = resolveDisplayName(uri) ?: "backup.mochi.json"
            val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalStateException("无法读取所选文件内容")
            fileName to text
        }
        cb(res)
    }

    private val openCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val cb = pendingCsvCallback
        pendingCsvCallback = null
        if (cb == null) return@registerForActivityResult
        if (uri == null) {
            cb(Result.failure(IllegalStateException("已取消选择账单文件")))
            return@registerForActivityResult
        }
        val res = runCatching {
            val fileName = resolveDisplayName(uri) ?: "statement.csv"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("无法读取所选账单文件")
            fileName to decodeStatementBytes(bytes)
        }
        cb(res)
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val cb = pendingExportCallback
        val content = pendingExportContent
        pendingExportCallback = null
        pendingExportContent = null
        if (cb == null) return@registerForActivityResult
        if (uri == null || content == null) {
            cb(Result.failure(IllegalStateException("已取消导出")))
            return@registerForActivityResult
        }
        val res = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: throw IllegalStateException("无法写入目标文件")
            resolveDisplayName(uri) ?: uri.toString()
        }
        cb(res)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AndroidPlatformContext.openBackupFilePicker = { callback ->
            pendingImportCallback = callback
            openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        AndroidPlatformContext.openStatementCsvPicker = { callback ->
            pendingCsvCallback = callback
            openCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
        }
        AndroidPlatformContext.saveBackupFilePicker = { suggestedFileName, content, callback ->
            pendingExportContent = content
            pendingExportCallback = callback
            createDocumentLauncher.launch(suggestedFileName)
        }

        setContent {
            MochiLedgerApp(repository = MochiApplication.instance.repository)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            AndroidPlatformContext.openBackupFilePicker = null
            AndroidPlatformContext.openStatementCsvPicker = null
            AndroidPlatformContext.saveBackupFilePicker = null
        }
    }

    private fun decodeStatementBytes(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        val looksLikeUtf8 = !utf8.contains('\uFFFD') &&
            (utf8.contains("交易时间") || utf8.contains("交易对方") || utf8.contains("微信") || utf8.contains("支付宝"))
        if (looksLikeUtf8) return utf8.removePrefix("\uFEFF")
        return runCatching {
            String(bytes, Charset.forName("GB18030")).removePrefix("\uFEFF")
        }.getOrElse { utf8.removePrefix("\uFEFF") }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
