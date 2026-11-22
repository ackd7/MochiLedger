package com.mochiledger.android.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
 * Lightweight floating confirmation banner attached directly via applicationContext WindowManager.
 *
 * Advantages over a background Service:
 * 1. Immune to Android 8+ (API 26+) BackgroundServiceStartNotAllowedException when triggered from
 *    BroadcastReceiver while MochiLedger is in the background.
 * 2. Starts with FLAG_NOT_FOCUSABLE so it never blocks WeChat/Alipay Back gestures; dynamically
 *    acquires focus & opens IME only if the user taps the custom note EditText.
 */
object QuickConfirmOverlayManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var countdownRunnable: Runnable? = null
    private var remainingSeconds: Int = 6
    private var paused: Boolean = false

    fun show(context: Context, txId: String, timeoutSec: Int) {
        val appCtx = context.applicationContext
        if (txId.isBlank() || !Settings.canDrawOverlays(appCtx)) return

        mainHandler.post {
            val repo = MochiApplication.instance.repository
            val tx = repo.state.value.transactions.firstOrNull { it.id == txId && !it.isDeleted } ?: return@post
            dismissInternal()
            showInternal(appCtx, tx, timeoutSec.coerceIn(3, 30))
        }
    }

    private fun showInternal(ctx: Context, tx: LedgerTransaction, timeoutSec: Int) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm
        val repo = MochiApplication.instance.repository
        val categoryName = repo.state.value.categories.firstOrNull { it.id == tx.categoryId }?.name ?: "其他"

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F21C2230"))
                cornerRadius = dp(ctx, 20).toFloat()
                setStroke(dp(ctx, 1), Color.parseColor("#447A8CFF"))
            }
            elevation = dp(ctx, 12).toFloat()
        }

        // Top row: badge + amount + countdown + close
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val headerText = TextView(ctx).apply {
            text = "墨记已记一笔 · $categoryName"
            setTextColor(Color.parseColor("#A9B6FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        }
        val merchantText = TextView(ctx).apply {
            val prefix = if (tx.type == TransactionType.INCOME) "+" else "-"
            text = "$prefix${formatCents(tx.amountCents)}  ${tx.merchant.ifBlank { "扫码消费" }}"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
        }
        titleCol.addView(headerText)
        titleCol.addView(merchantText)

        val countdownView = TextView(ctx).apply {
            text = "${timeoutSec}s"
            setTextColor(Color.parseColor("#9AA4B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4))
        }
        val closeBtn = TextView(ctx).apply {
            text = "✕"
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4))
            setOnClickListener { dismissInternal() }
        }
        topRow.addView(titleCol)
        topRow.addView(countdownView)
        topRow.addView(closeBtn)
        root.addView(topRow)

        // Quick scenario tags (1-tap tagging for street vendors / QR payments)
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
        val chipScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(ctx, 10) }
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickTags.forEach { (label, catId) ->
            val chip = TextView(ctx).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(ctx, 11), dp(ctx, 6), dp(ctx, 11), dp(ctx, 6))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2E3750"))
                    cornerRadius = dp(ctx, 14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { rightMargin = dp(ctx, 6) }
                setOnClickListener {
                    val newNote = if (tx.merchant.isNotBlank()) "$label · ${tx.merchant}" else label
                    repo.upsertTransaction(
                        tx.copy(
                            categoryId = if (tx.type == TransactionType.EXPENSE) catId else tx.categoryId,
                            note = newNote,
                        ),
                    )
                    Toast.makeText(ctx, "已备注：$label", Toast.LENGTH_SHORT).show()
                    dismissInternal()
                }
            }
            chipRow.addView(chip)
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        // Bottom row: custom note input + ignore + save
        val bottomRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(ctx, 10) }
        }

        val noteInput = EditText(ctx).apply {
            hint = "补充买了什么..."
            setText(tx.note)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8892A6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true
            setPadding(dp(ctx, 10), dp(ctx, 7), dp(ctx, 10), dp(ctx, 7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#262E42"))
                cornerRadius = dp(ctx, 10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(ctx, 8)
            }
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    paused = true
                    countdownView.text = "编辑中"
                    enableOverlayKeyboardFocus(ctx, v)
                }
                false
            }
        }

        val deleteBtn = TextView(ctx).apply {
            text = "忽略"
            setTextColor(Color.parseColor("#FF7A7A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FF5A5A"))
                cornerRadius = dp(ctx, 10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = dp(ctx, 6) }
            setOnClickListener {
                repo.deleteTransaction(tx.id)
                Toast.makeText(ctx, "已忽略该笔自动记账", Toast.LENGTH_SHORT).show()
                dismissInternal()
            }
        }

        val saveBtn = TextView(ctx).apply {
            text = "保存"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4F6EF7"))
                cornerRadius = dp(ctx, 10).toFloat()
            }
            setOnClickListener {
                val customNote = noteInput.text?.toString()?.trim().orEmpty()
                if (customNote != tx.note) {
                    repo.upsertTransaction(tx.copy(note = customNote))
                }
                Toast.makeText(ctx, "已保存备注", Toast.LENGTH_SHORT).show()
                dismissInternal()
            }
        }

        bottomRow.addView(noteInput)
        bottomRow.addView(deleteBtn)
        bottomRow.addView(saveBtn)
        root.addView(bottomRow)

        val params = WindowManager.LayoutParams(
            ctx.resources.displayMetrics.widthPixels - dp(ctx, 24),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(ctx, 44)
        }

        runCatching {
            wm.addView(root, params)
            overlayView = root
            layoutParams = params
            startCountdown(timeoutSec, countdownView)
        }
    }

    private fun enableOverlayKeyboardFocus(ctx: Context, targetView: View) {
        val wm = windowManager ?: return
        val root = overlayView ?: return
        val params = layoutParams ?: return
        if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            runCatching { wm.updateViewLayout(root, params) }
        }
        targetView.requestFocus()
        mainHandler.postDelayed({
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(targetView, InputMethodManager.SHOW_IMPLICIT)
        }, 120L)
    }

    private fun startCountdown(seconds: Int, countdownView: TextView) {
        remainingSeconds = seconds
        paused = false
        val runnable = object : Runnable {
            override fun run() {
                if (paused) return
                remainingSeconds--
                if (remainingSeconds <= 0) {
                    dismissInternal()
                } else {
                    countdownView.text = "${remainingSeconds}s"
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
        countdownRunnable = runnable
        mainHandler.postDelayed(runnable, 1000L)
    }

    private fun dismissInternal() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        layoutParams = null
    }

    private fun dp(ctx: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics,
        ).toInt()
    }
}
