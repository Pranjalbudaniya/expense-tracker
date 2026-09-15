package com.example.expensetracker.core.preferences

/**
 * Supported text sizing / font scale preferences for the application.
 *
 * @property displayName User-friendly label for the option.
 * @property scaleFactor Multiplier applied to typography font sizes and line heights.
 */
enum class FontSizePreference(
    val displayName: String,
    val scaleFactor: Float
) {
    SMALL("Small", 0.85f),
    DEFAULT("Medium", 1.0f),
    LARGE("Large", 1.15f),
    EXTRA_LARGE("Extra Large", 1.3f);

    companion object {
        val DEFAULT_SIZE = DEFAULT

        fun fromString(value: String?): FontSizePreference {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT_SIZE
        }
    }
}
