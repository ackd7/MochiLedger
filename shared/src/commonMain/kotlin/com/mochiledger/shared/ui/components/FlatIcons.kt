package com.mochiledger.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Official flat 24x24 brand vector glyphs (Simple Icons) for minimalist Miuix/Mochi wallet badges
private const val PATH_WECHAT =
    "M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm5.34 2.867c-1.797-.052-3.746.512-5.28 1.786-1.72 1.428-2.687 3.72-1.78 6.22.942 2.453 3.666 4.229 6.884 4.229.826 0 1.622-.12 2.361-.336a.722.722 0 0 1 .598.082l1.584.926a.272.272 0 0 0 .14.047c.134 0 .24-.111.24-.247 0-.06-.023-.12-.038-.177l-.327-1.233a.582.582 0 0 1-.023-.156.49.49 0 0 1 .201-.398C23.024 18.48 24 16.82 24 14.98c0-3.21-2.931-5.837-6.656-6.088V8.89c-.135-.01-.27-.027-.407-.03zm-2.53 3.274c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.97-.982zm4.844 0c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.969-.982z"

private const val PATH_ALIPAY =
    "M19.695 15.07c3.426 1.158 4.203 1.22 4.203 1.22V3.846c0-2.124-1.705-3.845-3.81-3.845H3.914C1.808.001.102 1.722.102 3.846v16.31c0 2.123 1.706 3.845 3.813 3.845h16.173c2.105 0 3.81-1.722 3.81-3.845v-.157s-6.19-2.602-9.315-4.119c-2.096 2.602-4.8 4.181-7.607 4.181-4.75 0-6.361-4.19-4.112-6.949.49-.602 1.324-1.175 2.617-1.497 2.025-.502 5.247.313 8.266 1.317a16.796 16.796 0 0 0 1.341-3.302H5.781v-.952h4.799V6.975H4.77v-.953h5.81V3.591s0-.409.411-.409h2.347v2.84h5.744v.951h-5.744v1.704h4.69a19.453 19.453 0 0 1-1.986 5.06c1.424.52 2.702 1.011 3.654 1.333m-13.81-2.032c-.596.06-1.71.325-2.321.869-1.83 1.608-.735 4.55 2.968 4.55 2.151 0 4.301-1.388 5.99-3.61-2.403-1.182-4.438-2.028-6.637-1.809"

