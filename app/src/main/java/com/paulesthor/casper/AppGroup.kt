package com.paulesthor.casper

/** The kinds of conversations Casper follows, each covering one or more apps. */
enum class AppGroup(val label: String, val color: Int, val packages: Set<String>) {
    WHATSAPP("WhatsApp", 0xFF25D366.toInt(), setOf("com.whatsapp", "com.whatsapp.w4b")),
    INSTAGRAM("Instagram", 0xFFE1306C.toInt(), setOf("com.instagram.android")),
    MESSENGER("Messenger", 0xFF0A7CFF.toInt(), setOf("com.facebook.orca", "com.facebook.mlite")),
    SMS(
        "SMS", 0xFF4C8DF6.toInt(),
        setOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
    ),
    CALLS(
        "Appels", 0xFF34C759.toInt(),
        setOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.android.server.telecom"),
    );

    companion object {
        fun of(pkg: String): AppGroup? = entries.firstOrNull { pkg in it.packages }

        /** Fake messages posted with adb in debug builds show up as SMS. */
        fun orDefault(pkg: String): AppGroup = of(pkg) ?: SMS
    }
}
