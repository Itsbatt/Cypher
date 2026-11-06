package com.cypher.music.update

/**
 * Semantic version with prerelease precedence (SemVer 2.0).
 * Accepts tags such as "v1.0.2", "1.0", "1" and "1.0.3-beta.1".
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList()
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // A release always outranks its prereleases (1.0.0 > 1.0.0-rc.1).
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1

        for (i in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease[i]
            val right = other.prerelease[i]
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1 // numeric identifiers have lower precedence
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (prerelease.isNotEmpty()) append('-').append(prerelease.joinToString("."))
    }

    companion object {
        private val PATTERN = Regex(
            "^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$"
        )

        fun parse(raw: String?): AppVersion? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return null
            val match = PATTERN.matchEntire(value) ?: return null
            val parts = match.groupValues
            return AppVersion(
                major = parts[1].toIntOrNull() ?: return null,
                minor = parts[2].toIntOrNull() ?: 0,
                patch = parts[3].toIntOrNull() ?: 0,
                prerelease = parts[4]
                    .takeIf { it.isNotBlank() }
                    ?.split('.')
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            )
        }
    }
}
