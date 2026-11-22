package com.mochiledger.shared

import com.mochiledger.shared.model.AccountType
import com.mochiledger.shared.model.AutoBackupConfig
import com.mochiledger.shared.model.BackupServerConfigBundle
import com.mochiledger.shared.model.DefaultLedgerSeed
import com.mochiledger.shared.model.LedgerAccount
import com.mochiledger.shared.model.LedgerSnapshot
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.model.SelfHostedSyncConfig
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.WebDavConfig
import com.mochiledger.shared.parser.PaymentMessageParser
import com.mochiledger.shared.sync.LedgerJson
import com.mochiledger.shared.sync.LedgerMerger
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MochiLedgerCoreTest {

    private val now = 1_760_000_000_000L
    private val accounts = listOf(
        LedgerAccount("acc_wechat", "微信零钱", AccountType.DIGITAL_WALLET, 0L, updatedAt = now),
        LedgerAccount("acc_alipay", "支付宝", AccountType.DIGITAL_WALLET, 0L, updatedAt = now),
        LedgerAccount("acc_huabei", "花呗", AccountType.CREDIT_CARD, 0L, updatedAt = now),
    )
    private val rules = DefaultLedgerSeed.defaultRules(now)

    @Test
    fun parseWeChatExpenseAndMatchRule() {
        val parsed = PaymentMessageParser.parse(
            packageName = PaymentChannel.WECHAT.packageName,
            title = "微信支付",
            content = "向瑞幸咖啡付款19.90元，已从零钱扣除",
            rules = rules,
            accounts = accounts,
            nowMillis = now,
        )
        assertNotNull(parsed)
        assertEquals(PaymentChannel.WECHAT, parsed.channel)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(1990L, parsed.amountCents)
        assertEquals("瑞幸咖啡", parsed.merchant)
        assertEquals("cat_food", parsed.suggestedCategoryId)
        assertEquals("acc_wechat", parsed.suggestedAccountId)
    }

    @Test
    fun parseAlipayHuabeiExpense() {
        val parsed = PaymentMessageParser.parse(
            packageName = PaymentChannel.ALIPAY.packageName,
            title = "支付宝交易提醒",
            content = "你已通过花呗向美团外卖付款38.50元",
            rules = rules,
            accounts = accounts,
            nowMillis = now,
        )
        assertNotNull(parsed)
        assertEquals(PaymentChannel.ALIPAY, parsed.channel)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(3850L, parsed.amountCents)
        assertEquals("美团外卖", parsed.merchant)
        assertEquals("cat_food", parsed.suggestedCategoryId)
        assertEquals("acc_huabei", parsed.suggestedAccountId)
    }

    @Test
    fun parseWeChatIncomeReceipt() {
        val parsed = PaymentMessageParser.parse(
            packageName = PaymentChannel.WECHAT.packageName,
            title = "微信支付",
            content = "微信支付收款50.00元",
            rules = rules,
            accounts = accounts,
            nowMillis = now,
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(5000L, parsed.amountCents)
    }

    @Test
    fun ignoreVerificationCodeMessage() {
        val parsed = PaymentMessageParser.parse(
            packageName = PaymentChannel.ALIPAY.packageName,
            title = "支付宝安全提醒",
            content = "您的登录验证码为 882910，5分钟内有效",
            rules = rules,
            accounts = accounts,
            nowMillis = now,
        )
        assertNull(parsed)
    }

    @Test
    fun lwwMergeResolvesEditsAndKeepsBothDevices() {
        val txWin = LedgerTransaction(
            id = "tx_1",
            type = TransactionType.EXPENSE,
            amountCents = 1500,
            categoryId = "cat_food",
            accountId = "acc_wechat",
            timestamp = now,
            merchant = "旧商户",
            updatedAt = now,
        )
        val txWinUpdatedOnAndroid = txWin.copy(merchant = "新商户", updatedAt = now + 5000)
        val txAndroidOnly = LedgerTransaction(
            id = "tx_2",
            type = TransactionType.EXPENSE,
            amountCents = 2500,
            categoryId = "cat_transport",
            accountId = "acc_alipay",
            timestamp = now + 1000,
            merchant = "滴滴出行",
            updatedAt = now + 1000,
        )

        val local = LedgerSnapshot(exportedAt = now, transactions = listOf(txWin))
        val remote = LedgerSnapshot(exportedAt = now + 5000, transactions = listOf(txWinUpdatedOnAndroid, txAndroidOnly))
        val merged = LedgerMerger.merge(local, remote, now + 6000, "win-test")

        assertEquals(2, merged.transactions.size)
        assertEquals("新商户", merged.transactions.first { it.id == "tx_1" }.merchant)
        assertTrue(merged.transactions.any { it.id == "tx_2" })
    }

    @Test
    fun multiSourceMergeDeduplicatesAccountsAndTransactions() {
        val accA = LedgerAccount("acc_1", "微信零钱", AccountType.DIGITAL_WALLET, 10000, updatedAt = now)
        val accADuplicate = LedgerAccount("acc_2", "微信零钱", AccountType.DIGITAL_WALLET, 10000, updatedAt = now + 2000)

        val txA = LedgerTransaction(
            id = "tx_a",
            type = TransactionType.EXPENSE,
            amountCents = 3200,
            categoryId = "cat_food",
            accountId = "acc_1",
            timestamp = now,
            merchant = "星巴克",
            updatedAt = now,
        )
        val txADuplicate = LedgerTransaction(
            id = "tx_b",
            type = TransactionType.EXPENSE,
            amountCents = 3200,
            categoryId = "cat_food",
            accountId = "acc_2",
            timestamp = now + 200, // same second
            merchant = "星巴克",
            updatedAt = now + 3000,
        )

        val snapWebDav = LedgerSnapshot(exportedAt = now, accounts = listOf(accA), transactions = listOf(txA))
        val snapServer = LedgerSnapshot(exportedAt = now + 3000, accounts = listOf(accADuplicate), transactions = listOf(txADuplicate))
        val merged = LedgerMerger.merge(snapWebDav, snapServer, now + 4000, "win-test")

        assertEquals(1, merged.accounts.count { !it.isDeleted })
        assertEquals(1, merged.transactions.count { !it.isDeleted })
        assertEquals("acc_2", merged.accounts.first().id)
        assertEquals("acc_2", merged.transactions.first().accountId)
    }

    @Test
    fun parseLsposedPaymentSuccessUiScreenText() {
        val parsed = PaymentMessageParser.parse(
            packageName = PaymentChannel.WECHAT.packageName,
            title = "微信支付",
            content = "支付成功 星巴克咖啡 ￥32.00 付款方式 零钱 完成",
            rules = rules,
            accounts = accounts,
            nowMillis = now,
        )
        assertNotNull(parsed)
        assertEquals(PaymentChannel.WECHAT, parsed.channel)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(3200L, parsed.amountCents)
        assertEquals("cat_food", parsed.suggestedCategoryId)
        assertEquals("acc_wechat", parsed.suggestedAccountId)
    }

    @Test
    fun fullBackupSnapshotRoundTripsServerConfig() {
        val bundle = BackupServerConfigBundle(
            webDav = WebDavConfig(
                enabled = true,
                serverUrl = "https://dav.jianguoyun.com/dav/",
                username = "tester@example.com",
                password = "secret-app-password",
                remoteDirectory = "MochiLedger",
            ),
            selfHosted = SelfHostedSyncConfig(
                enabled = true,
                serverUrl = "https://sync.ackd7.dpdns.org",
                authToken = "qq0759",
                ledgerId = "default",
            ),
            autoBackup = AutoBackupConfig(
                dailyFirstOpenEnabled = true,
                scheduledEnabled = true,
                intervalHours = 6,
                includeServerConfig = true,
            ),
        )
        val snapshot = LedgerSnapshot(
            exportedAt = now,
            accounts = accounts,
            serverConfig = bundle,
        )
        val json = LedgerJson.codec.encodeToString(snapshot)
        val decoded = LedgerJson.codec.decodeFromString<LedgerSnapshot>(json)

        assertNotNull(decoded.serverConfig)
        assertEquals("https://sync.ackd7.dpdns.org", decoded.serverConfig?.selfHosted?.serverUrl)
        assertEquals("qq0759", decoded.serverConfig?.selfHosted?.authToken)
        assertEquals("tester@example.com", decoded.serverConfig?.webDav?.username)
        assertTrue(decoded.serverConfig?.autoBackup?.dailyFirstOpenEnabled == true)
        assertEquals(6, decoded.serverConfig?.autoBackup?.intervalHours)
    }
}
