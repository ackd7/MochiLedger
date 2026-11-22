package com.mochiledger.android.service

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.mochiledger.android.MochiApplication
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.RecordSource
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.formatCents
import com.mochiledger.shared.model.parseAmountToCents
import com.mochiledger.shared.platform.PlatformBridge

/**
 * Silent translucent bridge handling `mochiledger://add?...` DeepLinks.
 * Compatible with AutoAccounting (自动记账), Tasker, MacroDroid, and custom scripts.
 *
 * Example URI:
 *   mochiledger://add?type=expense&amount=32.50&merchant=星巴克&category=餐饮美食&account=支付宝&remark=拿铁
 */
class DeepLinkRecordActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val uri = intent?.data
            if (uri != null && uri.scheme.equals("mochiledger", ignoreCase = true)) {
                handleUri(uri)
            }
        }
        finish()
    }

    private fun handleUri(uri: Uri) {
        val amountRaw = uri.getQueryParameter("amount")
            ?: uri.getQueryParameter("money")
            ?: return
        val amountCents = parseAmountToCents(amountRaw) ?: return
        if (amountCents <= 0L) return

        val typeRaw = (uri.getQueryParameter("type") ?: "expense").trim().lowercase()
        val txType = when (typeRaw) {
            "1", "income", "收入" -> TransactionType.INCOME
            "2", "transfer", "转账" -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }

        val repo = MochiApplication.instance.repository
        val state = repo.state.value

        val merchant = (uri.getQueryParameter("merchant")
            ?: uri.getQueryParameter("shop")
            ?: uri.getQueryParameter("payee")
            ?: "").trim()
        val note = (uri.getQueryParameter("remark")
            ?: uri.getQueryParameter("note")
            ?: "").trim()

        val catParam = (uri.getQueryParameter("category")
            ?: uri.getQueryParameter("cat")
            ?: uri.getQueryParameter("cate")
            ?: "").trim()
        val categoryId = state.activeCategories.firstOrNull {
            it.id.equals(catParam, ignoreCase = true) || it.name.equals(catParam, ignoreCase = true)
        }?.id ?: when (txType) {
            TransactionType.INCOME -> "cat_other_inc"
            TransactionType.TRANSFER -> "cat_transfer"
            TransactionType.EXPENSE -> "cat_other_exp"
        }

        val accParam = (uri.getQueryParameter("account")
            ?: uri.getQueryParameter("accountname")
            ?: "").trim()
        val accountId = state.activeAccounts.firstOrNull {
            it.id.equals(accParam, ignoreCase = true) || it.name.contains(accParam, ignoreCase = true)
        }?.id ?: state.activeAccounts.firstOrNull()?.id ?: "acc_default"

        val now = PlatformBridge.currentTimeMillis()
        val tx = LedgerTransaction(
            id = PlatformBridge.randomUuid(),
            type = txType,
            amountCents = amountCents,
            categoryId = categoryId,
            accountId = accountId,
            timestamp = now,
            merchant = merchant.ifBlank { note.ifBlank { "外部自动记账" } },
            note = note,
            source = RecordSource.NOTIFICATION_LISTENER,
            rawAutoMessage = uri.toString(),
            updatedAt = now,
        )
        repo.upsertTransaction(tx)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = NotificationCompat.Builder(this, MochiApplication.CHANNEL_AUTO_RECORD)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("墨记已记账（外部协议）")
            .setContentText("${tx.merchant} ${formatCents(tx.amountCents)}")
            .setAutoCancel(true)
            .build()
        nm.notify((now % 100_000).toInt(), notification)
    }
}
