package com.paulesthor.casper

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * "Liquid glass" panel: a translucent fill that is lighter at the top, and a hairline
 * edge that catches the light on the top-left and fades out towards the bottom-right.
 */
class GlassDrawable(context: Context, radiusDp: Float, private val sheet: Boolean = false) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val radius = radiusDp * density
    private val top = context.getColor(if (sheet) R.color.sheet_top else R.color.glass_top)
    private val bottom = context.getColor(if (sheet) R.color.sheet_bottom else R.color.glass_bottom)
    private val edgeTop = context.getColor(R.color.edge_top)
    private val edgeBottom = context.getColor(R.color.edge_bottom)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val rect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        rect.inset(density / 2, density / 2)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        fill.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(), bounds.left + w * 0.35f, bounds.top + h,
            intArrayOf(top, bottom, bottom), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        val clear = edgeTop and 0x00FFFFFF
        edge.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
            intArrayOf(edgeTop, clear, clear, edgeBottom), floatArrayOf(0f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, edge)
    }

    override fun getOutline(outline: android.graphics.Outline) {
        outline.setRoundRect(bounds, radius)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        edge.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        edge.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
