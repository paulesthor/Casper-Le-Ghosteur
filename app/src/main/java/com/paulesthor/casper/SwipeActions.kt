package com.paulesthor.casper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Swipe a conversation left to ignore it, right to answer it. While a card moves, it
 * turns opaque so the colored action underneath only shows in the uncovered part.
 */
class SwipeActions(
    private val context: Context,
    private val onIgnore: (position: Int) -> Unit,
    private val onReply: (position: Int) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val radius = context.dp(26f).toFloat()
    private val inset = context.dp(5f).toFloat()
    private val margin = context.dp(24f)
    private val ignoreColor = context.getColor(R.color.swipe_ignore)
    private val replyColor = context.getColor(R.color.accent)
    private val ignoreIcon = context.getDrawable(R.drawable.ic_eye_off)!!
    private val replyIcon = context.getDrawable(R.drawable.ic_reply)!!.mutate().apply { setTint(context.getColor(R.color.on_accent)) }
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15 * context.resources.displayMetrics.scaledDensity
        typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
    }
    private val rect = RectF()

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.35f

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val view = viewHolder.itemView
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        )
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        if (direction == ItemTouchHelper.LEFT) onIgnore(position) else onReply(position)
    }

    override fun onChildDraw(
        c: Canvas, rv: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean,
    ) {
        val view = viewHolder.itemView
        setOpaque(view, dX != 0f)

        if (dX != 0f) {
            val ignoring = dX < 0
            rect.set(view.left.toFloat(), view.top + inset, view.right.toFloat(), view.bottom - inset)
            background.color = if (ignoring) ignoreColor else replyColor
            c.drawRoundRect(rect, radius, radius, background)

            val icon = if (ignoring) ignoreIcon else replyIcon
            val text = if (ignoring) "Ignorer" else "Répondre"
            label.color = if (ignoring) 0xFFFFFFFF.toInt() else context.getColor(R.color.on_accent)
            val size = icon.intrinsicWidth
            val iconTop = (rect.centerY() - size / 2).toInt()
            val textWidth = label.measureText(text)
            val baseline = rect.centerY() - (label.descent() + label.ascent()) / 2
            val gap = context.dp(8f)
            if (ignoring) {
                val iconLeft = view.right - margin - size
                icon.setBounds(iconLeft, iconTop, iconLeft + size, iconTop + size)
                c.drawText(text, iconLeft - gap - textWidth, baseline, label)
            } else {
                val iconLeft = view.left + margin
                icon.setBounds(iconLeft, iconTop, iconLeft + size, iconTop + size)
                c.drawText(text, (iconLeft + size + gap).toFloat(), baseline, label)
            }
            icon.draw(c)
        }
        super.onChildDraw(c, rv, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        setOpaque(viewHolder.itemView, false)
    }

    private fun setOpaque(item: View, opaque: Boolean) {
        val card = item.findViewById<View>(R.id.card)
        if (card.getTag(R.id.card) == opaque) return
        card.setTag(R.id.card, opaque)
        card.background = GlassDrawable(context, 26f, sheet = opaque)
    }
}
