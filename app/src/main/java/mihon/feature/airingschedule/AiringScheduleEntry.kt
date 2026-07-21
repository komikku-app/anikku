package mihon.feature.airingschedule

data class AiringScheduleEntry(
    val scheduleId: Int,
    val airingAt: Long,
    val episode: Int,
    val mediaId: Int,
    val titleUserPreferred: String,
    val titleEnglish: String?,
    val titleRomaji: String?,
    val titleNative: String?,
    val coverImageUrl: String,
    val totalEpisodes: Int?,
    val averageScore: Int?,
    val format: String?,
    val status: String?,
    val isAdult: Boolean,
    val genres: List<String>,
) {
    fun displayTitle(language: SchedulePreferences.TitleLanguage): String = when (language) {
        SchedulePreferences.TitleLanguage.ENGLISH -> titleEnglish?.takeIf { it.isNotBlank() } ?: titleRomaji?.takeIf { it.isNotBlank() } ?: titleUserPreferred
        SchedulePreferences.TitleLanguage.ROMAJI -> titleRomaji?.takeIf { it.isNotBlank() } ?: titleUserPreferred
        SchedulePreferences.TitleLanguage.NATIVE -> titleNative?.takeIf { it.isNotBlank() } ?: titleUserPreferred
        SchedulePreferences.TitleLanguage.USER_PREFERRED -> titleUserPreferred
    }

    fun hasAired(): Boolean = airingAt <= System.currentTimeMillis() / 1000L
}
