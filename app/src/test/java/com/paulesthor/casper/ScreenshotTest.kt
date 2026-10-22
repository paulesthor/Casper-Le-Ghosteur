package com.paulesthor.casper

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Renders the real screens to PNG files to check the design without a phone.
 * Only runs with `./gradlew testDebugUnitTest -Pscreenshots`; images land in app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-w400dp-h868dp-xxhdpi")
class ScreenshotTest {

    private val out = File("build/screenshots").apply { mkdirs() }

    @Before
    fun seed() {
        assumeTrue(System.getProperty("screenshots") == "true")
        MessageStore.resetForTests()
        val now = System.currentTimeMillis()
        val h = 3_600_000L
        val store = MessageStore.get(ApplicationProvider.getApplicationContext())
        store.insert(
            listOf(
                CapturedMessage("com.google.android.dialer", "Maman", "Maman", MessageExtractor.MISSED_CALL_TEXT, now - 50 * h),
                CapturedMessage("com.google.android.dialer", "Maman", "Maman", MessageExtractor.MISSED_CALL_TEXT, now - 30 * h),
                CapturedMessage("com.whatsapp", "Coloc", "Tom", "Qui paie le loyer ce mois-ci ?", now - 26 * h),
                CapturedMessage("com.whatsapp", "Coloc", "Tom", "Faut que ce soit fait avant le 5", now - 25 * h),
                CapturedMessage("com.whatsapp", "Coloc", "Inès", "Moi je l'ai fait le mois dernier", now - 4 * h),
                CapturedMessage("com.whatsapp", "Coloc", "Tom", "Allo ?", now - 2 * h),
                CapturedMessage("com.instagram.android", "julie_92", "julie_92", "t'as vu ma story ?", now - 5 * h),
                CapturedMessage("com.whatsapp", "Léa", "Léa", "Tu viens samedi ? On part vers 20h", now - 3 * h - 5 * 60_000),
                CapturedMessage("com.facebook.orca", "Hugo Martin", "Hugo Martin", "Envoie-moi le doc quand tu peux", now - 40 * 60_000),
                CapturedMessage("com.google.android.apps.messaging", "06 12 34 56 78", "06 12 34 56 78", "Votre colis arrive demain entre 9h et 13h", now - 12 * 60_000),
            )
        )
        // A real-looking WhatsApp notification for Léa, so the quick reply is available.
        val replyAction = androidx.core.app.NotificationCompat.Action.Builder(
            0, "Répondre",
            android.app.PendingIntent.getBroadcast(
                ApplicationProvider.getApplicationContext(), 0, android.content.Intent("reply"),
                android.app.PendingIntent.FLAG_MUTABLE,
            ),
        ).addRemoteInput(androidx.core.app.RemoteInput.Builder("text").setLabel("Message").build()).build()
        val notification = androidx.core.app.NotificationCompat.Builder(ApplicationProvider.getApplicationContext(), "x")
            .setSmallIcon(android.R.drawable.ic_dialog_info).addAction(replyAction).build()
        ConversationOpener.rememberReply("com.whatsapp", "Léa", notification)

        store.saveRule(ReminderRule(person = "Maman", groups = setOf(AppGroup.WHATSAPP, AppGroup.SMS, AppGroup.CALLS), delayMinutes = 60, repeatMinutes = 60))
        store.saveRule(ReminderRule(person = "Coloc", groups = setOf(AppGroup.WHATSAPP), delayMinutes = 180))
        store.saveRule(ReminderRule(person = "Hugo Martin", groups = setOf(AppGroup.MESSENGER), delayMinutes = 30, enabled = false))
    }

    @Test
    fun light() = capture("light")

    @Test
    @Config(qualifiers = "+night")
    fun dark() = capture("dark")

