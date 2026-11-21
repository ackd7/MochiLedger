package com.mochiledger.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Crisp, flat, cross-platform vector icons rendered with Compose Canvas so Windows and Android
 * share identical minimalist iconography without OS-dependent emoji rendering.
 */
@Composable
fun FlatIcon(
    name: String,
    tint: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = (w * 0.095f).coerceAtLeast(1.6.dp.toPx()),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        when (name) {
            "ledger", "transactions" -> {
                // Flat notebook / ledger icon
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.14f),
                    size = Size(w * 0.62f, h * 0.72f),
                    cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                    style = stroke,
                )
                drawLine(tint, Offset(w * 0.34f, h * 0.36f), Offset(w * 0.68f, h * 0.36f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.54f), Offset(w * 0.68f, h * 0.54f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.70f), Offset(w * 0.54f, h * 0.70f), stroke.width, StrokeCap.Round)
            }

            "analytics", "chart" -> {
                // Flat 3-bar analytics chart
                drawLine(tint, Offset(w * 0.14f, h * 0.84f), Offset(w * 0.86f, h * 0.84f), stroke.width, StrokeCap.Round)
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.50f), Size(w * 0.14f, h * 0.34f), CornerRadius(w * 0.04f), style = stroke)
                drawRoundRect(tint, Offset(w * 0.44f, h * 0.24f), Size(w * 0.14f, h * 0.60f), CornerRadius(w * 0.04f), style = stroke)
                drawRoundRect(tint, Offset(w * 0.66f, h * 0.38f), Size(w * 0.14f, h * 0.46f), CornerRadius(w * 0.04f), style = stroke)
            }

            "accounts", "wallet", "credit", "bank", "cash", "alipay", "wechat" -> {
                // Flat card / wallet icon
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.22f),
                    size = Size(w * 0.76f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                    style = stroke,
                )
                drawLine(tint, Offset(w * 0.14f, h * 0.42f), Offset(w * 0.86f, h * 0.42f), stroke.width, StrokeCap.Round)
                drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.70f, h * 0.60f))
            }

            "settings", "gear" -> {
                // Flat minimalist gear / slider-ring icon
                drawCircle(tint, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(tint, radius = w * 0.35f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            }

            "sync", "cloud" -> {
                // Flat circular sync arrows
                drawArc(
                    color = tint,
                    startAngle = -150f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 30f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = stroke,
                )
            }

            "bolt", "autotrack" -> {
                val p = Path().apply {
                    moveTo(w * 0.56f, h * 0.12f)
                    lineTo(w * 0.24f, h * 0.54f)
                    lineTo(w * 0.48f, h * 0.54f)
                    lineTo(w * 0.44f, h * 0.88f)
                    lineTo(w * 0.76f, h * 0.46f)
                    lineTo(w * 0.52f, h * 0.46f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            }

            "calendar" -> {
                drawRoundRect(tint, Offset(w * 0.15f, h * 0.22f), Size(w * 0.70f, h * 0.64f), CornerRadius(w * 0.12f), style = stroke)
                drawLine(tint, Offset(w * 0.16f, h * 0.42f), Offset(w * 0.84f, h * 0.42f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.14f), Offset(w * 0.34f, h * 0.28f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, h * 0.14f), Offset(w * 0.66f, h * 0.28f), stroke.width, StrokeCap.Round)
            }

            "plus" -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.80f), stroke.width * 1.15f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), stroke.width * 1.15f, StrokeCap.Round)
            }

            "minimize" -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.52f), Offset(w * 0.78f, h * 0.52f), stroke.width, StrokeCap.Round)
            }

            "maximize" -> {
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.22f), Size(w * 0.56f, h * 0.56f), CornerRadius(w * 0.10f), style = stroke)
            }

            "close" -> {
                drawLine(tint, Offset(w * 0.25f, h * 0.25f), Offset(w * 0.75f, h * 0.75f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.25f, h * 0.75f), stroke.width, StrokeCap.Round)
            }

            "food" -> {
                drawCircle(tint, radius = w * 0.28f, center = Offset(w * 0.50f, h * 0.54f), style = stroke)
                drawLine(tint, Offset(w * 0.32f, h * 0.20f), Offset(w * 0.32f, h * 0.34f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.32f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.20f), Offset(w * 0.68f, h * 0.34f), stroke.width, StrokeCap.Round)
            }

            "transport" -> {
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.30f), Size(w * 0.68f, h * 0.38f), CornerRadius(w * 0.12f), style = stroke)
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.32f, h * 0.74f), style = stroke)
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.74f), style = stroke)
            }

            "shopping" -> {
                drawRoundRect(tint, Offset(w * 0.20f, h * 0.32f), Size(w * 0.60f, h * 0.52f), CornerRadius(w * 0.10f), style = stroke)
                drawArc(tint, -180f, 180f, false, Offset(w * 0.34f, h * 0.16f), Size(w * 0.32f, h * 0.30f), style = stroke)
            }

            "home" -> {
                val p = Path().apply {
                    moveTo(w * 0.16f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.18f)
                    lineTo(w * 0.84f, h * 0.48f)
                }
                drawPath(p, tint, style = stroke)
                drawRoundRect(tint, Offset(w * 0.26f, h * 0.46f), Size(w * 0.48f, h * 0.38f), CornerRadius(w * 0.06f), style = stroke)
            }

            "salary", "invest" -> {
                val p = Path().apply {
                    moveTo(w * 0.16f, h * 0.72f)
                    lineTo(w * 0.40f, h * 0.46f)
                    lineTo(w * 0.58f, h * 0.58f)
                    lineTo(w * 0.84f, h * 0.26f)
                }
                drawPath(p, tint, style = stroke)
                drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.84f, h * 0.26f))
            }

            "transfer", "refund" -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.38f), Offset(w * 0.80f, h * 0.38f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, h * 0.24f), Offset(w * 0.80f, h * 0.38f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.80f, h * 0.62f), Offset(w * 0.20f, h * 0.62f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.76f), Offset(w * 0.20f, h * 0.62f), stroke.width, StrokeCap.Round)
            }

            else -> {
                // Clean flat tag / diamond icon
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.22f), Size(w * 0.56f, h * 0.56f), CornerRadius(w * 0.16f), style = stroke)
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.50f, h * 0.50f))
            }
        }
    }
}
