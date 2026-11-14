package com.ola.espcamdrive

import java.util.Locale

/** Dostępne odstępy między zdjęciami w trybie cyklicznym (sekundy). */
val intervalOptions = listOf(0.5, 1.0, 2.0, 3.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0, 1800.0, 3600.0)

fun formatInterval(s: Double): String {
    if (s >= 60 && s % 60 == 0.0) return "${(s / 60).toInt()} min"
    if (s % 1 == 0.0) return "${s.toInt()} s"
    return String.format(Locale.US, "%.1f s", s).replace(".", ",")
}

fun formatDuration(t: Double): String {
    val s = maxOf(0, t.toInt())
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}
