package mihon.domain.animemigration.models

enum class AnimeMigrationFlag(val flag: Int) {
    EPISODE(0b00001),
    CATEGORY(0b00010),
    TRACK(0b00100),
    CUSTOM_BACKGROUND(0b01000),
    CUSTOM_COVER(0b10000),
    REMOVE_DOWNLOAD(0b100000),
    EXTRA(0b1000000),
    ;

    companion object {
        fun fromBit(bit: Int): Set<AnimeMigrationFlag> {
            return buildSet {
                entries.forEach { entry ->
                    if (bit and entry.flag != 0) add(entry)
                }
            }
        }

        fun toBit(flags: Set<AnimeMigrationFlag>): Int {
            return flags.map { it.flag }
                .reduceOrNull { acc, mask -> acc or mask }
                ?: 0
        }
    }
}
