package com.paulesthor.casper

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageStoreTest {

    private val store = MessageStore(ApplicationProvider.getApplicationContext())

    @Test
    fun repostedMessagesAreStoredOnceAndGroupedByConversation() {
        val first = CapturedMessage("com.whatsapp", "Léa", "Léa", "Tu viens samedi ?", 1000)
        val second = CapturedMessage("com.whatsapp", "Léa", "Léa", "Et tu ramènes quoi ?", 3000)
        val other = CapturedMessage("com.instagram.android", "julie_92", "julie_92", "coucou", 2000)

        assertEquals(1, store.insert(listOf(first)))
        assertEquals(2, store.insert(listOf(first, second, other)))

        val pending = store.pendingConversations()
        assertEquals(listOf("Léa", "julie_92"), pending.map { it.conversation })
        assertEquals(2, pending[0].count)
        assertEquals("Et tu ramènes quoi ?", pending[0].lastText)

        store.markHandled("com.whatsapp", "Léa")
        assertEquals(listOf("julie_92"), store.pendingConversations().map { it.conversation })
    }

    @Test
    fun longestWaitingConversationComesFirst() {
        store.insert(
            listOf(
                CapturedMessage("com.whatsapp", "Tom", "Tom", "ancien", 1000),
                CapturedMessage("com.instagram.android", "julie_92", "julie_92", "milieu", 2000),
                CapturedMessage("com.whatsapp", "Tom", "Tom", "récent", 9000),
            )
        )

        val pending = store.pendingConversations()
        assertEquals(listOf("Tom", "julie_92"), pending.map { it.conversation })
        assertEquals(1000, pending[0].firstTimestamp)
        assertEquals(9000, pending[0].lastTimestamp)
        assertEquals("récent", pending[0].lastText)
    }

    @Test
    fun handlingUpToAReplyKeepsLaterMessagesPending() {
        store.insert(
            listOf(
                CapturedMessage("com.whatsapp", "Léa", "Léa", "avant", 1000),
                CapturedMessage("com.whatsapp", "Léa", "Léa", "après", 3000),
            )
        )

        assertEquals(1, store.markHandled("com.whatsapp", "Léa", upTo = 2000))
        assertEquals(listOf("après"), store.pendingMessages("com.whatsapp", "Léa").map { it.text })
    }

    @Test
    fun rulesAreSavedAndReadBack() {
        val id = store.saveRule(ReminderRule(person = "Maman", groups = setOf(AppGroup.SMS, AppGroup.CALLS), delayMinutes = 60, repeatMinutes = 60))
        assertEquals(
            listOf(ReminderRule(id, "Maman", setOf(AppGroup.SMS, AppGroup.CALLS), 60, 60, true)),
            store.rules(),
        )
        store.saveRule(store.rules().single().copy(enabled = false))
        assertEquals(false, store.rules().single().enabled)
        store.deleteRule(id)
        assertEquals(emptyList<ReminderRule>(), store.rules())
    }

    @Test
    fun ignoreCanBeUndoneExactly() {
        store.insert(listOf(CapturedMessage("com.whatsapp", "Léa", "Léa", "ancien", 1000)))
        store.markHandled("com.whatsapp", "Léa")
        store.insert(listOf(CapturedMessage("com.whatsapp", "Léa", "Léa", "nouveau", 5000)))
        store.saveReminderState("com.whatsapp", "Léa", ReminderState(lastRemindedAt = 4242))

        val undo = store.ignore("com.whatsapp", "Léa")
        assertEquals(emptyList<Conversation>(), store.pendingConversations())

        store.undo(undo)
        // Only what "ignore" took away comes back, with its reminder state.
        assertEquals(listOf("nouveau"), store.pendingMessages("com.whatsapp", "Léa").map { it.text })
        assertEquals(ReminderState(lastRemindedAt = 4242), store.reminderState("com.whatsapp", "Léa"))
    }

    @Test
    fun unfollowingAnAccountHidesItsMessagesUntilUndone() {
        val ig = "com.instagram.android"
        assertEquals(true, store.noteAccount(ig, "perso"))
        assertEquals(true, store.noteAccount(ig, "boutique_pro"))
        store.insert(
            listOf(
                CapturedMessage(ig, "julie_92", "julie_92", "coucou", 1000, account = "perso"),
                CapturedMessage(ig, "client_1", "client_1", "dispo ?", 2000, account = "boutique_pro"),
            )
        )

        val at = store.disableAccount(ig, "boutique_pro")
        assertEquals(false, store.noteAccount(ig, "boutique_pro"))
        assertEquals(listOf("julie_92"), store.pendingConversations().map { it.conversation })
        assertEquals(listOf("perso" to true, "boutique_pro" to false), store.accounts().map { it.name to it.enabled })

        store.enableAccount(ig, "boutique_pro", undoDisabledAt = at)
        assertEquals(listOf("julie_92", "client_1"), store.pendingConversations().map { it.conversation })
    }
}
