package gd.app.musicplayer.core.designsystem.view

/** Port of `y7.c`: one index letter plus the adapter position it jumps to. */
data class LetterPosition(
    val letter: String,
    val position: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is LetterPosition && letter == other.letter
    }

    override fun hashCode(): Int = letter.hashCode()
}

/** Port of `y7.d`: letter extraction, index building and current-letter lookup. */
object LetterIndex {

    private const val OTHER = "#"
    private const val OTHER_TRAILING = "~"

    /**
     * `y7.d.d`: one entry per first letter in list order. Non A–Z names collapse into a single
     * bucket rendered as `#` (or `~` when the list is reversed and the bucket trails).
     */
    fun build(
        names: List<String?>,
        reversed: Boolean
    ): List<LetterPosition> {
        val letters = mutableListOf<LetterPosition>()
        val seen = hashSetOf<String>()

        var leadingOtherPosition = -1
        var trailingOtherPosition = -1

        names.forEachIndexed { index, name ->
            val letter = firstLetterOf(name)

            if (letter != OTHER) {
                if (seen.add(letter)) {
                    letters += LetterPosition(letter, index)
                }
                trailingOtherPosition = -1
            } else if (index == 0 && names.size > 1) {
                leadingOtherPosition = 0
            } else if (trailingOtherPosition == -1) {
                trailingOtherPosition = index
            }
        }

        if (leadingOtherPosition != -1) {
            letters.add(
                0,
                LetterPosition(if (reversed) OTHER else OTHER_TRAILING, leadingOtherPosition)
            )
        }

        if (trailingOtherPosition != -1) {
            val trailingLetter = if (reversed && letters.isNotEmpty()) OTHER_TRAILING else OTHER
            letters += LetterPosition(trailingLetter, trailingOtherPosition)
        }

        return letters
    }

    /** `y7.d.a`: the letter owning [position], i.e. the last entry at or before it. */
    fun letterAt(
        letters: List<LetterPosition>,
        position: Int,
        headerCount: Int = 0
    ): LetterPosition? {
        if (letters.isEmpty()) return null

        val dataPosition = position - headerCount
        var index = letters.count { it.position <= dataPosition }
        if (index > 0) index--

        return letters.getOrNull(index.coerceIn(0, letters.lastIndex))
    }

    private fun firstLetterOf(name: String?): String {
        val first = name?.firstOrNull() ?: return OTHER

        return when {
            first in 'A'..'Z' -> first.toString()
            first in 'a'..'z' -> (first - 32).toString()
            else -> OTHER
        }
    }
}
