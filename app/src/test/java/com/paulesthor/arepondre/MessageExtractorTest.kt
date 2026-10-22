package com.paulesthor.arepondre

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageExtractorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun builder() = NotificationCompat.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_info)

    @Test
    fun messagingStyleKeepsOthersMessagesAndDropsMine() {
        val me = Person.Builder().setName("Moi").build()
        val lea = Person.Builder().setName("Léa").build()
        val n = builder()
            .setStyle(
                NotificationCompat.MessagingStyle(me)
                    .addMessage("Tu viens samedi ?", 1000, lea)
                    .addMessage("Oui !", 2000, me)
                    .addMessage("Et tu ramènes quoi ?", 3000, lea)
            )
            .build()

        val messages = MessageExtractor.extract("com.whatsapp", n, 5000)

        assertEquals(
            listOf(
                CapturedMessage("com.whatsapp", "Léa", "Léa", "Tu viens samedi ?", 1000),
                CapturedMessage("com.whatsapp", "Léa", "Léa", "Et tu ramènes quoi ?", 3000),
            ),
            messages,
        )
    }

    @Test
    fun groupConversationUsesGroupTitle() {
        val me = Person.Builder().setName("Moi").build()
        val tom = Person.Builder().setName("Tom").build()
        val n = builder()
            .setStyle(
                NotificationCompat.MessagingStyle(me)
                    .setConversationTitle("Coloc")
                    .setGroupConversation(true)
                    .addMessage("Qui paie le loyer ?", 1000, tom)
            )
            .build()

        val m = MessageExtractor.extract("com.whatsapp", n, 5000).single()
        assertEquals("Coloc", m.conversation)
        assertEquals("Tom", m.sender)
    }

    @Test
    fun plainNotificationWithMessageCategoryIsKept() {
        val n = builder()
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentTitle("julie_92")
            .setContentText("t'as vu ma story ?")
            .setWhen(4000)
            .build()

        assertEquals(
            listOf(CapturedMessage("com.instagram.android", "julie_92", "julie_92", "t'as vu ma story ?", 4000)),
            MessageExtractor.extract("com.instagram.android", n, 9000),
        )
    }

    @Test
    fun nonMessageNotificationsAreIgnored() {
        val like = builder().setContentTitle("Instagram").setContentText("julie_92 a aimé ta photo").build()
        val summary = builder()
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentTitle("WhatsApp").setContentText("5 messages de 3 discussions")
            .setGroup("g").setGroupSummary(true)
            .build()
        val ongoing = builder()
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentTitle("WhatsApp").setContentText("Recherche de nouveaux messages")
            .setOngoing(true)
            .build()

        for (n in listOf(like, summary, ongoing)) {
            assertTrue(MessageExtractor.extract("x", n, 0).isEmpty())
        }
    }
}
