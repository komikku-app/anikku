package app.anikku.macos.ui.screens.models

// Test-only mock data (moved out of the main source set — it was shipped
// in the binary but only ever used by UI tests).

/**
 * Provides mock data for previewing screens.
 * Remove this when domain layer is connected.
 */
object MockData {

    val sampleAnime: List<AnimeModel> = listOf(
        AnimeModel(1L, "Attack on Titan", status = 2, author = "Hajime Isayama", genre = listOf("Action", "Drama", "Fantasy"), description = "Years ago, humanity was driven to the brink of extinction by the sudden appearance of giant humanoid creatures known as Titans. Now, the last of humanity lives within three great walled cities. Eren Jaeger and his friends Mikasa and Armin live on the edge of Wall Maria, dreaming of exploring the outside world.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/10/47347.jpg", url = "https://myanimelist.net/anime/16498/Attack_on_Titan"),
        AnimeModel(2L, "Jujutsu Kaisen", status = 1, author = "Gege Akutami", genre = listOf("Action", "Supernatural"), description = "Yuuji Itadori is a boy with tremendous physical strength who lives a completely ordinary high school life. One day, to save a classmate who was attacked by curses, he eats the finger of Ryomen Sukuna, taking the curse into his own soul.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/1171/109222.jpg", url = "https://myanimelist.net/anime/40748/Jujutsu_Kaisen"),
        AnimeModel(3L, "Demon Slayer", status = 2, author = "Koyoharu Gotouge", genre = listOf("Action", "Historical", "Supernatural"), description = "It is the Taisho Period in Japan. Tanjiro, a kindhearted boy who sells charcoal for a living, finds his family slaughtered by a demon. To make matters worse, his younger sister Nezuko has been turned into a demon.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/1286/99889.jpg", url = "https://myanimelist.net/anime/38000/Kimetsu_no_Yaiba"),
        AnimeModel(4L, "One Piece", status = 1, author = "Eiichiro Oda", genre = listOf("Action", "Adventure", "Fantasy"), description = "Monkey D. Luffy sets out on an adventure in search of the legendary treasure known as One Piece, aiming to become the King of the Pirates.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/6/73245.jpg", url = "https://myanimelist.net/anime/21/One_Piece"),
        AnimeModel(5L, "Chainsaw Man", status = 2, author = "Tatsuki Fujimoto", genre = listOf("Action", "Comedy", "Horror"), description = "Denji is a teenage boy living with a chainsaw devil named Pochita. After being betrayed and killed, Denji merges with Pochita and becomes Chainsaw Man.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/1806/126216.jpg", url = "https://myanimelist.net/anime/44511/Chainsaw_Man"),
        AnimeModel(6L, "My Hero Academia", status = 1, author = "Kohei Horikoshi", genre = listOf("Action", "Comedy", "School"), description = "In a world where 80% of the population has superpowers, a quirkless boy named Izuku Midoriya dreams of becoming a hero.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/10/78745.jpg", url = "https://myanimelist.net/anime/31964/Boku_no_Hero_Academia"),
        AnimeModel(7L, "Full Metal Alchemist", status = 2, genre = listOf("Action", "Adventure", "Drama"), description = "Two brothers search for the Philosopher's Stone after an attempt to revive their deceased mother goes wrong.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/1208/94745.jpg", url = "https://myanimelist.net/anime/5114/Fullmetal_Alchemist_Brotherhood"),
        AnimeModel(8L, "Steins;Gate", status = 2, genre = listOf("Sci-Fi", "Thriller"), description = "A self-proclaimed mad scientist discovers that his microwave can send emails to the past and gets entangled in a conspiracy.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/1935/127974.jpg", url = "https://myanimelist.net/anime/9253/Steins_Gate"),
        AnimeModel(9L, "Cowboy Bebop", status = 2, genre = listOf("Action", "Sci-Fi", "Space"), description = "Spike Spiegel and his rag-tag crew of bounty hunters travel the solar system in search of their next big score.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/4/19644.jpg", url = "https://myanimelist.net/anime/1/Cowboy_Bebop"),
        AnimeModel(10L, "Death Note", status = 2, genre = listOf("Mystery", "Supernatural", "Thriller"), description = "A high school student discovers a supernatural notebook that allows him to kill anyone whose name he writes in it.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/9/9453.jpg", url = "https://myanimelist.net/anime/1535/Death_Note"),
        AnimeModel(11L, "Spy x Family", status = 1, author = "Tatsuya Endo", genre = listOf("Action", "Comedy", "Slice of Life"), description = "A spy must build a fake family for a mission, but ends up with a telepathic daughter and an assassin wife.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/1441/122795.jpg", url = "https://myanimelist.net/anime/50265/Spy_x_Family"),
        AnimeModel(12L, "Vinland Saga", status = 1, genre = listOf("Action", "Adventure", "Historical"), description = "A young Viking boy seeks revenge against the man who killed his father, but eventually questions the value of violence.", thumbnailUrl = "https://cdn.myanimelist.net/images/anime/1500/103005.jpg", url = "https://myanimelist.net/anime/37521/Vinland_Saga"),
    )

    val sampleEpisodes: List<EpisodeModel> = listOf(
        EpisodeModel(1L, 1L, "To You, 2,000 Years Later", 1.0, seen = true),
        EpisodeModel(2L, 1L, "The Day the World Fell", 2.0, seen = true),
        EpisodeModel(3L, 1L, "A Dim Light Amid Despair", 3.0, seen = false),
        EpisodeModel(4L, 1L, "Declaration of War", 4.0, seen = false),
        EpisodeModel(5L, 1L, "From You, 2,000 Years Ago", 5.0, seen = false),
        EpisodeModel(6L, 1L, "The War Hammer Titan", 6.0, seen = false),
        EpisodeModel(7L, 1L, "Assault", 7.0, seen = false),
        EpisodeModel(8L, 1L, "Two Brothers", 8.0, seen = false),
    )

    val sampleHistory: List<HistoryEntryModel> = listOf(
        HistoryEntryModel(1L, 1L, "Attack on Titan", 3L, 3.0, seenAt = System.currentTimeMillis() - 3600000),
        HistoryEntryModel(2L, 3L, "Demon Slayer", 5L, 5.0, seenAt = System.currentTimeMillis() - 7200000),
        HistoryEntryModel(3L, 2L, "Jujutsu Kaisen", 4L, 4.0, seenAt = System.currentTimeMillis() - 10800000),
        HistoryEntryModel(4L, 5L, "Chainsaw Man", 2L, 2.0, seenAt = System.currentTimeMillis() - 14400000),
        HistoryEntryModel(5L, 4L, "One Piece", 1072L, 1072.0, seenAt = System.currentTimeMillis() - 86400000),
    )

    val sampleUpdates: List<UpdateModel> = listOf(
        UpdateModel(2L, "Jujutsu Kaisen", 22L, "Episode 22 - The Origin of Obedience (Part 2)", seen = false),
        UpdateModel(6L, "My Hero Academia", 139L, "Episode 139 - Deku vs. Class A", seen = false),
        UpdateModel(11L, "Spy x Family", 38L, "Episode 38 - Enjoy the Resort to the Fullest", seen = false),
        UpdateModel(4L, "One Piece", 1092L, "Episode 1092 - A Night to Remember", seen = false),
        UpdateModel(12L, "Vinland Saga", 25L, "Episode 25 - The Birth of a New King", seen = true),
        UpdateModel(1L, "Attack on Titan", 88L, "Episode 88 - The Dawn of Humanity", seen = true),
    )
}
