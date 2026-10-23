package com.paulesthor.casper

data class CapturedMessage(
    val app: String,
    val conversation: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    /** The user's own account the message was sent to, for apps with several (Instagram). */
    val account: String? = null,
)
