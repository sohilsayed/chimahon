package chimahon.dictionary

private val simpleVersionRegex = Regex("""^(\d+\.)*\d+$""")

fun hasNewerRevision(current: String?, latest: String?): Boolean {
    if (current == null || latest == null) return false

    val cur = current.trim()
    val lat = latest.trim()
    if (cur == lat) return false

    // If neither is a dot-separated integer version, fall back to string comparison
    if (!simpleVersionRegex.matches(cur) || !simpleVersionRegex.matches(lat)) {
        return cur < lat
    }

    val currentParts = cur.split(".").map { it.toInt() }
    val latestParts = lat.split(".").map { it.toInt() }

    for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
        val c = currentParts.getOrNull(i)
        val l = latestParts.getOrNull(i)
        when {
            c == null && l != null -> return true
            c != null && l == null -> return false
            c != null && l != null -> {
                if (c != l) return c < l
            }
        }
    }
    return false
}
