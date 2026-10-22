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
        val list = activity.findViewById<android.widget.ListView>(R.id.list)
        list.performItemClick(list.getChildAt(1), 1, 1)
        // No app installed in tests, so the conversation sheet opens instead.
        settle()
        saveWithSheet(activity.window.decorView, ShadowDialog.getLatestDialog(), "$theme-4-conversation")
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
