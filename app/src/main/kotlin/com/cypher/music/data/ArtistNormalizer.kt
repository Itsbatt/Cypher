package com.cypher.music.data

import java.util.Locale

/**
 * Normalizes artist names by stripping common YouTube/Streaming suffixes,
 * topic channel tags, VEVO appendages, and aliases so all variations of an artist
 * (e.g., "Milo J Music", "Milo J - Topic", "Milo J VEVO") unify under the canonical artist ("Milo J").
 */
object ArtistNormalizer {

    // Common channel & publisher suffixes in streaming / YouTube
    private val SUFFIX_PATTERNS = listOf(
        Regex("(?i)\\s*-\\s*Topic$"),
        Regex("(?i)\\s+Topic$"),
        Regex("(?i)\\s*-\\s*Canal\\s+Oficial$"),
        Regex("(?i)\\s*-\\s*Official\\s+Channel$"),
        Regex("(?i)\\s+Canal\\s+Oficial$"),
        Regex("(?i)\\s+Official\\s+Channel$"),
        Regex("(?i)\\s+Canal\\s+de\\s+M[uú]sica$"),
        Regex("(?i)\\s+Official$"),
        Regex("(?i)\\s+Oficial$"),
        Regex("(?i)\\s+VEVO$"),
        Regex("(?i)VEVO$"),
        Regex("(?i)\\s+Music$"),
        Regex("(?i)\\s+Records$"),
        Regex("(?i)\\s+Entertainment$"),
        Regex("(?i)\\s+TV$"),
        Regex("(?i)\\s+Producciones$"),
        Regex("(?i)\\s*-\\s*YouTube$"),
        Regex("(?i)\\s*-\\s*Audio$")
    )

    // Well-known canonical artist aliases for high-fidelity grouping
    private val KNOWN_ALIASES = mapOf(
        "miloj" to "Milo J",
        "milo j" to "Milo J",
        "the weeknd" to "The Weeknd",
        "theweeknd" to "The Weeknd",
        "bad bunny" to "Bad Bunny",
        "bizarrap" to "Bizarrap",
        "bzrp" to "Bizarrap",
        "duki" to "Duki",
        "quevedo" to "Quevedo",
        "feid" to "Feid",
        "rosalia" to "Rosalía",
        "c. tangana" to "C. Tangana",
        "c tangana" to "C. Tangana",
        "morad" to "Morad",
        "trueno" to "Trueno",
        "wos" to "WOS",
        "kanye west" to "Kanye West",
        "travis scott" to "Travis Scott",
        "drake" to "Drake",
        "taylor swift" to "Taylor Swift",
        "billie eilish" to "Billie Eilish",
        "erik satie" to "Erik Satie",
        "ludwig van beethoven" to "Ludwig van Beethoven",
        "frédéric chopin" to "Frédéric Chopin",
        "frederic chopin" to "Frédéric Chopin"
    )

    /**
     * Normalizes a raw artist string into a clean, canonical artist name.
     */
    fun normalize(rawArtist: String?): String {
        if (rawArtist.isNullOrBlank()) return "Artista Desconocido"

        var cleaned = rawArtist.trim()

        // Strip known suffixes iteratively
        var modified = true
        var passes = 0
        while (modified && passes < 3) {
            modified = false
            passes++
            for (pattern in SUFFIX_PATTERNS) {
                val next = cleaned.replace(pattern, "").trim()
                // Only accept stripping if we don't end up with an empty name
                if (next.isNotBlank() && next != cleaned) {
                    cleaned = next
                    modified = true
                }
            }
        }

        // Clean trailing punctuation or separators (e.g. "Milo J -", "Milo J |")
        cleaned = cleaned.trimEnd('-', '|', '/', ':', ',', '.', ';', '•').trim()

        // Check canonical alias map
        val lookupKey = cleaned.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9áéíóúñ\\s]"), "")
            .trim()
        val canonical = KNOWN_ALIASES[lookupKey]
        if (canonical != null) {
            return canonical
        }

        // If artist string looks like "NameVEVO" (e.g. "EminemVEVO")
        if (cleaned.endsWith("VEVO", ignoreCase = true) && cleaned.length > 4) {
            cleaned = cleaned.substring(0, cleaned.length - 4).trim()
        }

        return cleaned.ifEmpty { rawArtist.trim() }
    }

    /**
     * Checks if two artist strings refer to the same unified artist.
     */
    fun areSameArtist(artistA: String?, artistB: String?): Boolean {
        val normA = normalize(artistA).lowercase(Locale.ROOT)
        val normB = normalize(artistB).lowercase(Locale.ROOT)
        return normA == normB
    }
}
