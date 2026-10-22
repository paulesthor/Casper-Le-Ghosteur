package com.paulesthor.casper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * Soft colored blobs drifting slowly behind the content. Each blob is a radial
 * gradient fading to transparent, so it looks blurred without any blur pass.
 */
class BlobBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private class Blob(
        val color: Int,
        /** Center and radius as fractions of the view width/height. */
        val x: Float, val y: Float, val radius: Float,
        /** How far it drifts, as fractions of the view size. */
        val dx: Float, val dy: Float,
        val periodSeconds: Float,
        val phase: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix()
    }

    private val blobs = listOf(
        Blob(context.getColor(R.color.blob_a), 0.20f, 0.18f, 0.75f, 0.25f, 0.10f, 28f, 0f),
        Blob(context.getColor(R.color.blob_b), 0.90f, 0.12f, 0.60f, -0.30f, 0.14f, 34f, 1.3f),
        Blob(context.getColor(R.color.blob_c), 0.70f, 0.78f, 0.85f, -0.20f, -0.12f, 40f, 2.1f),
        Blob(context.getColor(R.color.blob_d), 0.05f, 0.95f, 0.50f, 0.25f, -0.08f, 36f, 0.7f),
    )
    private val background = context.getColor(R.color.bg)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        for (blob in blobs) {
            val r = blob.radius * w
            val transparent = blob.color and 0x00FFFFFF
            val half = (blob.color ushr 24) / 2 shl 24 or transparent
            blob.paint.shader = RadialGradient(
                0f, 0f, r,
                intArrayOf(blob.color, half, transparent),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(background)
        val t = SystemClock.uptimeMillis() / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        for (blob in blobs) {
            // Smooth back-and-forth drift, each blob on its own period.
            val p = (sin(2 * PI * t / blob.periodSeconds + blob.phase)).toFloat()
            val cx = (blob.x + blob.dx * p) * w
            val cy = (blob.y + blob.dy * p) * h
            blob.matrix.setTranslate(cx, cy)
            blob.paint.shader?.setLocalMatrix(blob.matrix)
            canvas.drawCircle(cx, cy, blob.radius * w, blob.paint)
        }
        // The motion is slow, so ~20 frames per second is plenty and spares the battery.
        if (isShown) postInvalidateDelayed(FRAME_MS)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) invalidate()
    }

    private companion object {
        const val FRAME_MS = 50L
    }
}
