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
}