private const val PATH_APPLE_PAY =
    "M2.15 4.318a42.16 42.16 0 0 0-.454.003c-.15.005-.303.013-.452.04a1.44 1.44 0 0 0-1.06.772c-.07.138-.114.278-.14.43-.028.148-.037.3-.04.45A10.2 10.2 0 0 0 0 6.222v11.557c0 .07.002.138.003.207.004.15.013.303.04.452.027.15.072.291.142.429a1.436 1.436 0 0 0 .63.63c.138.07.278.115.43.142.148.027.3.036.45.04l.208.003h20.194l.207-.003c.15-.004.303-.013.452-.04.15-.027.291-.071.428-.141a1.432 1.432 0 0 0 .631-.631c.07-.138.115-.278.141-.43.027-.148.036-.3.04-.45.002-.07.003-.138.003-.208l.001-.246V6.221c0-.07-.002-.138-.004-.207a2.995 2.995 0 0 0-.04-.452 1.446 1.446 0 0 0-1.2-1.201 3.022 3.022 0 0 0-.452-.04 10.448 10.448 0 0 0-.453-.003zm0 .512h19.942c.066 0 .131.002.197.003.115.004.25.01.375.032.109.02.2.05.287.094a.927.927 0 0 1 .407.407.997.997 0 0 1 .094.288c.022.123.028.258.031.374.002.065.003.13.003.197v11.552c0 .065 0 .13-.003.196-.003.115-.009.25-.032.375a.927.927 0 0 1-.5.693 1.002 1.002 0 0 1-.286.094 2.598 2.598 0 0 1-.373.032l-.2.003H1.906c-.066 0-.133-.002-.196-.003a2.61 2.61 0 0 1-.375-.032c-.109-.02-.2-.05-.288-.094a.918.918 0 0 1-.406-.407 1.006 1.006 0 0 1-.094-.288 2.531 2.531 0 0 1-.032-.373 9.588 9.588 0 0 1-.002-.197V6.224c0-.065 0-.131.002-.197.004-.114.01-.248.032-.375.02-.108.05-.199.094-.287a.925.925 0 0 1 .407-.406 1.03 1.03 0 0 1 .287-.094c.125-.022.26-.029.375-.032.065-.002.131-.002.196-.003zm4.71 3.7c-.3.016-.668.199-.88.456-.191.22-.36.58-.316.918.338.03.675-.169.888-.418.205-.258.345-.603.308-.955zm2.207.42v5.493h.852v-1.877h1.18c1.078 0 1.835-.739 1.835-1.812 0-1.07-.742-1.805-1.808-1.805zm.852.719h.982c.739 0 1.161.396 1.161 1.089 0 .692-.422 1.092-1.164 1.092h-.979zm-3.154.3c-.45.01-.83.28-1.05.28-.235 0-.593-.264-.981-.257a1.446 1.446 0 0 0-1.23.747c-.527.908-.139 2.255.374 2.995.249.366.549.769.944.754.373-.014.52-.242.973-.242.454 0 .586.242.98.235.41-.007.667-.366.915-.733.286-.417.403-.82.41-.841-.007-.008-.79-.308-.797-1.209-.008-.754.615-1.113.644-1.135-.352-.52-.9-.578-1.09-.593a1.123 1.123 0 0 0-.092-.002zm8.204.397c-.99 0-1.606.533-1.652 1.256h.777c.072-.358.369-.586.845-.586.502 0 .803.266.803.711v.309l-1.097.064c-.951.054-1.488.484-1.488 1.184 0 .72.548 1.207 1.332 1.207.526 0 1.032-.281 1.264-.727h.019v.659h.788v-2.76c0-.803-.62-1.317-1.591-1.317zm1.94.072l1.446 4.009c0 .003-.073.24-.073.247-.125.41-.33.571-.711.571-.069 0-.206 0-.267-.015v.666c.06.011.267.019.335.019.83 0 1.226-.312 1.568-1.283l1.5-4.214h-.868l-1.012 3.259h-.015l-1.013-3.26zm-1.167 2.189v.316c0 .521-.45.917-1.024.917-.442 0-.731-.228-.731-.579 0-.342.278-.56.769-.593z"

private const val PATH_VISA =
    "M9.112 8.262L5.97 15.758H3.92L2.374 9.775c-.094-.368-.175-.503-.461-.658C1.447 8.864.677 8.627 0 8.479l.046-.217h3.3a.904.904 0 01.894.764l.817 4.338 2.018-5.102zm8.033 5.049c.008-1.979-2.736-2.088-2.717-2.972.006-.269.262-.555.822-.628a3.66 3.66 0 011.913.336l.34-1.59a5.207 5.207 0 00-1.814-.333c-1.917 0-3.266 1.02-3.278 2.479-.012 1.079.963 1.68 1.698 2.04.756.367 1.01.603 1.006.931-.005.504-.602.725-1.16.734-.975.015-1.54-.263-1.992-.473l-.351 1.642c.453.208 1.289.39 2.156.398 2.037 0 3.37-1.006 3.377-2.564m5.061 2.447H24l-1.565-7.496h-1.656a.883.883 0 00-.826.55l-2.909 6.946h2.036l.405-1.12h2.488zm-2.163-2.656l1.02-2.815.588 2.815zm-8.16-4.84l-1.603 7.496H8.34l1.605-7.496z"

