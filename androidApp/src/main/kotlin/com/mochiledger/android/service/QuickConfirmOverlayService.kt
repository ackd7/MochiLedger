package com.mochiledger.android.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mochiledger.android.MochiApplication
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.formatCents

/**
 * Lightweight floating confirmation banner shown after auto-recording a transaction.
 * Allows 1-tap scenario tagging ("早餐", "午饭", "超市", etc.) or typing a quick note without leaving the current app.
 * Works on both rooted (LSPosed) and non-root (NotificationListener) Android devices.
 */
class QuickConfirmOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var remainingSeconds: Int = 6
    private var paused: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val txId = intent?.getStringExtra(EXTRA_TX_ID).orEmpty()
        val timeoutSec = intent?.getIntExtra(EXTRA_TIMEOUT_SEC, 6)?.coerceIn(3, 30) ?: 6
        if (txId.isBlank() || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val repo = MochiApplication.instance.repository
        val tx = repo.state.value.transactions.firstOrNull { it.id == txId && !it.isDeleted }
        if (tx == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        removeOverlay()
        showOverlayForTransaction(tx, timeoutSec)
        return START_NOT_STICKY
    }

    private fun showOverlayForTransaction(tx: LedgerTransaction, timeoutSec: Int) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            stopSelf()
            return
        }
        windowManager = wm
        val repo = MochiApplication.instance.repository
        val categoryName = repo.state.value.categories.firstOrNull { it.id == tx.categoryId }?.name ?: "其他"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F21C2230"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#447A8CFF"))
            }
            elevation = dp(12).toFloat()
        }

        // Top row: badge + amount + countdown
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val headerText = TextView(this).apply {
            text = "墨记已记一笔 · $categoryName"
            setTextColor(Color.parseColor("#A9B6FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        }
        val merchantText = TextView(this).apply {
            val prefix = if (tx.type == TransactionType.INCOME) "+" else "-"
            text = "$prefix${formatCents(tx.amountCents)}  ${tx.merchant.ifBlank { "扫码消费" }}"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
        }
        titleCol.addView(headerText)
        titleCol.addView(merchantText)

        val countdownView = TextView(this).apply {
            text = "${timeoutSec}s"
            setTextColor(Color.parseColor("#9AA4B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener { dismissAndStop() }
        }
        topRow.addView(titleCol)
        topRow.addView(countdownView)
        topRow.addView(closeBtn)
        root.addView(topRow)

        // Quick scenario chips (1-tap tagging for street vendors / QR payments)
        val quickTags = listOf(
            "早餐" to "cat_food",
            "午饭" to "cat_food",
            "晚饭" to "cat_food",
            "买菜" to "cat_food",
            "咖啡奶茶" to "cat_food",
            "超市日用" to "cat_shopping",
            "交通出行" to "cat_transport",
            "休闲娱乐" to "cat_entertainment",
        )
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickTags.forEach { (label, catId) ->
            val chip = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(11), dp(6), dp(11), dp(6))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2E3750"))
                    cornerRadius = dp(14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { rightMargin = dp(6) }
                setOnClickListener {
                    val newNote = if (tx.merchant.isNotBlank()) "$label · ${tx.merchant}" else label
                    repo.upsertTransaction(
                        tx.copy(
                            categoryId = if (tx.type == TransactionType.EXPENSE) catId else tx.categoryId,
                            note = newNote,
                        ),
                    )
                    Toast.makeText(this@QuickConfirmOverlayService, "已备注：$label", Toast.LENGTH_SHORT).show()
                    dismissAndStop()
                }
            }
            chipRow.addView(chip)
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        // Bottom row: custom note input + delete + save
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }

        val noteInput = EditText(this).apply {
            hint = "补充买了什么..."
            setText(tx.note)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8892A6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#262E42"))
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    paused = true
                    countdownView.text = "编辑中"
                }
            }
            setOnClickListener {
                paused = true
                countdownView.text = "编辑中"
            }
        }

        val deleteBtn = TextView(this).apply {
            text = "忽略"
            setTextColor(Color.parseColor("#FF7A7A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FF5A5A"))
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = dp(6) }
            setOnClickListener {
                repo.deleteTransaction(tx.id)
                Toast.makeText(this@QuickConfirmOverlayService, "已忽略该笔自动记账", Toast.LENGTH_SHORT).show()
                dismissAndStop()
            }
        }

        val saveBtn = TextView(this).apply {
            text = "保存"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4F6EF7"))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener {
                val customNote = noteInput.text?.toString()?.trim().orEmpty()
                if (customNote != tx.note) {
                    repo.upsertTransaction(tx.copy(note = customNote))
                }
                Toast.makeText(this@QuickConfirmOverlayService, "已保存备注", Toast.LENGTH_SHORT).show()
                dismissAndStop()
            }
        }

        bottomRow.addView(noteInput)
        bottomRow.addView(deleteBtn)
        bottomRow.addView(saveBtn)
        root.addView(bottomRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(44)
            width = resources.displayMetrics.widthPixels - dp(24)
        }

        runCatching {
            wm.addView(root, params)
            overlayView = root
            startCountdown(timeoutSec, countdownView)
        }.onFailure {
            stopSelf()
        }
    }

    private fun startCountdown(seconds: Int, countdownView: TextView) {
        remainingSeconds = seconds
        paused = false
        val runnable = object : Runnable {
            override fun run() {
                if (paused) return
                remainingSeconds--
                if (remainingSeconds <= 0) {
                    dismissAndStop()
                } else {
                    countdownView.text = "${remainingSeconds}s"
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        countdownRunnable = runnable
        handler.postDelayed(runnable, 1000L)
    }

    private fun dismissAndStop() {
        removeOverlay()
        stopSelf()
    }

    private fun removeOverlay() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }

    companion object {
        const val EXTRA_TX_ID = "extra_tx_id"
        const val EXTRA_TIMEOUT_SEC = "extra_timeout_sec"

        fun show(context: Context, txId: String, timeoutSec: Int) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, QuickConfirmOverlayService::class.java).apply {
                putExtra(EXTRA_TX_ID, txId)
                putExtra(EXTRA_TIMEOUT_SEC, timeoutSec)
            }
            runCatching { context.startService(intent) }
        }
    }
}
