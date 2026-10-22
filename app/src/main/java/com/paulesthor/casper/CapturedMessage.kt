package com.paulesthor.casper

data class CapturedMessage(
    val app: String,
    val conversation: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
)