private fun buildBrandVector(name: String, pathData: String): ImageVector {
    val nodes = PathParser().parsePathString(pathData).toNodes()
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = nodes,
        fill = SolidColor(Color.Black),
    ).build()
}

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
    val brandPath = when (name) {
        "wechat" -> PATH_WECHAT
        "alipay" -> PATH_ALIPAY
        "applepay" -> PATH_APPLE_PAY
        "visa" -> PATH_VISA
        else -> null
    }
    if (brandPath != null) {
        val vector = remember(name) { buildBrandVector(name, brandPath) }
        Image(
            imageVector = vector,
            contentDescription = name,
            colorFilter = ColorFilter.tint(tint),
            modifier = modifier.size(size),
        )
        return
    }

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

            "wechat" -> {
                // WeChat signature overlapping dual chat bubbles
                drawOval(
                    color = tint,
                    topLeft = Offset(w * 0.10f, h * 0.18f),
                    size = Size(w * 0.56f, h * 0.44f),
                    style = stroke,
                )
                drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.29f, h * 0.37f))
                drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.47f, h * 0.37f))
                drawOval(
                    color = tint,
                    topLeft = Offset(w * 0.40f, h * 0.44f),
                    size = Size(w * 0.50f, h * 0.38f),
                    style = stroke,
                )
                drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.58f, h * 0.61f))
                drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.72f, h * 0.61f))
            }

            "alipay" -> {
                // Alipay signature rounded square badge with stylized "支" geometry
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.14f),
                    size = Size(w * 0.72f, h * 0.72f),
                    cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
                    style = stroke,
                )
                // Horizontal top bar + vertical stem
                drawLine(tint, Offset(w * 0.30f, h * 0.36f), Offset(w * 0.70f, h * 0.36f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.25f), Offset(w * 0.50f, h * 0.50f), stroke.width, StrokeCap.Round)
                // Crossed lower strokes resembling 支
                val p = Path().apply {
                    moveTo(w * 0.33f, h * 0.52f)
                    lineTo(w * 0.65f, h * 0.52f)
                    lineTo(w * 0.34f, h * 0.74f)
                }
                drawPath(p, tint, style = stroke)
                drawLine(tint, Offset(w * 0.42f, h * 0.56f), Offset(w * 0.72f, h * 0.74f), stroke.width, StrokeCap.Round)
            }

            "bank" -> {
                // Bank building: roof pediment + 3 pillars + base
                val roof = Path().apply {
                    moveTo(w * 0.14f, h * 0.36f)
                    lineTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.86f, h * 0.36f)
                    close()
                }
                drawPath(roof, tint, style = stroke)
                drawLine(tint, Offset(w * 0.26f, h * 0.44f), Offset(w * 0.26f, h * 0.74f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.44f), Offset(w * 0.50f, h * 0.74f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.74f, h * 0.44f), Offset(w * 0.74f, h * 0.74f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.14f, h * 0.82f), Offset(w * 0.86f, h * 0.82f), stroke.width, StrokeCap.Round)
            }

            "cash" -> {
                // Banknote with central currency circle
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.26f),
                    size = Size(w * 0.76f, h * 0.48f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke,
                )
                drawCircle(tint, radius = w * 0.13f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.24f, h * 0.50f))
                drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.76f, h * 0.50f))
            }

            "accounts", "wallet", "credit" -> {
                // Credit card / wallet icon with stripe and chip
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.22f),
                    size = Size(w * 0.76f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                    style = stroke,
                )
                drawLine(tint, Offset(w * 0.14f, h * 0.42f), Offset(w * 0.86f, h * 0.42f), stroke.width, StrokeCap.Round)
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.54f), Size(w * 0.18f, h * 0.12f), CornerRadius(w * 0.03f), style = stroke)
                drawCircle(tint, radius = w * 0.055f, center = Offset(w * 0.72f, h * 0.60f))
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

            "back" -> {
                // Clean flat left chevron back arrow
                val p = Path().apply {
                    moveTo(w * 0.62f, h * 0.22f)
                    lineTo(w * 0.34f, h * 0.50f)
                    lineTo(w * 0.62f, h * 0.78f)
                }
                drawPath(p, color = tint, style = stroke)
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