    private fun capture(theme: String) {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settle()
        save(activity.window.decorView, "$theme-1-inbox")

        activity.findViewById<View>(R.id.tab_reminders).performClick()
        settle()
        save(activity.window.decorView, "$theme-2-reminders")

        activity.findViewById<View>(R.id.rules).let { (it as android.view.ViewGroup).getChildAt(0) }
            .findViewById<View>(R.id.card).performClick()
        settle()
        saveWithSheet(activity.window.decorView, ShadowDialog.getLatestDialog(), "$theme-3-rule")
        ShadowDialog.getLatestDialog().dismiss()

        activity.findViewById<View>(R.id.tab_inbox).performClick()
        settle()
        val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
        list.getChildAt(1).findViewById<View>(R.id.card).performClick()
        // No app installed in tests, so the conversation sheet opens instead.
        settle()
        saveWithSheet(activity.window.decorView, ShadowDialog.getLatestDialog(), "$theme-4-conversation")
        ShadowDialog.getLatestDialog().dismiss()

        // Léa is the 4th card; open her quick reply as a right swipe would.
        Sheets.showReply(activity, MessageStore.get(activity).pendingConversations()[3]) {}
        settle()
        saveWithSheet(activity.window.decorView, ShadowDialog.getLatestDialog(), "$theme-5-reply")
        ShadowDialog.getLatestDialog().dismiss()

        activity.findViewById<View>(R.id.tab_reminders).performClick()
        settle()
        activity.findViewById<View>(R.id.quiet_rule).findViewById<View>(R.id.card).performClick()
        settle()
        saveWithSheet(activity.window.decorView, ShadowDialog.getLatestDialog(), "$theme-6-quiet")
        ShadowDialog.getLatestDialog().dismiss()

        if (theme == "light") widgets(activity)
    }

    /** The widgets for 0, 3 and 10 people waiting, on a plain wallpaper. */
    private fun widgets(activity: android.app.Activity) {
        val density = activity.resources.displayMetrics.density
        val sheet = Bitmap.createBitmap((400 * density).toInt(), (560 * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF3A2F4F.toInt())
        val store = MessageStore.get(activity)
        fun draw(layout: Int, x: Float, y: Float, w: Int, h: Int) {
            val parent = android.widget.FrameLayout(activity)
            val view = CasperWidget.views(activity, layout).apply(activity, parent)
            parent.addView(view, android.widget.FrameLayout.LayoutParams((w * density).toInt(), (h * density).toInt()))
            parent.measure(
                View.MeasureSpec.makeMeasureSpec((w * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((h * density).toInt(), View.MeasureSpec.EXACTLY),
            )
            parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
            canvas.save()
            canvas.translate(x * density, y * density)
            parent.draw(canvas)
            canvas.restore()
        }
        val pending = store.pendingConversations()
        pending.forEach { store.markHandled(it.app, it.conversation) }
        draw(R.layout.widget_small, 20f, 20f, 170, 170)
        val now = System.currentTimeMillis()
        store.insert((1..3).map { CapturedMessage("com.whatsapp", "Ami $it", "Ami $it", "yo", now - it * 3_600_000L) })
        draw(R.layout.widget_small, 210f, 20f, 170, 170)
        store.insert((4..10).map { CapturedMessage("com.whatsapp", "Ami $it", "Ami $it", "yo", now - it * 3_600_000L) })
        draw(R.layout.widget_wide, 20f, 210f, 360, 170)
        write(sheet, "widgets")
    }

    private fun settle() {
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
    }

    private fun render(view: View): Bitmap {
        val w = view.width
        val h = view.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun save(view: View, name: String) = write(render(view), name)

    private fun saveWithSheet(screen: View, dialog: Dialog, name: String) {
        val base = render(screen)
        val canvas = Canvas(base)
        canvas.drawColor(RuntimeEnvironment.getApplication().getColor(R.color.scrim))
        val sheet = dialog.window!!.decorView
        val sheetBitmap = render(sheet)
        canvas.drawBitmap(sheetBitmap, 0f, (base.height - sheetBitmap.height).toFloat(), null)
        write(base, name)
    }

    private fun write(bitmap: Bitmap, name: String) {
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
