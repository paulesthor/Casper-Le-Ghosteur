package com.paulesthor.casper

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

fun initialOf(name: String): String {
    val first = name.firstOrNull { it.isLetterOrDigit() || it == '+' } ?: return "?"
    return if (first.isDigit() || first == '+') "#" else first.uppercase()
}

/** Fills an item_avatar: the person's initial and a small colored badge for the app. */
fun View.bindAvatar(name: String, group: AppGroup?) {
    findViewById<TextView>(R.id.avatar).text = initialOf(name)
    val badge = findViewById<ImageView>(R.id.app_badge)
    if (group == null) {
        badge.visibility = View.GONE
        return
    }
    badge.visibility = View.VISIBLE
    badge.setImageResource(
        when (group) {
            AppGroup.CALLS -> R.drawable.ic_app_phone
            AppGroup.INSTAGRAM -> R.drawable.ic_app_camera
            else -> R.drawable.ic_app_bubble
        }
    )
    badge.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(group.color)
        setStroke(context.dp(2f), context.getColor(R.color.bg))
    }
}

/** Waiting time colored by urgency: neutral, then amber after 3 h, red after a day. */
fun TextView.showWaiting(since: Long) {
    val waited = System.currentTimeMillis() - since
    text = Reminders.formatWait(waited)
    setTextColor(
        context.getColor(
            when {
                waited >= DateUtils.DAY_IN_MILLIS -> R.color.urgent
                waited >= 3 * DateUtils.HOUR_IN_MILLIS -> R.color.warning
                else -> R.color.text_secondary
            }
        )
    )
}

/** A glass panel that slides up from the bottom of the screen. */
fun Context.bottomSheet(layout: Int): Dialog {
    val dialog = Dialog(this, R.style.Theme_Casper_Sheet)
    dialog.setContentView(layout)
    val content = dialog.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    content.background = GlassDrawable(this, 32f, sheet = true)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.BOTTOM)
        decorView.setPadding(dp(8f), 0, dp(8f), dp(8f))
        setDimAmount(0.45f)
        // Android 12+: blur what's behind the sheet, like frosted glass.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            attributes = attributes.apply { blurBehindRadius = dp(12f) }
        }
    }
    return dialog
}

/** Keeps a sheet's scrolling part from pushing its buttons off screen. */
fun View.limitHeight(maxDp: Float) {
    post {
        val max = context.dp(maxDp)
        if (height > max) layoutParams = layoutParams.apply { height = max }
    }
}
