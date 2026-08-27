package app.anikku.macos.player

/**
 * Formats seconds into MM:SS or HH:MM:SS.
 */
fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Formats seconds as a Double into MM:SS or HH:MM:SS.
 */
fun formatDuration(totalSeconds: Double): String {
    return formatDuration(totalSeconds.toLong())
}

/**
 * Formats milliseconds into a time string.
 */
fun prettyTime(millis: Long): String {
    val totalSeconds = millis / 1000
    return formatDuration(totalSeconds)
}
