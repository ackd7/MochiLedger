package com.mochiledger.shared

import com.mochiledger.shared.model.AccountType
import com.mochiledger.shared.model.AutoBackupConfig
import com.mochiledger.shared.model.BackupServerConfigBundle
import com.mochiledger.shared.model.DefaultLedgerSeed
import com.mochiledger.shared.model.LedgerAccount
import com.mochiledger.shared.model.LedgerSnapshot
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.model.RecordSource
import com.mochiledger.shared.model.SelfHostedSyncConfig
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.WebDavConfig
import com.mochiledger.shared.parser.BillStatementImporter
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

        val cfg = assertNotNull(decoded.serverConfig)
        assertEquals("https://sync.ackd7.dpdns.org", cfg.selfHosted.serverUrl)
        assertEquals("qq0759", cfg.selfHosted.authToken)
        assertEquals("tester@example.com", cfg.webDav.username)
        assertTrue(cfg.autoBackup.dailyFirstOpenEnabled)
        assertEquals(6, cfg.autoBackup.intervalHours)
    }

    @Test
    fun parseLsposedStructuredMerchantAndProductDetail() {
        val parsed = PaymentMessageParser.parse(
            packageName = PaymentChannel.WECHAT.packageName,
            title = "微信支付",
            content = "商户：瑞幸咖啡 商品：生椰拿铁大杯 付款方式：零钱 支付成功 ￥19.90",
            rules = rules,
            accounts = accounts,
            nowMillis = now,
        )
        assertNotNull(parsed)
        assertEquals("瑞幸咖啡", parsed.merchant)
        assertEquals(1990L, parsed.amountCents)
        assertTrue(parsed.note.contains("生椰拿铁大杯"))
        assertEquals("cat_food", parsed.suggestedCategoryId)
    }

    @Test
    fun billStatementCsvEnrichesExistingAndImportsMissing() {
        val csv = """
            微信支付账单明细
            ----------------------
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2026-11-22 12:15:10,商户消费,美团外卖,"黄焖鸡米饭套餐",支出,¥28.50,零钱,支付成功,1001,2001,/
            2026-11-22 18:30:00,商户消费,京东自营,"Type-C快充数据线",支出,¥39.90,零钱,支付成功,1002,2002,/
        """.trimIndent()

        // First parse to obtain exact local-timezone timestamp of row 1
        val (_, parsedRows) = BillStatementImporter.parseCsv(csv)
        assertEquals(2, parsedRows.size)

        // Simulate an earlier auto-tracked record with generic merchant & empty detail (12s apart)
        val existingAutoTx = LedgerTransaction(
            id = "tx_auto_1",
            type = TransactionType.EXPENSE,
            amountCents = 2850L,
            categoryId = "cat_other_exp",
            accountId = "acc_wechat",
            timestamp = parsedRows[0].timestamp + 12_000L,
            merchant = "微信支付",
            note = "微信支付",
            updatedAt = now,
        )

        val res = BillStatementImporter.applyImportAndEnrich(
            csvText = csv,
            existingTransactions = listOf(existingAutoTx),
            accounts = accounts,
            rules = rules,
            importMissing = true,
            nowMillis = now + 5000L,
        )

        assertTrue(res.success)
        assertEquals(1, res.enrichedCount)
        assertEquals(1, res.importedCount)
        assertEquals(2, res.updatedTransactions.size)

        val enriched = res.updatedTransactions.first { it.id == "tx_auto_1" }
        assertEquals("美团外卖", enriched.merchant)
        assertTrue(enriched.note.contains("黄焖鸡米饭套餐"))
        assertEquals("cat_food", enriched.categoryId)

        val imported = res.updatedTransactions.first { it.id != "tx_auto_1" }
        assertEquals("京东自营", imported.merchant)
        assertTrue(imported.note.contains("Type-C快充数据线"))
        assertEquals("cat_shopping", imported.categoryId)
    }

    @Test
    fun multiAppNotificationsParseMeituanJdAndBlockPromosOrChats() {
        val meituan = PaymentMessageParser.parse(
            packageName = "com.sankuai.meituan",
            title = "美团外卖",
            content = "订单支付成功：店铺：肯德基宅急送 商品：香辣鸡腿堡套餐 实付29.90元",
            rules = rules,
            accounts = accounts,
            source = RecordSource.NOTIFICATION_LISTENER,
            nowMillis = now,
        )
        assertNotNull(meituan)
        assertEquals(PaymentChannel.MEITUAN, meituan.channel)
        assertEquals(2990L, meituan.amountCents)
        assertEquals("肯德基宅急送", meituan.merchant)
        assertEquals("cat_food", meituan.suggestedCategoryId)

        val jd = PaymentMessageParser.parse(
            packageName = "com.jingdong.app.mall",
            title = "京东",
            content = "支付成功：购买商品：Type-C快充数据线 实付款39.90元",
            rules = rules,
            accounts = accounts,
            source = RecordSource.NOTIFICATION_LISTENER,
            nowMillis = now + 2000,
        )
        assertNotNull(jd)
        assertEquals(PaymentChannel.JINGDONG, jd.channel)
        assertEquals(3990L, jd.amountCents)
        assertEquals("cat_shopping", jd.suggestedCategoryId)

        // Promo coupon notification must be ignored
        val promo = PaymentMessageParser.parse(
            packageName = "com.sankuai.meituan",
            title = "美团神券",
            content = "神券膨胀至18.00元，快去下单支付吧",
            rules = rules,
            accounts = accounts,
            source = RecordSource.NOTIFICATION_LISTENER,
            nowMillis = now,
        )
        assertNull(promo)

        // Real paid order mentioning discount/满减 must NOT be falsely blocked
        val paidWithDiscount = PaymentMessageParser.parse(
            packageName = "com.sankuai.meituan",
            title = "美团外卖",
            content = "订单支付成功：店铺：麦当劳 实付25.90元（已享满减6元）",
            rules = rules,
            accounts = accounts,
            source = RecordSource.NOTIFICATION_LISTENER,
            nowMillis = now + 4000,
        )
        assertNotNull(paidWithDiscount)
        assertEquals(2590L, paidWithDiscount.amountCents)
        assertEquals("麦当劳", paidWithDiscount.merchant)

        // Personal friend chat in WeChat must be ignored
        val friendChat = PaymentMessageParser.parse(
            packageName = PaymentChannel.WECHAT.packageName,
            title = "张三",
            content = "我刚向老板付款29.90元",
            rules = rules,
            accounts = accounts,
            source = RecordSource.NOTIFICATION_LISTENER,
            nowMillis = now,
        )
        assertNull(friendChat)

        // Cross-app deduplication: WeChat Pay ¥29.90 + Meituan ¥29.90 within 20s must deduplicate
        assertTrue(
            PaymentMessageParser.isDuplicate(
                candidate = meituan,
                recentType = TransactionType.EXPENSE,
                recentAmountCents = 2990L,
                recentTimestamp = now - 3000L,
                dedupeWindowSeconds = 20,
            ),
        )
    }
}
