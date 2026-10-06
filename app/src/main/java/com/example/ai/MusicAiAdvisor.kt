package com.example.ai

import com.example.data.model.Song
import java.util.Calendar

data class StructuredAiFilter(
    val artists: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val targetDurationMinutes: Int? = null,
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val maxPlayCount: Int? = null,
    val minPlayCount: Int? = null,
    val onlyFavorites: Boolean = false,
    val moodTag: String? = null, // "chill", "workout", "study", "party", "sleep"
    val keywords: List<String> = emptyList()
)

data class AiPlaylistResult(
    val query: String,
    val matchedSongs: List<Song>,
    val explanation: String,
    val filterSummary: String,
    val hasSufficientMatches: Boolean,
    val alternativeSuggestions: List<String> = emptyList()
)

data class LocalRecommendationGroup(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

object MusicAiAdvisor {

    /**
     * Natural Language Request -> Structured Filters -> Local DB Query -> Ranking
     */
    fun processNaturalLanguageRequest(
        prompt: String,
        library: List<Song>,
        relaxLevel: Int = 0 // 0 = strict, 1 = relax filters, 2 = similar/full library
    ): AiPlaylistResult {
        if (library.isEmpty() || prompt.isBlank()) {
            return AiPlaylistResult(
                query = prompt,
                matchedSongs = emptyList(),
                explanation = "Your local music library is empty. Please add audio files to your device.",
                filterSummary = "No local tracks",
                hasSufficientMatches = false,
                alternativeSuggestions = listOf("Scan device for music files")
            )
        }

        val filter = parsePromptToFilter(prompt)
        val matched = applyFilterToLibrary(filter, library, relaxLevel)

        val hasEnough = matched.size >= 3

        val explanation = when {
            hasEnough -> "Found ${matched.size} matching local track${if (matched.size == 1) "" else "s"} based on your request."
            matched.isNotEmpty() -> "Found only ${matched.size} track${if (matched.size == 1) "" else "s"}. Your library doesn't have enough songs matching this exactly."
            else -> "Your library doesn't have enough songs matching \"$prompt\"."
        }

        val alternatives = if (!hasEnough) {
            listOf(
                "Relax the filters",
                "Include similar artists in library",
                "Use your full local library"
            )
        } else emptyList()

        val summaryParts = mutableListOf<String>()
        if (filter.artists.isNotEmpty()) summaryParts.add("Artist: ${filter.artists.joinToString()}")
        if (filter.genres.isNotEmpty()) summaryParts.add("Genre: ${filter.genres.joinToString()}")
        if (filter.moodTag != null) summaryParts.add("Mood: ${filter.moodTag}")
        if (filter.minYear != null || filter.maxYear != null) summaryParts.add("Year: ${filter.minYear ?: "Any"}-${filter.maxYear ?: "Any"}")
        if (filter.targetDurationMinutes != null) summaryParts.add("Target: ~${filter.targetDurationMinutes} min")
        if (filter.onlyFavorites) summaryParts.add("Favorites only")
        if (filter.maxPlayCount != null) summaryParts.add("Unplayed/Low plays")

        val summaryStr = if (summaryParts.isNotEmpty()) summaryParts.joinToString(" • ") else "Local Library Analysis"

        return AiPlaylistResult(
            query = prompt,
            matchedSongs = matched,
            explanation = explanation,
            filterSummary = summaryStr,
            hasSufficientMatches = hasEnough,
            alternativeSuggestions = alternatives
        )
    }

    private fun parsePromptToFilter(prompt: String): StructuredAiFilter {
        val lower = prompt.lowercase()
        val tokens = lower.split(" ", ",", "-", "_", "'", "\"").filter { it.isNotBlank() }

        var targetDurationMins: Int? = null
        val durationRegex = Regex("(\\d+)\\s*(min|minute|minutes|hr|hour)")
        durationRegex.find(lower)?.let { match ->
            val num = match.groupValues[1].toIntOrNull()
            val unit = match.groupValues[2]
            if (num != null) {
                targetDurationMins = if (unit.startsWith("hr")) num * 60 else num
            }
        }

        var minYear: Int? = null
        var maxYear: Int? = null
        val yearRegex = Regex("\\b(19\\d{2}|20\\d{2})\\b")
        val yearMatches = yearRegex.findAll(lower).mapNotNull { it.value.toIntOrNull() }.toList()
        if (yearMatches.isNotEmpty()) {
            minYear = yearMatches.minOrNull()
            maxYear = yearMatches.maxOrNull()
        }

        val onlyFavs = lower.contains("favorite") || lower.contains("fav") || lower.contains("loved") || lower.contains("starred")

        var maxPlays: Int? = null
        if (lower.contains("barely") || lower.contains("never listened") || lower.contains("unheard") || lower.contains("rarely")) {
            maxPlays = 2
        }

        var moodTag: String? = null
        when {
            lower.contains("gym") || lower.contains("workout") || lower.contains("run") || lower.contains("pump") || lower.contains("high energy") -> moodTag = "workout"
            lower.contains("study") || lower.contains("focus") || lower.contains("work") || lower.contains("read") -> moodTag = "study"
            lower.contains("chill") || lower.contains("relax") || lower.contains("calm") || lower.contains("late night") -> moodTag = "chill"
            lower.contains("sleep") || lower.contains("bedtime") -> moodTag = "sleep"
            lower.contains("party") || lower.contains("dance") || lower.contains("upbeat") -> moodTag = "party"
        }

        // Extract potential artist or genre keywords
        val ignoredWords = setOf("make", "me", "a", "playlist", "songs", "song", "track", "tracks", "for", "with", "the", "my", "of", "and", "in")
        val keywords = tokens.filter { it.length > 2 && !ignoredWords.contains(it) }

        return StructuredAiFilter(
            targetDurationMinutes = targetDurationMins,
            minYear = minYear,
            maxYear = maxYear,
            maxPlayCount = maxPlays,
            onlyFavorites = onlyFavs,
            moodTag = moodTag,
            keywords = keywords
        )
    }

    private fun applyFilterToLibrary(
        filter: StructuredAiFilter,
        library: List<Song>,
        relaxLevel: Int
    ): List<Song> {
        var candidates = library.toList()

        if (relaxLevel == 2) {
            // Level 2: Return full library or shuffled subset
            return candidates.shuffled()
        }

        if (filter.onlyFavorites && relaxLevel == 0) {
            candidates = candidates.filter { it.isFavorite }
        }

        if (filter.maxPlayCount != null && relaxLevel == 0) {
            candidates = candidates.filter { it.playCount <= filter.maxPlayCount }
        }

        if (filter.minYear != null && filter.maxYear != null && relaxLevel == 0) {
            candidates = candidates.filter { it.year in filter.minYear..filter.maxYear }
        }

        // Match keywords against artist, title, genre, album, customTags
        val scored = candidates.map { song ->
            var score = 0
            val titleL = song.title.lowercase()
            val artistL = song.artist.lowercase()
            val genreL = song.genre.lowercase()
            val albumL = song.album.lowercase()
            val tagsL = song.customTags.lowercase()

            for (kw in filter.keywords) {
                if (artistL.contains(kw)) score += 5
                if (titleL.contains(kw)) score += 4
                if (genreL.contains(kw)) score += 3
                if (albumL.contains(kw)) score += 2
                if (tagsL.contains(kw)) score += 3
            }

            filter.moodTag?.let { mood ->
                when (mood) {
                    "workout" -> if (genreL.contains("rock") || genreL.contains("pop") || genreL.contains("electronic") || genreL.contains("hip") || tagsL.contains("workout")) score += 4
                    "chill" -> if (genreL.contains("lo-fi") || genreL.contains("ambient") || genreL.contains("acoustic") || genreL.contains("pop") || tagsL.contains("chill")) score += 4
                    "study" -> if (genreL.contains("classical") || genreL.contains("instrumental") || genreL.contains("lo-fi") || tagsL.contains("study")) score += 4
                    "sleep" -> if (genreL.contains("ambient") || genreL.contains("sleep") || song.durationMs < 300000) score += 3
                    "party" -> if (genreL.contains("dance") || genreL.contains("pop") || genreL.contains("electronic")) score += 4
                }
            }

            if (song.isFavorite) score += 2

            song to score
        }

        val filteredScored = if (relaxLevel == 1) {
            // Relaxed matching: include score >= 1 or top duration candidates
            scored.filter { it.second >= 1 }
        } else {
            scored.filter { it.second > 0 }
        }

        var result = filteredScored.sortedByDescending { it.second }.map { it.first }

        // If target duration specified, trim or pad result to match target time
        filter.targetDurationMinutes?.let { targetMins ->
            val targetMs = targetMins * 60 * 1000L
            var currentMs = 0L
            val durationList = mutableListOf<Song>()
            for (song in result) {
                durationList.add(song)
                currentMs += song.durationMs
                if (currentMs >= targetMs) break
            }
            if (durationList.isNotEmpty()) {
                result = durationList
            }
        }

        return result
    }

    /**
     * Generates intelligent, local contextual recommendations based on real listening data.
     */
    fun generateLocalRecommendations(
        allSongs: List<Song>,
        recentlyPlayed: List<Song>,
        mostPlayed: List<Song>,
        favorites: List<Song>
    ): List<LocalRecommendationGroup> {
        if (allSongs.isEmpty()) return emptyList()

        val groups = mutableListOf<LocalRecommendationGroup>()

        // 1. Most played artist insight
        val topArtist = mostPlayed.groupBy { it.artist }
            .filterKeys { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            .maxByOrNull { it.value.size }

        if (topArtist != null) {
            val artistName = topArtist.key
            val artistSongs = allSongs.filter { it.artist.equals(artistName, ignoreCase = true) }
            if (artistSongs.isNotEmpty()) {
                groups.add(
                    LocalRecommendationGroup(
                        title = "Because you love $artistName",
                        subtitle = "Tracks from $artistName in your local collection",
                        songs = artistSongs.take(8)
                    )
                )
            }
        }

        // 2. Rediscover Unheard / Low play count local tracks
        val unplayed = allSongs.filter { it.playCount == 0 }.shuffled()
        if (unplayed.isNotEmpty()) {
            groups.add(
                LocalRecommendationGroup(
                    title = "Rediscover Unheard Music",
                    subtitle = "Songs in your storage that you haven't played yet",
                    songs = unplayed.take(8)
                )
            )
        }

        // 3. Favorites mix
        if (favorites.isNotEmpty()) {
            groups.add(
                LocalRecommendationGroup(
                    title = "Your High-Rotation Favorites",
                    subtitle = "Handpicked from your favorited tracks",
                    songs = favorites.take(8)
                )
            )
        }

        // 4. Recently Added
        val recentAdded = allSongs.sortedByDescending { it.dateAdded }.take(8)
        if (recentAdded.isNotEmpty()) {
            groups.add(
                LocalRecommendationGroup(
                    title = "Freshly Imported Tracks",
                    subtitle = "Recently scanned and added to your device",
                    songs = recentAdded
                )
            )
        }

        return groups
    }
}
